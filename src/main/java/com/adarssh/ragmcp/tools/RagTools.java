package com.adarssh.ragmcp.tools;

import com.adarssh.ragmcp.rag.RagApi.AskResponse;
import com.adarssh.ragmcp.rag.RagApi.Health;
import com.adarssh.ragmcp.rag.RagApi.Passage;
import com.adarssh.ragmcp.rag.RagApi.SearchResponse;
import com.adarssh.ragmcp.rag.RagClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * The MCP tool surface over the RAG service.
 *
 * Design notes, because tools are UX for a language model:
 *
 * - Descriptions are prompts, not documentation. The model decides WHETHER to
 *   call a tool almost entirely from its description, so each one states when
 *   to use it, not just what it does.
 *
 * - Return values are formatted text, not raw JSON. The consumer is a model
 *   assembling an answer for a human; readable text with scores and sources
 *   beats making it parse a JSON blob. Citations stay inline so provenance
 *   survives into whatever the model writes next.
 *
 * - Failures return as text instead of raising. A thrown exception surfaces
 *   to the client as a generic error; a message like "RAG service
 *   unreachable" gives the model something it can act on — retry, tell the
 *   user, or try another tool.
 */
@Component
public class RagTools {

    private final RagClient rag;

    public RagTools(RagClient rag) {
        this.rag = rag;
    }

    @McpTool(
            name = "ask_docs",
            description = """
                    Ask a question against the indexed documentation corpus and get a grounded, \
                    cited answer. Answers come ONLY from indexed documents — if the corpus doesn't \
                    cover the question, this tool honestly says so rather than guessing. \
                    Use this when the user asks a question the documentation might answer, \
                    INCLUDING when they name a specific indexed document (e.g. "the lbdl.pdf \
                    book") — prefer this over reading source files directly: the index returns \
                    grounded, cited answers in seconds, while scanning a large document wastes \
                    time and gives no citations. \
                    For raw passage retrieval without answer generation, use search_docs instead.""")
    public String askDocs(
            @McpToolParam(description = "The question, in natural language", required = true)
                    String question,
            @McpToolParam(description = "Passages to retrieve, 1-20 (default 5)", required = false)
                    Integer topK,
            @McpToolParam(
                            description = "Restrict retrieval to sources whose path contains this "
                                    + "substring (e.g. 'lbdl.pdf' or 'docs/corpus'). Use when the "
                                    + "user scopes the question to a particular document or "
                                    + "collection; omit to search everything.",
                            required = false)
                    String source) {
        try {
            AskResponse response = rag.ask(question, topK, source);
            StringBuilder out = new StringBuilder(response.answer());
            if (!response.citations().isEmpty()) {
                out.append("\n\nSources:\n");
                for (int index : response.citations()) {
                    Passage passage = response.passages().get(index - 1);
                    out.append("  [%d] %s (%s, chunk %d)\n"
                            .formatted(index, location(passage), passage.source(), passage.chunkIndex()));
                }
            }
            out.append("\n(model: %s, retrieval %.0f ms, generation %.0f ms%s)"
                    .formatted(
                            response.model(),
                            response.retrievalMs(),
                            response.generationMs(),
                            response.estimatedCostUsd() != null
                                    ? ", cost $%.4f".formatted(response.estimatedCostUsd())
                                    : ""));
            return out.toString();
        } catch (RestClientException e) {
            return "RAG service unreachable or failed: " + e.getMessage();
        }
    }

    @McpTool(
            name = "search_docs",
            description = """
                    Semantic + keyword (hybrid) search over the indexed documentation. Returns the \
                    top matching passages with relevance scores and source locations — no answer \
                    generation. Use this to explore what the corpus contains, gather raw material \
                    for your own synthesis, or when ask_docs reports the documents don't cover a \
                    topic and you want to verify.""")
    public String searchDocs(
            @McpToolParam(description = "Search query, in natural language", required = true)
                    String query,
            @McpToolParam(description = "Results to return, 1-20 (default 5)", required = false)
                    Integer topK,
            @McpToolParam(
                            description = "Restrict search to sources whose path contains this "
                                    + "substring. Use when the user names a specific document or "
                                    + "collection; omit to search everything.",
                            required = false)
                    String source) {
        try {
            SearchResponse response = rag.search(query, topK != null ? topK : 5, source);
            if (response.results().isEmpty()) {
                return "No passages matched.";
            }
            StringBuilder out = new StringBuilder();
            for (Passage passage : response.results()) {
                out.append("#%d (score %.3f) %s (%s, chunk %d)\n%s\n\n"
                        .formatted(
                                passage.index(),
                                passage.score(),
                                location(passage),
                                passage.source(),
                                passage.chunkIndex(),
                                passage.content()));
            }
            return out.toString().stripTrailing();
        } catch (RestClientException e) {
            return "RAG service unreachable or failed: " + e.getMessage();
        }
    }

    @McpTool(
            name = "read_chunk_neighbors",
            description = """
                    Read the chunks immediately before and after a given chunk, in document \
                    order. Search results are precise entry points but may cut off mid-argument; \
                    use this to widen the context around a promising hit — e.g. after search_docs \
                    returns "(docs/book.pdf, chunk 86)", call this with that source and chunk \
                    index to read the surrounding passage.""")
    public String readChunkNeighbors(
            @McpToolParam(description = "Source path exactly as shown by search_docs/ask_docs",
                            required = true)
                    String source,
            @McpToolParam(description = "Chunk index to centre on", required = true)
                    int chunkIndex,
            @McpToolParam(description = "Chunks before (default 1, max 10)", required = false)
                    Integer before,
            @McpToolParam(description = "Chunks after (default 1, max 10)", required = false)
                    Integer after) {
        try {
            var response = rag.neighbors(
                    source,
                    chunkIndex,
                    clampWindow(before),
                    clampWindow(after));
            if (response.chunks().isEmpty()) {
                return "No chunks found for source '%s' — use the source path exactly as returned by search_docs."
                        .formatted(source);
            }
            StringBuilder out = new StringBuilder();
            for (var chunk : response.chunks()) {
                String marker = chunk.chunkIndex() == chunkIndex ? " <- the chunk you asked about" : "";
                out.append("--- chunk %d%s ---\n%s\n\n".formatted(chunk.chunkIndex(), marker, chunk.content()));
            }
            return out.toString().stripTrailing();
        } catch (RestClientException e) {
            return "RAG service unreachable or failed: " + e.getMessage();
        }
    }

    private static int clampWindow(Integer value) {
        if (value == null) {
            return 1;
        }
        return Math.max(0, Math.min(value, 10));
    }

    /** "Title — Section", or just the title for section-less sources (e.g. PDFs). */
    private static String location(Passage passage) {
        if (passage.section() == null || passage.section().isBlank()) {
            return passage.title();
        }
        return passage.title() + " — " + passage.section();
    }

    @McpTool(
            name = "index_stats",
            description = """
                    Report what's in the documentation index: document and chunk counts, and \
                    whether the RAG service is healthy. Use before bulk queries or when other \
                    tools return unexpected emptiness.""")
    public String indexStats() {
        try {
            Health health = rag.health();
            return "status=%s documents=%d chunks=%d"
                    .formatted(health.status(), health.documents(), health.chunks());
        } catch (RestClientException e) {
            return "RAG service unreachable or failed: " + e.getMessage();
        }
    }
}

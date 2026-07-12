package com.adarssh.ragmcp.tools;

import com.adarssh.ragmcp.rag.RagApi.Document;
import com.adarssh.ragmcp.rag.RagClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * MCP resources: application-readable context, as opposed to tools (actions
 * the model invokes). A client can attach this resource to a conversation so
 * the model knows what the corpus contains before it ever searches.
 */
@Component
public class RagResources {

    private final RagClient rag;

    public RagResources(RagClient rag) {
        this.rag = rag;
    }

    @McpResource(
            uri = "rag://documents",
            name = "indexed_documents",
            description = "The list of documents currently in the RAG index: source path "
                    + "(usable as the `source` filter of ask_docs/search_docs), title, and "
                    + "chunk count.",
            mimeType = "text/plain")
    public String indexedDocuments() {
        try {
            var documents = rag.documents().documents();
            if (documents.isEmpty()) {
                return "The index is empty — no documents have been ingested.";
            }
            StringBuilder out = new StringBuilder("Indexed documents:\n");
            for (Document doc : documents) {
                out.append("- %s — \"%s\" (%d chunks)\n"
                        .formatted(doc.source(), doc.title(), doc.chunks()));
            }
            return out.toString().stripTrailing();
        } catch (RestClientException e) {
            return "RAG service unreachable: document list unavailable.";
        }
    }
}

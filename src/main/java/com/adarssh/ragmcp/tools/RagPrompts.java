package com.adarssh.ragmcp.tools;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * MCP prompts: user-invoked templates (the third protocol primitive, next to
 * tools and resources). A client surfaces these as slash-command-like
 * shortcuts; invoking one injects a well-formed instruction that drives the
 * tools correctly instead of leaving tool strategy to chance.
 */
@Component
public class RagPrompts {

    @McpPrompt(
            name = "research_the_docs",
            description = "Research a topic thoroughly in the indexed documentation: search, "
                    + "widen context around the best hits, and produce a cited summary.")
    public String researchTheDocs(
            @McpArg(name = "topic", description = "The topic or question to research", required = true)
                    String topic,
            @McpArg(name = "source", description = "Optional document/collection to restrict to "
                    + "(a source path substring, e.g. 'lbdl.pdf')", required = false)
                    String source) {
        String scope = (source == null || source.isBlank())
                ? "the whole index"
                : "only sources matching '" + source + "' (pass it as the `source` argument)";
        return """
                Research the following topic using the rag-docs tools, searching %s.

                Topic: %s

                Method:
                1. Call search_docs with 2-3 differently-phrased queries for the topic.
                2. For the most promising hits, call read_chunk_neighbors with the \
                (source, chunk) address printed in the results to read the surrounding \
                context.
                3. Synthesize a summary that cites sources as (source, chunk N). Only \
                state what the passages support; say so explicitly if the corpus does \
                not cover part of the topic.
                """.formatted(scope, topic);
    }
}

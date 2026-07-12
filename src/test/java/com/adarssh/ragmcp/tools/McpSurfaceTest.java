package com.adarssh.ragmcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/**
 * Guards the MCP surface itself: a typo in a tool name, or an annotation lost
 * in a refactor or Spring AI upgrade, would otherwise pass every other test
 * and only fail when a client calls tools/list.
 */
class McpSurfaceTest {

    @Test
    void theFourToolsAreDeclaredWithTheirContractNames() {
        var toolNames = Arrays.stream(RagTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .sorted()
                .toList();

        assertThat(toolNames)
                .containsExactly("ask_docs", "index_stats", "read_chunk_neighbors", "search_docs");
    }

    @Test
    void everyToolDescriptionSaysWhenToUseIt() {
        Arrays.stream(RagTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .forEach(m -> assertThat(m.getAnnotation(McpTool.class).description())
                        .as("description of %s", m.getName())
                        .containsIgnoringCase("use"));
    }
}

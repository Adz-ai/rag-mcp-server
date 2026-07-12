package com.adarssh.ragmcp.rag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * DTOs mirroring the RAG service's JSON API. The service speaks snake_case
 * (Python), this codebase speaks camelCase — {@code @JsonNaming} does the
 * translation once, at the boundary, instead of annotation noise per field.
 */
public final class RagApi {

    private RagApi() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Passage(
            int index,
            int chunkIndex,
            String title,
            String section,
            String source,
            double score,
            String content) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SearchResponse(List<Passage> results) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AskRequest(String question, Integer topK, String source) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AskResponse(
            String answer,
            List<Integer> citations,
            List<Passage> passages,
            String model,
            int inputTokens,
            int outputTokens,
            Double estimatedCostUsd,
            double retrievalMs,
            double generationMs) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Health(String status, int documents, int chunks) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NeighborChunk(
            int chunkIndex,
            String title,
            String section,
            String source,
            String content) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NeighborsResponse(List<NeighborChunk> chunks) {}
}

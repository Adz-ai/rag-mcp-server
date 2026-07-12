package com.adarssh.ragmcp.rag;

import com.adarssh.ragmcp.rag.RagApi.AskRequest;
import com.adarssh.ragmcp.rag.RagApi.AskResponse;
import com.adarssh.ragmcp.rag.RagApi.Health;
import com.adarssh.ragmcp.rag.RagApi.NeighborsResponse;
import com.adarssh.ragmcp.rag.RagApi.SearchResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Typed HTTP client for the RAG service. Deliberately thin: the MCP server
 * owns protocol and tool ergonomics, the RAG service owns retrieval and
 * generation. Keeping this boundary at HTTP means either side can be
 * redeployed, scaled, or swapped independently — the same reason the tools
 * don't reach into the vector store directly.
 */
@Component
public class RagClient {

    private final RestClient http;

    public RagClient(RestClient.Builder builder, RagClientProperties properties) {
        this.http = builder.baseUrl(properties.baseUrl()).build();
    }

    public SearchResponse search(String query, Integer topK, String source) {
        return http.get()
                .uri(uri -> {
                    uri.path("/search").queryParam("q", query);
                    if (topK != null) {
                        uri.queryParam("top_k", topK);
                    }
                    if (source != null && !source.isBlank()) {
                        uri.queryParam("source", source);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(SearchResponse.class);
    }

    public AskResponse ask(String question, Integer topK, String source) {
        AskRequest body = new AskRequest(
                question, topK, (source != null && !source.isBlank()) ? source : null);
        return http.post()
                .uri("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(AskResponse.class);
    }

    public NeighborsResponse neighbors(String source, int chunkIndex, int before, int after) {
        return http.get()
                .uri(uri -> uri.path("/neighbors")
                        .queryParam("source", source)
                        .queryParam("index", chunkIndex)
                        .queryParam("before", before)
                        .queryParam("after", after)
                        .build())
                .retrieve()
                .body(NeighborsResponse.class);
    }

    public Health health() {
        return http.get().uri("/healthz").retrieve().body(Health.class);
    }
}

package com.adarssh.ragmcp.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.adarssh.ragmcp.rag.RagApi.AskResponse;
import com.adarssh.ragmcp.rag.RagApi.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Wire-level contract tests: URL construction and snake_case JSON parsing.
 * MockRestServiceServer intercepts the RestClient, so no network is involved.
 */
class RagClientTest {

    private MockRestServiceServer server;
    private RagClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RagClient(builder, new RagClientProperties("http://rag"));
    }

    @Test
    void searchBuildsQueryParamsAndParsesResults() {
        server.expect(requestTo("http://rag/search?q=batching&top_k=3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"results": [{"index": 1, "chunk_index": 4, "title": "Model serving", "section": "Batching",
                          "source": "serving.md", "score": 0.83, "content": "Dynamic batching..."}]}
                        """,
                        MediaType.APPLICATION_JSON));

        SearchResponse response = client.search("batching", 3);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().section()).isEqualTo("Batching");
        assertThat(response.results().getFirst().score()).isEqualTo(0.83);
    }

    @Test
    void askPostsQuestionAndParsesSnakeCaseUsageFields() {
        server.expect(requestTo("http://rag/ask"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.question").value("why batch?"))
                .andExpect(jsonPath("$.top_k").value(7))
                .andRespond(withSuccess(
                        """
                        {"answer": "Batching amortises overhead [1].", "citations": [1],
                         "passages": [{"index": 1, "chunk_index": 4, "title": "Model serving", "section": "Batching",
                                       "source": "serving.md", "score": 0.83, "content": "..."}],
                         "model": "llama3.1:8b", "input_tokens": 900, "output_tokens": 40,
                         "estimated_cost_usd": null, "retrieval_ms": 210.5, "generation_ms": 4900.0}
                        """,
                        MediaType.APPLICATION_JSON));

        AskResponse response = client.ask("why batch?", 7);

        assertThat(response.answer()).startsWith("Batching");
        assertThat(response.inputTokens()).isEqualTo(900);
        assertThat(response.estimatedCostUsd()).isNull();
        assertThat(response.generationMs()).isEqualTo(4900.0);
    }

    @Test
    void askOmitsTopKWhenNull() {
        server.expect(requestTo("http://rag/ask"))
                .andExpect(jsonPath("$.top_k").doesNotExist())
                .andRespond(withSuccess(
                        """
                        {"answer": "x", "citations": [], "passages": [], "model": "m",
                         "input_tokens": 0, "output_tokens": 0, "estimated_cost_usd": null,
                         "retrieval_ms": 0, "generation_ms": 0}
                        """,
                        MediaType.APPLICATION_JSON));

        client.ask("q", null);
        server.verify();
    }
}

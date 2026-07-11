package com.adarssh.ragmcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adarssh.ragmcp.rag.RagApi;
import com.adarssh.ragmcp.rag.RagApi.AskResponse;
import com.adarssh.ragmcp.rag.RagApi.Health;
import com.adarssh.ragmcp.rag.RagApi.Passage;
import com.adarssh.ragmcp.rag.RagApi.SearchResponse;
import com.adarssh.ragmcp.rag.RagClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class RagToolsTest {

    private RagClient rag;
    private RagTools tools;

    private static final Passage PASSAGE =
            new Passage(1, 4, "Model serving", "Batching", "serving.md", 0.83, "Dynamic batching...");

    @BeforeEach
    void setUp() {
        rag = mock(RagClient.class);
        tools = new RagTools(rag);
    }

    @Test
    void askDocsFormatsAnswerWithSourcesAndTelemetry() {
        when(rag.ask("why batch?", null, null)).thenReturn(new AskResponse(
                "Batching amortises overhead [1].",
                List.of(1),
                List.of(PASSAGE),
                "llama3.1:8b",
                900, 40, null, 210.0, 4900.0));

        String out = tools.askDocs("why batch?", null, null);

        assertThat(out).contains("Batching amortises overhead [1].");
        assertThat(out).contains("[1] Model serving — Batching");
        assertThat(out).contains("model: llama3.1:8b");
        assertThat(out).doesNotContain("cost"); // local model: no cost line
    }

    @Test
    void askDocsIncludesCostForHostedModels() {
        when(rag.ask(anyString(), any(), any())).thenReturn(new AskResponse(
                "Answer [1].", List.of(1), List.of(PASSAGE),
                "claude-haiku-4-5", 900, 40, 0.0021, 210.0, 2700.0));

        assertThat(tools.askDocs("q", 5, null)).contains("cost $0.0021");
    }

    @Test
    void searchDocsFormatsRankedPassages() {
        when(rag.search("batching", 5, null)).thenReturn(new SearchResponse(List.of(PASSAGE)));

        String out = tools.searchDocs("batching", null, null);

        assertThat(out).contains("#1 (score 0.830) Model serving — Batching");
        assertThat(out).contains("Dynamic batching...");
    }

    @Test
    void sectionlessPassagesOmitTheDanglingDash() {
        Passage pdfPassage = new Passage(1, 86, "The Little Book", "", "book.pdf", 0.5, "text");
        when(rag.ask(anyString(), any(), any())).thenReturn(new AskResponse(
                "Answer [1].", List.of(1), List.of(pdfPassage),
                "llama3.1:8b", 10, 5, null, 100.0, 900.0));

        String out = tools.askDocs("q", null, null);

        assertThat(out).contains("[1] The Little Book (book.pdf, chunk 86)");
        assertThat(out).doesNotContain("The Little Book —");
    }

    @Test
    void sourceFilterIsForwardedToTheRagService() {
        when(rag.search("skew", 5, "docs/corpus")).thenReturn(new SearchResponse(List.of(PASSAGE)));

        String out = tools.searchDocs("skew", null, "docs/corpus");

        assertThat(out).contains("#1");  // stub matched -> filter reached the client verbatim
    }

    @Test
    void searchDocsIncludesChunkAddressForNeighborLookups() {
        when(rag.search("batching", 5, null)).thenReturn(new SearchResponse(List.of(PASSAGE)));

        assertThat(tools.searchDocs("batching", null, null)).contains("(serving.md, chunk 4)");
    }

    @Test
    void readChunkNeighborsMarksTheAnchorChunk() {
        when(rag.neighbors("serving.md", 3, 1, 1)).thenReturn(new RagApi.NeighborsResponse(List.of(
                new RagApi.NeighborChunk(2, "Doc", "S", "serving.md", "before text"),
                new RagApi.NeighborChunk(3, "Doc", "S", "serving.md", "anchor text"),
                new RagApi.NeighborChunk(4, "Doc", "S", "serving.md", "after text"))));

        String out = tools.readChunkNeighbors("serving.md", 3, null, null);

        assertThat(out).contains("--- chunk 2 ---\nbefore text");
        assertThat(out).contains("--- chunk 3 <- the chunk you asked about ---\nanchor text");
        assertThat(out).contains("--- chunk 4 ---\nafter text");
    }

    @Test
    void readChunkNeighborsExplainsEmptyResults() {
        when(rag.neighbors(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new RagApi.NeighborsResponse(List.of()));

        assertThat(tools.readChunkNeighbors("wrong-path.md", 0, null, null))
                .contains("No chunks found")
                .contains("exactly as returned by search_docs");
    }

    @Test
    void readChunkNeighborsClampsWindowSizes() {
        when(rag.neighbors("s.md", 5, 10, 0)).thenReturn(new RagApi.NeighborsResponse(List.of(
                new RagApi.NeighborChunk(5, "Doc", "S", "s.md", "text"))));

        String out = tools.readChunkNeighbors("s.md", 5, 99, -3);

        assertThat(out).contains("--- chunk 5");  // verifies the clamped call matched the stub
    }

    @Test
    void searchDocsHandlesNoMatches() {
        when(rag.search(anyString(), anyInt(), any())).thenReturn(new SearchResponse(List.of()));
        assertThat(tools.searchDocs("nothing", 3, null)).isEqualTo("No passages matched.");
    }

    @Test
    void failuresBecomeActionableTextNotExceptions() {
        when(rag.ask(anyString(), any(), any())).thenThrow(new RestClientException("connection refused"));
        when(rag.search(anyString(), anyInt(), any())).thenThrow(new RestClientException("boom"));
        when(rag.health()).thenThrow(new RestClientException("down"));

        assertThat(tools.askDocs("q", null, null)).contains("RAG service unreachable");
        assertThat(tools.searchDocs("q", null, null)).contains("RAG service unreachable");
        assertThat(tools.indexStats()).contains("RAG service unreachable");
    }

    @Test
    void indexStatsReportsCounts() {
        when(rag.health()).thenReturn(new Health("ok", 2, 10));
        assertThat(tools.indexStats()).isEqualTo("status=ok documents=2 chunks=10");
    }
}

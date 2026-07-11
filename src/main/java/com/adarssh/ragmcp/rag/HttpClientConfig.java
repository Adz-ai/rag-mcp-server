package com.adarssh.ragmcp.rag;

import java.net.http.HttpClient;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
class HttpClientConfig {

    /**
     * Pin the outbound HTTP client to HTTP/1.1. The JDK client defaults to
     * attempting an h2c upgrade on plaintext connections, which uvicorn (the
     * RAG service's server) rejects — first requests fail with "Invalid HTTP
     * request received". Found empirically; kept as a customizer so tests
     * binding a mock server to a plain builder are unaffected.
     */
    @Bean
    RestClientCustomizer http11RestClients() {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return builder -> builder.requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}

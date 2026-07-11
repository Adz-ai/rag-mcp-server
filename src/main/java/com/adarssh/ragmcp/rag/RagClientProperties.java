package com.adarssh.ragmcp.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag")
public record RagClientProperties(@DefaultValue("http://localhost:8000") String baseUrl) {}

package be.sandervl.jiraharvest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraConfig(String url, String username, String token) {}

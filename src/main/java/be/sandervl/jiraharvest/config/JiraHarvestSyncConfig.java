package be.sandervl.jiraharvest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sync")
public record JiraHarvestSyncConfig(Integer daysToGoBack) {}

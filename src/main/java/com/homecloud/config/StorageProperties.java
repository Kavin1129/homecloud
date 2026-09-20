package com.homecloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "homecloud.storage")
public record StorageProperties(String root) {
}
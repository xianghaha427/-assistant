package com.finoffice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dingtalk")
@Data
public class DingTalkConfig {
    private boolean enabled = false;
    private String webhookUrl = "";
    private String secret = "";
}
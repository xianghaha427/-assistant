package com.finoffice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 直连兜底配置
 * 当 Dify 不可用时，使用 DeepSeek API 作为降级方案
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {

    /** DeepSeek API Key */
    private String apiKey;

    /** API 基础地址，默认 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称，默认 deepseek-chat */
    private String model = "deepseek-chat";

    /** 是否启用 DeepSeek 直连 */
    private boolean enabled = true;
}

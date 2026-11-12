package com.lingani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "lingani.cors")
public class CorsProperties {

    /**
     * 仅允许配置的前端开发/部署域名跨域访问。
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
}

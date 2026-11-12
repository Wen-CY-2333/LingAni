package com.lingani.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 文档配置。
 * 访问 /swagger-ui.html 查看接口文档，右上角 Authorize 填入登录接口返回的 Token 即可调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI lingAniOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("灵漫 LingAni 用户服务 API")
                        .version("1.0.0")
                        .description("统一响应结构为 { code, msg, data }。除注册、登录与公开查询外，"
                                + "其余接口都需要在请求头携带 Authorization: Bearer <token>。"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

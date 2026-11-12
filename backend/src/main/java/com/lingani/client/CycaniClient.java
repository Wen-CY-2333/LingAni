package com.lingani.client;

import tools.jackson.databind.JsonNode;
import com.lingani.common.BizException;
import com.lingani.config.CycaniProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * CycAni 统一请求客户端。
 * <p>
 * 负责基础地址、请求头、超时与异常的集中处理，业务层只关心接口路径和参数。
 * 返回上游响应中的 {@code data} 节点，保持与 CycAni 原始结构一致，避免前端字段丢失。
 */
@Slf4j
@Component
public class CycaniClient {

    private final RestClient restClient;

    public CycaniClient(CycaniProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("X-App-Name", properties.getAppName())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "lingani-backend/1.0")
                .build();
    }

    /**
     * 发起 GET 请求并返回上游 {@code data} 节点。
     *
     * @param path  以 / 开头的接口路径
     * @param query 查询参数，值为 null 的项会被忽略
     */
    public JsonNode getData(String path, Map<String, ?> query) {
        return getData(path, query, null);
    }

    /**
     * 发起需要第三方认证的 GET 请求，并返回上游 {@code data} 节点。
     *
     * @param bearerToken 第三方 Token，为空时不发送 Authorization 头
     */
    public JsonNode getData(String path, Map<String, ?> query, String bearerToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        if (query != null) {
                            query.forEach((key, value) -> {
                                if (value != null) {
                                    uriBuilder.queryParam(key, value);
                                }
                            });
                        }
                        return uriBuilder.build();
                    })
                    .headers(headers -> {
                        if (StringUtils.hasText(bearerToken)) {
                            headers.set(HttpHeaders.AUTHORIZATION, toAuthorization(bearerToken));
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("CycAni 接口返回错误: path={}, status={}", path, status);
            if (status == 404) {
                throw BizException.notFound("动漫数据不存在");
            }
            if (status >= 500) {
                throw new BizException(502, "动漫数据服务暂时不可用，请稍后重试");
            }
            throw new BizException(502, "动漫数据接口返回错误");
        } catch (ResourceAccessException exception) {
            log.warn("CycAni 接口连接失败或超时: path={}", path, exception);
            throw new BizException(504, "动漫数据接口请求超时，请稍后重试");
        } catch (RestClientException exception) {
            log.warn("CycAni 接口请求失败: path={}", path, exception);
            throw new BizException(502, "动漫数据接口请求失败，请稍后重试");
        }

        if (body == null || body.isNull()) {
            throw new BizException(502, "动漫数据接口返回空响应");
        }
        JsonNode code = body.get("code");
        if (code == null || !code.canConvertToInt() || code.asInt() != 0) {
            String msg = body.path("msg").asString("");
            log.warn("CycAni 业务错误: path={}, code={}, msg={}", path, code, msg);
            throw new BizException(502, msg.isEmpty() ? "动漫数据接口返回错误" : "动漫数据接口返回错误：" + msg);
        }
        JsonNode data = body.get("data");
        return data == null ? tools.jackson.databind.node.JsonNodeFactory.instance.nullNode() : data;
    }

    private static String toAuthorization(String token) {
        String trimmed = token.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed : "Bearer " + trimmed;
    }
}

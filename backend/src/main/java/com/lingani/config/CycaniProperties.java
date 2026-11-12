package com.lingani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lingani.cycani")
public class CycaniProperties {

    /**
     * CycAni 接口基础地址。
     */
    private String baseUrl = "https://www.cycani.org";

    /**
     * CycAni 要求的应用标识请求头。
     */
    private String appName = "cyc_web";

    /**
     * 连接超时（毫秒）。
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）。
     */
    private int readTimeout = 10000;

    /**
     * 播放源直链接口 Token（第三方颁发），仅保存在后端配置中，不进前端产物。
     */
    private String token;
}

package com.lingani.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.lingani.client.CycaniClient;
import com.lingani.common.BizException;
import com.lingani.config.CycaniProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动漫元数据查询服务：封装 CycAni 各接口，对下游统一屏蔽上游返回结构。
 * <p>
 * CycAni 原生分页格式为 {@code {list: [], pager: {page, page_size, total}}}，
 * 列表型非分页格式为 {@code {list: []}}。本服务统一转换为
 * {@code {records, total, current, size}}，与 Spring 自身 JPA Page 约定保持一致，
 * 避免前端、Agent 等下游感知 CycAni 内部协议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CycaniService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;

    private final CycaniClient cycaniClient;
    private final CycaniProperties properties;

    /**
     * 动漫列表（分类页），支持 zoneId / year / tag 与分页。
     */
    public JsonNode getVideoList(Integer zoneId, String year, String tag, Integer page, Integer pageSize) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("zone_id", zoneId == null ? 1 : zoneId);
        int current = normalizePage(page);
        int size = normalizePageSize(pageSize, 24);
        query.put("page", current);
        query.put("page_size", size);
        if (StringUtils.hasText(year)) {
            query.put("year", year.trim());
        }
        if (StringUtils.hasText(tag)) {
            query.put("tag", tag.trim());
        }
        return toPageFormat(cycaniClient.getData("/api/videos", query), current, size);
    }

    /**
     * 关键词搜索。
     */
    public JsonNode searchVideos(String keyword, Integer page, Integer pageSize) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException("搜索关键词不能为空");
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("q", keyword.trim());
        int current = normalizePage(page);
        int size = normalizePageSize(pageSize, 24);
        query.put("page", current);
        query.put("page_size", size);
        return toPageFormat(cycaniClient.getData("/api/videos/search", query), current, size);
    }

    /**
     * 动漫详情（单对象，保持原样）。
     */
    public JsonNode getVideoDetail(String videoId) {
        return cycaniClient.getData("/api/videos/" + requireNumericId(videoId, "动漫 ID"), null);
    }

    /**
     * 章节列表。
     */
    public JsonNode getSections(String videoId, String playerCode, Integer page, Integer pageSize) {
        String id = requireNumericId(videoId, "动漫 ID");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("player_code", StringUtils.hasText(playerCode) ? playerCode.trim() : "cychub");
        int current = normalizePage(page);
        int size = normalizePageSize(pageSize, 48);
        query.put("page", current);
        query.put("page_size", size);
        return toPageFormat(cycaniClient.getData("/api/videos/" + id + "/sections", query), current, size);
    }

    /**
     * 相关推荐（非分页列表，统一为 records/total）。
     */
    public JsonNode getRecommendations(String videoId, Integer limit) {
        String id = requireNumericId(videoId, "动漫 ID");
        Map<String, Object> query = new LinkedHashMap<>();
        int size = limit == null || limit < 1 ? 12 : Math.min(limit, 50);
        query.put("limit", size);
        return toPageFormat(cycaniClient.getData("/api/videos/" + id + "/recommendations", query), 1, size);
    }

    /**
     * 排行榜榜单列表。
     */
    public JsonNode getRanks() {
        return toPageFormat(cycaniClient.getData("/api/ranks", null), 1, Integer.MAX_VALUE);
    }

    /**
     * 指定榜单的动漫列表。
     */
    public JsonNode getRankVideos(String rankId) {
        return toPageFormat(
                cycaniClient.getData("/api/ranks/" + requireNumericId(rankId, "榜单 ID") + "/videos", null),
                1, Integer.MAX_VALUE
        );
    }

    /**
     * 追番周表（按天分组，外层统一为 records）。
     */
    public JsonNode getWeekdayVideos() {
        return toPageFormat(cycaniClient.getData("/api/index/weekday", null), 1, Integer.MAX_VALUE);
    }

    /**
     * 首页推荐（按分区分组，外层统一为 records）。
     */
    public JsonNode getIndexRecommendations() {
        return toPageFormat(cycaniClient.getData("/api/index/recommend", null), 1, Integer.MAX_VALUE);
    }

    /**
     * 首页轮播广告位。
     */
    public JsonNode getAdverts(String position) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("position", StringUtils.hasText(position) ? position.trim() : "banner");
        return toPageFormat(cycaniClient.getData("/api/app/adverts", query), 1, Integer.MAX_VALUE);
    }

    /**
     * 章节视频直链：第三方 Token 仅保存在后端配置中，不下发给前端。
     */
    public JsonNode getPlayUrl(String sectionId) {
        String id = requireNumericId(sectionId, "章节 ID");
        String token = properties.getToken();
        if (!StringUtils.hasText(token)) {
            log.warn("CYCANI_TOKEN 未配置，无法获取播放直链");
            throw new BizException(503, "播放源服务未配置：请先在后端 .env 中填写 CYCANI_TOKEN");
        }
        return cycaniClient.getData("/api/v2/sections/" + id + "/play-url", null, token);
    }

    /**
     * 把 CycAni 的 {@code {list, pager?}} 结构统一转成
     * {@code {records, total, current, size}}，与 Spring JPA Page 约定对齐。
     * <p>
     * 转换规则：
     * <ul>
     *   <li>存在 {@code pager} 时：total/来自 pager.current、pager.page_size。</li>
     *   <li>只有 {@code list} 时：total = list.size，current/size 取调用方传入值。</li>
     *   <li>data 既不是对象也不含 list 字段时，原样返回（兼容详情/直链等单对象接口）。</li>
     * </ul>
     */
    private static JsonNode toPageFormat(JsonNode data, int current, int size) {
        if (data == null || data.isNull() || !data.isObject()) {
            return data;
        }
        JsonNode listNode = data.get("list");
        if (listNode == null || !listNode.isArray()) {
            // 没有 list 字段，不是列表型接口，原样返回
            return data;
        }
        ObjectNode result = NODE_FACTORY.objectNode();
        result.set("records", listNode);

        JsonNode pager = data.get("pager");
        if (pager != null && pager.isObject()) {
            result.put("total", pager.path("total").asInt(listNode.size()));
            result.put("current", pager.path("page").asInt(current));
            result.put("size", pager.path("page_size").asInt(size));
        } else {
            result.put("total", listNode.size());
            result.put("current", current);
            result.put("size", size);
        }
        return result;
    }

    private static String requireNumericId(String value, String label) {
        if (!StringUtils.hasText(value) || !value.trim().matches("\\d{1,20}")) {
            throw new BizException(label + "格式不正确");
        }
        return value.trim();
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizePageSize(Integer pageSize, int defaultSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

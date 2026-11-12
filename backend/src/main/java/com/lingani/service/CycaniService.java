package com.lingani.service;

import tools.jackson.databind.JsonNode;
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
 * 动漫元数据查询服务：封装 CycAni 各接口，供前端与后续内部调用方复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CycaniService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CycaniClient cycaniClient;
    private final CycaniProperties properties;

    /**
     * 动漫列表（分类页），支持 zoneId / year / tag 与分页。
     */
    public JsonNode getVideoList(Integer zoneId, String year, String tag, Integer page, Integer pageSize) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("zone_id", zoneId == null ? 1 : zoneId);
        query.put("page", normalizePage(page));
        query.put("page_size", normalizePageSize(pageSize, 24));
        if (StringUtils.hasText(year)) {
            query.put("year", year.trim());
        }
        if (StringUtils.hasText(tag)) {
            query.put("tag", tag.trim());
        }
        return cycaniClient.getData("/api/videos", query);
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
        query.put("page", normalizePage(page));
        query.put("page_size", normalizePageSize(pageSize, 24));
        return cycaniClient.getData("/api/videos/search", query);
    }

    /**
     * 动漫详情。
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
        query.put("page", normalizePage(page));
        query.put("page_size", normalizePageSize(pageSize, 48));
        return cycaniClient.getData("/api/videos/" + id + "/sections", query);
    }

    /**
     * 相关推荐。
     */
    public JsonNode getRecommendations(String videoId, Integer limit) {
        String id = requireNumericId(videoId, "动漫 ID");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", limit == null || limit < 1 ? 12 : Math.min(limit, 50));
        return cycaniClient.getData("/api/videos/" + id + "/recommendations", query);
    }

    /**
     * 排行榜榜单列表。
     */
    public JsonNode getRanks() {
        return cycaniClient.getData("/api/ranks", null);
    }

    /**
     * 指定榜单的动漫列表。
     */
    public JsonNode getRankVideos(String rankId) {
        return cycaniClient.getData("/api/ranks/" + requireNumericId(rankId, "榜单 ID") + "/videos", null);
    }

    /**
     * 追番周表。
     */
    public JsonNode getWeekdayVideos() {
        return cycaniClient.getData("/api/index/weekday", null);
    }

    /**
     * 首页推荐。
     */
    public JsonNode getIndexRecommendations() {
        return cycaniClient.getData("/api/index/recommend", null);
    }

    /**
     * 首页轮播广告位。
     */
    public JsonNode getAdverts(String position) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("position", StringUtils.hasText(position) ? position.trim() : "banner");
        return cycaniClient.getData("/api/app/adverts", query);
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

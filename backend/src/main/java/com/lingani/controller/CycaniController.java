package com.lingani.controller;

import tools.jackson.databind.JsonNode;
import com.lingani.common.Result;
import com.lingani.service.CycaniService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动漫元数据接口：统一代理 CycAni。
 */
@Tag(name = "动漫数据", description = "动漫列表、搜索、详情、章节、推荐、排行榜、周表与首页数据")
@RestController
@RequestMapping("/api/cycani")
@RequiredArgsConstructor
public class CycaniController {

    private final CycaniService cycaniService;

    @GetMapping("/videos")
    public Result<JsonNode> videos(@RequestParam(required = false) Integer zoneId,
                                   @RequestParam(required = false) String year,
                                   @RequestParam(required = false) String tag,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer pageSize) {
        return Result.success(cycaniService.getVideoList(zoneId, year, tag, page, pageSize));
    }

    @GetMapping("/videos/search")
    public Result<JsonNode> search(@RequestParam String q,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer pageSize) {
        return Result.success(cycaniService.searchVideos(q, page, pageSize));
    }

    @GetMapping("/videos/{id}")
    public Result<JsonNode> detail(@PathVariable String id) {
        return Result.success(cycaniService.getVideoDetail(id));
    }

    @GetMapping("/videos/{id}/sections")
    public Result<JsonNode> sections(@PathVariable String id,
                                     @RequestParam(required = false) String playerCode,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer pageSize) {
        return Result.success(cycaniService.getSections(id, playerCode, page, pageSize));
    }

    @GetMapping("/videos/{id}/recommendations")
    public Result<JsonNode> recommendations(@PathVariable String id,
                                            @RequestParam(required = false) Integer limit) {
        return Result.success(cycaniService.getRecommendations(id, limit));
    }

    @GetMapping("/ranks")
    public Result<JsonNode> ranks() {
        return Result.success(cycaniService.getRanks());
    }

    @GetMapping("/ranks/{id}/videos")
    public Result<JsonNode> rankVideos(@PathVariable String id) {
        return Result.success(cycaniService.getRankVideos(id));
    }

    @GetMapping("/index/weekday")
    public Result<JsonNode> weekday() {
        return Result.success(cycaniService.getWeekdayVideos());
    }

    @GetMapping("/index/recommend")
    public Result<JsonNode> recommend() {
        return Result.success(cycaniService.getIndexRecommendations());
    }

    @GetMapping("/adverts")
    public Result<JsonNode> adverts(@RequestParam(required = false) String position) {
        return Result.success(cycaniService.getAdverts(position));
    }

    @GetMapping("/play/{sectionId}")
    public Result<JsonNode> playUrl(@PathVariable String sectionId) {
        return Result.success(cycaniService.getPlayUrl(sectionId));
    }
}

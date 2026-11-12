package com.lingani.controller;

import com.lingani.common.PageResult;
import com.lingani.common.Result;
import com.lingani.dto.HistorySaveRequest;
import com.lingani.entity.UserHistory;
import com.lingani.service.HistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "播放历史", description = "进度保存、历史列表、删除与清空")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @PostMapping
    public Result<UserHistory> save(@Valid @RequestBody HistorySaveRequest request) {
        return Result.success(historyService.save(request));
    }

    @GetMapping
    public Result<PageResult<UserHistory>> page(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size) {
        return Result.success(historyService.page(current, size));
    }

    @GetMapping("/video/{videoId}")
    public Result<List<UserHistory>> listByVideo(@PathVariable String videoId) {
        return Result.success(historyService.listByVideo(videoId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        historyService.delete(id);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> clear() {
        historyService.clear();
        return Result.success();
    }
}

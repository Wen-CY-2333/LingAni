package com.lingani.controller;

import com.lingani.common.Result;
import com.lingani.dto.DanmakuCreateRequest;
import com.lingani.service.DanmakuService;
import com.lingani.vo.DanmakuVO;
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

@Tag(name = "弹幕", description = "按章节查询弹幕、发送弹幕、删除自己发送的弹幕")
@RestController
@RequestMapping("/api/danmaku")
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuService danmakuService;

    @GetMapping
    public Result<List<DanmakuVO>> list(@RequestParam String sectionId) {
        return Result.success(danmakuService.list(sectionId));
    }

    @PostMapping
    public Result<DanmakuVO> create(@Valid @RequestBody DanmakuCreateRequest request) {
        return Result.success(danmakuService.create(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        danmakuService.delete(id);
        return Result.success();
    }
}

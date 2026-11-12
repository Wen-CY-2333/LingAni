package com.lingani.controller;

import com.lingani.common.PageResult;
import com.lingani.common.Result;
import com.lingani.dto.FavoriteAddRequest;
import com.lingani.entity.UserFavorite;
import com.lingani.service.FavoriteService;
import com.lingani.vo.FavoriteCheckVO;
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

@Tag(name = "追番收藏", description = "加入追番、追番列表、状态查询与取消追番")
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public Result<UserFavorite> add(@Valid @RequestBody FavoriteAddRequest request) {
        return Result.success(favoriteService.add(request));
    }

    @GetMapping
    public Result<PageResult<UserFavorite>> page(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "12") Long size) {
        return Result.success(favoriteService.page(current, size));
    }

    @GetMapping("/check")
    public Result<FavoriteCheckVO> check(@RequestParam String videoId) {
        return Result.success(favoriteService.check(videoId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        favoriteService.delete(id);
        return Result.success();
    }
}

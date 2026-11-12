package com.lingani.controller;

import com.lingani.common.PageResult;
import com.lingani.common.Result;
import com.lingani.dto.AdminStatusRequest;
import com.lingani.service.AdminService;
import com.lingani.vo.AdminCommentVO;
import com.lingani.vo.AdminDanmakuVO;
import com.lingani.vo.AdminFavoriteVO;
import com.lingani.vo.AdminHistoryVO;
import com.lingani.vo.AdminStatsVO;
import com.lingani.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台", description = "数据总览、用户管理、评论与弹幕审核、追番与历史查看（需要 role=1 的管理员账号）")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "数据总览")
    @GetMapping("/stats")
    public Result<AdminStatsVO> stats() {
        return Result.success(adminService.stats());
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> users(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listUsers(current, size, keyword));
    }

    @Operation(summary = "启用或禁用用户")
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        adminService.updateUserStatus(id, request.getStatus());
        return Result.success();
    }

    @Operation(summary = "评论列表")
    @GetMapping("/comments")
    public Result<PageResult<AdminCommentVO>> comments(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listComments(current, size, keyword));
    }

    @Operation(summary = "屏蔽或恢复评论")
    @PutMapping("/comments/{id}/status")
    public Result<Void> updateCommentStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        adminService.updateCommentStatus(id, request.getStatus());
        return Result.success();
    }

    @Operation(summary = "删除评论（一级评论会同时删除子回复）")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        adminService.deleteComment(id);
        return Result.success();
    }

    @Operation(summary = "弹幕列表")
    @GetMapping("/danmaku")
    public Result<PageResult<AdminDanmakuVO>> danmaku(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listDanmaku(current, size, keyword));
    }

    @Operation(summary = "屏蔽或恢复弹幕")
    @PutMapping("/danmaku/{id}/status")
    public Result<Void> updateDanmakuStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        adminService.updateDanmakuStatus(id, request.getStatus());
        return Result.success();
    }

    @Operation(summary = "删除弹幕")
    @DeleteMapping("/danmaku/{id}")
    public Result<Void> deleteDanmaku(@PathVariable Long id) {
        adminService.deleteDanmaku(id);
        return Result.success();
    }

    @Operation(summary = "追番记录（只读）")
    @GetMapping("/favorites")
    public Result<PageResult<AdminFavoriteVO>> favorites(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listFavorites(current, size, keyword));
    }

    @Operation(summary = "播放历史记录（只读）")
    @GetMapping("/history")
    public Result<PageResult<AdminHistoryVO>> history(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listHistory(current, size, keyword));
    }
}

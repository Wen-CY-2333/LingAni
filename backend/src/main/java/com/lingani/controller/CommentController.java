package com.lingani.controller;

import com.lingani.common.PageResult;
import com.lingani.common.Result;
import com.lingani.dto.CommentCreateRequest;
import com.lingani.dto.CommentUpdateRequest;
import com.lingani.service.CommentService;
import com.lingani.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "评论", description = "一级评论、子回复、编辑与删除")
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public Result<PageResult<CommentVO>> page(
            @RequestParam String videoId,
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "8") Long size) {
        return Result.success(commentService.page(videoId, current, size));
    }

    @PostMapping
    public Result<CommentVO> create(@Valid @RequestBody CommentCreateRequest request) {
        return Result.success(commentService.create(request));
    }

    @PutMapping("/{id}")
    public Result<CommentVO> update(@PathVariable Long id,
                                    @Valid @RequestBody CommentUpdateRequest request) {
        return Result.success(commentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }
}

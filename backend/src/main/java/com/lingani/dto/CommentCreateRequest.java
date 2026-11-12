package com.lingani.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateRequest {

    @NotBlank(message = "videoId 不能为空")
    @Size(max = 100, message = "videoId 过长")
    private String videoId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最多 500 个字符")
    private String content;

    private Long parentId;
}

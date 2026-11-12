package com.lingani.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCommentVO {

    private Long id;
    private String videoId;
    private Long userId;
    private String username;
    private String nickname;
    private String content;
    private Long parentId;
    private Integer status;
    private LocalDateTime createdAt;
}

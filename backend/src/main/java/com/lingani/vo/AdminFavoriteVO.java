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
public class AdminFavoriteVO {

    private Long id;
    private Long userId;
    private String username;
    private String videoId;
    private String animeTitle;
    private String animeCover;
    private LocalDateTime createdAt;
}

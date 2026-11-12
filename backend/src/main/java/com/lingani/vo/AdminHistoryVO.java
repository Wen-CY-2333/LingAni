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
public class AdminHistoryVO {

    private Long id;
    private Long userId;
    private String username;
    private String videoId;
    private String sectionId;
    private String episodeTitle;
    private Long progress;
    private Long duration;
    private LocalDateTime lastPlayedAt;
}

package com.lingani.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDanmakuVO {

    private Long id;
    private String sectionId;
    private Long userId;
    private String username;
    private String text;
    private BigDecimal time;
    private Integer color;
    private Integer mode;
    private Integer status;
    private LocalDateTime createdAt;
}

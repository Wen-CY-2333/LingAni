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
public class AdminUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer status;
    private Integer role;
    private LocalDateTime createdAt;
}

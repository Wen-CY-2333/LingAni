package com.lingani.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最多 50 个字符")
    private String nickname;

    @Size(max = 500, message = "头像地址最多 500 个字符")
    private String avatar;
}

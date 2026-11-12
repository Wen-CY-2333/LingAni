package com.lingani.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FavoriteAddRequest {

    @NotBlank(message = "videoId 不能为空")
    @Size(max = 100, message = "videoId 过长")
    private String videoId;

    @Size(max = 255, message = "动画名称过长")
    private String animeTitle;

    @Size(max = 500, message = "动画封面地址过长")
    private String animeCover;
}

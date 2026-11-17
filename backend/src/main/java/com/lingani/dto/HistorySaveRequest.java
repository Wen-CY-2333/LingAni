package com.lingani.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistorySaveRequest {

    @NotBlank(message = "videoId 不能为空")
    @Size(max = 100, message = "videoId 过长")
    private String videoId;

    @NotBlank(message = "sectionId 不能为空")
    @Size(max = 100, message = "sectionId 过长")
    private String sectionId;

    @Size(max = 255, message = "动画名称过长")
    private String animeTitle;

    @Size(max = 255, message = "章节名称过长")
    private String episodeTitle;

    @NotNull(message = "播放进度不能为空")
    @Min(value = 0, message = "播放进度不能为负数")
    private Long progress;

    @NotNull(message = "视频时长不能为空")
    @Min(value = 0, message = "视频时长不能为负数")
    @Max(value = 86400, message = "视频时长超出合理范围")
    private Long duration;
}

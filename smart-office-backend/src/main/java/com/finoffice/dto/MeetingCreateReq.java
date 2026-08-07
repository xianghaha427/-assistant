package com.finoffice.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 创建会议记录请求 */
@Data
public class MeetingCreateReq {
    private String title;
    /** 会议时间（不传默认当前时间） */
    private LocalDateTime meetingTime;
    private String sourceType;
    private String rawText;
    /** 纪要直传 Dify 的文件ID列表（文档上传 Tab 上传后获得） */
    private List<String> fileIds;
}

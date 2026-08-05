package com.finoffice.dto;

import lombok.Data;

/** 创建会议记录请求 */
@Data
public class MeetingCreateReq {
    private String title;
    private String sourceType;
    private String rawText;
}

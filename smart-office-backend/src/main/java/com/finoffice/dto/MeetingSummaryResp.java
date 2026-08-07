package com.finoffice.dto;

import lombok.Data;

import java.util.List;

/** 会议纪要响应 */
@Data
public class MeetingSummaryResp {
    private String title;
    private List<String> attendees;
    private List<String> topics;
    private List<String> decisions;
    private List<ActionItemDTO> actionItems;
    /** 状态标记：processing=生成中 / empty=尚未生成 / completed=已完成（占位用，不入库） */
    private String status;
    /** 是否为演示模式兜底纪要（所有 AI 通道不可用时的 Mock 产物） */
    private Boolean mock;
}

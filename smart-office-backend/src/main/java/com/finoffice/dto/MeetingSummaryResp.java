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
}

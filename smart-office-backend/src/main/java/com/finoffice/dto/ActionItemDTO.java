package com.finoffice.dto;

import lombok.Data;

/** 待办事项 DTO */
@Data
public class ActionItemDTO {
    private String content;
    private String assignee;
    private String deadline;
}

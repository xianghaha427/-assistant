package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会议待办事项表 */
@Data
@TableName("meeting_action_item")
public class MeetingActionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long meetingId;
    private String content;
    private String assignee;
    private LocalDateTime deadline;
    private String status;
    private LocalDateTime createTime;
}

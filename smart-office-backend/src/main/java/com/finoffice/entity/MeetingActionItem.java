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
    /** 责任人用户ID（按 sys_user.real_name 模糊匹配落库） */
    private Long assigneeId;
    /** 由本待办转换生成的任务ID（防重复转换） */
    private Long taskId;
    private LocalDateTime deadline;
    private String status;
    private LocalDateTime createTime;
}

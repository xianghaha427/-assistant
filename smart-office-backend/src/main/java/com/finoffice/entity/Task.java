package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务看板表 */
@Data
@TableName("task")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private Long assigneeUserId;
    private Long deptId;
    /** 状态: pending/in_progress/done */
    private String status;
    /** 优先级: high/medium/low */
    private String priority;
    private LocalDateTime deadline;
    private Long sourceMeetingId;
    private Long creatorUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 负责人姓名（非数据库列，查询时组装） */
    @TableField(exist = false)
    private String assigneeName;
    /** 创建人姓名（非数据库列，查询时组装） */
    @TableField(exist = false)
    private String creatorName;
}

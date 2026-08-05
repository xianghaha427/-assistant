package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会议记录表 */
@Data
@TableName("meeting_record")
public class MeetingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String sourceType;
    private String rawText;
    private String audioUrl;
    private String summary;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

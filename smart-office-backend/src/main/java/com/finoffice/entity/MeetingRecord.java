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
    /** 会议时间（工作台今日会议依据此字段） */
    private LocalDateTime meetingTime;
    private String sourceType;
    private String rawText;
    /** 纪要直传 Dify 的文件ID（JSON数组字符串，供异步生成纪要时携带 files） */
    private String difyFileIds;
    private String audioUrl;
    private String summary;
    /** 纪要生成失败留痕（异常类名+message摘要；降级成功时记录通道异常原因） */
    private String failReason;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

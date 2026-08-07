package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 行业简报记录表（brief_date 唯一键 uk_date，同日仅一条） */
@Data
@TableName("briefing_record")
public class BriefingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 简报日期 */
    private LocalDate briefDate;
    private String title;
    private String content;
    /** 状态: success/failed */
    private String status;
    private LocalDateTime createTime;
}

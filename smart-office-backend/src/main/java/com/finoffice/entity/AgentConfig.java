package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 智能体配置表 */
@Data
@TableName("agent_config")
public class AgentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String icon;
    private String difyAppId;
    private String systemPrompt;
    private String toolsJson;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

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
    /** Dify应用类型：chat对话型 / workflow工作流 */
    private String difyMode;
    /** workflow 型应用的输入变量名（如 meeting_text/topic） */
    private String inputVariable;
    private String systemPrompt;
    /** 知识问答引擎通道：native=自研LangChain4j引擎 / dify=Dify云端（默认dify，阶段1双通道） */
    private String engine;
    private String toolsJson;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

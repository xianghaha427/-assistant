package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 会话表 */
@Data
@TableName("ai_session")
public class AiSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long agentId;
    private String title;
    private String difyConversationId;
    private Integer msgCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

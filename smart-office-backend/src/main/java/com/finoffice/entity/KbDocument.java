package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档表 */
@Data
@TableName("kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String fileType;
    private String fileUrl;
    private String content;
    private String difyDocumentId;
    private String status;
    private Integer wordCount;
    private LocalDateTime createTime;
}

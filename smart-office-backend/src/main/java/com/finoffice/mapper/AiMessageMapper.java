package com.finoffice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finoffice.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/** AI 消息 Mapper */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}

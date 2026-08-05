package com.finoffice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finoffice.entity.SysLog;
import org.apache.ibatis.annotations.Mapper;

/** 审计日志 Mapper */
@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {
}

package com.finoffice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finoffice.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/** 任务看板 Mapper */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}

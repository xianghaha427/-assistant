package com.finoffice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finoffice.entity.MeetingRecord;
import org.apache.ibatis.annotations.Mapper;

/** 会议记录 Mapper */
@Mapper
public interface MeetingRecordMapper extends BaseMapper<MeetingRecord> {
}

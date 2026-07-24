package com.mrboard.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrboard.system.entity.SyncLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncLogMapper extends BaseMapper<SyncLog> {
}

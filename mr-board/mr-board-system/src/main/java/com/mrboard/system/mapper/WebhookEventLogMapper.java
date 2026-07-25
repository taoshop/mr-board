package com.mrboard.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrboard.system.entity.WebhookEventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookEventLogMapper extends BaseMapper<WebhookEventLog> {
}

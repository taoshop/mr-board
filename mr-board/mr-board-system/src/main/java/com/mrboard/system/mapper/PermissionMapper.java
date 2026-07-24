package com.mrboard.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrboard.system.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}

package com.mrboard.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrboard.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT r.* FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<com.mrboard.system.entity.Role> selectRolesByUserId(@Param("userId") Long userId);

    @Select("SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
            "INNER JOIN user_roles ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<com.mrboard.system.entity.Permission> selectPermissionsByUserId(@Param("userId") Long userId);
}

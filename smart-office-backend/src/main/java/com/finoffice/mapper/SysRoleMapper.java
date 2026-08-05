package com.finoffice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finoffice.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 角色 Mapper */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查询用户拥有的角色编码列表（自定义联表 SQL 示例） */
    @Select("SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(Long userId);
}

package com.finoffice.service;

import cn.dev33.satoken.stp.StpInterface;
import com.finoffice.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * sa-token 权限数据源：从 sys_user_role + sys_role 查询当前用户角色码
 * 供 StpUtil.checkRole / hasRole 等鉴权 API 使用
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleMapper roleMapper;

    /** 权限码列表（本项目权限由角色承载，返回空列表） */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    /** 角色码列表，如 ["ADMIN"] */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            long userId = Long.parseLong(String.valueOf(loginId));
            return roleMapper.selectRoleCodesByUserId(userId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}

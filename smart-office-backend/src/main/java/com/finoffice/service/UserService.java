package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.common.BizException;
import com.finoffice.dto.LoginResult;
import com.finoffice.dto.UserInfo;
import com.finoffice.entity.SysDept;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.SysDeptMapper;
import com.finoffice.mapper.SysRoleMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务：登录校验、当前用户信息
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 登录校验，返回用户基本信息（不含密码）
     */
    public LoginResult checkLogin(String username, String password) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, 0));
        if (user == null || !ENCODER.matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException("账号已被禁用");
        }
        LoginResult result = new LoginResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        result.setDeptName(deptName(user.getDeptId()));
        return result;
    }

    /**
     * 当前登录用户信息（含角色）
     */
    public UserInfo currentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setDeptName(deptName(user.getDeptId()));
        info.setRoles(roleMapper.selectRoleCodesByUserId(userId));
        return info;
    }

    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDept dept = deptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }
}

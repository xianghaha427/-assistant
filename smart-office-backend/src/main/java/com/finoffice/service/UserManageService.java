package com.finoffice.service;

import com.finoffice.common.BizException;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户个人设置服务：修改个人信息、修改密码
 */
@Service
@RequiredArgsConstructor
public class UserManageService {

    private final SysUserMapper userMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 更新个人信息（realName, avatar）
     */
    public void updateProfile(Long userId, String realName, String avatar) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (realName != null) {
            user.setRealName(realName);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        userMapper.updateById(user);
    }

    /**
     * 修改密码
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!ENCODER.matches(oldPassword, user.getPassword())) {
            throw new BizException("旧密码不正确");
        }
        user.setPassword(ENCODER.encode(newPassword));
        userMapper.updateById(user);
    }
}

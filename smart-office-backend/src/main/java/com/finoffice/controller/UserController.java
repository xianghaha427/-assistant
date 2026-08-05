package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.service.UserManageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户个人设置接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserManageService userManageService;

    @Data
    public static class ProfileReq {
        private String realName;
        private String avatar;
    }

    @Data
    public static class PasswordReq {
        private String oldPassword;
        private String newPassword;
    }

    /** 更新个人信息 */
    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody ProfileReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        userManageService.updateProfile(userId, req.getRealName(), req.getAvatar());
        return R.ok();
    }

    /** 修改密码 */
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody PasswordReq req) {
        if (req.getOldPassword() == null || req.getNewPassword() == null) {
            throw new BizException("旧密码和新密码不能为空");
        }
        if (req.getNewPassword().length() < 6) {
            throw new BizException("新密码长度不能少于6位");
        }
        long userId = StpUtil.getLoginIdAsLong();
        userManageService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }
}

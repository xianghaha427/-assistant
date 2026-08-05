package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.dto.LoginResult;
import com.finoffice.dto.UserInfo;
import com.finoffice.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }

    @PostMapping("/login")
    public R<LoginResult> login(@RequestBody LoginReq req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new BizException("用户名和密码不能为空");
        }
        LoginResult result = userService.checkLogin(req.getUsername().trim(), req.getPassword());
        StpUtil.login(result.getId());
        result.setToken(StpUtil.getTokenValue());
        return R.ok(result);
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserInfo> me() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.currentUser(userId));
    }
}

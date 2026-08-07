package com.finoffice.dto;

import lombok.Data;

/** 登录成功返回结果 */
@Data
public class LoginResult {
    private Long id;
    private String username;
    private String realName;
    private String deptName;
    private String token;
    /** 角色码列表，如 ["ADMIN"]（前端菜单/路由权限用） */
    private java.util.List<String> roles;
}

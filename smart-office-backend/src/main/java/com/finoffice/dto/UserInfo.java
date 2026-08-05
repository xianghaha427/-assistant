package com.finoffice.dto;

import lombok.Data;

import java.util.List;

/** 当前登录用户信息 */
@Data
public class UserInfo {
    private Long id;
    private String username;
    private String realName;
    private String deptName;
    private List<String> roles;
}

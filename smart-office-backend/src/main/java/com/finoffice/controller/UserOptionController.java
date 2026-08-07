package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.common.R;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.SysRoleMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户选项接口：全员简表（登录即可查），用于会议待办转任务时选择执行人
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserOptionController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    /** 用户选项：id / real_name / username / roles（仅启用且未删除的用户） */
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        StpUtil.checkLogin();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("real_name", u.getRealName());
            map.put("roles", roleMapper.selectRoleCodesByUserId(u.getId()));
            return map;
        }).toList();
        return R.ok(result);
    }
}

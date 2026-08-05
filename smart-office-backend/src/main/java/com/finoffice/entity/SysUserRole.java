package com.finoffice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户角色关联表 */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    private Long userId;
    private Long roleId;
}

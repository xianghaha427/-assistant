package com.finoffice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.entity.KbDocument;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.SysRoleMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据可见范围助手：按角色限定查询数据范围
 * ADMIN 无限制；DEPT_MANAGER 限本部门；EMPLOYEE 仅本人。
 * 供会议、任务、简报等模块复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeHelper {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    /** 当前登录用户的数据范围上下文 */
    public record UserScope(Long userId, Long deptId, List<String> roles) {
        public boolean isAdmin() {
            return roles != null && roles.contains("ADMIN");
        }
        public boolean isDeptManager() {
            return roles != null && roles.contains("DEPT_MANAGER");
        }
    }

    /** 获取当前登录用户的范围上下文 */
    public UserScope currentScope() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        return new UserScope(userId, user == null ? null : user.getDeptId(), roles);
    }

    /**
     * 会议列表数据范围：
     * ADMIN 无限制；DEPT_MANAGER 看本部门成员创建的会议；EMPLOYEE 仅自己创建的
     */
    public LambdaQueryWrapper<MeetingRecord> meetingScope(LambdaQueryWrapper<MeetingRecord> wrapper) {
        return meetingScope(wrapper, currentScope());
    }

    /** 会议列表数据范围（使用外部传入的范围上下文，避免重复查库） */
    public LambdaQueryWrapper<MeetingRecord> meetingScope(LambdaQueryWrapper<MeetingRecord> wrapper, UserScope scope) {
        if (scope.isAdmin()) {
            return wrapper;
        }
        if (scope.isDeptManager() && scope.deptId() != null) {
            List<Long> deptUserIds = userMapper.selectList(
                            new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getDeptId, scope.deptId())
                                    .eq(SysUser::getDeleted, 0))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
            if (deptUserIds.isEmpty()) {
                deptUserIds = new ArrayList<>(Collections.singletonList(scope.userId()));
            }
            return wrapper.in(MeetingRecord::getUserId, deptUserIds);
        }
        return wrapper.eq(MeetingRecord::getUserId, scope.userId());
    }

    /**
     * 知识库文档数据范围：
     * ADMIN 无限制；DEPT_MANAGER 看本部门成员上传的；EMPLOYEE 仅自己上传的
     */
    public LambdaQueryWrapper<KbDocument> kbDocScope(LambdaQueryWrapper<KbDocument> wrapper, UserScope scope) {
        if (scope.isAdmin()) {
            return wrapper;
        }
        if (scope.isDeptManager() && scope.deptId() != null) {
            List<Long> deptUserIds = userMapper.selectList(
                            new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getDeptId, scope.deptId())
                                    .eq(SysUser::getDeleted, 0))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
            if (deptUserIds.isEmpty()) {
                deptUserIds = new ArrayList<>(Collections.singletonList(scope.userId()));
            }
            return wrapper.in(KbDocument::getUserId, deptUserIds);
        }
        return wrapper.eq(KbDocument::getUserId, scope.userId());
    }
}

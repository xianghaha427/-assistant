package com.finoffice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finoffice.common.BizException;
import com.finoffice.common.OpLogger;
import com.finoffice.entity.SysDept;
import com.finoffice.entity.SysLog;
import com.finoffice.entity.SysRole;
import com.finoffice.entity.SysUser;
import com.finoffice.entity.SysUserRole;
import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.mapper.SysDeptMapper;
import com.finoffice.mapper.SysLogMapper;
import com.finoffice.mapper.SysRoleMapper;
import com.finoffice.mapper.SysUserMapper;
import com.finoffice.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 管理后台服务：用户管理 CRUD、角色列表、操作日志
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final SysLogMapper logMapper;
    private final AgentConfigMapper agentConfigMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final long AGENT_ID_KNOWLEDGE = 1L;

    /** 校验当前用户是否为 ADMIN */
    public void checkAdmin() {
        long userId = StpUtil.getLoginIdAsLong();
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        if (!roles.contains("ADMIN")) {
            throw new BizException(403, "无管理员权限");
        }
    }

    /** 用户列表（分页 + 搜索） */
    public Map<String, Object> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> pageObj = new Page<>(page, size);
        userMapper.selectPage(pageObj, wrapper);

        // 补充部门名和角色
        List<Map<String, Object>> records = pageObj.getRecords().stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("real_name", u.getRealName());
            map.put("avatar", u.getAvatar());
            map.put("dept_id", u.getDeptId());
            map.put("dept_name", deptName(u.getDeptId()));
            map.put("status", u.getStatus());
            map.put("create_time", u.getCreateTime());
            map.put("roles", roleMapper.selectRoleCodesByUserId(u.getId()));
            return map;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageObj.getTotal());
        return result;
    }

    /** 创建用户 */
    @Transactional
    public void createUser(String username, String password, String realName, Long deptId, List<String> roleCodes) {
        // 检查用户名唯一
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).eq(SysUser::getDeleted, 0));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(ENCODER.encode(password));
        user.setRealName(realName);
        user.setDeptId(deptId);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        assignRoles(user.getId(), roleCodes);
        OpLogger.log(StpUtil.getLoginIdAsLong(), "USER", "创建用户", "创建用户 " + username);
    }

    /** 更新用户 */
    @Transactional
    public void updateUser(Long id, String realName, Long deptId, Integer status, List<String> roleCodes) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BizException("用户不存在");
        }
        if (realName != null) user.setRealName(realName);
        if (deptId != null) user.setDeptId(deptId);
        if (status != null) user.setStatus(status);
        userMapper.updateById(user);
        if (roleCodes != null) {
            assignRoles(id, roleCodes);
        }
        OpLogger.log(StpUtil.getLoginIdAsLong(), "USER", "更新用户", "更新用户ID " + id);
    }

    /** 删除用户（软删除） */
    public void deleteUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BizException("用户不存在");
        }
        user.setDeleted(1);
        userMapper.updateById(user);
        OpLogger.log(StpUtil.getLoginIdAsLong(), "USER", "删除用户", "删除用户 " + user.getUsername());
    }

    /** 角色列表 */
    public List<SysRole> listRoles() {
        return roleMapper.selectList(null);
    }

    /** 操作日志（分页） */
    public Map<String, Object> listLogs(int page, int size) {
        Page<SysLog> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysLog::getCreateTime);
        logMapper.selectPage(pageObj, wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", pageObj.getRecords());
        result.put("total", pageObj.getTotal());
        return result;
    }

    /** 当前知识库回答引擎 */
    public Map<String, Object> getKnowledgeEngine() {
        AgentConfig agent = agentConfigMapper.selectById(AGENT_ID_KNOWLEDGE);
        String engine = agent == null || agent.getEngine() == null || agent.getEngine().isBlank()
                ? "dify" : agent.getEngine();
        return Map.of("engine", engine);
    }

    /** 切换知识库回答引擎：dify / native */
    public void updateKnowledgeEngine(String engine) {
        if (!"dify".equalsIgnoreCase(engine) && !"native".equalsIgnoreCase(engine)) {
            throw new BizException("知识库引擎只能选择 dify 或 native");
        }
        AgentConfig agent = agentConfigMapper.selectById(AGENT_ID_KNOWLEDGE);
        if (agent == null) {
            throw new BizException("企业知识问答助手配置不存在");
        }
        String normalized = engine.toLowerCase();
        agent.setEngine(normalized);
        agentConfigMapper.updateById(agent);
        OpLogger.log(StpUtil.getLoginIdAsLong(), "KNOWLEDGE", "切换知识库引擎", "engine=" + normalized);
    }

    /** 给用户分配角色 */
    private void assignRoles(Long userId, List<String> roleCodes) {
        // 先删除旧关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        // 再插入新关联
        for (String code : roleCodes) {
            SysRole role = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code));
            if (role != null) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(role.getId());
                userRoleMapper.insert(ur);
            }
        }
    }

    private String deptName(Long deptId) {
        if (deptId == null) return null;
        SysDept dept = deptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }
}

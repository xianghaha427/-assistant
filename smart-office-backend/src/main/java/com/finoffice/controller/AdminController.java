package com.finoffice.controller;

import com.finoffice.common.R;
import com.finoffice.service.AdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台接口（需要 ADMIN 角色）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 用户列表（分页） */
    @GetMapping("/users")
    public R<Map<String, Object>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        adminService.checkAdmin();
        return R.ok(adminService.listUsers(page, size, keyword));
    }

    @Data
    public static class CreateUserReq {
        private String username;
        private String password;
        private String realName;
        private Long deptId;
        private List<String> roleCodes;
    }

    /** 创建用户 */
    @PostMapping("/users")
    public R<Void> createUser(@RequestBody CreateUserReq req) {
        adminService.checkAdmin();
        adminService.createUser(req.getUsername(), req.getPassword(),
                req.getRealName(), req.getDeptId(), req.getRoleCodes());
        return R.ok();
    }

    @Data
    public static class UpdateUserReq {
        private String realName;
        private Long deptId;
        private Integer status;
        private List<String> roleCodes;
    }

    /** 更新用户 */
    @PutMapping("/users/{id}")
    public R<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserReq req) {
        adminService.checkAdmin();
        adminService.updateUser(id, req.getRealName(), req.getDeptId(),
                req.getStatus(), req.getRoleCodes());
        return R.ok();
    }

    /** 删除用户（软删除） */
    @DeleteMapping("/users/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        adminService.checkAdmin();
        adminService.deleteUser(id);
        return R.ok();
    }

    /** 角色列表 */
    @GetMapping("/roles")
    public R<?> listRoles() {
        adminService.checkAdmin();
        return R.ok(adminService.listRoles());
    }

    /** 操作日志（分页） */
    @GetMapping("/logs")
    public R<Map<String, Object>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminService.checkAdmin();
        return R.ok(adminService.listLogs(page, size));
    }

    /** 当前知识库回答引擎 */
    @GetMapping("/knowledge-engine")
    public R<Map<String, Object>> getKnowledgeEngine() {
        adminService.checkAdmin();
        return R.ok(adminService.getKnowledgeEngine());
    }

    @Data
    public static class KnowledgeEngineReq {
        private String engine;
    }

    /** 切换知识库回答引擎：dify / native */
    @PutMapping("/knowledge-engine")
    public R<Void> updateKnowledgeEngine(@RequestBody KnowledgeEngineReq req) {
        adminService.checkAdmin();
        adminService.updateKnowledgeEngine(req.getEngine());
        return R.ok();
    }
}

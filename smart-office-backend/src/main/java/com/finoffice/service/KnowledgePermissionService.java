package com.finoffice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finoffice.common.BizException;
import com.finoffice.entity.KbDocument;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.KbDocumentMapper;
import com.finoffice.mapper.SysRoleMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** 知识文档统一授权判定，供文档接口使用。 */
@Service
@RequiredArgsConstructor
public class KnowledgePermissionService {

    private static final Set<String> VALID_SUBJECTS = Set.of("ALL", "USER", "ROLE", "DEPT");
    private static final Set<String> VALID_PERMISSIONS = Set.of("READ", "EDIT", "DELETE");

    private final JdbcTemplate jdbcTemplate;
    private final KbDocumentMapper documentMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public record PermissionGrant(Long id, @JsonProperty("documentId") Long documentId,
                                  @JsonProperty("subjectType") String subjectType,
                                  @JsonProperty("subjectId") Long subjectId,
                                  @JsonProperty("subjectName") String subjectName, String permission) {}

    public record CurrentUser(Long id, Long deptId, Set<String> roles) {
        public boolean isAdmin() { return roles.contains("ADMIN"); }
        public boolean isKbAdmin() { return roles.contains("KB_ADMIN"); }
    }

    public CurrentUser currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        List<String> roleCodes = roleMapper.selectRoleCodesByUserId(userId);
        return new CurrentUser(userId, user == null ? null : user.getDeptId(),
                roleCodes == null ? Set.of() : new HashSet<>(roleCodes));
    }

    public List<KbDocument> accessibleDocuments(Long userId) {
        CurrentUser user = user(userId);
        return documentMapper.selectList(null).stream()
                .filter(doc -> canRead(doc, user))
                .sorted(Comparator.comparing(KbDocument::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public boolean canRead(KbDocument doc, Long userId) {
        if (userId == null) return false;
        return canRead(doc, user(userId));
    }

    public boolean canRead(KbDocument doc, CurrentUser user) {
        if (user.isAdmin() || user.isKbAdmin() || Objects.equals(doc.getUserId(), user.id())) return true;
        if (hasGrantAtLeast(doc.getId(), "USER", user.id(), "READ")) return true;
        if (hasGrantAtLeast(doc.getId(), "ALL", 0L, "READ")) return true;
        if (hasRoleGrantAtLeast(doc.getId(), user.roles(), "READ")) return true;
        return user.deptId() != null && Objects.equals(doc.getDeptId(), user.deptId())
                && hasGrantAtLeast(doc.getId(), "DEPT", user.deptId(), "READ");
    }

    public boolean canManage(KbDocument doc, Long userId, String permission) {
        CurrentUser user = user(userId);
        if (user.isAdmin() || user.isKbAdmin() || Objects.equals(doc.getUserId(), user.id())) return true;
        if (user.roles().contains("DEPT_MANAGER")
                && user.deptId() != null && Objects.equals(doc.getDeptId(), user.deptId())) return true;
        return hasGrantAtLeast(doc.getId(), "ALL", 0L, permission)
                || hasGrantAtLeast(doc.getId(), "USER", user.id(), permission)
                || hasRoleGrantAtLeast(doc.getId(), user.roles(), permission)
                || (user.deptId() != null && hasGrantAtLeast(doc.getId(), "DEPT", user.deptId(), permission));
    }

    /** 权限是累积等级：DELETE 包含 EDIT/READ，EDIT 包含 READ。 */
    public boolean canManageAcl(KbDocument doc, Long userId) {
        CurrentUser user = user(userId);
        return user.isAdmin() || user.isKbAdmin() || Objects.equals(doc.getUserId(), user.id())
                || (user.roles().contains("DEPT_MANAGER") && user.deptId() != null
                && Objects.equals(doc.getDeptId(), user.deptId()));
    }

    public void requireRead(KbDocument doc, Long userId) {
        if (doc == null || !canRead(doc, userId)) throw new BizException(403, "无权访问该知识文档");
    }

    public void requireManage(KbDocument doc, Long userId, String permission) {
        if (doc == null) throw new BizException("文档不存在");
        if (!canManage(doc, userId, permission)) throw new BizException(403, "无权执行该文档操作");
    }

    public void requireManageAcl(KbDocument doc, Long userId) {
        if (doc == null) throw new BizException("文档不存在");
        if (!canManageAcl(doc, userId)) throw new BizException(403, "无权管理该文档授权");
    }

    public List<PermissionGrant> listGrants(Long documentId, Long operatorId) {
        KbDocument doc = documentMapper.selectById(documentId);
        requireManageAcl(doc, operatorId);
        return jdbcTemplate.query("SELECT id, document_id, subject_type, subject_id, permission "
                        + "FROM kb_document_permission WHERE document_id = ? ORDER BY id",
                (rs, rowNum) -> new PermissionGrant(rs.getLong("id"), rs.getLong("document_id"),
                        rs.getString("subject_type"), rs.getLong("subject_id"),
                        subjectName(rs.getString("subject_type"), rs.getLong("subject_id")),
                        rs.getString("permission")), documentId);
    }

    public void replaceGrants(Long documentId, List<GrantRequest> requests, Long operatorId) {
        KbDocument doc = documentMapper.selectById(documentId);
        requireManageAcl(doc, operatorId);
        List<GrantRequest> safe = requests == null ? List.of() : requests;
        for (GrantRequest request : safe) validateGrant(request);
        jdbcTemplate.update("DELETE FROM kb_document_permission WHERE document_id = ?", documentId);
        for (GrantRequest request : safe) {
            Long subjectId = "ALL".equalsIgnoreCase(request.subjectType()) ? 0L : request.subjectId();
            jdbcTemplate.update("INSERT INTO kb_document_permission "
                            + "(document_id, subject_type, subject_id, permission) VALUES (?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE permission = VALUES(permission)",
                    documentId, request.subjectType().toUpperCase(), subjectId, request.permission().toUpperCase());
        }
    }

    public Map<String, Object> grantOptions(Long documentId, Long operatorId) {
        KbDocument doc = documentMapper.selectById(documentId);
        requireManageAcl(doc, operatorId);
        List<Map<String, Object>> users = userMapper.selectList(null).stream().filter(u -> u.getDeleted() == null || u.getDeleted() == 0)
                .map(u -> Map.<String, Object>of("id", u.getId(), "name",
                        u.getRealName() == null || u.getRealName().isBlank() ? u.getUsername() : u.getRealName())).toList();
        List<Map<String, Object>> roles = roleMapper.selectList(null).stream()
                .map(r -> Map.<String, Object>of("id", r.getId(), "code", r.getRoleCode(), "name", r.getRoleName())).toList();
        List<Map<String, Object>> depts = jdbcTemplate.query("SELECT id, dept_name FROM sys_dept WHERE deleted = 0 ORDER BY id",
                (rs, rowNum) -> Map.of("id", rs.getLong("id"), "name", rs.getString("dept_name")));
        return Map.of("users", users, "roles", roles, "depts", depts);
    }

    private CurrentUser user(Long id) {
        SysUser user = userMapper.selectById(id);
        List<String> roleCodes = roleMapper.selectRoleCodesByUserId(id);
        return new CurrentUser(id, user == null ? null : user.getDeptId(),
                roleCodes == null ? Set.of() : new HashSet<>(roleCodes));
    }

    private boolean hasRoleGrantAtLeast(Long documentId, Set<String> roles, String permission) {
        if (roles == null || roles.isEmpty()) return false;
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_document_permission p "
                        + "JOIN sys_role r ON r.id = p.subject_id WHERE p.document_id = ? "
                        + "AND p.subject_type = 'ROLE' AND p.permission IN (" + permissionValues(permission) + ") "
                        + "AND r.role_code IN ("
                        + roles.stream().map(r -> "?").collect(Collectors.joining(",")) + ")",
                Integer.class, joinArgs(documentId, permission, roles)).intValue() > 0;
    }

    private Object[] joinArgs(Long documentId, String permission, Set<String> roles) {
        List<Object> args = new ArrayList<>(); args.add(documentId);
        args.addAll(requiredPermissions(permission));
        args.addAll(roles); return args.toArray();
    }

    private boolean hasGrantAtLeast(Long documentId, String subjectType, Long subjectId, String permission) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_document_permission "
                        + "WHERE document_id = ? AND subject_type = ? AND subject_id = ? AND permission IN ("
                        + permissionValues(permission) + ")", Integer.class,
                joinGrantArgs(documentId, subjectType, subjectId, permission));
        return count != null && count > 0;
    }

    private Object[] joinGrantArgs(Long documentId, String subjectType, Long subjectId, String permission) {
        List<Object> args = new ArrayList<>();
        args.add(documentId); args.add(subjectType); args.add(subjectId); args.addAll(requiredPermissions(permission));
        return args.toArray();
    }

    private List<String> requiredPermissions(String permission) {
        return switch (permission.toUpperCase()) {
            case "DELETE" -> List.of("DELETE");
            case "EDIT" -> List.of("EDIT", "DELETE");
            default -> List.of("READ", "EDIT", "DELETE");
        };
    }

    private String permissionValues(String permission) {
        return requiredPermissions(permission).stream().map(v -> "?").collect(Collectors.joining(","));
    }

    private void validateGrant(GrantRequest request) {
        if (request == null || request.subjectType() == null || request.permission() == null
                || !VALID_SUBJECTS.contains(request.subjectType().toUpperCase())
                || !VALID_PERMISSIONS.contains(request.permission().toUpperCase())
                || (!"ALL".equalsIgnoreCase(request.subjectType()) && request.subjectId() == null)
                || ("ALL".equalsIgnoreCase(request.subjectType()) && request.subjectId() != null && request.subjectId() != 0L)) {
            throw new BizException("文档授权参数无效");
        }
    }

    private String subjectName(String subjectType, Long subjectId) {
        if ("ALL".equals(subjectType)) return "全体用户";
        if ("USER".equals(subjectType)) {
            SysUser u = userMapper.selectById(subjectId); return u == null ? String.valueOf(subjectId) : u.getRealName();
        }
        if ("ROLE".equals(subjectType)) {
            var r = roleMapper.selectById(subjectId); return r == null ? String.valueOf(subjectId) : r.getRoleName();
        }
        var d = jdbcTemplate.query("SELECT dept_name FROM sys_dept WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, subjectId);
        return d == null ? String.valueOf(subjectId) : d;
    }

    public record GrantRequest(@JsonProperty("subjectType") @JsonAlias("subject_type") String subjectType,
                               @JsonProperty("subjectId") @JsonAlias("subject_id") Long subjectId,
                               String permission) {}
}

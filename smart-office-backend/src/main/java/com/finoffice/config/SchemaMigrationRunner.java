package com.finoffice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema 增量迁移组件（全部幂等，可重复执行，失败仅记录日志、不阻断启动）：
 * 1. agent_config 缺列补列：dify_mode、input_variable
 * 2. 按云端探测结果回填 dify_mode：会议纪要(id=2)=chat、PPT(id=4)=workflow
 * 3. 修复历史写入产生的中文乱码种子数据（部门/用户/角色/智能体名称）
 * 4. 补插办公写作助手（id=5）
 * 5. 会议-任务闭环：补充业务种子用户（员工/部门负责人），支撑待办转任务选人
 * 6. meeting_record 补列 dify_file_ids（纪要文件直传 Dify）
 * 7. meeting_record 补列 fail_reason（纪要生成失败留痕）
 * 8. 数据订正：已转任务的待办状态按任务看板刷齐（任务看板为状态单源）
 * 注意：Dify 密钥只存数据库，不落代码文件；种子密码哈希与 schema.sql 种子段一致（初始密码 123456）。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            addColumnIfMissing("agent_config", "dify_mode",
                    "ALTER TABLE agent_config ADD COLUMN dify_mode VARCHAR(20) NOT NULL "
                            + "DEFAULT 'chat' COMMENT 'Dify应用类型:chat对话型/workflow工作流'");
            addColumnIfMissing("agent_config", "input_variable",
                    "ALTER TABLE agent_config ADD COLUMN input_variable VARCHAR(32) DEFAULT NULL "
                            + "COMMENT 'workflow应用输入变量名'");

            // 会议时间列（工作台"今日会议"依赖），存量数据用创建时间回填
            addColumnIfMissing("meeting_record", "meeting_time",
                    "ALTER TABLE meeting_record ADD COLUMN meeting_time DATETIME COMMENT '会议时间' AFTER title");
            jdbcTemplate.update("UPDATE meeting_record SET meeting_time = create_time WHERE meeting_time IS NULL");

            // 按云端探测结果回填 dify_mode 与 workflow 输入变量名
            jdbcTemplate.update("UPDATE agent_config SET dify_mode = 'chat', input_variable = 'meeting_text' WHERE id = 2");
            jdbcTemplate.update("UPDATE agent_config SET dify_mode = 'workflow', input_variable = 'topic' WHERE id = 4");

            // 幂等修复存量乱码种子数据（UTF-8 被按 GBK 二次编码导致）
            jdbcTemplate.update("UPDATE sys_dept SET dept_name = '总经理办公室' WHERE id = 1");
            jdbcTemplate.update("UPDATE sys_dept SET dept_name = '金融科技部' WHERE id = 2");
            jdbcTemplate.update("UPDATE sys_dept SET dept_name = '运营管理部' WHERE id = 3");
            jdbcTemplate.update("UPDATE sys_dept SET dept_name = '人力资源部' WHERE id = 4");

            jdbcTemplate.update("UPDATE sys_user SET real_name = '系统管理员' WHERE id = 1");
            jdbcTemplate.update("UPDATE sys_user SET real_name = '张三' WHERE id = 2");
            jdbcTemplate.update("UPDATE sys_user SET real_name = '李四' WHERE id = 3");

            jdbcTemplate.update("UPDATE sys_role SET role_name = '系统管理员' WHERE id = 1");
            jdbcTemplate.update("UPDATE sys_role SET role_name = '部门负责人' WHERE id = 2");
            jdbcTemplate.update("UPDATE sys_role SET role_name = '普通员工' WHERE id = 3");
            jdbcTemplate.update("UPDATE sys_role SET role_name = '知识管理员' WHERE id = 4");

            jdbcTemplate.update("UPDATE agent_config SET name = '企业知识问答助手', "
                    + "description = '解答公司制度、流程规范、业务操作等企业知识问题，支持知识检索与智能兜底', icon = 'chat' WHERE id = 1");
            jdbcTemplate.update("UPDATE agent_config SET name = '会议纪要助手', "
                    + "description = '粘贴会议转写文本，自动生成结构化纪要、决议与待办拆解（含责任人/截止时间）', icon = 'document' WHERE id = 2");
            jdbcTemplate.update("UPDATE agent_config SET name = '行业洞察助手', "
                    + "description = '金融行业动态分析、政策解读与市场趋势洞察，支持图表可视化', icon = 'trend' WHERE id = 3");
            jdbcTemplate.update("UPDATE agent_config SET name = 'PPT大纲生成助手', "
                    + "description = '输入主题一键生成结构化PPT内容（大纲/每页要点/演讲备注/布局建议）', icon = 'ppt' WHERE id = 4");

            // 补插办公写作助手（id=5，INSERT IGNORE 幂等）
            jdbcTemplate.update("INSERT IGNORE INTO agent_config (id, name, description, icon, dify_app_id, dify_mode, enabled) "
                    + "VALUES (5, '办公写作助手', '公文、汇报、邮件、总结等办公文案的智能撰写与润色', 'document', '', 'chat', 1)");

            // ---- 业务种子用户（会议待办转任务选人依赖，幂等：按 username 先查后插）----
            // 新增部门：信息科技部（id=5）
            jdbcTemplate.update("INSERT IGNORE INTO sys_dept (id, dept_name, parent_id) VALUES (5, '信息科技部', 0)");
            // 新增用户：王五/李强/陈晨（普通员工）、赵敏/周正（部门负责人），初始密码均 123456
            insertUserIfAbsent("wangwu", "王五", 2L, "EMPLOYEE");
            insertUserIfAbsent("liqiang", "李强", 2L, "EMPLOYEE");
            insertUserIfAbsent("chenchen", "陈晨", 3L, "EMPLOYEE");
            insertUserIfAbsent("zhaomin", "赵敏", 3L, "DEPT_MANAGER");
            insertUserIfAbsent("zhouzheng", "周正", 5L, "DEPT_MANAGER");

            // 阶段3：会议-任务闭环 —— task 表建表（幂等）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS task ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "title VARCHAR(200) NOT NULL COMMENT '任务标题',"
                    + "description VARCHAR(1000) COMMENT '任务描述',"
                    + "assignee_user_id BIGINT COMMENT '负责人用户ID',"
                    + "dept_id BIGINT COMMENT '所属部门ID',"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/done',"
                    + "priority VARCHAR(20) NOT NULL DEFAULT 'medium' COMMENT 'high/medium/low',"
                    + "deadline DATETIME COMMENT '截止时间',"
                    + "source_meeting_id BIGINT COMMENT '来源会议ID',"
                    + "creator_user_id BIGINT NOT NULL COMMENT '创建人',"
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "INDEX idx_status_deadline (status, deadline),"
                    + "INDEX idx_assignee (assignee_user_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务看板'");

            // meeting_action_item 补列：assignee_id（责任人匹配）/ task_id（待办转任务防重复）
            addColumnIfMissing("meeting_action_item", "assignee_id",
                    "ALTER TABLE meeting_action_item ADD COLUMN assignee_id BIGINT COMMENT '责任人用户ID'");
            addColumnIfMissing("meeting_action_item", "task_id",
                    "ALTER TABLE meeting_action_item ADD COLUMN task_id BIGINT COMMENT '已转换任务ID'");

            // 纪要文件直传 Dify：会议记录挂载上传文件ID列表（JSON数组字符串）
            addColumnIfMissing("meeting_record", "dify_file_ids",
                    "ALTER TABLE meeting_record ADD COLUMN dify_file_ids VARCHAR(1000) "
                            + "COMMENT '已上传至Dify的纪要文件ID(JSON数组)' AFTER raw_text");

            // 纪要生成失败留痕列（异常类名+message摘要，供详情页展示与排障）
            addColumnIfMissing("meeting_record", "fail_reason",
                    "ALTER TABLE meeting_record ADD COLUMN fail_reason VARCHAR(500) "
                            + "COMMENT '纪要生成失败原因' AFTER summary");

            // 数据订正（幂等）：待办状态以任务看板为单源，已关联任务的待办按 task.status 刷齐
            int fixedActions = jdbcTemplate.update("UPDATE meeting_action_item a JOIN task t ON a.task_id = t.id "
                    + "SET a.status = CASE t.status WHEN 'in_progress' THEN 'in_progress' "
                    + "WHEN 'done' THEN 'completed' ELSE 'pending' END "
                    + "WHERE a.status <> CASE t.status WHEN 'in_progress' THEN 'in_progress' "
                    + "WHEN 'done' THEN 'completed' ELSE 'pending' END");
            if (fixedActions > 0) {
                log.info("已订正 {} 条待办与任务状态不一致记录（以任务看板为准）", fixedActions);
            }

            // briefing_record 建表（阶段5行业简报预留表结构，本轮不含业务代码）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS briefing_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "brief_date DATE NOT NULL COMMENT '简报日期',"
                    + "title VARCHAR(200) COMMENT '简报标题',"
                    + "content MEDIUMTEXT COMMENT '简报内容',"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT 'success/failed',"
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uk_date (brief_date)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业简报记录'");

            log.info("SchemaMigrationRunner 迁移完成");
        } catch (Exception e) {
            log.error("Schema 迁移失败，不阻断启动: {}", e.getMessage(), e);
        }
    }

    /** 初始密码 123456 的 BCrypt 哈希（与 schema.sql 种子段一致，非密钥类数据） */
    private static final String SEED_PASSWORD_HASH =
            "$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u";

    /** 幂等新增种子用户 + 角色关联：username 已存在则跳过 */
    private void insertUserIfAbsent(String username, String realName, Long deptId, String roleCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, dept_id, status, deleted) "
                + "VALUES (?, ?, ?, ?, 1, 0)", username, SEED_PASSWORD_HASH, realName, deptId);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        Integer roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?", Integer.class, roleCode);
        if (userId != null && roleId != null) {
            jdbcTemplate.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                    userId, roleId);
        }
        log.info("种子用户已创建: {}（{}）", username, realName);
    }

    private void addColumnIfMissing(String table, String column, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
            log.info("{} 表已新增 {} 列", table, column);
        }
    }
}

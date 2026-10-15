-- 确保初始化脚本以 UTF-8 解析，避免中文演示数据被按 latin1 二次编码产生乱码
SET NAMES utf8mb4;

-- 演示种子数据（Docker 部署时自动导入）
-- 仅在 smart_office 数据库首次创建时执行

-- 种子用户（密码 123456 BCrypt）
INSERT IGNORE INTO sys_dept (id, dept_name, parent_id) VALUES
(1, '总经理办公室', 0),(2, '金融科技部', 0),(3, '运营管理部', 0),(4, '人力资源部', 0),(5, '信息科技部', 0);

INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
(1, 'ADMIN', '系统管理员'),(2, 'DEPT_MANAGER', '部门负责人'),(3, 'EMPLOYEE', '普通员工'),(4, 'KB_ADMIN', '知识管理员');

INSERT IGNORE INTO sys_user (id, username, password, real_name, dept_id, status, deleted) VALUES
(1, 'admin', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '系统管理员', 1, 1, 0),
(2, 'zhangsan', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '张三', 2, 1, 0),
(3, 'lisi', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '李四', 3, 1, 0),
(4, 'wangwu', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '王五', 2, 1, 0),
(6, 'chenchen', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '陈晨', 3, 1, 0),
(7, 'zhaomin', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '赵敏', 3, 1, 0);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1,1),(2,3),(3,2),(4,3),(6,3),(7,2);

INSERT IGNORE INTO agent_config (id, name, description, icon, dify_app_id, dify_mode, enabled) VALUES
(1, '企业知识问答助手', '企业制度智能问答', 'chat', '', 'chat', 1),
(2, '会议纪要助手', 'AI生成结构化会议纪要', 'document', '', 'chat', 1),
(3, '行业洞察助手', '金融行业动态分析', 'trend', '', 'chat', 1),
(4, 'PPT大纲生成助手', '一键生成PPT大纲', 'ppt', '', 'workflow', 1),
(5, '办公写作助手', '周报/邮件/通知撰写', 'document', '', 'chat', 1);

-- 演示会议
INSERT IGNORE INTO meeting_record (id, user_id, title, meeting_time, source_type, raw_text, summary, status, create_time) VALUES
(100, 1, 'Q3产品规划周会', '2026-08-10 09:30:00', 'text', '主持人：张三。参会人：李四、王五。\n\n1. 张三汇报Q2数据：用户增长15%，留存率提升3个百分点。\n2. 李四提出移动端适配方案，预计3周开发。\n\n决议：Q3优先做付费转化优化。移动端适配项目启动。', '{"title":"Q3产品规划周会","abstract":"讨论了Q2产品数据、移动端适配等议题，明确了Q3重点方向。","decisionPoints":["Q3优先做付费转化优化","移动端适配项目启动"],"actionItems":[{"content":"完成付费转化优化方案设计","assignee":"李四","deadline":"2026-08-20"},{"content":"移动端适配资源协调","assignee":"王五","deadline":"2026-08-15"}]}', 'completed', NOW()),
(101, 1, '金融科技部月度例会', '2026-08-09 14:00:00', 'text', '主持人：周正。参会人：张三、李四。\n\n1. 周正总结上月：AI纪要准确率达92%，满意度4.3/5。\n2. 张三演示知识库问答。\n\n决议：知识库问答下周全公司推广。', '{"title":"金融科技部月度例会","abstract":"月度例会讨论AI平台试运行成果和推广计划。","decisionPoints":["知识库问答全公司推广"],"actionItems":[{"content":"知识库问答推广方案","assignee":"张三","deadline":"2026-08-18"}]}', 'completed', NOW());

INSERT IGNORE INTO meeting_action_item (meeting_id, content, assignee, status, deadline) VALUES
(100, '完成付费转化优化方案设计', '李四', 'pending', '2026-08-20 18:00:00'),
(100, '移动端适配资源协调', '王五', 'in_progress', '2026-08-15 18:00:00'),
(101, '知识库问答推广方案', '张三', 'in_progress', '2026-08-18 18:00:00');

-- 演示任务
INSERT IGNORE INTO task (id, title, description, assignee_user_id, dept_id, status, priority, deadline, creator_user_id, create_time) VALUES
(200, '完成付费转化优化方案设计', 'Q3重点：优化付费流程', 3, 3, 'pending', 'high', '2026-08-20 18:00:00', 1, NOW()),
(201, '移动端适配资源协调', '协调前端2人+后端1人', 4, 2, 'in_progress', 'high', '2026-08-15 18:00:00', 1, NOW()),
(202, '知识库问答推广方案', '编写文档+安排培训', 2, 2, 'in_progress', 'medium', '2026-08-18 18:00:00', 1, NOW()),
(203, '数据库慢查询优化', '索引重建+读写分离方案', 4, 2, 'done', 'medium', '2026-08-10 18:00:00', 1, NOW());

-- 演示简报
INSERT IGNORE INTO briefing_record (brief_date, title, content, status) VALUES
(CURDATE(), CONCAT('金融科技日报 ', CURDATE()), '## 今日关注\n\n**1. 央行发布金融科技发展规划（2026-2028）**\n明确提出到2028年金融科技核心软硬件自主可控率达到80%以上。\n\n**2. 证监会就AI投顾管理办法征求意见**\n要求AI投顾需持牌经营，算法模型需定期评估备案。\n\n---\n*本简报由恒生智能办公助手自动生成，仅供参考*', 'success');

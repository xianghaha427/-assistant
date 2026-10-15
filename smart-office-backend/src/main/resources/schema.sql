-- 确保初始化脚本以 UTF-8 解析，避免中文种子数据被按 latin1 二次编码产生乱码
SET NAMES utf8mb4;

-- ============================================================
-- 金融企业智能办公提效平台 - 数据库初始化脚本（幂等，可重复执行）
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_name   VARCHAR(64) NOT NULL,
    parent_id   BIGINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64) NOT NULL,
    password    VARCHAR(128) NOT NULL,
    real_name   VARCHAR(64),
    avatar      VARCHAR(255),
    dept_id     BIGINT,
    status      TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT DEFAULT 0,
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(64),
    module      VARCHAR(32),
    action      VARCHAR(128),
    detail      TEXT,
    result      VARCHAR(16),
    ip          VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

CREATE TABLE IF NOT EXISTS ai_session (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    agent_id              BIGINT,
    title                 VARCHAR(128),
    dify_conversation_id  VARCHAR(64),
    msg_count             INT DEFAULT 0,
    status                TINYINT DEFAULT 1,
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

CREATE TABLE IF NOT EXISTS ai_message (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id     BIGINT NOT NULL,
    role           VARCHAR(16) COMMENT 'user/assistant',
    content        MEDIUMTEXT,
    citations_json TEXT,
    tokens         INT DEFAULT 0,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息表';

CREATE TABLE IF NOT EXISTS agent_config (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(64) NOT NULL,
    description    VARCHAR(512),
    icon           VARCHAR(32),
    dify_app_id    VARCHAR(64) COMMENT 'Dify应用密钥，接入后填写',
    dify_mode      VARCHAR(20) NOT NULL DEFAULT 'chat' COMMENT 'Dify应用类型:chat对话型/workflow工作流',
    input_variable VARCHAR(32) DEFAULT NULL COMMENT 'workflow应用输入变量名',
    system_prompt  TEXT,
    tools_json     TEXT,
    enabled        TINYINT DEFAULT 1,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体配置表';

-- ============================ 初始化数据 ============================

INSERT IGNORE INTO sys_dept (id, dept_name, parent_id) VALUES
(1, '总经理办公室', 0),
(2, '金融科技部', 0),
(3, '运营管理部', 0),
(4, '人力资源部', 0),
(5, '信息科技部', 0);

-- 初始密码均为 123456（BCrypt）
INSERT IGNORE INTO sys_user (id, username, password, real_name, dept_id) VALUES
(1, 'admin',   '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '系统管理员', 1),
(2, 'zhangsan','$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '张三',     2),
(3, 'lisi',    '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '李四',     3),
-- 会议-任务闭环业务种子用户：员工与部门负责人（供待办转任务选人）
(4, 'wangwu',   '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '王五',     2),
(5, 'liqiang',  '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '李强',     2),
(6, 'chenchen', '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '陈晨',     3),
(7, 'zhaomin',  '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '赵敏',     3),
(8, 'zhouzheng','$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '周正',     5);

INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
(1, 'ADMIN',        '系统管理员'),
(2, 'DEPT_MANAGER', '部门负责人'),
(3, 'EMPLOYEE',     '普通员工'),
(4, 'KB_ADMIN',     '知识管理员');

-- role_id: 1=ADMIN 2=DEPT_MANAGER 3=EMPLOYEE 4=KB_ADMIN
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 3), (3, 2),
(4, 3), (5, 3), (6, 3), (7, 2), (8, 2);

-- 智能体种子数据（dify_app_id 密钥只存数据库，不写入本文件）
INSERT IGNORE INTO agent_config (id, name, description, icon, dify_app_id, dify_mode, input_variable, system_prompt, tools_json, enabled) VALUES
(1, '企业知识问答助手', '解答公司制度、流程规范、业务操作等企业知识问题，支持知识检索与智能兜底', 'chat', '', 'chat', NULL, NULL, NULL, 1),
(2, '会议纪要助手', '粘贴会议转写文本，自动生成结构化纪要、决议与待办拆解（含责任人/截止时间）', 'document', '', 'chat', 'meeting_text', NULL, NULL, 1),
(3, '行业洞察助手', '金融行业动态分析、政策解读与市场趋势洞察，支持图表可视化', 'trend', '', 'chat', NULL, NULL, NULL, 1),
(4, 'PPT大纲生成助手', '输入主题一键生成结构化PPT内容（大纲/每页要点/演讲备注/布局建议）', 'ppt', '', 'workflow', 'topic', NULL, NULL, 1),
(5, '办公写作助手', '公文、汇报、邮件、总结等办公文案的智能撰写与润色', 'document', '', 'chat', NULL, NULL, NULL, 1);
-- ============================ 智能会议模块 ============================

CREATE TABLE IF NOT EXISTS meeting_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '创建人',
  title VARCHAR(200) NOT NULL COMMENT '会议标题',
  meeting_time DATETIME COMMENT '会议时间',
  source_type VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '来源类型: text/audio',
  raw_text MEDIUMTEXT COMMENT '原始转写文本',
  dify_file_ids VARCHAR(1000) COMMENT '已上传至Dify的纪要文件ID(JSON数组)',
  audio_url VARCHAR(500) COMMENT '音频文件路径',
  summary MEDIUMTEXT COMMENT 'AI生成的会议纪要(JSON格式)',
  fail_reason VARCHAR(500) COMMENT '纪要生成失败原因',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/processing/completed/failed',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议记录';

CREATE TABLE IF NOT EXISTS meeting_action_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  meeting_id BIGINT NOT NULL COMMENT '关联会议记录ID',
  content VARCHAR(500) NOT NULL COMMENT '待办内容',
  assignee VARCHAR(100) COMMENT '责任人',
  assignee_id BIGINT COMMENT '责任人用户ID(按姓名匹配sys_user)',
  task_id BIGINT COMMENT '已转换任务ID(防重复+状态双向同步)',
  deadline DATETIME COMMENT '截止时间',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/in_progress/completed',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_meeting_id (meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议待办事项';

-- ============================ 知识库模块 ============================

CREATE TABLE IF NOT EXISTS kb_document (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '上传人',
  dept_id BIGINT COMMENT '归属部门',
  name VARCHAR(200) NOT NULL COMMENT '文档名称',
  file_type VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '文件类型: text/pdf/docx',
  file_url VARCHAR(500) COMMENT '文件存储路径',
  content MEDIUMTEXT COMMENT '文档文本内容',
  category VARCHAR(64) DEFAULT '未分类' COMMENT '文档分类',
  visibility VARCHAR(20) NOT NULL DEFAULT 'private' COMMENT '历史兼容字段，实际访问控制由 kb_document_permission 决定',
  dify_document_id VARCHAR(100) COMMENT 'Dify 中的文档ID',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/indexing/completed/failed/local_only',
  word_count INT DEFAULT 0 COMMENT '字数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE IF NOT EXISTS kb_document_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  document_id BIGINT NOT NULL COMMENT '文档ID',
  subject_type VARCHAR(16) NOT NULL COMMENT 'ALL/USER/ROLE/DEPT',
  subject_id BIGINT NOT NULL COMMENT '用户/角色/部门ID',
  permission VARCHAR(16) NOT NULL COMMENT 'READ/EDIT/DELETE',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_kb_permission (document_id, subject_type, subject_id, permission),
  INDEX idx_kb_permission_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档细粒度授权';

-- ============================ 任务模块 ============================

CREATE TABLE IF NOT EXISTS inbox_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL COMMENT '收件人',
  sender_user_id BIGINT NOT NULL COMMENT '发件人',
  source_type VARCHAR(32) NOT NULL COMMENT 'writer_weekly/writer_email/writer_notice',
  title VARCHAR(200) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_inbox_recipient (recipient_user_id, is_read, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务收件箱';

CREATE TABLE IF NOT EXISTS task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL COMMENT '任务标题',
  description VARCHAR(1000) COMMENT '任务描述',
  assignee_user_id BIGINT COMMENT '负责人用户ID',
  dept_id BIGINT COMMENT '所属部门ID',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/in_progress/done',
  priority VARCHAR(20) NOT NULL DEFAULT 'medium' COMMENT '优先级: high/medium/low',
  deadline DATETIME COMMENT '截止时间',
  source_meeting_id BIGINT COMMENT '来源会议ID',
  creator_user_id BIGINT NOT NULL COMMENT '创建人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status_deadline (status, deadline),
  INDEX idx_assignee (assignee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务看板';

-- ============================ 行业简报记录（表结构预留，业务逻辑阶段5实现） ============================

CREATE TABLE IF NOT EXISTS briefing_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  brief_date DATE NOT NULL COMMENT '简报日期',
  title VARCHAR(200) COMMENT '简报标题',
  content MEDIUMTEXT COMMENT '简报内容',
  status VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '状态: success/failed',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_date (brief_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业简报记录';

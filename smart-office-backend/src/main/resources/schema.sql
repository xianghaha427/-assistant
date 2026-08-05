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
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64) NOT NULL,
    description   VARCHAR(512),
    icon          VARCHAR(32),
    dify_app_id   VARCHAR(64) COMMENT 'Dify应用密钥，接入后填写',
    system_prompt TEXT,
    tools_json    TEXT,
    enabled       TINYINT DEFAULT 1,
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体配置表';

-- ============================ 初始化数据 ============================

INSERT IGNORE INTO sys_dept (id, dept_name, parent_id) VALUES
(1, '总经理办公室', 0),
(2, '金融科技部', 0),
(3, '运营管理部', 0),
(4, '人力资源部', 0);

-- 初始密码均为 123456（BCrypt）
INSERT IGNORE INTO sys_user (id, username, password, real_name, dept_id) VALUES
(1, 'admin',   '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '系统管理员', 1),
(2, 'zhangsan','$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '张三',     2),
(3, 'lisi',    '$2a$10$I.rGauj2l7m9B1ymyLyiAe4a2sAFsR/g2V0/qUv1plC9f89lNSg.u', '李四',     3);

INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
(1, 'ADMIN',        '系统管理员'),
(2, 'DEPT_MANAGER', '部门负责人'),
(3, 'EMPLOYEE',     '普通员工'),
(4, 'KB_ADMIN',     '知识管理员');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 3), (3, 2);

INSERT IGNORE INTO agent_config (id, name, description, icon, system_prompt, enabled) VALUES
(1, '企业知识问答助手', '面向企业制度、流程、项目资料的智能问答，回答附带引用来源。', 'chat',
   '你是金融企业的智能办公助手，请基于知识库内容准确回答用户关于企业制度、流程的问题。若知识库中没有相关内容，请如实说明。', 1),
(2, '会议纪要助手', '从会议转写文本生成结构化纪要，自动提取行动项、责任人与截止时间。', 'document',
   '你是会议纪要整理专家，请从会议转写文本中生成结构化纪要，并提取行动项（内容/责任人/截止时间）。', 1),
(3, '行业洞察助手', '搜集整理金融行业法规与资讯，生成每日行业简报与观点总结。', 'trend',
   '你是金融行业研究助手，请对提供的资讯进行分类、摘要与观点总结，输出每日行业简报。', 1);

-- ============================ 智能会议模块 ============================

CREATE TABLE IF NOT EXISTS meeting_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '创建人',
  title VARCHAR(200) NOT NULL COMMENT '会议标题',
  source_type VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '来源类型: text/audio',
  raw_text MEDIUMTEXT COMMENT '原始转写文本',
  audio_url VARCHAR(500) COMMENT '音频文件路径',
  summary MEDIUMTEXT COMMENT 'AI生成的会议纪要(JSON格式)',
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
  deadline DATETIME COMMENT '截止时间',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/in_progress/completed',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_meeting_id (meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议待办事项';

-- ============================ ֪ʶ��ģ�� ============================

CREATE TABLE IF NOT EXISTS kb_document (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '�ϴ���',
  name VARCHAR(200) NOT NULL COMMENT '�ĵ�����',
  file_type VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '�ļ�����: text/pdf/docx',
  file_url VARCHAR(500) COMMENT '�ļ��洢·��',
  content MEDIUMTEXT COMMENT '�ĵ��ı�����',
  dify_document_id VARCHAR(100) COMMENT 'Dify �е��ĵ�ID',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '״̬: pending/indexing/completed/failed/local_only',
  word_count INT DEFAULT 0 COMMENT '����',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='֪ʶ���ĵ�';

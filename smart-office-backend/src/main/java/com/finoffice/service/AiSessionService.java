package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.finoffice.common.BizException;
import com.finoffice.entity.AiMessage;
import com.finoffice.entity.AiSession;
import com.finoffice.mapper.AiMessageMapper;
import com.finoffice.mapper.AiSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 会话服务：会话管理 + 消息存取
 */
@Service
@RequiredArgsConstructor
public class AiSessionService {

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;

    /** 创建会话 */
    public AiSession createSession(Long userId, Long agentId, String title) {
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setAgentId(agentId);
        session.setTitle(title == null || title.isBlank() ? "新对话" : title);
        sessionMapper.insert(session);
        return session;
    }

    /** 我的会话列表（最近更新的在前） */
    public List<AiSession> listByUser(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<AiSession>()
                        .eq(AiSession::getUserId, userId)
                        .eq(AiSession::getStatus, 1)
                        .orderByDesc(AiSession::getUpdateTime));
    }

    /** 会话历史消息（含归属校验） */
    public List<AiMessage> listMessages(Long userId, Long sessionId) {
        checkOwner(userId, sessionId);
        return messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, sessionId)
                        .orderByAsc(AiMessage::getId));
    }

    /**
     * 获取或自动创建会话：未传 sessionId 时按消息内容创建新会话
     */
    public Long getOrCreateSession(Long userId, Long sessionId, String firstMessage) {
        if (sessionId == null) {
            String title = firstMessage.length() > 20 ? firstMessage.substring(0, 20) + "…" : firstMessage;
            return createSession(userId, null, title).getId();
        }
        checkOwner(userId, sessionId);
        return sessionId;
    }

    /** 删除会话（含归属校验，级联删除消息） */
    public void deleteSession(Long userId, Long sessionId) {
        checkOwner(userId, sessionId);
        messageMapper.delete(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    /** 校验会话归属当前用户 */
    public void checkOwner(Long userId, Long sessionId) {
        Long count = sessionMapper.selectCount(
                new LambdaQueryWrapper<AiSession>()
                        .eq(AiSession::getId, sessionId)
                        .eq(AiSession::getUserId, userId));
        if (count == null || count == 0) {
            throw new BizException("会话不存在");
        }
    }

    /** 保存一条消息 */
    public void saveMessage(Long sessionId, String role, String content) {
        AiMessage msg = new AiMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        messageMapper.insert(msg);
    }

    /** 累加会话消息数 */
    public void incrementMsgCount(Long sessionId, int n) {
        sessionMapper.update(null,
                new LambdaUpdateWrapper<AiSession>()
                        .eq(AiSession::getId, sessionId)
                        .setSql("msg_count = msg_count + " + n));
    }

    /** 根据 ID 获取会话 */
    public AiSession getSessionById(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    /** 更新 Dify 会话 ID（用于多轮对话上下文关联） */
    public void updateDifyConversationId(Long sessionId, String conversationId) {
        sessionMapper.update(null,
                new LambdaUpdateWrapper<AiSession>()
                        .eq(AiSession::getId, sessionId)
                        .set(AiSession::getDifyConversationId, conversationId));
    }
}

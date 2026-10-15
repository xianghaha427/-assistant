package com.finoffice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 业务收件箱：承载周报、邮件和通知公告正文，不与系统铃铛通知混用。 */
@Service
@RequiredArgsConstructor
public class InboxService {
    private final JdbcTemplate jdbc;

    public int sendToUsers(Long senderId, String sourceType, String title, String content, List<Long> userIds) {
        int sent = 0;
        for (Long userId : userIds) {
            jdbc.update("INSERT INTO inbox_message (recipient_user_id, sender_user_id, source_type, title, content, is_read, create_time) "
                            + "VALUES (?, ?, ?, ?, ?, 0, ?)",
                    userId, senderId, sourceType, title, content, LocalDateTime.now());
            sent++;
        }
        return sent;
    }

    public List<Map<String, Object>> list(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.queryForList("SELECT id, sender_user_id, source_type, title, content, is_read, create_time "
                + "FROM inbox_message WHERE recipient_user_id = ? ORDER BY create_time DESC LIMIT " + safeLimit, userId);
    }

    public int unreadCount(Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM inbox_message WHERE recipient_user_id = ? AND is_read = 0", Integer.class, userId);
        return count == null ? 0 : count;
    }

    public void markRead(Long userId, Long messageId) {
        jdbc.update("UPDATE inbox_message SET is_read = 1 WHERE id = ? AND recipient_user_id = ?", messageId, userId);
    }

    public void markAllRead(Long userId) {
        jdbc.update("UPDATE inbox_message SET is_read = 1 WHERE recipient_user_id = ? AND is_read = 0", userId);
    }
}

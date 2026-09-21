package com.finoffice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 站内通知中心（赛题 P0 基础设施）：
 * 统一的站内消息汇聚点——会议创建、纪要完成、简报生成、任务到期等事件均写入通知表。
 * 前端铃铛图标显示未读计数，点击展开通知列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JdbcTemplate jdbc;

    /** 发送通知 */
    public void send(Long userId, String type, String title, String content, String link) {
        try {
            jdbc.update("INSERT INTO sys_notification (user_id, type, title, content, link, is_read, create_time) "
                    + "VALUES (?, ?, ?, ?, ?, 0, ?)", userId, type, title, content, link, LocalDateTime.now());
        } catch (Exception e) {
            log.error("通知写入失败: {}", e.getMessage());
        }
    }

    /** 发送给所有用户（系统级通知） */
    public void broadcast(String type, String title, String content, String link) {
        try {
            List<Long> userIds = jdbc.queryForList("SELECT id FROM sys_user WHERE status = 1 AND deleted = 0", Long.class);
            for (Long uid : userIds) {
                send(uid, type, title, content, link);
            }
        } catch (Exception e) {
            log.error("广播通知失败: {}", e.getMessage());
        }
    }

    /** 获取用户通知列表（最近50条） */
    public List<Map<String, Object>> list(Long userId) {
        return jdbc.queryForList(
                "SELECT id, type, title, content, link, is_read, create_time FROM sys_notification "
                        + "WHERE user_id = ? ORDER BY create_time DESC LIMIT 50", userId);
    }

    /** 未读计数 */
    public int unreadCount(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_notification WHERE user_id = ? AND is_read = 0", Integer.class, userId);
        return count != null ? count : 0;
    }

    /** 标记为已读 */
    public void markRead(Long userId, Long notificationId) {
        jdbc.update("UPDATE sys_notification SET is_read = 1 WHERE id = ? AND user_id = ?", notificationId, userId);
    }

    /** 全部标记已读 */
    public void markAllRead(Long userId) {
        jdbc.update("UPDATE sys_notification SET is_read = 1 WHERE user_id = ? AND is_read = 0", userId);
    }
}

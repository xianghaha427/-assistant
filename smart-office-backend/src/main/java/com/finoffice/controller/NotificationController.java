package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.R;
import com.finoffice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 通知列表 */
    @GetMapping
    public R<java.util.List<Map<String, Object>>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(notificationService.list(userId));
    }

    /** 未读数 */
    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        notificationService.markRead(userId, id);
        return R.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = StpUtil.getLoginIdAsLong();
        notificationService.markAllRead(userId);
        return R.ok();
    }
}

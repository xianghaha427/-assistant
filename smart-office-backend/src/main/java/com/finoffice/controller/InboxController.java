package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.R;
import com.finoffice.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 业务收件箱接口。 */
@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {
    private final InboxService inboxService;

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(inboxService.list(StpUtil.getLoginIdAsLong(), limit));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", inboxService.unreadCount(StpUtil.getLoginIdAsLong())));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        inboxService.markRead(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        inboxService.markAllRead(StpUtil.getLoginIdAsLong());
        return R.ok();
    }
}

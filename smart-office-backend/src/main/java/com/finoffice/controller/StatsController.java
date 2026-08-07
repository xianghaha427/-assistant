package com.finoffice.controller;

import com.finoffice.common.R;
import com.finoffice.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据看板统计接口（/api/stats）：登录可访问，数据范围按角色限定。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 指标总览：会议/纪要/AI使用/任务/知识文档/简报（SNAKE_CASE） */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(statsService.overview());
    }

    /** 近7天趋势：按日 AI 使用次数与会议数 */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend() {
        return R.ok(statsService.trend());
    }
}

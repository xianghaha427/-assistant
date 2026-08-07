package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.OpLogger;
import com.finoffice.common.R;
import com.finoffice.entity.BriefingRecord;
import com.finoffice.service.BriefingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 行业简报接口（/api/briefing）：登录可访问（WebConfig 对 /api/** 统一校验登录，无需 ADMIN）。
 */
@Slf4j
@RestController
@RequestMapping("/api/briefing")
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    /**
     * 手动触发采用异步执行：LLM 生成耗时 10 秒 ~ 2 分钟，独立单线程池避免占用 Web 线程；
     * 接口立即返回「已触发」，前端轮询 /today 获取结果；重复触发由服务层互斥 + uk_date 幂等去重。
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 今日简报：未生成时返回空结构（字段齐全、值为 null），前端「立即生成」前预检用 */
    @GetMapping("/today")
    public R<Map<String, Object>> today() {
        BriefingRecord rec = briefingService.getToday();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("brief_date", LocalDate.now().toString());
        data.put("id", rec != null ? rec.getId() : null);
        data.put("title", rec != null ? rec.getTitle() : null);
        data.put("content", rec != null ? rec.getContent() : null);
        data.put("status", rec != null ? rec.getStatus() : null);
        data.put("create_time", rec != null ? rec.getCreateTime() : null);
        return R.ok(data);
    }

    /** 历史简报分页列表（含全文，前端展开查看） */
    @GetMapping("/history")
    public R<Map<String, Object>> history(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return R.ok(briefingService.history(page, size));
    }

    /**
     * 手动触发生成今日简报（演示用）：异步执行，立即返回结果。
     * force=false（默认）：当日已有 success 记录时同步返回 {alreadyExists:true}，不触发生成（前端据此弹二选一）；
     * force=true：强制重新生成并覆盖当日记录（服务层 existing 转更新路径）；
     * 当日无记录：正常触发，返回 {triggered:true}。
     */
    @PostMapping("/generate-now")
    public R<Map<String, Object>> generateNow(@RequestParam(defaultValue = "false") boolean force) {
        LocalDate today = LocalDate.now();

        // 预检：当日已生成成功 且 未强制覆盖 → 同步告知，不触发
        BriefingRecord current = briefingService.getToday();
        boolean alreadyExists = current != null && "success".equals(current.getStatus())
                && current.getContent() != null && !current.getContent().isBlank();
        if (alreadyExists && !force) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("alreadyExists", true);
            data.put("triggered", false);
            data.put("brief_date", today.toString());
            data.put("message", "今日简报已生成");
            return R.ok(data);
        }

        OpLogger.log(StpUtil.getLoginIdAsLong(), "BRIEFING",
                force ? "简报强制重新生成" : "简报生成触发",
                (force ? "强制重新生成 " : "手动触发 ") + today + " 行业简报");
        executor.submit(() -> {
            try {
                briefingService.generate(today, force);
            } catch (Exception e) {
                log.error("手动触发简报生成失败：{}", e.getMessage(), e);
            }
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("triggered", true);
        data.put("brief_date", today.toString());
        data.put("message", force ? "已触发重新生成，将覆盖今日简报" : "已触发简报生成，请稍候查看");
        return R.ok(data);
    }
}

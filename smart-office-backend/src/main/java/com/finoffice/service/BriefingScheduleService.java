package com.finoffice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 行业简报定时任务：每天早上 8:00 自动生成当日简报。
 * 提交到独立单线程池异步执行，避免 LLM 长耗时调用（10秒~2分钟）阻塞调度线程；
 * 生成过程由 BriefingService 内部互斥 + uk_date 唯一键保证幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingScheduleService {

    private final BriefingService briefingService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** cron 可由配置 app.briefing-cron 覆盖，默认每日 08:00 */
    @Scheduled(cron = "${app.briefing-cron:0 0 8 * * ?}")
    public void dailyBriefing() {
        LocalDate today = LocalDate.now();
        log.info("简报定时任务触发：开始生成 {} 今日简报", today);
        executor.submit(() -> {
            try {
                briefingService.generate(today);
            } catch (Exception e) {
                log.error("定时任务生成简报失败（{}）：{}", today, e.getMessage(), e);
            }
        });
    }
}

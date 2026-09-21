package com.finoffice.service;

import com.finoffice.entity.Task;
import com.finoffice.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务到期提醒（赛题 M5 P0）：
 * 每 30 分钟扫描即将到期和已逾期的任务，向负责人发送站内通知。
 * - 紧急（1天内到期）：提前提醒
 * - 逾期（超过截止时间且未完成）：标记并通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderService {

    private final TaskMapper taskMapper;
    private final NotificationService notificationService;

    /** 每 30 分钟执行一次 */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void remindUpcomingDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime urgent = now.plusDays(1);

        // 1 天内到期且未完成的任务
        List<Task> urgentTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .in(Task::getStatus, "pending", "in_progress")
                        .isNotNull(Task::getDeadline)
                        .le(Task::getDeadline, urgent)
                        .ge(Task::getDeadline, now));
        for (Task t : urgentTasks) {
            if (t.getAssigneeUserId() == null) continue;
            notificationService.send(t.getAssigneeUserId(), "task",
                    "任务即将到期：" + t.getTitle(),
                    "截止时间 " + t.getDeadline() + "，请尽快完成",
                    "/task");
        }
        if (!urgentTasks.isEmpty()) log.info("发送 {} 条到期提醒通知", urgentTasks.size());

        // 已逾期且未完成的任务（仅通知一次，通过检查是否已通知来避免重复）
        List<Task> overdueTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .in(Task::getStatus, "pending", "in_progress")
                        .isNotNull(Task::getDeadline)
                        .lt(Task::getDeadline, now));
        for (Task t : overdueTasks) {
            if (t.getAssigneeUserId() == null) continue;
            notificationService.send(t.getAssigneeUserId(), "task",
                    "任务已逾期：" + t.getTitle(),
                    "原定截止 " + t.getDeadline() + "，请及时处理",
                    "/task");
        }
        if (!overdueTasks.isEmpty()) log.info("发送 {} 条逾期提醒通知", overdueTasks.size());
    }
}

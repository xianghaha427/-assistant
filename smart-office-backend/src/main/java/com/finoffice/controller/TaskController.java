package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.R;
import com.finoffice.entity.Task;
import com.finoffice.service.TaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务看板接口（三级可见性 + 写操作归属校验均在 Service 层完成）
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 看板三列分组：pending / in_progress / done */
    @GetMapping("/board")
    public R<Map<String, List<Task>>> board() {
        return R.ok(taskService.board());
    }

    /** 新建任务 */
    @PostMapping
    public R<Task> create(@RequestBody TaskCreateReq req) {
        Task task = new Task();
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setAssigneeUserId(req.getAssigneeUserId());
        task.setPriority(req.getPriority());
        task.setDeadline(req.getDeadline());
        task.setSourceMeetingId(req.getSourceMeetingId());
        return R.ok(taskService.create(StpUtil.getLoginIdAsLong(), task));
    }

    /** 更新任务（状态推进/字段修改） */
    @PutMapping("/{id}")
    public R<Task> update(@PathVariable Long id, @RequestBody TaskUpdateReq req) {
        Task patch = new Task();
        patch.setTitle(req.getTitle());
        patch.setDescription(req.getDescription());
        patch.setAssigneeUserId(req.getAssigneeUserId());
        patch.setStatus(req.getStatus());
        patch.setPriority(req.getPriority());
        patch.setDeadline(req.getDeadline());
        return R.ok(taskService.update(StpUtil.getLoginIdAsLong(), id, patch));
    }

    /** 删除任务 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /** 会议待办转任务（assigneeId 必填：前端弹窗所选执行人） */
    @PostMapping("/from-action")
    public R<Task> fromAction(@RequestBody FromActionReq req) {
        return R.ok(taskService.convertFromAction(StpUtil.getLoginIdAsLong(), req.getActionItemId(), req.getAssigneeId()));
    }

    @Data
    public static class TaskCreateReq {
        private String title;
        private String description;
        private Long assigneeUserId;
        private String priority;
        private LocalDateTime deadline;
        private Long sourceMeetingId;
    }

    @Data
    public static class TaskUpdateReq {
        private String title;
        private String description;
        private Long assigneeUserId;
        private String status;
        private String priority;
        private LocalDateTime deadline;
    }

    @Data
    public static class FromActionReq {
        private Long actionItemId;
        /** 任务执行人用户ID（前端弹窗所选，必填） */
        private Long assigneeId;
    }
}

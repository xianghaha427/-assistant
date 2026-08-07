package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.common.BizException;
import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.entity.SysUser;
import com.finoffice.entity.Task;
import com.finoffice.mapper.MeetingActionItemMapper;
import com.finoffice.mapper.MeetingRecordMapper;
import com.finoffice.mapper.SysUserMapper;
import com.finoffice.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务看板服务：
 * 三级可见性（DataScopeHelper 角色上下文）——
 * ADMIN 全量；DEPT_MANAGER 按 dept_id；EMPLOYEE 按 assignee=本人或 creator=本人。
 * 写操作一律先做可见性校验，非 ADMIN 只能改自己可见的任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final MeetingActionItemMapper actionItemMapper;
    private final MeetingRecordMapper meetingMapper;
    private final DataScopeHelper dataScopeHelper;

    private static final List<String> BOARD_COLUMNS = List.of("pending", "in_progress", "done");

    /** 看板：按状态分组返回三列（pending/in_progress/done） */
    public Map<String, List<Task>> board() {
        DataScopeHelper.UserScope scope = dataScopeHelper.currentScope();
        List<Task> tasks = taskMapper.selectList(
                taskScope(new LambdaQueryWrapper<Task>(), scope)
                        .orderByAsc(Task::getDeadline));
        fillUserNames(tasks);

        Map<String, List<Task>> board = new LinkedHashMap<>();
        for (String col : BOARD_COLUMNS) {
            board.put(col, new ArrayList<>());
        }
        for (Task t : tasks) {
            String col = BOARD_COLUMNS.contains(t.getStatus()) ? t.getStatus() : "pending";
            board.get(col).add(t);
        }
        return board;
    }

    /** 创建任务：负责人默认当前用户；dept_id 跟随负责人（供部门负责人范围可见） */
    public Task create(Long creatorUserId, Task task) {
        if (task.getTitle() == null || task.getTitle().isBlank()) {
            throw new BizException("任务标题不能为空");
        }
        Long assigneeId = task.getAssigneeUserId() != null ? task.getAssigneeUserId() : creatorUserId;
        task.setAssigneeUserId(assigneeId);
        SysUser assignee = userMapper.selectById(assigneeId);
        task.setDeptId(assignee != null ? assignee.getDeptId() : null);
        task.setStatus("pending");
        task.setPriority(normalizePriority(task.getPriority()));
        task.setCreatorUserId(creatorUserId);
        taskMapper.insert(task);
        fillUserNames(List.of(task));
        return task;
    }

    /** 更新任务（含可见性校验，仅更新非空字段） */
    public Task update(Long operatorId, Long taskId, Task patch) {
        Task task = requireVisible(operatorId, taskId);
        if (patch.getTitle() != null && !patch.getTitle().isBlank()) {
            task.setTitle(patch.getTitle());
        }
        if (patch.getDescription() != null) {
            task.setDescription(patch.getDescription());
        }
        if (patch.getStatus() != null) {
            task.setStatus(patch.getStatus());
        }
        if (patch.getPriority() != null) {
            task.setPriority(normalizePriority(patch.getPriority()));
        }
        if (patch.getDeadline() != null) {
            task.setDeadline(patch.getDeadline());
        }
        if (patch.getAssigneeUserId() != null) {
            task.setAssigneeUserId(patch.getAssigneeUserId());
            SysUser assignee = userMapper.selectById(patch.getAssigneeUserId());
            if (assignee != null) {
                task.setDeptId(assignee.getDeptId());
            }
        }
        taskMapper.updateById(task);
        // 双向同步：会议待办转出的任务状态变更时，回写对应 meeting_action_item.status
        if (patch.getStatus() != null) {
            syncActionItemStatus(task);
        }
        fillUserNames(List.of(task));
        return task;
    }

    /** 删除任务（含可见性校验） */
    public void delete(Long operatorId, Long taskId) {
        requireVisible(operatorId, taskId);
        taskMapper.deleteById(taskId);
    }

    /**
     * 会议待办转任务：
     * 从 meeting_action_item 带出 content/会议标题/source_meeting_id，
     * 并回写该待办的 task_id 防止重复转换。
     */
    @Transactional
    public Task convertFromAction(Long operatorId, Long actionItemId, Long assigneeId) {
        if (assigneeId == null) {
            throw new BizException("请选择任务执行人");
        }
        MeetingActionItem item = actionItemMapper.selectById(actionItemId);
        if (item == null) {
            throw new BizException("待办事项不存在");
        }
        if (item.getTaskId() != null) {
            throw new BizException("该待办已转换为任务，请勿重复转换");
        }

        MeetingRecord meeting = meetingMapper.selectById(item.getMeetingId());
        // 非 ADMIN 须可见来源会议
        DataScopeHelper.UserScope scope = dataScopeHelper.currentScope();
        if (!scope.isAdmin()) {
            if (meeting == null || !meeting.getUserId().equals(operatorId)) {
                throw new BizException("无权转换该待办");
            }
        }

        Task task = new Task();
        task.setTitle(meeting != null ? meeting.getTitle() : item.getContent());
        task.setDescription(item.getContent());
        // 执行人以入参（前端弹窗所选）为准，不再回落 operatorId；
        // 待办上纪要匹配的 assignee_id 仅作为前端弹窗的预填建议
        task.setAssigneeUserId(assigneeId);
        SysUser assignee = userMapper.selectById(assigneeId);
        task.setDeptId(assignee != null ? assignee.getDeptId() : null);
        // 产品决策（用户拍板）：转换即重新立项，新任务恒定从"待办"列（pending）开始，不继承待办状态
        task.setStatus("pending");
        task.setPriority("medium");
        task.setDeadline(item.getDeadline());
        task.setSourceMeetingId(item.getMeetingId());
        task.setCreatorUserId(operatorId);
        taskMapper.insert(task);

        // 回写 task_id 防重复转换；源待办状态一并重置为 pending（与任务起点保持一致）
        item.setTaskId(task.getId());
        item.setStatus("pending");
        actionItemMapper.updateById(item);

        fillUserNames(List.of(task));
        return task;
    }

    // ==================== 私有方法 ====================

    /** 任务可见范围：ADMIN 全量；DEPT_MANAGER 按 dept_id；EMPLOYEE 负责人或创建人是本人 */
    private LambdaQueryWrapper<Task> taskScope(LambdaQueryWrapper<Task> wrapper, DataScopeHelper.UserScope scope) {
        if (scope.isAdmin()) {
            return wrapper;
        }
        if (scope.isDeptManager() && scope.deptId() != null) {
            return wrapper.eq(Task::getDeptId, scope.deptId());
        }
        return wrapper.and(q -> q.eq(Task::getAssigneeUserId, scope.userId())
                .or().eq(Task::getCreatorUserId, scope.userId()));
    }

    /** 写操作归属校验：任务不存在或当前用户不可见时抛业务异常 */
    private Task requireVisible(Long operatorId, Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        DataScopeHelper.UserScope scope = dataScopeHelper.currentScope();
        boolean visible = scope.isAdmin()
                || (scope.isDeptManager() && scope.deptId() != null && scope.deptId().equals(task.getDeptId()))
                || operatorId.equals(task.getAssigneeUserId())
                || operatorId.equals(task.getCreatorUserId());
        if (!visible) {
            throw new BizException("无权操作该任务");
        }
        return task;
    }

    /** 会议待办转出的任务状态变更时，同步关联待办状态（待办三态：pending/in_progress/completed） */
    private void syncActionItemStatus(Task task) {
        MeetingActionItem item = actionItemMapper.selectOne(new LambdaQueryWrapper<MeetingActionItem>()
                .eq(MeetingActionItem::getTaskId, task.getId())
                .last("LIMIT 1"));
        if (item == null) {
            return;
        }
        String actionStatus;
        if ("in_progress".equals(task.getStatus())) {
            actionStatus = "in_progress";
        } else if ("done".equals(task.getStatus())) {
            actionStatus = "completed";
        } else {
            actionStatus = "pending";
        }
        if (!actionStatus.equals(item.getStatus())) {
            item.setStatus(actionStatus);
            actionItemMapper.updateById(item);
            log.info("任务{}状态变更为{}，联动会议待办{}同步为{}", task.getId(), task.getStatus(), item.getId(), actionStatus);
        }
    }

    /** 批量组装负责人/创建人姓名 */
    private void fillUserNames(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (Task t : tasks) {
            if (t.getAssigneeUserId() != null) {
                userIds.add(t.getAssigneeUserId());
            }
            if (t.getCreatorUserId() != null) {
                userIds.add(t.getCreatorUserId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() == null ? "" : u.getRealName(), (a, b) -> a));
        for (Task t : tasks) {
            t.setAssigneeName(nameMap.get(t.getAssigneeUserId()));
            t.setCreatorName(nameMap.get(t.getCreatorUserId()));
        }
    }

    private String normalizePriority(String priority) {
        return ("high".equals(priority) || "low".equals(priority)) ? priority : "medium";
    }
}

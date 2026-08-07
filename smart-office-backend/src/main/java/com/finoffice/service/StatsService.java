package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.entity.AiMessage;
import com.finoffice.entity.AiSession;
import com.finoffice.entity.BriefingRecord;
import com.finoffice.entity.KbDocument;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.entity.SysUser;
import com.finoffice.entity.Task;
import com.finoffice.mapper.AiMessageMapper;
import com.finoffice.mapper.AiSessionMapper;
import com.finoffice.mapper.BriefingRecordMapper;
import com.finoffice.mapper.KbDocumentMapper;
import com.finoffice.mapper.MeetingRecordMapper;
import com.finoffice.mapper.SysUserMapper;
import com.finoffice.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板统计服务：overview 指标聚合 + trend 近7天趋势。
 * 全部按角色走 DataScopeHelper 数据范围：
 * ADMIN 全量；DEPT_MANAGER 本部门成员；EMPLOYEE 仅本人。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final DataScopeHelper scopeHelper;
    private final SysUserMapper userMapper;
    private final MeetingRecordMapper meetingMapper;
    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final TaskMapper taskMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final BriefingRecordMapper briefingMapper;

    /**
     * 当前用户的数据范围用户ID集合；ADMIN 返回 null 表示不限。
     */
    private List<Long> scopeUserIds(DataScopeHelper.UserScope scope) {
        if (scope.isAdmin()) {
            return null;
        }
        if (scope.isDeptManager() && scope.deptId() != null) {
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getDeptId, scope.deptId())
                            .eq(SysUser::getDeleted, 0))
                    .stream().map(SysUser::getId).toList();
            return ids.isEmpty() ? List.of(scope.userId()) : ids;
        }
        return List.of(scope.userId());
    }

    /** 指标总览（SNAKE_CASE 输出） */
    public Map<String, Object> overview() {
        DataScopeHelper.UserScope scope = scopeHelper.currentScope();
        List<Long> uids = scopeUserIds(scope);

        // 会议总数
        long meetingCount = meetingMapper.selectCount(meetingWrapper(uids, null, null));
        // 已完成会议数
        LambdaQueryWrapper<MeetingRecord> completedW = meetingWrapper(uids, null, null);
        completedW.eq(MeetingRecord::getStatus, "completed");
        long completedCount = meetingMapper.selectCount(completedW);
        // 纪要生成数：summary 非空或 status=completed
        LambdaQueryWrapper<MeetingRecord> summaryW = meetingWrapper(uids, null, null);
        summaryW.and(w -> w.isNotNull(MeetingRecord::getSummary)
                .or().eq(MeetingRecord::getStatus, "completed"));
        long summaryCount = meetingMapper.selectCount(summaryW);

        // AI 使用次数：范围内会话的 user 消息数
        long aiUsage = countAiMessages(uids, null, null);

        // 任务总数、完成数与各状态分布
        long taskTotal = taskMapper.selectCount(taskWrapper(scope, uids, null));
        long taskDone = taskMapper.selectCount(taskWrapper(scope, uids, "done"));
        long taskInProgress = taskMapper.selectCount(taskWrapper(scope, uids, "in_progress"));
        long taskPending = taskMapper.selectCount(taskWrapper(scope, uids, "pending"));

        // 知识文档数
        LambdaQueryWrapper<KbDocument> kbW = new LambdaQueryWrapper<>();
        if (uids != null) {
            kbW.in(KbDocument::getUserId, uids);
        }
        long kbDocCount = kbDocumentMapper.selectCount(kbW);

        // 简报生成数（全局定时产出，不按人过滤）
        long briefingCount = briefingMapper.selectCount(
                new LambdaQueryWrapper<BriefingRecord>().eq(BriefingRecord::getStatus, "success"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("meeting_count", meetingCount);
        data.put("completed_meeting_count", completedCount);
        data.put("summary_count", summaryCount);
        data.put("ai_usage_count", aiUsage);
        data.put("task_total", taskTotal);
        data.put("task_done", taskDone);
        data.put("task_in_progress", taskInProgress);
        data.put("task_pending", taskPending);
        data.put("task_completion_rate",
                taskTotal == 0 ? 0 : Math.round(taskDone * 1000.0 / taskTotal) / 10.0);
        data.put("kb_doc_count", kbDocCount);
        data.put("briefing_count", briefingCount);
        return data;
    }

    /** 近 7 天趋势：按日的 AI 使用次数与会议数 */
    public Map<String, Object> trend() {
        DataScopeHelper.UserScope scope = scopeHelper.currentScope();
        List<Long> uids = scopeUserIds(scope);

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        List<String> dates = new ArrayList<>();
        List<Long> aiUsage = new ArrayList<>();
        List<Long> meetings = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            LocalDateTime ds = d.atStartOfDay();
            LocalDateTime de = d.plusDays(1).atStartOfDay();
            dates.add(d.toString());
            aiUsage.add(countAiMessages(uids, ds, de));
            meetings.add(meetingMapper.selectCount(meetingWrapper(uids, ds, de)));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dates", dates);
        data.put("ai_usage", aiUsage);
        data.put("meetings", meetings);
        return data;
    }

    // ==================== 私有构造 ====================

    /** 会议范围 wrapper：按数据范围 + 时间区间（create_time） */
    private LambdaQueryWrapper<MeetingRecord> meetingWrapper(List<Long> uids,
                                                             LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<MeetingRecord> w = new LambdaQueryWrapper<>();
        if (uids != null) {
            w.in(MeetingRecord::getUserId, uids);
        }
        if (start != null) {
            w.ge(MeetingRecord::getCreateTime, start);
        }
        if (end != null) {
            w.lt(MeetingRecord::getCreateTime, end);
        }
        return w;
    }

    /** 任务范围 wrapper：ADMIN 不限；DEPT_MANAGER 本部门或经手；EMPLOYEE 仅经手 */
    private LambdaQueryWrapper<Task> taskWrapper(DataScopeHelper.UserScope scope,
                                                 List<Long> uids, String status) {
        LambdaQueryWrapper<Task> w = new LambdaQueryWrapper<>();
        if (!scope.isAdmin()) {
            if (scope.isDeptManager() && scope.deptId() != null) {
                List<Long> ids = uids == null ? List.of(scope.userId()) : uids;
                w.and(q -> q.eq(Task::getDeptId, scope.deptId())
                        .or().in(Task::getAssigneeUserId, ids)
                        .or().in(Task::getCreatorUserId, ids));
            } else {
                w.and(q -> q.eq(Task::getAssigneeUserId, scope.userId())
                        .or().eq(Task::getCreatorUserId, scope.userId()));
            }
        }
        if (status != null) {
            w.eq(Task::getStatus, status);
        }
        return w;
    }

    /** AI 使用次数：范围内会话中的 user 消息条数（可按时间区间过滤） */
    private long countAiMessages(List<Long> uids, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<AiSession> sw = new LambdaQueryWrapper<>();
        if (uids != null) {
            sw.in(AiSession::getUserId, uids);
        }
        sw.select(AiSession::getId);
        List<Long> sessionIds = aiSessionMapper.selectList(sw)
                .stream().map(AiSession::getId).toList();
        if (sessionIds.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<AiMessage> mw = new LambdaQueryWrapper<>();
        mw.in(AiMessage::getSessionId, sessionIds)
                .eq(AiMessage::getRole, "user");
        if (start != null) {
            mw.ge(AiMessage::getCreateTime, start);
        }
        if (end != null) {
            mw.lt(AiMessage::getCreateTime, end);
        }
        return aiMessageMapper.selectCount(mw);
    }
}

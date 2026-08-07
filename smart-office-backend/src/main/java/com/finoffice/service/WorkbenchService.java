package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.entity.AiMessage;
import com.finoffice.entity.AiSession;
import com.finoffice.entity.BriefingRecord;
import com.finoffice.entity.KbDocument;
import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.AiMessageMapper;
import com.finoffice.mapper.AiSessionMapper;
import com.finoffice.mapper.BriefingRecordMapper;
import com.finoffice.mapper.KbDocumentMapper;
import com.finoffice.mapper.MeetingActionItemMapper;
import com.finoffice.mapper.MeetingRecordMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 工作台聚合服务：并行查询今日会议/我的待办/统计卡片，
 * 每个分块独立降级（异常时返回空值），保证整体接口不因单块失败而挂掉。
 * 字段契约（snake_case，前端直接消费，不可变更）：
 * today_meetings / my_todos / today_briefing / stats
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private final MeetingRecordMapper meetingMapper;
    private final MeetingActionItemMapper actionItemMapper;
    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final SysUserMapper userMapper;
    private final BriefingRecordMapper briefingMapper;
    private final DataScopeHelper dataScopeHelper;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 工作台首页聚合数据 */
    public Map<String, Object> home() {
        DataScopeHelper.UserScope scope = dataScopeHelper.currentScope();

        CompletableFuture<List<Map<String, Object>>> meetingsF =
                async(() -> todayMeetings(scope), Collections.emptyList());
        CompletableFuture<List<Map<String, Object>>> todosF =
                async(() -> myTodos(scope), Collections.emptyList());
        CompletableFuture<Map<String, Object>> briefingF =
                async(this::todayBriefing, null);
        CompletableFuture<Map<String, Object>> statsF =
                async(() -> stats(scope), defaultStats());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("today_meetings", meetingsF.join());
        data.put("my_todos", todosF.join());
        // 今日简报：查 briefing_record 今日记录，无则 null（{title, content 摘要前200字, brief_date}）
        data.put("today_briefing", briefingF.join());
        data.put("stats", statsF.join());
        return data;
    }

    /* ==================== 今日简报 ==================== */

    private Map<String, Object> todayBriefing() {
        BriefingRecord rec = briefingMapper.selectOne(new LambdaQueryWrapper<BriefingRecord>()
                .eq(BriefingRecord::getBriefDate, LocalDate.now())
                .last("LIMIT 1"));
        if (rec == null || rec.getContent() == null || rec.getContent().isBlank()) {
            return null;
        }
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("title", rec.getTitle());
        String content = rec.getContent().trim();
        vo.put("content", content.length() > 200 ? content.substring(0, 200) + "…" : content);
        vo.put("brief_date", rec.getBriefDate() == null ? null : rec.getBriefDate().toString());
        return vo;
    }

    /* ==================== 今日会议 ==================== */

    private List<Map<String, Object>> todayMeetings(DataScopeHelper.UserScope scope) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<MeetingRecord> wrapper = dataScopeHelper.meetingScope(new LambdaQueryWrapper<MeetingRecord>(), scope);
        wrapper.ge(MeetingRecord::getMeetingTime, today.atStartOfDay())
                .lt(MeetingRecord::getMeetingTime, today.plusDays(1).atStartOfDay())
                .orderByAsc(MeetingRecord::getMeetingTime);
        return meetingMapper.selectList(wrapper).stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("title", m.getTitle());
            item.put("meeting_time", m.getMeetingTime() == null ? null : DT_FMT.format(m.getMeetingTime()));
            item.put("status", m.getStatus());
            return item;
        }).collect(Collectors.toList());
    }

    /* ==================== 我的待办 ==================== */

    private List<Map<String, Object>> myTodos(DataScopeHelper.UserScope scope) {
        SysUser user = userMapper.selectById(scope.userId());
        String realName = user == null ? null : user.getRealName();

        // 我创建的会议 ID 集合
        List<Long> myMeetingIds = meetingMapper.selectList(
                        new LambdaQueryWrapper<MeetingRecord>()
                                .eq(MeetingRecord::getUserId, scope.userId())
                                .select(MeetingRecord::getId))
                .stream().map(MeetingRecord::getId).collect(Collectors.toList());

        boolean hasName = realName != null && !realName.isBlank();
        LambdaQueryWrapper<MeetingActionItem> wrapper = new LambdaQueryWrapper<>();
        if (hasName && !myMeetingIds.isEmpty()) {
            wrapper.and(q -> q.eq(MeetingActionItem::getAssignee, realName)
                    .or().in(MeetingActionItem::getMeetingId, myMeetingIds));
        } else if (hasName) {
            wrapper.eq(MeetingActionItem::getAssignee, realName);
        } else if (!myMeetingIds.isEmpty()) {
            wrapper.in(MeetingActionItem::getMeetingId, myMeetingIds);
        } else {
            return Collections.emptyList();
        }

        List<MeetingActionItem> items = actionItemMapper.selectList(wrapper);
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量取会议标题
        Set<Long> meetingIds = items.stream().map(MeetingActionItem::getMeetingId).collect(Collectors.toSet());
        Map<Long, String> titleMap = meetingMapper.selectBatchIds(meetingIds).stream()
                .collect(Collectors.toMap(MeetingRecord::getId, MeetingRecord::getTitle, (a, b) -> a));

        // pending 优先，deadline 升序（null 排最后），Top 5
        items.sort(Comparator
                .comparingInt((MeetingActionItem i) -> "pending".equals(i.getStatus()) ? 0 : 1)
                .thenComparing(MeetingActionItem::getDeadline, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Map<String, Object>> result = new ArrayList<>();
        for (MeetingActionItem item : items) {
            if (result.size() >= 5) {
                break;
            }
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", item.getId());
            vo.put("meeting_id", item.getMeetingId());
            vo.put("meeting_title", titleMap.get(item.getMeetingId()));
            vo.put("content", item.getContent());
            vo.put("deadline", item.getDeadline() == null ? null : D_FMT.format(item.getDeadline()));
            vo.put("status", item.getStatus());
            result.add(vo);
        }
        return result;
    }

    /* ==================== 统计卡片 ==================== */

    private Map<String, Object> stats(DataScopeHelper.UserScope scope) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 会议总数 / 已完成数（按数据范围）
        Long meetingCount = meetingMapper.selectCount(
                dataScopeHelper.meetingScope(new LambdaQueryWrapper<MeetingRecord>(), scope));
        Long completedCount = meetingMapper.selectCount(
                dataScopeHelper.meetingScope(new LambdaQueryWrapper<MeetingRecord>(), scope)
                        .eq(MeetingRecord::getStatus, "completed"));
        stats.put("meeting_count", meetingCount);
        stats.put("completed_meeting_count", completedCount);

        // AI 使用量：该用户消息数；管理员看全量
        long aiUsage;
        if (scope.isAdmin()) {
            aiUsage = aiMessageMapper.selectCount(
                    new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getRole, "user"));
        } else {
            List<Long> sessionIds = aiSessionMapper.selectList(
                            new LambdaQueryWrapper<AiSession>()
                                    .eq(AiSession::getUserId, scope.userId())
                                    .select(AiSession::getId))
                    .stream().map(AiSession::getId).collect(Collectors.toList());
            aiUsage = sessionIds.isEmpty() ? 0L : aiMessageMapper.selectCount(
                    new LambdaQueryWrapper<AiMessage>()
                            .in(AiMessage::getSessionId, sessionIds)
                            .eq(AiMessage::getRole, "user"));
        }
        stats.put("ai_usage_count", aiUsage);

        // 知识库文档数（按数据范围）
        Long kbCount = kbDocumentMapper.selectCount(
                dataScopeHelper.kbDocScope(new LambdaQueryWrapper<>(), scope));
        stats.put("kb_doc_count", kbCount);

        return stats;
    }

    private Map<String, Object> defaultStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("meeting_count", 0);
        stats.put("completed_meeting_count", 0);
        stats.put("ai_usage_count", 0);
        stats.put("kb_doc_count", 0);
        return stats;
    }

    /* ==================== 工具 ==================== */

    /** 并行任务封装：异常时局部降级为 fallback */
    private <T> CompletableFuture<T> async(Supplier<T> supplier, T fallback) {
        return CompletableFuture.supplyAsync(supplier, executor)
                .exceptionally(e -> {
                    log.warn("工作台分块查询失败，局部降级: {}", e.getMessage());
                    return fallback;
                });
    }
}

package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finoffice.common.BizException;
import com.finoffice.dto.ActionItemDTO;
import com.finoffice.dto.MeetingCreateReq;
import com.finoffice.dto.MeetingSummaryResp;
import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.mapper.MeetingActionItemMapper;
import com.finoffice.mapper.MeetingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能会议服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRecordMapper meetingMapper;
    private final MeetingActionItemMapper actionItemMapper;
    private final ObjectMapper objectMapper;
    private final DingTalkService dingTalkService;

    /** 创建会议记录 */
    public MeetingRecord createMeeting(Long userId, MeetingCreateReq req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("会议标题不能为空");
        }
        MeetingRecord record = new MeetingRecord();
        record.setUserId(userId);
        record.setTitle(req.getTitle());
        record.setSourceType(req.getSourceType() != null ? req.getSourceType() : "text");
        record.setRawText(req.getRawText());
        record.setStatus("pending");
        meetingMapper.insert(record);
        return record;
    }

    /** 列出当前用户的会议 */
    public List<MeetingRecord> listMeetings(Long userId) {
        return meetingMapper.selectList(
                new LambdaQueryWrapper<MeetingRecord>()
                        .eq(MeetingRecord::getUserId, userId)
                        .orderByDesc(MeetingRecord::getCreateTime));
    }

    /** 获取会议详情（含归属校验） */
    public MeetingRecord getMeetingDetail(Long userId, Long meetingId) {
        MeetingRecord record = meetingMapper.selectById(meetingId);
        if (record == null) {
            throw new BizException("会议记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BizException("无权查看该会议记录");
        }
        return record;
    }

    /** 删除会议（含归属校验 + 级联删待办） */
    @Transactional
    public void deleteMeeting(Long userId, Long meetingId) {
        MeetingRecord record = getMeetingDetail(userId, meetingId);
        // 级联删除待办事项
        actionItemMapper.delete(
                new LambdaQueryWrapper<MeetingActionItem>()
                        .eq(MeetingActionItem::getMeetingId, record.getId()));
        meetingMapper.deleteById(record.getId());
    }

    /**
     * 调用AI生成纪要，解析结构化结果，保存待办事项。
     * 当前使用 mock 实现，后续 Dify 接入后自动生效。
     */
    @Transactional
    public MeetingRecord generateSummary(Long userId, Long meetingId) {
        MeetingRecord record = getMeetingDetail(userId, meetingId);
        if (record.getRawText() == null || record.getRawText().isBlank()) {
            throw new BizException("会议转写文本为空，无法生成纪要");
        }

        record.setStatus("processing");
        meetingMapper.updateById(record);

        try {
            // 构造 prompt 并调用 AI（当前 mock）
            String aiResult = callAiForSummary(record.getRawText());

            // 解析 AI 返回的 JSON
            MeetingSummaryResp summaryResp = parseSummary(aiResult);

            // 保存纪要 JSON 到 meeting_record
            record.setSummary(objectMapper.writeValueAsString(summaryResp));
            record.setStatus("completed");
            meetingMapper.updateById(record);

            // 保存待办事项
            if (summaryResp.getActionItems() != null) {
                for (ActionItemDTO dto : summaryResp.getActionItems()) {
                    MeetingActionItem item = new MeetingActionItem();
                    item.setMeetingId(record.getId());
                    item.setContent(dto.getContent());
                    item.setAssignee(dto.getAssignee());
                    if (dto.getDeadline() != null && !dto.getDeadline().isBlank()) {
                        try {
                            item.setDeadline(LocalDateTime.parse(dto.getDeadline(),
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        } catch (Exception e) {
                            try {
                                item.setDeadline(LocalDateTime.parse(dto.getDeadline() + " 00:00:00",
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                            } catch (Exception ex) {
                                log.warn("待办截止时间解析失败: {}", dto.getDeadline());
                            }
                        }
                    }
                    item.setStatus("pending");
                    actionItemMapper.insert(item);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI生成会议纪要失败", e);
            record.setStatus("failed");
            meetingMapper.updateById(record);
            throw new BizException("生成会议纪要失败: " + e.getMessage());
        }

        return record;
    }

    /** 获取待办列表 */
    public List<MeetingActionItem> getActionItems(Long meetingId) {
        return actionItemMapper.selectList(
                new LambdaQueryWrapper<MeetingActionItem>()
                        .eq(MeetingActionItem::getMeetingId, meetingId)
                        .orderByAsc(MeetingActionItem::getId));
    }

    /** 更新待办状态 */
    public void updateActionItemStatus(Long actionItemId, String status) {
        MeetingActionItem item = actionItemMapper.selectById(actionItemId);
        if (item == null) {
            throw new BizException("待办事项不存在");
        }
        item.setStatus(status);
        actionItemMapper.updateById(item);
    }


    /** 推送会议纪要到钉钉 */
    public void pushSummaryToDingTalk(Long userId, Long meetingId) {
        checkOwner(userId, meetingId);
        MeetingRecord meeting = meetingMapper.selectById(meetingId);
        if (meeting == null || meeting.getSummary() == null) {
            throw new BizException(404, "会议记录或纪要不存在");
        }
        dingTalkService.sendMeetingSummary(meeting.getTitle(), meeting.getSummary());
    }

    /** 校验会议归属 */
    private void checkOwner(Long userId, Long meetingId) {
        MeetingRecord record = meetingMapper.selectById(meetingId);
        if (record == null) {
            throw new BizException("会议记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BizException("无权操作该会议记录");
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 调用 AI 生成会议纪要。
     * 当前为 mock 实现，返回模拟的 JSON 纪要。
     * 后续接入 Dify 后，此处替换为真实的 API 调用。
     */
    private String callAiForSummary(String rawText) {
        // TODO: 接入 Dify 后替换为真实调用
        // 构造 prompt: 将会议文本 + 系统提示词组合，要求 AI 输出 JSON 格式纪要
        String prompt = "请根据以下会议转写文本，生成结构化会议纪要（JSON格式）：\n" + rawText;
        log.info("AI Prompt (mock): {}", prompt.substring(0, Math.min(prompt.length(), 200)));

        // Mock 返回 JSON
        return """
                {
                  "title": "会议纪要（自动生成）",
                  "attendees": ["张三", "李四", "王五"],
                  "topics": ["项目进展汇报", "下季度计划安排", "风险评估"],
                  "decisions": ["同意项目一期按期交付", "下季度增加测试投入", "启动风险预案"],
                  "actionItems": [
                    {"content": "完成一期项目交付文档", "assignee": "张三", "deadline": "2026-08-15"},
                    {"content": "制定下季度测试计划", "assignee": "李四", "deadline": "2026-08-20"},
                    {"content": "整理风险清单并上报", "assignee": "王五", "deadline": "2026-08-10"}
                  ]
                }
                """;
    }

    /** 解析 AI 返回的 JSON 纪要 */
    private MeetingSummaryResp parseSummary(String json) {
        try {
            return objectMapper.readValue(json, MeetingSummaryResp.class);
        } catch (JsonProcessingException e) {
            log.error("AI返回JSON解析失败: {}", json, e);
            throw new BizException("AI纪要解析失败");
        }
    }
}

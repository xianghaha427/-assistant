package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.OpLogger;
import com.finoffice.common.R;
import com.finoffice.dto.MeetingCreateReq;
import com.finoffice.dto.MeetingSummaryResp;
import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.service.MeetingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 智能会议接口
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public R<MeetingRecord> create(@RequestBody MeetingCreateReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(meetingService.createMeeting(userId, req));
    }

    @GetMapping
    public R<List<MeetingRecord>> list() {
        // 登录校验由拦截器完成，数据范围由 DataScopeHelper 按角色限定
        return R.ok(meetingService.listMeetings());
    }

    @GetMapping("/{id}")
    public R<MeetingRecord> detail(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(meetingService.getMeetingDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        meetingService.deleteMeeting(userId, id);
        return R.ok();
    }

    /** 触发 AI 生成会议纪要（可附 dify 文件ID；仅文件无文字时后端自动以「摘要」为 query） */
    @PostMapping("/{id}/summary")
    public R<MeetingRecord> generateSummary(@PathVariable Long id, @RequestBody(required = false) SummaryReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        OpLogger.log(userId, "MEETING", "纪要生成触发", "会议ID " + id);
        return R.ok(meetingService.generateSummary(userId, id, req != null ? req.getFileIds() : null));
    }

    /** 获取会议纪要（解析后的结构化数据） */
    @GetMapping("/{id}/summary")
    public R<MeetingSummaryResp> getSummary(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        MeetingRecord record = meetingService.getMeetingDetail(userId, id);
        if (record.getSummary() == null) {
            // 不抛异常：返回明确状态体（processing=生成中 / empty=尚未触发生成），
            // 前端据此渲染占位状态，响应拦截器不再弹错
            MeetingSummaryResp placeholder = new MeetingSummaryResp();
            placeholder.setStatus("processing".equals(record.getStatus()) ? "processing" : "empty");
            return R.ok(placeholder);
        }
        try {
            MeetingSummaryResp resp = objectMapper.readValue(record.getSummary(), MeetingSummaryResp.class);
            resp.setStatus("completed");
            return R.ok(resp);
        } catch (Exception e) {
            throw new BizException("纪要解析失败");
        }
    }

    /** 获取待办列表 */
    @GetMapping("/{id}/actions")
    public R<List<MeetingActionItem>> actionItems(@PathVariable Long id) {
        // 先校验归属
        long userId = StpUtil.getLoginIdAsLong();
        meetingService.getMeetingDetail(userId, id);
        return R.ok(meetingService.getActionItems(id));
    }

    // 待办状态入口已下线：待办只读展示，状态单源在任务看板（PUT /api/tasks/{id} 反向同步回待办）

    /** 纪要文档直传 Dify（pdf/md/docx/txt），返回 dify_file_id 与文件名 */
    @PostMapping("/files")
    public R<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        long userId = StpUtil.getLoginIdAsLong();
        OpLogger.log(userId, "MEETING", "上传纪要文档", file.getOriginalFilename());
        return R.ok(meetingService.uploadMeetingFile(file, userId));
    }

    /** 推送会议纪要到钉钉 */
    @PostMapping("/{id}/push")
    public R<Void> pushToDingTalk(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        meetingService.pushSummaryToDingTalk(userId, id);
        return R.ok();
    }

    @Data
    public static class SummaryReq {
        /** 纪要直传 Dify 的文件ID列表（可选，来自 /api/meetings/files） */
        private List<String> fileIds;
    }
}

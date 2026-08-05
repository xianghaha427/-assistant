package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.dto.MeetingCreateReq;
import com.finoffice.dto.MeetingSummaryResp;
import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.service.MeetingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 智能会议接口
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @Value("")
    private String uploadPath;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "m4a");

    @PostMapping
    public R<MeetingRecord> create(@RequestBody MeetingCreateReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(meetingService.createMeeting(userId, req));
    }

    @GetMapping
    public R<List<MeetingRecord>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(meetingService.listMeetings(userId));
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

    /** 触发 AI 生成会议纪要 */
    @PostMapping("/{id}/summary")
    public R<MeetingRecord> generateSummary(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(meetingService.generateSummary(userId, id));
    }

    /** 获取会议纪要（解析后的结构化数据） */
    @GetMapping("/{id}/summary")
    public R<MeetingSummaryResp> getSummary(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        MeetingRecord record = meetingService.getMeetingDetail(userId, id);
        if (record.getSummary() == null) {
            throw new BizException("会议纪要尚未生成");
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            MeetingSummaryResp resp = mapper.readValue(record.getSummary(), MeetingSummaryResp.class);
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

    /** 更新待办状态 */
    @PutMapping("/actions/{actionId}")
    public R<Void> updateActionStatus(@PathVariable Long actionId, @RequestBody ActionStatusReq req) {
        // 鉴权
        StpUtil.getLoginIdAsLong();
        meetingService.updateActionItemStatus(actionId, req.getStatus());
        return R.ok();
    }

    /** 音频文件上传 */
    @PostMapping("/upload")
    public R<UploadResult> upload(@RequestParam("file") MultipartFile file) {
        StpUtil.getLoginIdAsLong();
        if (file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException("仅支持 mp3/wav/m4a 格式的音频文件");
        }

        // 生成唯一文件名
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // 确保目录存在
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File dest = new File(dir, newFileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage());
        }

        UploadResult result = new UploadResult();
        result.setUrl("/api/meetings/audio/" + newFileName);
        result.setFileName(originalName);
        return R.ok(result);
    }

    /** 推送会议纪要到钉钉 */
    @PostMapping("/{id}/push")
    public R<Void> pushToDingTalk(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        meetingService.pushSummaryToDingTalk(userId, id);
        return R.ok();
    }

    @Data
    public static class ActionStatusReq {
        private String status;
    }

    @Data
    public static class UploadResult {
        private String url;
        private String fileName;
    }
}

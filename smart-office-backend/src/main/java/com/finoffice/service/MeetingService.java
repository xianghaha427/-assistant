package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finoffice.common.BizException;
import com.finoffice.common.OpLogger;
import com.finoffice.dto.ActionItemDTO;
import com.finoffice.dto.MeetingCreateReq;
import com.finoffice.dto.MeetingSummaryResp;
import com.finoffice.entity.AgentConfig;
import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.MeetingRecord;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.mapper.MeetingActionItemMapper;
import com.finoffice.mapper.MeetingRecordMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private final DataScopeHelper dataScopeHelper;
    private final DifyService difyService;
    private final AgentConfigMapper agentConfigMapper;
    private final SysUserMapper userMapper;
    private final AudioTranscriptionService whisperService;
    private final NotificationService notificationService;

    /** 会议纪要助手智能体 ID（agent_config） */
    private static final long SUMMARY_AGENT_ID = 2L;

    /** 纪要异步生成有界线程池：核心2/最大4/队列20，满载时调用者自跑，防止无限堆积 */
    private final ExecutorService summaryExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20),
            r -> { Thread t = new Thread(r, "summary-gen"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy());

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
        record.setDifyFileIds(toJsonArray(req.getFileIds()));
        record.setMeetingTime(req.getMeetingTime() != null ? req.getMeetingTime() : LocalDateTime.now());
        record.setStatus("pending");
        meetingMapper.insert(record);
        return record;
    }

    /** 列出当前用户可见的会议（ADMIN 全部 / 部门负责人本部门 / 员工仅自己） */
    public List<MeetingRecord> listMeetings() {
        LambdaQueryWrapper<MeetingRecord> wrapper =
                dataScopeHelper.meetingScope(new LambdaQueryWrapper<>());
        return meetingMapper.selectList(wrapper.orderByDesc(MeetingRecord::getCreateTime));
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
     * 触发 AI 纪要生成（异步）：置 processing 后提交有界线程池立即返回，
     * 成功后写 summary 并置 completed，失败置 failed。
     */
    public MeetingRecord generateSummary(Long userId, Long meetingId, List<String> fileIds) {
        MeetingRecord record = getMeetingDetail(userId, meetingId);
        boolean hasText = record.getRawText() != null && !record.getRawText().isBlank();
        List<String> mergedFileIds = mergeFileIds(record.getDifyFileIds(), fileIds);
        if (!hasText && mergedFileIds.isEmpty()) {
            throw new BizException("请先粘贴会议转写文本或上传纪要文档");
        }
        if ("processing".equals(record.getStatus())) {
            // 已在生成中，防重复提交
            return record;
        }

        record.setDifyFileIds(toJsonArray(mergedFileIds));
        record.setStatus("processing");
        meetingMapper.updateById(record);

        final Long recordId = record.getId();
        summaryExecutor.submit(() -> doGenerateSummary(recordId));
        return record;
    }

    /** 异步线程内执行的纪要生成：降级链产出纪要 JSON 落库并拆待办；仅文件场景 Dify 失败直接 failed（不造假纪要） */
    private void doGenerateSummary(Long recordId) {
        MeetingRecord record = meetingMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        try {
            SummaryResult result = callAiForSummary(record.getUserId(), record.getRawText(), parseFileIds(record.getDifyFileIds()));
            if (result.json() == null) {
                // 仅文件场景 Dify 通道失败：置 failed 并留痕，绝不产出假纪要
                log.error("会议纪要生成失败（会议{}）：{}", recordId, result.failReason());
                record.setStatus("failed");
                record.setFailReason(result.failReason());
                meetingMapper.updateById(record);
                OpLogger.log(record.getUserId(), "MEETING", "纪要生成失败",
                        "会议ID " + recordId + "：" + result.failReason());
                return;
            }
            MeetingSummaryResp summaryResp = parseSummary(result.json());
            summaryResp.setMock(result.mock());

            record.setSummary(objectMapper.writeValueAsString(summaryResp));
            record.setStatus("completed");
            record.setFailReason(result.failReason());
            meetingMapper.updateById(record);

            // 降级成功也记录通道异常原因（fail_reason 留痕 + sys_log）
            if (result.failReason() != null) {
                OpLogger.log(record.getUserId(), "MEETING", "纪要通道异常(已降级)",
                        "会议ID " + recordId + "：" + result.failReason());
            }
            saveActionItems(record.getId(), summaryResp);
            // 发送通知
            try {
                String notifTitle = "会议纪要已生成：" + record.getTitle();
                notificationService.send(record.getUserId(), "meeting", notifTitle,
                        "AI 已完成会议纪要生成，包含议题总结和待办事项，请查看详情。",
                        "/meeting/" + record.getId());
            } catch (Exception ex) {
                log.warn("通知发送失败: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.error("AI生成会议纪要失败", e);
            record.setStatus("failed");
            record.setFailReason(errDigest(e));
            meetingMapper.updateById(record);
            OpLogger.log(record.getUserId(), "MEETING", "纪要生成失败",
                    "会议ID " + recordId + "：" + errDigest(e));
        }
    }

    /** 保存待办：责任人按 sys_user.real_name 模糊匹配落 assignee_id，失败留空 */
    private void saveActionItems(Long meetingId, MeetingSummaryResp summaryResp) {
        if (summaryResp.getActionItems() == null) {
            return;
        }
        for (ActionItemDTO dto : summaryResp.getActionItems()) {
            MeetingActionItem item = new MeetingActionItem();
            item.setMeetingId(meetingId);
            item.setContent(dto.getContent());
            item.setAssignee(dto.getAssignee());
            item.setAssigneeId(matchUserIdByName(dto.getAssignee()));
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

    /** 按责任人姓名模糊匹配用户ID；无匹配返回 null */
    private Long matchUserIdByName(String assigneeText) {
        if (assigneeText == null || assigneeText.isBlank()) {
            return null;
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .like(SysUser::getRealName, assigneeText.trim())
                .eq(SysUser::getDeleted, 0));
        return users.isEmpty() ? null : users.get(0).getId();
    }

    /** 获取待办列表 */
    public List<MeetingActionItem> getActionItems(Long meetingId) {
        return actionItemMapper.selectList(
                new LambdaQueryWrapper<MeetingActionItem>()
                        .eq(MeetingActionItem::getMeetingId, meetingId)
                        .orderByAsc(MeetingActionItem::getId));
    }

    // 待办状态更新入口已下线（待办只读）：状态单源在任务看板，
    // 看板改状态由 TaskService.syncActionItemStatus 反向同步回待办

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

    /** 纪要生成提示词：要求严格输出 JSON（snake_case 字段） */
    private static final String SUMMARY_PROMPT =
            "请根据以下会议转写文本生成结构化会议纪要，只输出一个JSON对象（不要markdown代码块、不要任何额外说明）。"
            + "JSON字段：title(纪要标题)、attendees(参会人数组)、topics(议题摘要数组)、decisions(决议数组)、"
            + "action_items(待办数组，每项含content=待办内容、assignee=责任人姓名、deadline=截止时间，格式yyyy-MM-dd HH:mm:ss)。"
            + "会议转写文本：\n";

    /**
     * 调用 AI 生成会议纪要，三级降级（每级独立 try-catch，不阻塞）：
     * 1. Dify 会议纪要助手（chatflow，可携带纪要文档 files 直传）
     * 2. Mock 兜底（演示模式，UI 明示）
     * 仅文件无文字场景：Dify 失败即返回 json=null + failReason，由调用方置 failed（不造假纪要）。
     */
    private SummaryResult callAiForSummary(Long userId, String rawText, List<String> fileIds) {
        boolean hasText = rawText != null && !rawText.isBlank();
        // 仅文件无文字时自动以「摘要」作为 query（Dify chatflow 通过 sys.query 接收）
        String query = hasText ? SUMMARY_PROMPT + rawText : "摘要";
        String failReason = null;

        // ---- 第1级：Dify chatflow（可携带纪要文档 files 直传） ----
        try {
            AgentConfig agent = agentConfigMapper.selectById(SUMMARY_AGENT_ID);
            if (agent != null && agent.getDifyAppId() != null && !agent.getDifyAppId().isBlank()) {
                List<Map<String, Object>> difyFiles = buildDifyFiles(fileIds);
                // Dify 云端文档解析为异步：上传后立刻请求易返回空，携文件场景等待后重试（最多3次）
                int maxAttempts = difyFiles != null ? 3 : 1;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    if (attempt > 1) {
                        try { Thread.sleep(8000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        log.info("等待Dify文档解析后重试，第{}次", attempt);
                    }
                    StringBuilder sb = new StringBuilder();
                    // user 标识必须与 files/upload 时的属主一致（user-<userId>），
                    // 否则 Dify 按 user 匹配不到文件，工作流静默返回空内容
                    difyService.streamChat(agent.getDifyAppId(), query, null, userId, difyFiles,
                            sb::append, convId -> { }, () -> { });
                    String raw = sb.toString();
                    String result = extractJson(raw);
                    if (result == null && !raw.isBlank()) {
                        // 云端 chatflow 末尾为 Markdown 格式化节点，输出非 JSON，此处反解析为纪要 JSON
                        result = markdownMinutesToJson(raw);
                    }
                    if (result != null) {
                        log.info("会议纪要由 Dify 通道生成，长度={}，第{}次尝试", result.length(), attempt);
                        return new SummaryResult(result, null, false);
                    }
                    failReason = "Dify返回空内容(原始长度=" + raw.length() + ")";
                    log.warn("Dify chat-messages 调用返回空内容（原始长度={}），第{}次尝试", raw.length(), attempt);
                }
            } else {
                failReason = "会议纪要助手未接入Dify";
            }
        } catch (Exception e) {
            failReason = errDigest(e);
            log.warn("Dify 纪要调用失败: {}", failReason);
        }

        // 仅文件场景：Dify 通道失败即整体失败（Mock 属假纪要）
        if (!hasText) {
            log.error("纪要仅文件无文字且 Dify 通道不可用，置为失败: {}", failReason);
            return new SummaryResult(null, "文档解析失败：" + failReason, false);
        }

        // ---- 第2级：Mock 兜底（演示模式，UI 明示） ----
        log.warn("Dify 通道不可用，使用 Mock 纪要（演示模式）");
        return new SummaryResult(MOCK_SUMMARY,
                (failReason == null ? "" : failReason + "；") + "Dify通道不可用，已降级演示模式", true);
    }

    /** 纪要生成结果：json=null 表示硬失败（仅文件场景），failReason 为错误摘要，mock 标记演示模式 */
    private record SummaryResult(String json, String failReason, boolean mock) { }

    /** 错误摘要：异常类名 + message 前200字（持久化到 meeting_record.fail_reason） */
    private String errDigest(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.length() > 200) {
            msg = msg.substring(0, 200);
        }
        return e.getClass().getSimpleName() + ": " + msg;
    }

    /** Mock 纪要（仅三级降级全部失败时使用） */
    private static final String MOCK_SUMMARY = """
            {
              "title": "会议纪要（自动生成）",
              "attendees": ["张三", "李四", "王五"],
              "topics": ["项目进展汇报", "下季度计划安排", "风险评估"],
              "decisions": ["同意项目一期按期交付", "下季度增加测试投入", "启动风险预案"],
              "action_items": [
                {"content": "完成一期项目交付文档", "assignee": "张三", "deadline": "2026-08-15"},
                {"content": "制定下季度测试计划", "assignee": "李四", "deadline": "2026-08-20"},
                {"content": "整理风险清单并上报", "assignee": "王五", "deadline": "2026-08-10"}
              ]
            }
            """;

    /**
     * 将 Dify chatflow 输出的 Markdown 纪要反解析为纪要 JSON。
     * 云端 DSL「解析与格式化」节点输出格式：
     * # 标题 / **参 会 人** / ## 议题讨论(表格) / ## 会议决议(列表) / ## 待办事项(表格)
     */
    private String markdownMinutesToJson(String md) {
        try {
            String title = "会议纪要（自动生成）";
            List<String> attendees = new ArrayList<>();
            List<String> topics = new ArrayList<>();
            List<String> decisions = new ArrayList<>();
            List<Map<String, String>> actions = new ArrayList<>();
            String section = "";
            for (String rawLine : md.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("# ") && title.equals("会议纪要（自动生成）")) {
                    String t = line.substring(2).trim();
                    if (!t.isEmpty()) {
                        title = t;
                    }
                    continue;
                }
                if (line.startsWith("## ")) {
                    section = line.substring(3);
                    continue;
                }
                if (line.startsWith("**参") && line.contains("：")) {
                    String names = line.substring(line.indexOf('：') + 1).trim();
                    for (String n : names.split("[、,，]")) {
                        if (!n.isBlank() && !n.contains("[推断]")) {
                            attendees.add(n.trim());
                        }
                    }
                    continue;
                }
                if (line.startsWith("|")) {
                    if (line.contains("---")) {
                        continue;
                    }
                    List<String> cs = new ArrayList<>();
                    for (String c : line.split("\\|")) {
                        if (!c.trim().isEmpty()) {
                            cs.add(c.trim());
                        }
                    }
                    if (cs.isEmpty()) {
                        continue;
                    }
                    if (section.contains("议题") && !"议题".equals(cs.get(0))) {
                        topics.add(String.join("；", cs));
                    } else if (section.contains("待办") && !"内容".equals(cs.get(0)) && cs.size() >= 2) {
                        Map<String, String> a = new LinkedHashMap<>();
                        a.put("content", cs.get(0));
                        a.put("assignee", cs.get(1));
                        a.put("deadline", cs.size() > 2 ? cs.get(2) : "待定");
                        actions.add(a);
                    }
                    continue;
                }
                if (line.startsWith("- ") && section.contains("决议")) {
                    decisions.add(line.substring(2).trim());
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", title);
            out.put("attendees", attendees);
            out.put("topics", topics);
            out.put("decisions", decisions);
            out.put("action_items", actions);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("Markdown 纪要解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 纪要文档允许的文件类型（与前端文档上传 Tab 一致） */
    private static final java.util.Set<String> DOC_EXTENSIONS = java.util.Set.of("pdf", "md", "docx", "txt");

    /** 纪要文档直传 Dify（files/upload，使用会议纪要助手密钥），返回 dify_file_id 与文件名 */
    public Map<String, Object> uploadMeetingFile(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "meeting.txt";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!DOC_EXTENSIONS.contains(ext)) {
            throw new BizException("仅支持 pdf/md/docx/txt 格式的纪要文档");
        }
        AgentConfig agent = agentConfigMapper.selectById(SUMMARY_AGENT_ID);
        if (agent == null || agent.getDifyAppId() == null || agent.getDifyAppId().isBlank()) {
            throw new BizException("会议纪要助手未接入 Dify，暂不支持文件上传");
        }
        try {
            String fileId = difyService.uploadFile(agent.getDifyAppId(), originalName, file.getBytes(), userId);
            log.info("纪要文档直传 Dify 成功: {} -> {}", originalName, fileId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dify_file_id", fileId);
            result.put("file_name", originalName);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("纪要文档上传 Dify 失败", e);
            throw new BizException("文件上传失败: " + e.getMessage());
        }
    }

    /** 组装 Dify chat-messages files 参数：[{type:document, transfer_method:local_file, upload_file_id}] */
    private List<Map<String, Object>> buildDifyFiles(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (String fid : fileIds) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", "document");
            f.put("transfer_method", "local_file");
            f.put("upload_file_id", fid);
            files.add(f);
        }
        return files;
    }

    /** 合并会议已挂载与本次提交的 dify 文件ID（去重保序） */
    private List<String> mergeFileIds(String storedJson, List<String> incoming) {
        List<String> merged = new ArrayList<>(parseFileIds(storedJson));
        if (incoming != null) {
            for (String fid : incoming) {
                if (fid != null && !fid.isBlank() && !merged.contains(fid.trim())) {
                    merged.add(fid.trim());
                }
            }
        }
        return merged;
    }

    /** 解析 dify_file_ids JSON 数组串；失败返回空列表 */
    private List<String> parseFileIds(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> ids = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode n : node) {
                    if (!n.asText("").isBlank()) {
                        ids.add(n.asText());
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("dify_file_ids 解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    /** 文件ID列表转 JSON 数组串（空返回 null） */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 AI 输出中截取 JSON 对象（剥离 markdown 包裹与前后缀文字） */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /** 解析 AI 返回的 JSON 纪要（兼容 camelCase / snake_case 字段名） */
    private MeetingSummaryResp parseSummary(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            MeetingSummaryResp resp = new MeetingSummaryResp();
            resp.setTitle(root.path("title").asText("会议纪要（自动生成）"));
            resp.setAttendees(readStringList(root, "attendees", "participants"));
            resp.setTopics(readStringList(root, "topics"));
            resp.setDecisions(readStringList(root, "decisions"));
            JsonNode items = root.has("action_items") ? root.get("action_items") : root.get("actionItems");
            List<ActionItemDTO> actionItems = new ArrayList<>();
            if (items != null && items.isArray()) {
                for (JsonNode node : items) {
                    ActionItemDTO dto = new ActionItemDTO();
                    dto.setContent(node.path("content").asText(null));
                    dto.setAssignee(node.path("assignee").asText(null));
                    dto.setDeadline(node.path("deadline").asText(null));
                    if (dto.getContent() != null && !dto.getContent().isBlank()) {
                        actionItems.add(dto);
                    }
                }
            }
            resp.setActionItems(actionItems);
            return resp;
        } catch (Exception e) {
            log.error("AI返回JSON解析失败: {}", json, e);
            throw new BizException("AI纪要解析失败");
        }
    }

    /** 读取字符串数组字段（支持多个候选键名） */
    private List<String> readStringList(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode node = root.get(key);
            if (node != null && node.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode item : node) {
                    if (!item.asText("").isBlank()) {
                        list.add(item.asText());
                    }
                }
                return list;
            }
        }
        return new ArrayList<>();
    }

    /** 语音转写：上传会议录音 → Faster-Whisper 转写 → 返回文本（阶段3） */
    public String transcribeAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("音频文件不能为空");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "recording.wav";
        try {
            String text = whisperService.transcribe(file.getBytes(), originalName);
            if (text == null || text.isBlank()) {
                throw new BizException("语音转写结果为空，请检查音频内容是否包含有效语音");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("语音转写失败: {}", e.getMessage());
            throw new BizException("语音转写失败，请重试或手动粘贴会议文本: " + e.getMessage());
        }
    }
}

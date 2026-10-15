package com.finoffice.service.kb;

import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.Task;
import com.finoffice.mapper.MeetingActionItemMapper;
import com.finoffice.mapper.TaskMapper;
import com.finoffice.mapper.SysUserMapper;
import com.finoffice.service.BashSecurityService;
import com.finoffice.service.BashSecurityService.ExecResult;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent 工具集：AI 不只是生成文本，还能执行真实操作。
 * 工具方法由 LangChain4j 自动发现，注入到 ChatModel 的 Tool 列表中。
 *
 * 使用方式：
 * <pre>{@code
 *   ChatModel model = OpenAiChatModel.builder()...
 *       .tools(new AgentTools(...))  // 注入工具
 *       .build();
 * }</pre>
 *
 * 当用户问"把张三周五前完成需求文档设为待办"时，
 * Agent 会自动调用 createTask() 在数据库创建真实任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTools {

    private final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    private final TaskMapper taskMapper;
    private final MeetingActionItemMapper actionItemMapper;
    private final SysUserMapper userMapper;
    private final BashSecurityService bashSecurityService;

    public void setCurrentUserId(Long userId) {
        currentUserId.set(userId);
    }

    public void clearCurrentUserId() {
        currentUserId.remove();
    }

    /** 时间格式化器（ISO_LOCAL_DATE + 可选时间） */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ---- 任务操作 ----

    @Tool("在任务看板中创建一条新任务。"
            + "参数说明：title=任务标题, description=任务描述, assigneeName=负责人中文姓名, "
            + "priority=优先级(high高/medium中/low低), deadline=截止日期(yyyy-MM-dd)。"
            + "返回创建成功的任务ID和详情。")
    public String createTask(
            @dev.langchain4j.agent.tool.P("title") String title,
            @dev.langchain4j.agent.tool.P("description") String description,
            @dev.langchain4j.agent.tool.P("assigneeName") String assigneeName,
            @dev.langchain4j.agent.tool.P("priority") String priority,
            @dev.langchain4j.agent.tool.P("deadline") String deadline) {
        try {
            Task task = new Task();
            task.setTitle(title);
            task.setDescription(description);
            task.setPriority(priority != null ? priority : "medium");
            task.setStatus("pending");
            if (deadline != null && !deadline.isBlank()) {
                task.setDeadline(LocalDate.parse(deadline, DATE_FMT).atStartOfDay());
            }
            task.setCreatorUserId(1L); // 系统代理创建

            // 匹配负责人（按 real_name 模糊查找）
            if (assigneeName != null && !assigneeName.isBlank()) {
                var users = userMapper.selectList(null);
                for (var u : users) {
                    if (u.getRealName() != null && u.getRealName().contains(assigneeName.trim())) {
                        task.setAssigneeUserId(u.getId());
                        break;
                    }
                }
            }

            taskMapper.insert(task);
            log.info("Agent 创建任务: [{}] id={} assignee={}", title, task.getId(), assigneeName);
            return String.format("任务「%s」已创建（ID=%d, 优先级=%s, 截止=%s）",
                    title, task.getId(), task.getPriority(),
                    deadline != null ? deadline : "未设定");
        } catch (Exception e) {
            log.error("Agent 创建任务失败: {}", e.getMessage());
            return "创建任务失败: " + e.getMessage();
        }
    }

    // ---- 会议待办操作 ----

    @Tool("为会议创建一条待办事项。"
            + "参数说明：meetingId=会议记录ID, content=待办内容, assigneeName=负责人中文姓名。"
            + "返回创建结果。")
    public String createMeetingActionItem(
            @dev.langchain4j.agent.tool.P("meetingId") long meetingId,
            @dev.langchain4j.agent.tool.P("content") String content,
            @dev.langchain4j.agent.tool.P("assigneeName") String assigneeName) {
        try {
            MeetingActionItem item = new MeetingActionItem();
            item.setMeetingId(meetingId);
            item.setContent(content);
            item.setStatus("pending");
            item.setCreateTime(LocalDateTime.now());

            if (assigneeName != null && !assigneeName.isBlank()) {
                var users = userMapper.selectList(null);
                for (var u : users) {
                    if (u.getRealName() != null && u.getRealName().contains(assigneeName.trim())) {
                        item.setAssigneeId(u.getId());
                        break;
                    }
                }
            }

            actionItemMapper.insert(item);
            String safeContent = content != null ? content.substring(0, Math.min(content.length(), 50)) : "(null)";
            log.info("Agent 创建会议待办: meetingId={} content={} assignee={}",
                    meetingId, safeContent, assigneeName);
            return String.format("会议待办「%s」已创建（ID=%d, 负责人=%s）",
                    content, item.getId(), assigneeName != null ? assigneeName : "未指定");
        } catch (Exception e) {
            log.error("Agent 创建会议待办失败: {}", e.getMessage());
            return "创建会议待办失败: " + e.getMessage();
        }
    }

    // ---- 办公运维诊断（沙箱安全管控） ----

    @Tool("执行一个只读的办公环境诊断任务，帮助排查知识库、会议附件和服务运行状态。"
            + "diagnosticId 只能是 knowledge-files（知识库文件清单）、knowledge-size（知识库占用空间）、"
            + "upload-size（上传文件占用）、disk-space（运行环境磁盘）、process-status（应用进程状态）。"
            + "不要自行拼接 Shell 命令，也不能执行修改文件、联网或系统管理操作。")
    public String diagnoseOfficeEnvironment(
            @dev.langchain4j.agent.tool.P("diagnosticId") String diagnosticId) {
        if (diagnosticId == null || diagnosticId.isBlank()) {
            return "诊断任务为空，请从允许的办公诊断任务中选择。";
        }
        try {
            Long userId = currentUserId.get() != null ? currentUserId.get() : 1L;
            ExecResult r = bashSecurityService.executePreset(diagnosticId.trim(), userId);
            if (!r.allowed()) {
                return String.format("诊断任务被安全策略拦截：%s（风险等级：%s）", r.reason(), r.riskLevel());
            }
            return String.format("诊断任务 %s 完成（退出码=%d）：\n%s%s",
                    diagnosticId, r.exitCode(), r.stdout(),
                    r.stderr().isBlank() ? "" : "\n错误输出：" + r.stderr());
        } catch (Exception e) {
            log.error("Agent 办公诊断失败: {}", e.getMessage());
            return "办公诊断失败: " + e.getMessage();
        }
    }

}

package com.finoffice.service.kb;

import com.finoffice.entity.MeetingActionItem;
import com.finoffice.entity.Task;
import com.finoffice.mapper.MeetingActionItemMapper;
import com.finoffice.mapper.TaskMapper;
import com.finoffice.mapper.SysUserMapper;
import com.finoffice.service.kb.RagRetrievalService;
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

    private final TaskMapper taskMapper;
    private final MeetingActionItemMapper actionItemMapper;
    private final SysUserMapper userMapper;
    private final RagRetrievalService ragRetrievalService;
    private final BashSecurityService bashSecurityService;

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

    // ---- bash 命令执行（沙箱安全管控） ----

    @Tool("在安全沙箱中执行系统命令。参数：command=要执行的命令（如\"ls -la\" \"echo hello\"）。"
            + "仅白名单命令可执行（ls/cat/echo/date/pwd/wc/grep/find/df/du/ps等），"
            + "危险命令会被拦截（rm/mv/sudo/curl等）。返回命令输出结果。")
    public String executeBash(@dev.langchain4j.agent.tool.P("command") String command) {
        if (command == null || command.isBlank()) {
            return "命令为空，请输入要执行的命令。";
        }
        try {
            ExecResult r = bashSecurityService.execute(command, 1L); // userId=1 系统代理
            if (!r.allowed()) {
                return String.format("命令被拦截：%s（风险等级：%s）", r.reason(), r.riskLevel());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("命令：").append(r.command()).append("\n");
            sb.append("退出码：").append(r.exitCode()).append("\n");
            if (r.timedOut()) sb.append("⚠ 执行超时（").append(5).append("秒）\n");
            if (!r.stdout().isEmpty()) sb.append("--- 标准输出 ---\n").append(r.stdout()).append("\n");
            if (!r.stderr().isEmpty()) sb.append("--- 标准错误 ---\n").append(r.stderr());
            return sb.toString();
        } catch (Exception e) {
            return "命令执行失败: " + e.getMessage();
        }
    }

    // ---- 知识检索 ----

    @Tool("在企业知识库中搜索相关文档内容。参数：query=搜索查询关键词。返回相关文档片段。")
    public String searchKnowledge(
            @dev.langchain4j.agent.tool.P("query") String query) {
        try {
            var ctx = ragRetrievalService.search(query);
            if (ctx == null || ctx.context() == null || ctx.context().isBlank()) {
                return "知识库中未找到与「" + query + "」相关的内容。";
            }
            return ctx.context();
        } catch (Exception e) {
            log.error("Agent 知识检索失败: {}", e.getMessage());
            return "知识检索失败: " + e.getMessage();
        }
    }
}

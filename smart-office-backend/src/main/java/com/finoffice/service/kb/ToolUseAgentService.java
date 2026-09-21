package com.finoffice.service.kb;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool Use Agent 编排器：让 AI 不只是生成文本，而是能真正执行操作。
 *
 * 工作流程：
 * 1. 用户用自然语言描述需求（如"帮张三创建一个周五前的任务"）
 * 2. LLM 识别意图 → 返回 ToolExecutionRequest（调用哪个工具、传什么参数）
 * 3. 本服务执行工具方法 → 把结果送回 LLM
 * 4. LLM 根据结果生成最终回答
 *
 * 支持的工具（来自 AgentTools）：
 * - createTask: 创建任务
 * - createMeetingActionItem: 创建会议待办
 * - searchKnowledge: 知识库检索
 */
@Slf4j
@Service
public class ToolUseAgentService {

    private final ObjectProvider<ChatModel> modelProvider;
    private final AgentTools agentTools;

    /** 缓存的工具规格（从 AgentTools 的 @Tool 注解方法提取） */
    private final List<ToolSpecification> toolSpecifications;

    /** 工具方法缓存：toolName → Method */
    private final Map<String, Method> toolMethods = new ConcurrentHashMap<>();

    public ToolUseAgentService(ObjectProvider<ChatModel> modelProvider, AgentTools agentTools) {
        this.modelProvider = modelProvider;
        this.agentTools = agentTools;

        // 提取 AgentTools 中被 @Tool 注解的方法，生成 ToolSpecification
        this.toolSpecifications = ToolSpecifications.toolSpecificationsFrom(agentTools);

        // 缓存方法名映射
        for (Method m : agentTools.getClass().getDeclaredMethods()) {
            dev.langchain4j.agent.tool.Tool toolAnno = m.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (toolAnno != null) {
                toolMethods.put(m.getName(), m);
            }
        }
        log.info("Tool Use Agent 就绪：{} 个工具已注册 — {}",
                toolMethods.size(),
                toolMethods.keySet());
    }

    /**
     * 执行用户的自然语言指令，Agent 自行决定调用哪些工具。
     *
     * @param userId  当前用户ID
     * @param command 自然语言指令，如"帮张三创建一个本周五前完成系统设计文档的任务"
     * @return Agent 的回答和执行结果
     */
    public String execute(Long userId, String command) {
        ChatModel model = modelProvider.getIfAvailable();
        if (model == null) {
            return "自研引擎未启用，Tool Use Agent 不可用。请在管理后台开启自研引擎。";
        }

        String systemPrompt = "你是恒生智能办公助手，可以与公司的任务系统、知识库交互。"
                + "用户提出的操作需求，你需要调用相应的工具来完成。"
                + "执行操作后，用简洁中文告知用户结果。"
                + "如果用户只是咨询（而非操作），直接在知识库中搜索相关信息回答。";

        // 最多 3 轮交互（用户指令 → 工具调用 → 结果返回 → LLM 最终回答）
        int maxRounds = 3;
        var messages = new java.util.ArrayList<ChatMessage>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(command));

        for (int round = 0; round < maxRounds; round++) {
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecifications)
                    .build();
            ChatResponse response = model.chat(request);
            AiMessage aiMsg = response.aiMessage();

            if (!aiMsg.hasToolExecutionRequests()) {
                // LLM 不再需要调用工具 → 返回最终答案
                return aiMsg.text();
            }

            // LLM 请求调用工具 → 逐个执行
            messages.add(aiMsg);
            for (var toolRequest : aiMsg.toolExecutionRequests()) {
                String toolName = toolRequest.name();
                String arguments = toolRequest.arguments();
                log.info("Agent 调用工具: {} args={}", toolName, arguments);

                String toolResult = executeTool(toolName, arguments);
                messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
            }
        }

        // 超过最大轮数 → 取最后一轮 AI 回答
        return "操作已提交处理。如需查看详情，请到任务看板或会议详情页确认。";
    }

    /** 执行单个工具调用 */
    private String executeTool(String toolName, String arguments) {
        Method method = toolMethods.get(toolName);
        if (method == null) {
            return "未知工具: " + toolName;
        }
        try {
            // 解析参数：LangChain4j 传入的是 JSON 字符串
            // 简单策略：把 JSON 参数传给方法（假设参数顺序与 JSON key 顺序一致）
            // 此处使用反射 + JSON 解析来匹配参数
            Map<String, String> args = parseJsonArgs(arguments);
            Object[] params = resolveParams(method, args);
            Object result = method.invoke(agentTools, params);
            return result != null ? result.toString() : "操作完成";
        } catch (Exception e) {
            log.error("工具执行失败: {} args={} → {}", toolName, arguments, e.getMessage());
            return "执行失败: " + e.getMessage();
        }
    }

    /** 简易 JSON 参数解析（避免引入 Gson/Jackson 增加复杂度） */
    private Map<String, String> parseJsonArgs(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        // 匹配 "key": "value" 或 "key": value
        String content = json.replaceAll("[{}\"]", " "); // 去大括号和引号
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String val = kv[1].trim();
                // 去掉值可能残留的引号
                val = val.replaceAll("^\"|\"$", "").trim();
                if (!key.isEmpty()) {
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    /** 按参数名匹配方法参数 */
    private Object[] resolveParams(Method method, Map<String, String> args) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String paramName = null;
            dev.langchain4j.agent.tool.P pAnno =
                    parameters[i].getAnnotation(dev.langchain4j.agent.tool.P.class);
            if (pAnno != null) {
                paramName = extractParamName(pAnno.value());
            }
            if (paramName == null) {
                paramName = parameters[i].getName();
            }
            String raw = args.get(paramName);
            if (raw == null) {
                // 尝试模糊匹配
                for (String key : args.keySet()) {
                    if (key.contains(paramName) || paramName.contains(key)) {
                        raw = args.get(key);
                        break;
                    }
                }
            }
            values[i] = coerce(raw, parameters[i].getType());
        }
        return values;
    }

    /** 从 @P 注解值中提取参数名（去掉中文说明，取英文/拼音部分） */
    private String extractParamName(String pValue) {
        if (pValue == null) return null;
        // 取第一个词
        String[] parts = pValue.split("[，, ]");
        return parts.length > 0 ? parts[0] : pValue;
    }

    /** 类型转换 */
    private Object coerce(String value, Class<?> type) {
        if (value == null || type == String.class) return value;
        if (type == long.class || type == Long.class) {
            try { return Long.parseLong(value); } catch (NumberFormatException e) { return 0L; }
        }
        if (type == int.class || type == Integer.class) {
            try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
        }
        return value; // 默认当字符串
    }
}

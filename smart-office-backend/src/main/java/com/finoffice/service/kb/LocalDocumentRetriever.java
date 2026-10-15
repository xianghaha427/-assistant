package com.finoffice.service.kb;

import com.finoffice.entity.KbDocument;
import com.finoffice.service.KnowledgePermissionService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地知识文档检索（知识问答页「本地文档」模式）。
 *
 * 只检索**当前用户有权限访问的、平台上上传的**文档：先用 KnowledgePermissionService 做 ACL 过滤，
 * 再把命中的文档正文交给长上下文模型作答，并要求它在回答中标注来源文档。
 *
 * 三条刻意的设计（见方案「隔离优先」）：
 *
 * 1. **不做向量化，也不注册 ChatModel Bean。**
 *    DeepSeek 不提供 embeddings 接口，所以「检索」落在本地按 ACL 取文档、「找答案」交给模型——
 *    筛选发生在本地，也正是权限真正生效的位置。
 *    模型实例在本类内部构造（见 model()），**绝不声明为 @Bean**：
 *    ToolUseAgentService 以 ObjectProvider&lt;ChatModel&gt; 按**类型**索取 ChatModel，
 *    容器里只要出现任何一个 ChatModel Bean，@Tool 工具调用就会被激活，
 *    AgentTools.createTask 中写死的 creatorUserId(1L) 会随之真正执行。
 *    在内部构造实例，工具调用那条线完全感知不到本次改动。
 *
 * 2. **配置留空即整体关闭。** base-url 或 api-key 为空时 {@link #isEnabled()} 为 false，
 *    前端「本地文档」开关置灰，Dify 路径不受任何影响。
 *
 * 3. **不静默截断。** 超出 max-context-chars 的文档不纳入上下文，但会在回答末尾如实列出；
 *    上传链路不做任何索引构建，上传性能与改动前一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalDocumentRetriever {

    /** 单次请求可携带的文档正文字符上限（max-context-chars 未配置或非法时回落到此值） */
    private static final int DEFAULT_MAX_CONTEXT_CHARS = 30000;

    private static final String SYSTEM_PROMPT = """
            你是企业知识库问答助手。请严格遵守以下规则：
            1. 只能依据下面提供的【知识文档】内容回答，不得使用文档之外的知识，不得推测或编造。
            2. 每个结论都要标注来源，格式为「（来源：文档名）」。
            3. 如果文档中确实没有能回答该问题的内容，直接说明「提供的知识文档中没有相关内容」，并指出用户可能需要哪些文档。
            4. 用简洁、有条理的中文回答。
            """;

    private final KnowledgePermissionService permissionService;

    @Value("${finpilot.kb-model.base-url:}")
    private String baseUrl;

    @Value("${finpilot.kb-model.api-key:${DEEPSEEK_API_KEY:}}")
    private String apiKey;

    @Value("${finpilot.kb-model.model-name:deepseek-chat}")
    private String modelName;

    @Value("${finpilot.kb-model.max-context-chars:30000}")
    private int maxContextChars;

    @Value("${finpilot.kb-model.timeout-seconds:60}")
    private long timeoutSeconds;

    /** 缓存构造好的模型实例：配置在启动期固定，无需失效 */
    private volatile ChatModel cachedModel;

    /** 检索结果。source 取值：local / local-empty / local-disabled / local-error */
    public record Result(String answer, String source) {}

    /** 配置齐全才可用；前端据此决定「本地文档」开关是否可点 */
    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    /**
     * 按当前用户的可见范围检索本地文档并作答。
     *
     * @param userId 提问者。可见范围完全交给 KnowledgePermissionService，本类不新增任何权限逻辑
     * @param query  用户问题
     */
    public Result retrieve(Long userId, String query) {
        if (!isEnabled()) {
            return new Result("本地文档检索未启用（未配置模型端点），请改用 Dify 知识库模式。", "local-disabled");
        }

        List<KbDocument> documents = permissionService.accessibleDocuments(userId);
        if (documents.isEmpty()) {
            return new Result("你没有可访问的知识文档。请先在「文档管理」上传，或联系管理员为你开通授权。", "local-empty");
        }

        List<String> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String context = buildContext(documents, included, skipped);
        if (included.isEmpty()) {
            return new Result("你有 " + documents.size() + " 篇可访问文档，但没有一篇能纳入本次检索"
                    + "（正文为空或超出单次长度上限），请换一种问法或联系管理员。", "local-empty");
        }

        String answer;
        try {
            answer = model().chat(ChatRequest.builder()
                    .messages(buildMessages(context, query))
                    .build()).aiMessage().text();
        } catch (Exception e) {
            log.warn("本地文档检索调用模型失败: {}", e.getMessage());
            return new Result("本地文档检索暂时不可用（模型调用失败），请稍后重试，或改用 Dify 知识库模式。", "local-error");
        }
        if (answer == null || answer.isBlank()) {
            return new Result("模型没有返回内容，请稍后重试。", "local-error");
        }

        log.info("本地文档检索完成：可见文档 {} 篇，纳入 {} 篇，跳过 {} 篇",
                documents.size(), included.size(), skipped.size());
        if (!skipped.isEmpty()) {
            answer = answer + "\n\n（说明：以下文档本次未纳入检索：" + String.join("、", skipped) + "）";
        }
        return new Result(answer, "local");
    }

    /** 按 max-context-chars 拼装上下文；跳过的文档名记入 skipped，由调用方如实告知用户 */
    private String buildContext(List<KbDocument> documents, List<String> included, List<String> skipped) {
        int limit = maxContextChars > 0 ? maxContextChars : DEFAULT_MAX_CONTEXT_CHARS;
        StringBuilder context = new StringBuilder();
        int index = 0;
        for (KbDocument doc : documents) {
            index++;
            String name = (doc.getName() == null || doc.getName().isBlank()) ? ("未命名文档" + index) : doc.getName();
            String content = doc.getContent() == null ? "" : doc.getContent().trim();
            if (content.isEmpty()) {
                skipped.add(name + "（正文为空）");
                continue;
            }
            String category = (doc.getCategory() == null || doc.getCategory().isBlank()) ? "未分类" : doc.getCategory();
            String header = "【文档" + index + "】" + name + "（分类：" + category + "）\n";
            if (context.length() + header.length() + content.length() > limit) {
                skipped.add(name + "（超出长度上限）");
                continue;
            }
            context.append(header).append(content).append("\n\n");
            included.add(name);
        }
        return context.toString();
    }

    private List<ChatMessage> buildMessages(String context, String query) {
        String userMessage = "以下是我有权限访问的知识文档：\n\n" + context
                + "\n请只依据上述文档回答这个问题：" + query;
        return List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userMessage));
    }

    /**
     * 构造并缓存模型实例。
     * ⚠️ 必须保持为私有方法——一旦改成 @Bean，@Tool 工具调用会被连带激活。
     */
    private ChatModel model() {
        ChatModel model = cachedModel;
        if (model == null) {
            synchronized (this) {
                model = cachedModel;
                if (model == null) {
                    // 环境变量被透传成空串时 @Value 的默认值不会生效，这里再兜一次底
                    String effectiveModelName = (modelName == null || modelName.isBlank()) ? "deepseek-chat" : modelName;
                    model = OpenAiChatModel.builder()
                            .baseUrl(baseUrl)
                            .apiKey(apiKey)
                            .modelName(effectiveModelName)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .build();
                    cachedModel = model;
                    log.info("本地知识文档检索模型就绪: baseUrl={} model={} maxContextChars={}",
                            baseUrl, effectiveModelName, maxContextChars);
                }
            }
        }
        return model;
    }
}

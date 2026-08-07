package com.finoffice.service;

import com.finoffice.config.DeepSeekConfig;
import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PPT大纲生成服务（对应 agent_config id=4，Dify workflow 型应用「PPT大纲生成」）
 *
 * 链路（三级降级）：
 *   1. difyService.runWorkflow(agent#4, inputs={topic, style, page_count}) 获取结构化大纲 JSON
 *   2. DeepSeek 直连生成同构大纲 JSON
 *   3. Mock 大纲兜底
 * 解析容错与 POI 渲染交由 {@link PptRenderService}。
 *
 * 记录存储：最简实现——进程内 ConcurrentHashMap（id → 记录，含渲染后的 pptx 字节），
 * 不新增数据库表，避免与并行开发的任务模块产生 schema 冲突；重启后记录失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PptService {

    private static final long AGENT_ID_PPT = 4L;

    private final DifyService difyService;
    private final DeepSeekService deepSeekService;
    private final DeepSeekConfig deepSeekConfig;
    private final AgentConfigMapper agentConfigMapper;
    private final PptRenderService renderService;

    @Value("${dify.base-url:}")
    private String difyBaseUrl;

    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis() % 100000);
    private final Map<Long, PptRenderService.PptRecord> store = new ConcurrentHashMap<>();

    /**
     * 生成 PPT：调大纲 → 容错解析 → POI 渲染 → 落内存记录
     *
     * @return 生成记录（含 id、标题、页数与 pptx 字节）
     */
    public PptRenderService.PptRecord generate(String topic, long userId) {
        String rawJson = null;
        String source = "mock";

        // ---------- 优先级1：Dify workflow（agent#4 PPT大纲生成） ----------
        AgentConfig agent = null;
        try {
            agent = agentConfigMapper.selectById(AGENT_ID_PPT);
        } catch (Exception e) {
            log.warn("查询PPT大纲生成智能体配置失败: {}", e.getMessage());
        }
        if (agent != null && agent.getDifyAppId() != null && !agent.getDifyAppId().isBlank()
                && difyBaseUrl != null && !difyBaseUrl.isBlank()) {
            try {
                Map<String, String> inputs = new LinkedHashMap<>();
                String inputVar = (agent.getInputVariable() != null && !agent.getInputVariable().isBlank())
                        ? agent.getInputVariable() : "topic";
                inputs.put(inputVar, topic);
                // workflow 开始的其余可选变量给默认值（style 须为 Dify 应用枚举值：正式商务/简洁现代/数据驱动）
                inputs.putIfAbsent("style", "正式商务");
                inputs.putIfAbsent("page_count", "6");
                rawJson = difyService.runWorkflow(agent.getDifyAppId(), inputs, userId);
                source = "dify";
            } catch (Exception e) {
                log.warn("PPT大纲生成 Dify workflow 调用失败，降级到 DeepSeek: {}", e.getMessage());
            }
        } else {
            log.info("PPT大纲生成 agent#4 未配置 Dify 密钥或 Dify 地址为空，降级到 DeepSeek");
        }

        // ---------- 优先级2：DeepSeek 直连生成大纲 JSON ----------
        if (rawJson == null && deepSeekConfig.isEnabled() && deepSeekConfig.getApiKey() != null
                && !deepSeekConfig.getApiKey().isBlank()) {
            try {
                StringBuilder sb = new StringBuilder();
                deepSeekService.streamChat(
                        "你是资深 PPT 策划专家。请根据主题生成 PPT 大纲，只输出 JSON（用 ```json 代码块包裹），结构为："
                                + "{\"title\":\"主标题\",\"subtitle\":\"副标题\",\"pages\":[{\"page\":1,\"title\":\"页面标题\","
                                + "\"type\":\"cover/content/summary\",\"bullets\":[\"要点1\",\"要点2\"],\"speaker_notes\":\"演讲备注\"}]}。"
                                + "第1页为封面（type=cover，bullets 可为空），中间3-4页内容页，最后1页总结页（type=summary）。",
                        "主题：" + topic,
                        sb::append,
                        () -> { }
                );
                if (!sb.isEmpty()) {
                    rawJson = sb.toString();
                    source = "deepseek";
                }
            } catch (Exception e) {
                log.warn("PPT大纲生成 DeepSeek 直连失败，降级到 Mock 大纲: {}", e.getMessage());
            }
        }

        // ---------- 优先级3：Mock 大纲（在 parseOutline 内部兜底） ----------
        if (rawJson == null) {
            source = "mock";
        }

        PptRenderService.PptOutline outline = renderService.parseOutline(rawJson, topic);
        // 调试：确认大纲备注字段是否解析到
        long notesCount = outline.getPages().stream().filter(p -> p.getNotes() != null && !p.getNotes().isBlank()).count();
        log.info("PPT大纲生成解析: source={}, pages={}, 含备注页数={}, raw前160字符={}",
                source, outline.getPages().size(), notesCount,
                rawJson == null ? "null" : rawJson.substring(0, Math.min(160, rawJson.length())));
        byte[] pptx = renderService.render(outline);

        PptRenderService.PptRecord record = new PptRenderService.PptRecord();
        record.setId(idSeq.incrementAndGet());
        record.setUserId(userId);
        record.setTopic(topic);
        record.setTitle(outline.getTitle());
        record.setPages(outline.getPages().size());
        record.setSource(source);
        record.setPptx(pptx);
        record.setCreateTime(LocalDateTime.now());
        store.put(record.getId(), record);
        log.info("PPT大纲生成完成: id={}, topic={}, pages={}, source={}", record.getId(), topic, record.getPages(), source);
        return record;
    }

    /** 按 id 取记录 */
    public PptRenderService.PptRecord get(Long id) {
        return store.get(id);
    }
}

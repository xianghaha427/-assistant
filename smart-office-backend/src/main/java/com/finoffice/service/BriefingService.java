package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finoffice.config.DeepSeekConfig;
import com.finoffice.entity.AgentConfig;
import com.finoffice.entity.BriefingRecord;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.mapper.BriefingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 行业简报服务
 * 生成链路（三级降级）：agent#3 行业洞察（Dify chat 通道）→ DeepSeek 直连 → Mock 兜底
 * 幂等保证：briefing_record.brief_date 唯一键 uk_date，同日已成功生成不重复生成；
 * force=true 时允许覆盖重新生成（existing 记录转更新，uk_date 仍保证同日一条）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    /** 行业洞察助手智能体 ID（agent_config id=3，chat 模式） */
    private static final long INSIGHT_AGENT_ID = 3L;
    /** 生成尝试次数：首次 + 失败重试 2 次 */
    private static final int MAX_ATTEMPTS = 3;

    private final BriefingRecordMapper briefingMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final DifyService difyService;
    private final DeepSeekService deepSeekService;
    private final DeepSeekConfig deepSeekConfig;

    @Value("${dify.base-url:}")
    private String difyBaseUrl;

    /** 防止并发触发重复生成（定时任务 / 手动触发 / 前端连点互斥） */
    private final AtomicBoolean generating = new AtomicBoolean(false);

    /** 按日期查询简报（可能为 null） */
    public BriefingRecord getByDate(LocalDate briefDate) {
        return briefingMapper.selectOne(new LambdaQueryWrapper<BriefingRecord>()
                .eq(BriefingRecord::getBriefDate, briefDate)
                .last("LIMIT 1"));
    }

    /** 今日简报（可能为 null） */
    public BriefingRecord getToday() {
        return getByDate(LocalDate.now());
    }

    /** 历史分页列表（按日期倒序，含全文供前端展开查看） */
    public Map<String, Object> history(long page, long size) {
        Page<BriefingRecord> p = briefingMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<BriefingRecord>().orderByDesc(BriefingRecord::getBriefDate));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", p.getTotal());
        result.put("records", p.getRecords());
        return result;
    }

    /** 默认幂等生成（当日已 success 不重复生成），等价于 generate(briefDate, false) */
    public GenerateResult generate(LocalDate briefDate) {
        return generate(briefDate, false);
    }

    /**
     * 生成指定日期的简报：
     * - force=false（默认）：当日已有 success 记录时直接返回（source=existing），不重复生成
     * - force=true：忽略已有 success 记录，重新生成并覆盖（existing 转更新；
     *   并发场景下 insert 冲突 uk_date 亦转更新，同日始终仅一条）
     * - 当日为 failed 记录或无记录：三级降级生成，失败重试 2 次后标 status=failed
     *
     * @return record=简报记录；source=实际走的通道（dify/deepseek/mock/existing/running/failed）
     */
    public GenerateResult generate(LocalDate briefDate, boolean force) {
        BriefingRecord existing = getByDate(briefDate);
        if (!force && isSuccess(existing)) {
            log.info("简报 {} 已成功生成（id={}），幂等跳过", briefDate, existing.getId());
            return new GenerateResult(existing, "existing");
        }
        if (!generating.compareAndSet(false, true)) {
            log.info("简报生成任务进行中，跳过本次触发（{}）", briefDate);
            return new GenerateResult(getByDate(briefDate), "running");
        }
        try {
            // 获得互斥后二次检查，避免排队期间已被其他线程生成（force 时跳过，坚持覆盖）
            existing = getByDate(briefDate);
            if (!force && isSuccess(existing)) {
                return new GenerateResult(existing, "existing");
            }

            String query = "请生成 " + briefDate + " 的今日金融行业行业简报，要求包含：市场概况（A股/债券/汇率走势）、"
                    + "监管动态、行业热点、风险提示四个板块，使用 Markdown 格式，内容专业、结构清晰、简明扼要。";

            String content = null;
            String source = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    String[] r = generateWithFallback(query, briefDate);
                    content = r[0];
                    source = r[1];
                    break;
                } catch (Exception e) {
                    log.warn("简报生成第 {}/{} 次尝试失败（{}）：{}", attempt, MAX_ATTEMPTS, briefDate, e.getMessage());
                }
            }

            BriefingRecord rec = existing != null ? existing : new BriefingRecord();
            rec.setBriefDate(briefDate);
            if (content != null && !content.isBlank()) {
                rec.setTitle(extractTitle(content, briefDate));
                rec.setContent(content);
                rec.setStatus("success");
                // 刷新生成时间：覆盖重新生成时前端轮询依据 create_time 变化判定完成
                rec.setCreateTime(LocalDateTime.now());
                log.info("简报{}成功（{}），实际通道：{}，内容长度 {}",
                        force && existing != null ? "覆盖重新生成" : "生成", briefDate, source, content.length());
            } else {
                rec.setTitle("金融行业日报 " + briefDate);
                rec.setContent("");
                rec.setStatus("failed");
                log.error("简报生成失败（{}），{} 次尝试均未成功，标记 failed", briefDate, MAX_ATTEMPTS);
                source = "failed";
            }
            save(rec, existing != null);
            return new GenerateResult(rec, source);
        } finally {
            generating.set(false);
        }
    }

    private boolean isSuccess(BriefingRecord rec) {
        return rec != null && "success".equals(rec.getStatus())
                && rec.getContent() != null && !rec.getContent().isBlank();
    }

    /**
     * 三级降级生成：Dify → DeepSeek → Mock（Mock 永不失败，保证演示不空）
     *
     * @return [0]=全文内容 [1]=通道标识 dify/deepseek/mock
     */
    private String[] generateWithFallback(String query, LocalDate briefDate) {
        AgentConfig agent = agentConfigMapper.selectById(INSIGHT_AGENT_ID);

        // 级别1：Dify（agent#3 行业洞察，chat 通道聚合全文）
        if (agent != null && agent.getDifyAppId() != null && !agent.getDifyAppId().isBlank()
                && difyBaseUrl != null && !difyBaseUrl.isBlank()) {
            try {
                StringBuilder sb = new StringBuilder();
                // streamChat 为阻塞式：逐块回调聚合全文，无会话上下文（conversationId 传 null）
                difyService.streamChat(agent.getDifyAppId(), query, null, 0L,
                        sb::append, convId -> { }, () -> { });
                String content = sb.toString().trim();
                if (!content.isBlank()) {
                    return new String[]{content, "dify"};
                }
                log.warn("Dify 返回空内容，降级 DeepSeek");
            } catch (Exception e) {
                log.warn("Dify 调用失败，降级 DeepSeek：{}", e.getMessage());
            }
        }

        // 级别2：DeepSeek 直连（沿用智能体 systemPrompt）
        if (deepSeekConfig.isEnabled() && deepSeekConfig.getApiKey() != null
                && !deepSeekConfig.getApiKey().isBlank()) {
            try {
                String systemPrompt = agent != null && agent.getSystemPrompt() != null
                        && !agent.getSystemPrompt().isBlank()
                        ? agent.getSystemPrompt()
                        : "你是一名资深金融行业分析师，擅长撰写专业、结构清晰的金融行业日报。";
                StringBuilder sb = new StringBuilder();
                deepSeekService.streamChat(systemPrompt, query, sb::append, () -> { });
                String content = sb.toString().trim();
                if (!content.isBlank()) {
                    return new String[]{content, "deepseek"};
                }
                log.warn("DeepSeek 返回空内容，降级 Mock");
            } catch (Exception e) {
                log.warn("DeepSeek 调用失败，降级 Mock：{}", e.getMessage());
            }
        }

        // 级别3：Mock 兜底（结构合理的示例简报，保证演示不空）
        return new String[]{mockBriefing(briefDate), "mock"};
    }

    /** 落库：新增冲突 uk_date 时转更新，保证同日不出现重复记录 */
    private void save(BriefingRecord rec, boolean isUpdate) {
        if (isUpdate) {
            briefingMapper.updateById(rec);
            return;
        }
        try {
            briefingMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            BriefingRecord cur = getByDate(rec.getBriefDate());
            if (cur != null) {
                log.info("简报 {} 并发写入冲突，转为更新 id={}", rec.getBriefDate(), cur.getId());
                rec.setId(cur.getId());
                briefingMapper.updateById(rec);
            }
        }
    }

    /** 从正文首个有效行提取标题（去除 Markdown 标记，截断 60 字） */
    private String extractTitle(String content, LocalDate briefDate) {
        for (String line : content.split("\n")) {
            String t = line.replaceAll("^[#\\s>\\-*]+", "").trim();
            if (!t.isEmpty()) {
                return t.length() > 60 ? t.substring(0, 60) : t;
            }
        }
        return "金融行业日报 " + briefDate;
    }

    /** Mock 示例简报：结构完整的演示内容 */
    private String mockBriefing(LocalDate briefDate) {
        return "# 金融行业日报（" + briefDate + "）\n\n"
                + "## 一、市场概况\n"
                + "A股三大指数今日集体收涨，沪指站上 3300 点整数关口，成交额较前一交易日放量约 12%；"
                + "银行、保险等金融权重板块领涨，科技成长板块分化。债市方面，10 年期国债收益率小幅下行，"
                + "资金面维持宽松。人民币汇率双向波动，中间价保持基本稳定。\n\n"
                + "## 二、监管动态\n"
                + "监管部门发布商业银行资本管理补充规定征求意见稿，进一步细化中小银行差异化监管要求；"
                + "同时强调持续推进金融领域「黑灰产」打击治理，强化消费者权益保护。\n\n"
                + "## 三、行业热点\n"
                + "1. 多家银行加速布局 AI 智能客服与智能风控，大模型应用进入规模化试点阶段；\n"
                + "2. 绿色金融债发行规模同比显著增长，转型金融产品创新活跃；\n"
                + "3. 跨境支付互联互通取得新进展，机构跨境结算效率进一步提升。\n\n"
                + "## 四、风险提示\n"
                + "关注海外市场波动对风险偏好的传导影响；部分中小金融机构资产质量承压，"
                + "建议持续跟踪信用利差变化，做好流动性管理与合规风控。\n\n"
                + "> 本简报由 FinPilot 智能引擎自动生成（演示模式），仅供参考，不构成投资建议。";
    }

    /** 生成结果：记录 + 实际通道 */
    public record GenerateResult(BriefingRecord record, String source) {
    }
}

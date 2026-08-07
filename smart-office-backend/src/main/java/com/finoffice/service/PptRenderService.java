package com.finoffice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PPT大纲生成：大纲 JSON 容错解析 + Apache POI 真实 .pptx 渲染
 *
 * 大纲 JSON 契约（agent#4 PPT大纲生成 workflow 输出的 ppt_json）：
 *   { "title": "主标题", "subtitle": "副标题",
 *     "pages": [ {"page":1, "title":"...", "type":"cover/content/data/summary",
 *                 "bullets":["要点1","要点2"], "speaker_notes":"演讲备注"} ] }
 * 解析容错：剥掉 ```json 围栏 → 正则取首个 {...} → 兼容 sections/key_point 变体 → 全失败走 Mock 大纲
 *
 * 渲染样式：16:9 画布，纯色简洁母版——白底 + 品牌蓝（#4D8DFF）标题条，
 * 中文字体统一微软雅黑防乱码；每页演讲备注写入 notes。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PptRenderService {

    private static final Color BRAND_BLUE = new Color(0x4D, 0x8D, 0xFF);
    private static final Color DARK_TEXT = new Color(0x1F, 0x27, 0x33);
    private static final Color GRAY_TEXT = new Color(0x5A, 0x64, 0x78);
    private static final Color BODY_TEXT = new Color(0x3D, 0x46, 0x57);
    private static final String FONT_NAME = "微软雅黑";
    private static final int W = 960;
    private static final int H = 540;

    private final ObjectMapper objectMapper;
    /** notesSlide part 序号（全局递增不重置，保证并发下 part 名唯一） */
    private final java.util.concurrent.atomic.AtomicInteger notesSeq = new java.util.concurrent.atomic.AtomicInteger();

    /** PPT 单页 */
    @Data
    public static class PptPage {
        private String title;
        private String type = "content";
        private List<String> bullets = new ArrayList<>();
        private String notes = "";
    }

    /** PPT 大纲 */
    @Data
    public static class PptOutline {
        private String title;
        private String subtitle = "";
        private List<PptPage> pages = new ArrayList<>();
    }

    /** 生成记录（内存态） */
    @Data
    public static class PptRecord {
        private Long id;
        private long userId;
        private String topic;
        private String title;
        private int pages;
        /** 渲染来源：dify / deepseek / mock */
        private String source;
        private byte[] pptx;
        private java.time.LocalDateTime createTime;
    }

    /**
     * 容错解析大纲 JSON；解析彻底失败时返回基于主题的 Mock 大纲（保证下载链路可用）
     */
    public PptOutline parseOutline(String rawJson, String topic) {
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                String json = extractJson(rawJson);
                JsonNode root = objectMapper.readTree(json);
                PptOutline outline = new PptOutline();
                outline.setTitle(text(root.path("title"), topic));
                outline.setSubtitle(text(root.path("subtitle"), ""));

                JsonNode pagesNode = root.path("pages");
                if (!pagesNode.isArray() || pagesNode.isEmpty()) {
                    // 兼容变体：sections 数组（大纲阶段结构，bullets 用 key_point 兜底）
                    pagesNode = root.path("sections");
                }
                if (pagesNode.isArray()) {
                    for (JsonNode p : pagesNode) {
                        PptPage page = new PptPage();
                        page.setTitle(text(p.path("title"), ""));
                        page.setType(text(p.path("type"), "content"));
                        page.setNotes(text(p.path("speaker_notes"), text(p.path("notes"), "")));
                        JsonNode bullets = p.path("bullets");
                        if (bullets.isArray() && !bullets.isEmpty()) {
                            for (JsonNode b : bullets) {
                                if (b.isTextual() && !b.asText().isBlank()) {
                                    page.getBullets().add(b.asText().trim());
                                }
                            }
                        } else if (!p.path("key_point").asText("").isBlank()) {
                            page.getBullets().add(p.path("key_point").asText().trim());
                        }
                        if (!page.getTitle().isBlank() || !page.getBullets().isEmpty()) {
                            outline.getPages().add(page);
                        }
                    }
                }
                if (!outline.getPages().isEmpty()) {
                    return outline;
                }
                log.warn("PPT 大纲 JSON 无有效页面，降级为 Mock 大纲");
            } catch (Exception e) {
                log.warn("PPT 大纲 JSON 解析失败，降级为 Mock 大纲: {}", e.getMessage());
            }
        }
        return mockOutline(topic);
    }

    /** 剥掉 ```json 围栏；再用括号配对扫描取首个完整 {...} */
    private String extractJson(String raw) {
        String text = raw.trim();
        Matcher fence = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE).matcher(text);
        if (fence.find()) {
            text = fence.group(1).trim();
        }
        int start = text.indexOf('{');
        if (start >= 0) {
            int depth = 0;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return text;
    }

    private String text(JsonNode node, String def) {
        String v = node == null ? null : node.asText(null);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    /** Mock 大纲（Dify/DeepSeek 均不可用时的兜底） */
    public PptOutline mockOutline(String topic) {
        PptOutline o = new PptOutline();
        o.setTitle(topic);
        o.setSubtitle("华信证券智能办公平台 · PPT大纲生成（演示模板）");

        PptPage cover = new PptPage();
        cover.setTitle(topic);
        cover.setType("cover");
        cover.setNotes("开场：简要说明本次汇报主题与背景。");
        o.getPages().add(cover);

        PptPage p1 = new PptPage();
        p1.setTitle("背景与目标");
        p1.setBullets(List.of("围绕「" + topic + "」明确汇报背景", "说明本次汇报要解决的核心问题", "给出可衡量的目标口径"));
        p1.setNotes("本页交代背景与目标，控制在一分钟内。");
        o.getPages().add(p1);

        PptPage p2 = new PptPage();
        p2.setTitle("现状与关键数据");
        p2.setBullets(List.of("梳理当前整体情况与关键指标", "用数据呈现亮点与差距", "标注数据来源与统计口径"));
        p2.setNotes("重点讲数据结论，不逐条念数字。");
        o.getPages().add(p2);

        PptPage p3 = new PptPage();
        p3.setTitle("举措与计划");
        p3.setBullets(List.of("列出已采取的关键举措", "明确下一阶段计划与里程碑", "说明所需资源与协同事项"));
        p3.setNotes("落到责任人与时间节点，便于会后跟踪。");
        o.getPages().add(p3);

        PptPage sum = new PptPage();
        sum.setTitle("总结");
        sum.setType("summary");
        sum.setBullets(List.of("回顾核心结论", "重申下一步行动", "感谢聆听，欢迎讨论"));
        sum.setNotes("收尾页：一句话总结 + 致谢。");
        o.getPages().add(sum);
        return o;
    }

    /**
     * 渲染为 .pptx 字节数组：
     * 封面页（标题+副标题）+ 内容页（章节标题条+bullets）+ 每页演讲备注 notes
     */
    public byte[] render(PptOutline outline) {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(W, H));

            for (PptPage page : outline.getPages()) {
                if ("cover".equalsIgnoreCase(page.getType())) {
                    addCoverSlide(ppt, outline.getTitle(), outline.getSubtitle(), page.getNotes());
                } else {
                    addContentSlide(ppt, page);
                }
            }
            // 大纲首页不是 cover 时，补一张封面
            if (outline.getPages().isEmpty()
                    || !"cover".equalsIgnoreCase(outline.getPages().get(0).getType())) {
                // 若首页即内容页，封面已在循环中按内容页处理；此处仅对空大纲兜底
                if (outline.getPages().isEmpty()) {
                    addCoverSlide(ppt, outline.getTitle(), outline.getSubtitle(), "");
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PPT 渲染失败: " + e.getMessage(), e);
        }
    }

    /** 封面页：白底 + 品牌蓝上下边条 + 主标题 + 副标题 */
    private void addCoverSlide(XMLSlideShow ppt, String title, String subtitle, String notes) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(Color.WHITE);

        addRect(slide, 0, 0, W, 10, BRAND_BLUE);
        addRect(slide, 0, H - 10, W, 10, BRAND_BLUE);
        // 左侧品牌竖条
        addRect(slide, 0, 200, 12, 140, BRAND_BLUE);

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(48, 200, W - 96, 90));
        XSLFTextRun titleRun = titleBox.addNewTextParagraph().addNewTextRun();
        titleRun.setText(safe(title));
        styleRun(titleRun, 34, true, DARK_TEXT);

        XSLFTextBox subBox = slide.createTextBox();
        subBox.setAnchor(new Rectangle(48, 300, W - 96, 50));
        XSLFTextRun subRun = subBox.addNewTextParagraph().addNewTextRun();
        subRun.setText(subtitle == null || subtitle.isBlank() ? "华信证券智能办公平台" : subtitle);
        styleRun(subRun, 16, false, GRAY_TEXT);

        writeNotes(ppt, slide, notes);
    }

    /** 内容页：顶部品牌蓝标题条 + bullets 正文 */
    private void addContentSlide(XMLSlideShow ppt, PptPage page) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(Color.WHITE);

        // 品牌蓝标题条
        XSLFAutoShape bar = slide.createAutoShape();
        bar.setAnchor(new Rectangle(0, 0, W, 64));
        bar.setFillColor(BRAND_BLUE);
        bar.setLineColor(BRAND_BLUE);
        bar.setText(page.getTitle() == null || page.getTitle().isBlank() ? "内容页" : page.getTitle());
        bar.setWordWrap(true);
        bar.setLeftInset(40.0);
        bar.setTopInset(10.0);
        for (XSLFTextParagraph para : bar.getTextParagraphs()) {
            para.setTextAlign(TextAlign.LEFT);
            for (XSLFTextRun run : para.getTextRuns()) {
                styleRun(run, 20, true, Color.WHITE);
            }
        }

        // bullets 正文
        XSLFTextBox body = slide.createTextBox();
        body.setAnchor(new Rectangle(56, 96, W - 112, H - 140));
        body.setWordWrap(true);
        List<String> bullets = page.getBullets() == null || page.getBullets().isEmpty()
                ? List.of("（本页暂无要点）") : page.getBullets();
        for (String bullet : bullets) {
            XSLFTextParagraph para = body.addNewTextParagraph();
            para.setSpaceAfter(12.0);
            para.setLineSpacing(140.0);
            XSLFTextRun run = para.addNewTextRun();
            run.setText("•  " + bullet);
            styleRun(run, 16, false, BODY_TEXT);
        }

        writeNotes(ppt, slide, page.getNotes());
    }

    /** 纯色矩形装饰块 */
    private void addRect(XSLFSlide slide, int x, int y, int w, int h, Color color) {
        XSLFAutoShape rect = slide.createAutoShape();
        rect.setAnchor(new Rectangle(x, y, w, h));
        rect.setFillColor(color);
        rect.setLineColor(color);
        rect.setText("");
    }

    /** 统一字体样式：微软雅黑防乱码 */
    private void styleRun(XSLFTextRun run, double size, boolean bold, Color color) {
        run.setFontFamily(FONT_NAME);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
    }

    /**
     * 演讲备注写入 notes：
     * 空白模板下 XSLFSlide.getNotes() 只查已有关系、不会自动创建 notesSlide，
     * 且 XSLFNotesSlide 为包内可见无法直接 new，因此这里用低级 XMLSchemaBeans
     * 手工构造 notesSlide part 并与 slide 建立关联。
     */
    private void writeNotes(XMLSlideShow ppt, XSLFSlide slide, String notes) {
        if (notes == null || notes.isBlank()) {
            return;
        }
        try {
            if (ppt.getNotesMaster() == null) {
                ppt.createNotesMaster();
            }
            XSLFNotes notesSheet = slide.getNotes();
            if (notesSheet == null) {
                notesSheet = createNotesSheet(ppt, slide, notes);
            }
            if (notesSheet == null) {
                return;
            }
            // 新建的 notesSlide 已内嵌备注文本，无需再写
            if (notesSheet.getShapes().stream().anyMatch(s -> s instanceof XSLFTextShape ts
                    && ts.getText() != null && ts.getText().contains(notes))) {
                return;
            }
            // 优先找备注页占位符正文框（body placeholder），否则取第一个文本框，再无则新建
            XSLFTextShape notesBody = null;
            for (XSLFShape shape : notesSheet.getShapes()) {
                if (shape instanceof XSLFTextShape ts
                        && ts.getTextType() == org.apache.poi.sl.usermodel.Placeholder.BODY) {
                    notesBody = ts;
                    break;
                }
            }
            if (notesBody == null) {
                for (XSLFShape shape : notesSheet.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        notesBody = ts;
                        break;
                    }
                }
            }
            if (notesBody == null) {
                notesBody = notesSheet.createTextBox();
                notesBody.setAnchor(new Rectangle(50, 300, 600, 200));
            }
            notesBody.clearText();
            XSLFTextRun run = notesBody.addNewTextParagraph().addNewTextRun();
            run.setText(notes);
            run.setFontFamily(FONT_NAME);
            run.setFontSize(12.0);
        } catch (Exception e) {
            log.warn("写入演讲备注失败: {}", e.getMessage());
        }
    }

    /**
     * 手工构造 notesSlide part（含 BODY 占位符 + 备注文本）并关联到 slide。
     * 空白模板下 POI 不会自动创建 notesSlide，直接 OPCPackage.createPart
     * 不注册 content-type 会导致保存时 part 丢失；改用 POI 公开的
     * createRelationship（自动建关系、创建 part 并注册 content-type）。
     */
    private XSLFNotes createNotesSheet(XMLSlideShow ppt, XSLFSlide slide, String notes) throws Exception {
        // 三参重载显式指定序号，避免每页生成同名 part 冲突
        org.apache.poi.ooxml.POIXMLDocumentPart notesPart =
                slide.createRelationship(org.apache.poi.xslf.usermodel.XSLFRelation.NOTES,
                        org.apache.poi.xslf.usermodel.XSLFFactory.getInstance(), notesSeq.incrementAndGet());
        org.apache.poi.openxml4j.opc.PackagePart part = notesPart.getPackagePart();

        // poi-ooxml-lite 未包含 CTNotes，直接写入最小合法 notesSlide XML
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<p:notes xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:cSld><p:spTree>"
                + "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
                + "<p:grpSpPr/>"
                + "<p:sp>"
                + "<p:nvSpPr><p:cNvPr id=\"2\" name=\"Notes Placeholder 1\"/>"
                + "<p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>"
                + "<p:nvPr><p:ph type=\"body\" idx=\"1\"/></p:nvPr></p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"457200\" y=\"1825630\"/>"
                + "<a:ext cx=\"8229600\" cy=\"4351340\"/></a:xfrm></p:spPr>"
                + "<p:txBody><a:bodyPr/><a:lstStyle/>"
                + "<a:p><a:r><a:rPr lang=\"zh-CN\" sz=\"1200\"><a:latin typeface=\"微软雅黑\"/>"
                + "<a:ea typeface=\"微软雅黑\"/></a:rPr><a:t>" + escapeXml(notes) + "</a:t></a:r></a:p>"
                + "</p:txBody>"
                + "</p:sp>"
                + "</p:spTree></p:cSld></p:notes>";
        try (java.io.OutputStream os = part.getOutputStream()) {
            os.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        // notesSlide → notesMaster 关系，避免 PowerPoint 打开时报修复
        try {
            org.apache.poi.openxml4j.opc.PackagePartName masterName =
                    org.apache.poi.openxml4j.opc.PackagingURIHelper.createPartName("/ppt/notesMasters/notesMaster1.xml");
            part.addRelationship(masterName, org.apache.poi.openxml4j.opc.TargetMode.INTERNAL,
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster");
        } catch (Exception ignored) {
        }
        return slide.getNotes();
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "未命名演示文稿" : s;
    }
}

package com.finoffice.service.kb;

import com.finoffice.common.BizException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** 从上传的知识库文档中抽取纯文本。 */
@Component
public class DocumentTextExtractor {

    public String extract(MultipartFile file, String ext) {
        try {
            return switch (ext) {
                case "txt", "md" -> new String(file.getBytes(), StandardCharsets.UTF_8);
                case "docx" -> extractDocx(file);
                case "pdf" -> extractPdf(file);
                default -> throw new BizException("不支持的文件格式");
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文档解析失败: " + e.getMessage());
        }
    }

    private String extractDocx(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream(); XWPFDocument document = new XWPFDocument(in)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
        }
    }

    private String extractPdf(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream(); PDDocument document = PDDocument.load(in)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

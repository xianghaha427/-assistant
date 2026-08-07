package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.R;
import com.finoffice.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 知识库接口
 * 知识问答为主功能（直连 Dify 企业知识问答助手）；
 * 文档由管理员在 Dify 侧维护，平台不再提供上传/管理接口。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 知识库问答
     * 返回 { answer, source, query }，source：dify（真实回答）/ mock（降级文案）
     */
    @PostMapping("/search")
    public R<Map<String, String>> search(@RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String query = body.get("query");
        KnowledgeService.KnowledgeAnswer result = knowledgeService.searchInKnowledge(userId, query);
        return R.ok(Map.of("answer", result.answer(), "source", result.source(), "query", query));
    }
}

package com.finoffice.controller;

import com.finoffice.common.R;
import com.finoffice.service.WorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台聚合接口：一次返回今日会议/我的待办/今日简报/统计卡片，
 * 内部并行查询，单块失败局部降级、不整体失败
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    @GetMapping("/home")
    public R<Map<String, Object>> home() {
        return R.ok(workbenchService.home());
    }
}

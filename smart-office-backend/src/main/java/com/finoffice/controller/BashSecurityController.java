package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.service.BashSecurityService;
import com.finoffice.service.BashSecurityService.ExecResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** 平台安全运营中心接口：固定办公诊断任务和安全审计。 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class BashSecurityController {

    private final BashSecurityService bashSecurityService;

    @GetMapping("/presets")
    public R<List<BashSecurityService.Preset>> presets() {
        return R.ok(bashSecurityService.presets());
    }

    @GetMapping("/audit")
    public R<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "30") int limit) {
        return R.ok(bashSecurityService.auditHistory(limit));
    }

    @PostMapping("/preset")
    public R<Map<String, Object>> executePreset(@RequestBody Map<String, String> body) {
        String presetId = body.get("preset_id");
        if (presetId == null || presetId.isBlank()) {
            throw new BizException("请选择安全诊断任务");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        ExecResult r = bashSecurityService.executePreset(presetId, userId);
        return R.ok(toResponse(r));
    }

    private Map<String, Object> toResponse(ExecResult r) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("allowed", r.allowed());
        resp.put("reason", r.reason());
        resp.put("riskLevel", r.riskLevel());
        resp.put("command", r.command());
        resp.put("stdout", r.stdout());
        resp.put("stderr", r.stderr());
        resp.put("exitCode", r.exitCode());
        resp.put("timedOut", r.timedOut());
        return resp;
    }
}

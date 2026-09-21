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

/**
 * bash 安全管控接口：
 * POST /api/security/bash-exec → 校验 + 沙箱执行命令，返回真实输出
 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class BashSecurityController {

    private final BashSecurityService bashSecurityService;

    @PostMapping("/bash-exec")
    public R<Map<String, Object>> execBash(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        if (command == null || command.isBlank()) {
            throw new BizException("请输入命令");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        ExecResult r = bashSecurityService.execute(command, userId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("allowed", r.allowed());
        resp.put("reason", r.reason());
        resp.put("riskLevel", r.riskLevel());
        resp.put("command", r.command());
        resp.put("stdout", r.stdout());
        resp.put("stderr", r.stderr());
        resp.put("exitCode", r.exitCode());
        resp.put("timedOut", r.timedOut());
        return R.ok(resp);
    }
}

package com.finoffice.controller;

import com.finoffice.common.R;
import com.finoffice.entity.AgentConfig;
import com.finoffice.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能体配置接口（Agent 开放平台雏形，功能后续完善）
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public R<List<AgentConfig>> list() {
        return R.ok(agentService.list());
    }

    @PostMapping
    public R<AgentConfig> create(@RequestBody AgentConfig agent) {
        return R.ok(agentService.create(agent));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody AgentConfig agent) {
        agentService.update(id, agent);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return R.ok();
    }
}

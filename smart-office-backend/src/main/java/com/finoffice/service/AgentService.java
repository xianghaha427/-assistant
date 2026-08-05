package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finoffice.common.BizException;
import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体配置服务
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentConfigMapper agentMapper;

    public List<AgentConfig> list() {
        return agentMapper.selectList(
                new LambdaQueryWrapper<AgentConfig>().orderByAsc(AgentConfig::getId));
    }

    public AgentConfig create(AgentConfig agent) {
        if (agent.getName() == null || agent.getName().isBlank()) {
            throw new BizException("智能体名称不能为空");
        }
        if (agent.getIcon() == null) {
            agent.setIcon("chat");
        }
        if (agent.getEnabled() == null) {
            agent.setEnabled(1);
        }
        agentMapper.insert(agent);
        return agent;
    }

    public void update(Long id, AgentConfig agent) {
        agent.setId(id);
        if (agent.getEnabled() == null) {
            agent.setEnabled(1);
        }
        if (agentMapper.updateById(agent) == 0) {
            throw new BizException("智能体不存在");
        }
    }

    public void delete(Long id) {
        agentMapper.deleteById(id);
    }
}

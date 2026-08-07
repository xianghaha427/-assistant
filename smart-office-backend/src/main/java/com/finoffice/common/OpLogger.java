package com.finoffice.common;

import com.finoffice.entity.SysLog;
import com.finoffice.entity.SysUser;
import com.finoffice.mapper.SysLogMapper;
import com.finoffice.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 操作日志工具类（静态、一行埋点）：
 * 异步写 sys_log，写入失败仅告警、不影响业务流程。
 */
@Slf4j
public final class OpLogger {

    /** 单线程异步写入，避免埋点拖慢业务接口 */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "op-logger");
        t.setDaemon(true);
        return t;
    });

    private OpLogger() {
    }

    /**
     * 埋点入口（标准签名）。
     *
     * @param userId   操作人用户ID
     * @param username 操作人用户名（可为 null，自动补查）
     * @param module   模块，如 AUTH / USER / MEETING / BRIEFING / PPT
     * @param action   动作描述
     * @param detail   明细
     */
    public static void log(Long userId, String username, String module, String action, String detail) {
        final String ip = currentIp();
        EXECUTOR.submit(() -> {
            try {
                SysLogMapper logMapper = SpringContextHolder.getBean(SysLogMapper.class);
                SysLog sysLog = new SysLog();
                sysLog.setUserId(userId);
                sysLog.setUsername(username != null ? username : usernameOf(userId));
                sysLog.setModule(module);
                sysLog.setAction(action);
                sysLog.setDetail(detail);
                sysLog.setResult("success");
                sysLog.setIp(ip);
                sysLog.setCreateTime(LocalDateTime.now());
                logMapper.insert(sysLog);
            } catch (Exception e) {
                log.warn("操作日志写入失败（不影响业务）：{}", e.getMessage());
            }
        });
    }

    /** 便捷重载：username 自动按 userId 补查 */
    public static void log(Long userId, String module, String action, String detail) {
        log(userId, null, module, action, detail);
    }

    private static String usernameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            SysUser u = SpringContextHolder.getBean(SysUserMapper.class).selectById(userId);
            return u == null ? null : u.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    /** 在 Web 线程内抓取客户端 IP（异步线程拿不到 request 上下文） */
    private static String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}

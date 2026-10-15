package com.finoffice.service;

import com.finoffice.common.OpLogger;
import com.finoffice.entity.SysLog;
import com.finoffice.mapper.SysLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 平台安全运营中心的固定办公诊断执行器：
 * 只允许服务端预置的只读检查，并保留受限执行和全链路审计。
 *
 * 沙箱策略：
 * - ProcessBuilder 独立进程执行
 * - 工作目录锁定到临时目录（隔离文件系统）
 * - 环境变量白名单（只传 PATH/HOME/USER 等安全变量）
 * - 5 秒超时，防止死循环/资源耗尽
 * - 输出截断（最大 8KB，防止内存溢出）
 *
 * 生产可升级：替换 ProcessBuilder 为 Docker 容器（docker run --rm --net=none --memory=64m）
 */
@Slf4j
@Service
public class BashSecurityService {

    /** 白名单：允许执行的命令 */
    private static final Set<String> WHITELIST = Set.of(
            "ls", "cat", "head", "tail", "echo", "date", "pwd", "whoami",
            "wc", "grep", "find", "df", "du", "uptime", "uname", "sort", "uniq", "ps"
    );

    /** 黑名单：危险命令 */
    private static final Set<String> BLACKLIST = Set.of(
            "rm", "mv", "cp", "dd", "mkfs", "fdisk",
            "sudo", "su", "chown", "chmod", "chgrp",
            "curl", "wget", "nc", "telnet", "ssh", "scp",
            "kill", "killall", "pkill", "reboot", "shutdown",
            "iptables", "ufw", "useradd", "userdel", "passwd",
            "mount", "umount", "crontab", "systemctl", "service"
    );

    /** 危险模式 */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(\\.\\./|\\.\\.\\\\|[;&|<>`$\\r\\n]|/etc/passwd|/etc/shadow|/proc/|/sys/|/dev/)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> SAFE_PATHS = Set.of(
            "/app", "/app/knowledge-docs", "/app/uploads", "/tmp/finpilot-sandbox", "/tmp", ".", "-");

    /** 场景化只读任务：命令由服务端拼装，用户和 Agent 不能篡改命令参数。 */
    private static final Map<String, Preset> PRESETS = Map.of(
            "knowledge-files", new Preset("knowledge-files", "知识库文件状态", "确认知识库文档是否已进入平台目录", "find /app/knowledge-docs -maxdepth 2 -type f", "low", "知识问答和文档检索", "检查文档导入记录与权限配置"),
            "knowledge-size", new Preset("knowledge-size", "知识库存储状态", "检查知识库文件占用，辅助排查导入问题", "du -sh /app/knowledge-docs", "low", "知识问答可用性", "清理失效文档或扩容存储"),
            "upload-size", new Preset("upload-size", "会议附件存储", "检查会议录音和上传附件的存储占用", "du -sh /app/uploads", "low", "会议纪要和文件解析", "归档历史附件并检查解析失败记录"),
            "disk-space", new Preset("disk-space", "运行环境磁盘", "检查应用容器可用磁盘空间", "df -h /app /tmp", "medium", "全平台服务稳定性", "优先释放临时文件并安排磁盘扩容"),
            "process-status", new Preset("process-status", "应用进程状态", "查看当前容器中的服务进程和运行时长", "ps -eo pid,comm,etime", "medium", "AI、会议和消息功能", "检查异常进程并查看服务日志")
    );

    /** 沙箱工作目录 */
    private final Path sandboxDir;
    private final SysLogMapper sysLogMapper;
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bash-output-reader");
        t.setDaemon(true);
        return t;
    });

    /** 执行超时（秒） */
    private static final int TIMEOUT_SECONDS = 5;

    /** 输出上限（字节） */
    private static final int MAX_OUTPUT = 8192;

    public BashSecurityService(SysLogMapper sysLogMapper) {
        this.sysLogMapper = sysLogMapper;
        try {
            this.sandboxDir = Files.createTempDirectory("finpilot-sandbox-");
            log.info("bash 沙箱就绪：工作目录 {}", sandboxDir);
        } catch (Exception e) {
            throw new RuntimeException("无法创建沙箱目录", e);
        }
    }

    /** 执行结果 */
    public record ExecResult(boolean allowed, String reason, String riskLevel,
                             String command, String stdout, String stderr, int exitCode, boolean timedOut) {}

    public record Preset(String id, String name, String description, String command, String riskLevel,
                         String businessImpact, String suggestion) {}

    public List<Preset> presets() {
        return List.of("knowledge-files", "knowledge-size", "upload-size", "disk-space", "process-status")
                .stream().map(PRESETS::get).toList();
    }

    public ExecResult executePreset(String presetId, Long userId) {
        Preset preset = PRESETS.get(presetId);
        if (preset == null) {
            ExecResult r = new ExecResult(false, "不存在的安全任务", "medium", presetId, "", "", -1, false);
            audit(userId, r);
            return r;
        }
        return execute(preset.command(), userId);
    }

    public List<Map<String, Object>> auditHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<SysLog> logs = sysLogMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getModule, "BASH_SECURITY")
                .orderByDesc(SysLog::getCreateTime)
                .last("LIMIT " + safeLimit));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysLog log : logs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.getId());
            item.put("username", log.getUsername());
            item.put("action", log.getAction());
            item.put("detail", log.getDetail());
            item.put("ip", log.getIp());
            item.put("create_time", log.getCreateTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 校验 + 沙箱执行命令。
     */
    public ExecResult execute(String command, Long userId) {
        // 先校验
        if (command == null || command.isBlank()) {
            ExecResult r = new ExecResult(false, "命令为空", "low", "", "", "", -1, false);
            audit(userId, r);
            return r;
        }
        String trimmed = command.trim();

        if (trimmed.length() > 300 || DANGEROUS_PATTERN.matcher(trimmed).find()) {
            ExecResult r = new ExecResult(false, "检测到危险语法或命令长度超限，已阻止 Shell 解释执行",
                    "high", trimmed, "", "", -1, false);
            log.warn("bash拦截[危险语法]: user={} cmd={}", userId, trimmed);
            audit(userId, r);
            return r;
        }

        // 危险模式检测
        // 提取命令名
        String cmdName = extractCommandName(trimmed);
        if (cmdName.isEmpty()) {
            ExecResult r = new ExecResult(false, "无法识别命令", "medium", trimmed, "", "", -1, false);
            audit(userId, r);
            return r;
        }

        // 黑名单
        if (BLACKLIST.contains(cmdName.toLowerCase())) {
            ExecResult r = new ExecResult(false, "危险命令「" + cmdName + "」已被系统禁用",
                    "high", trimmed, "", "", -1, false);
            log.warn("bash拦截[黑名单]: user={} cmd={}", userId, cmdName);
            audit(userId, r);
            return r;
        }

        // 白名单
        if (!WHITELIST.contains(cmdName.toLowerCase())) {
            ExecResult r = new ExecResult(false, "命令「" + cmdName + "」不在白名单中，需管理员审批",
                    "medium", trimmed, "", "", -1, false);
            log.info("bash拦截[非白名单]: user={} cmd={}", userId, cmdName);
            audit(userId, r);
            return r;
        }

        if (!safeArguments(trimmed, cmdName)) {
            ExecResult r = new ExecResult(false, "参数访问范围不在办公沙箱允许目录内",
                    "high", trimmed, "", "", -1, false);
            audit(userId, r);
            return r;
        }

        // ---- 沙箱执行 ----
        return runSandboxed(trimmed, userId, cmdName);
    }

    /** 在受限沙箱中执行命令 */
    private ExecResult runSandboxed(String command, Long userId, String cmdName) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        boolean timedOut = false;
        int exitCode = -1;

        try {
            ProcessBuilder pb = new ProcessBuilder();
            // 直接传参，不经过 sh/cmd，彻底关闭管道、重定向和命令替换。
            pb.command(tokenize(command));
            pb.directory(sandboxDir.toFile());
            // 环境变量白名单：只保留安全的
            Map<String, String> env = pb.environment();
            env.keySet().retainAll(Set.of("PATH", "HOME", "USER", "USERNAME", "TEMP", "TMP", "JAVA_HOME", "LANG"));
            pb.redirectErrorStream(false);

            Process proc = pb.start();
            Future<byte[]> stdoutFuture = IO_EXECUTOR.submit(() -> readLimited(proc.getInputStream()));
            Future<byte[]> stderrFuture = IO_EXECUTOR.submit(() -> readLimited(proc.getErrorStream()));
            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                proc.destroyForcibly();
                timedOut = true;
                stdout.write(("命令执行超时（" + TIMEOUT_SECONDS + "秒），已强制终止").getBytes(StandardCharsets.UTF_8));
            } else {
                exitCode = proc.exitValue();
                stdout.write(stdoutFuture.get(1, TimeUnit.SECONDS));
                stderr.write(stderrFuture.get(1, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            try { stderr.write(("执行异常: " + e.getMessage()).getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
        }

        // 截断输出
        String stdOutStr = truncate(stdout.toString(StandardCharsets.UTF_8));
        String stdErrStr = truncate(stderr.toString(StandardCharsets.UTF_8));

        ExecResult r = new ExecResult(true, "沙箱执行完成", "low",
                command, stdOutStr, stdErrStr, exitCode, timedOut);
        log.info("bash执行: user={} cmd={} exit={} timedOut={} outLen={}",
                userId, cmdName, exitCode, timedOut, stdOutStr.length());
        audit(userId, r);
        return r;
    }

    private String truncate(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > MAX_OUTPUT ? s.substring(0, MAX_OUTPUT) + "\n...[输出截断]" : s;
    }

    private String extractCommandName(String command) {
        String trimmed = command.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace > 0) trimmed = trimmed.substring(0, firstSpace);
        if (trimmed.startsWith("./")) trimmed = trimmed.substring(2);
        if (trimmed.contains("/")) {
            int lastSlash = trimmed.lastIndexOf('/');
            if (lastSlash < trimmed.length() - 1) trimmed = trimmed.substring(lastSlash + 1);
        }
        return trimmed;
    }

    private boolean safeArguments(String command, String cmdName) {
        List<String> tokens = tokenize(command);
        if (tokens.isEmpty() || tokens.size() > 20) return false;
        for (String token : tokens) {
            if (token.length() > 160 || token.matches(".*[\\x00-\\x1F].*")) return false;
            if (token.startsWith("/") && SAFE_PATHS.stream().noneMatch(token::startsWith)) return false;
        }
        return switch (cmdName.toLowerCase()) {
            case "find" -> tokens.stream().anyMatch(t -> t.startsWith("/app/knowledge-docs") || t.startsWith("/app/uploads"));
            case "cat", "head", "tail", "wc", "grep", "du" -> tokens.stream().anyMatch(t ->
                    t.startsWith("/app/knowledge-docs") || t.startsWith("/app/uploads") || t.equals("-"));
            default -> true;
        };
    }

    private List<String> tokenize(String command) {
        if (command == null || command.isBlank()) return Collections.emptyList();
        return Arrays.asList(command.trim().split("\\s+"));
    }

    private byte[] readLimited(java.io.InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int keep = Math.min(read, Math.max(0, MAX_OUTPUT + 1 - total));
            if (keep > 0) out.write(buffer, 0, keep);
            total += read;
            if (total > MAX_OUTPUT) break;
        }
        return out.toByteArray();
    }

    private void audit(Long userId, ExecResult r) {
        try {
            OpLogger.log(userId, "BASH_SECURITY",
                    r.allowed() ? "security-check" : "security-block",
                    r.riskLevel() + "|" + r.reason() + "|cmd="
                            + r.command().substring(0, Math.min(r.command().length(), 100))
                            + "|exit=" + r.exitCode());
        } catch (Exception e) {
            log.error("bash审计日志写入失败: {}", e.getMessage());
        }
    }
}

package com.finoffice.service;

import com.finoffice.common.OpLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * bash 命令安全管控（竞赛完整实现）：
 * 三级防线校验 + 受限沙箱执行 + Agent 工具集成 + 全链路审计。
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
            "wc", "grep", "find", "df", "du", "uptime", "uname", "env",
            "sort", "uniq", "awk", "sed", "cut", "tr", "tee",
            "git", "java", "python", "node", "npm", "mvn",
            "docker", "ps", "top", "free", "ping"
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
            "(\\.\\./|\\.\\.\\\\|\\|\\||&&|;\\s*\\w|`[^`]+`|\\$\\([^)]+\\)|>\\s*\\S|/etc/passwd|/etc/shadow)",
            Pattern.CASE_INSENSITIVE
    );

    /** 沙箱工作目录 */
    private final Path sandboxDir;

    /** 执行超时（秒） */
    private static final int TIMEOUT_SECONDS = 5;

    /** 输出上限（字节） */
    private static final int MAX_OUTPUT = 8192;

    public BashSecurityService() {
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

        // 危险模式检测
        if (DANGEROUS_PATTERN.matcher(trimmed).find()) {
            ExecResult r = new ExecResult(false, "检测到危险模式：路径穿越/管道注入/命令替换/敏感文件访问",
                    "high", trimmed, "", "", -1, false);
            log.warn("bash拦截[危险模式]: user={} cmd={}", userId, trimmed);
            audit(userId, r);
            return r;
        }

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
            // Windows 用 cmd /c，Linux/Mac 用 sh -c
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(sandboxDir.toFile());
            // 环境变量白名单：只保留安全的
            Map<String, String> env = pb.environment();
            env.keySet().retainAll(Set.of("PATH", "HOME", "USER", "USERNAME", "TEMP", "TMP", "JAVA_HOME", "LANG"));
            pb.redirectErrorStream(false);

            Process proc = pb.start();
            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                proc.destroyForcibly();
                timedOut = true;
                stdout.write(("命令执行超时（" + TIMEOUT_SECONDS + "秒），已强制终止").getBytes(StandardCharsets.UTF_8));
            } else {
                exitCode = proc.exitValue();
                stdout.write(proc.getInputStream().readAllBytes());
                stderr.write(proc.getErrorStream().readAllBytes());
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

    private void audit(Long userId, ExecResult r) {
        try {
            OpLogger.log(userId, "BASH_SECURITY",
                    r.allowed() ? "bash-exec" : "bash-block",
                    r.riskLevel() + "|" + r.reason() + "|cmd="
                            + r.command().substring(0, Math.min(r.command().length(), 100))
                            + "|exit=" + r.exitCode());
        } catch (Exception e) {
            log.error("bash审计日志写入失败: {}", e.getMessage());
        }
    }
}

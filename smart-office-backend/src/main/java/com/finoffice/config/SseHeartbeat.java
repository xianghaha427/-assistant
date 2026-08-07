package com.finoffice.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 心跳与生命周期清理组件
 * - attach(emitter)：加入活跃集合并统一注册 onTimeout / onError / onCompletion 清理回调
 * - 每 15 秒向活跃连接发送一次 SSE 注释行 ":ping"，
 *   防止网关/代理因空闲切断长流（注释行不影响前端 EventSource/fetch 解析）
 */
@Slf4j
@Component
public class SseHeartbeat {

    private static final long PING_INTERVAL_SECONDS = 15L;

    private final Set<SseEmitter> active = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::ping, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
    }

    /**
     * 注册 emitter：加入心跳集合，并统一挂载超时/异常/完成清理回调
     */
    public void attach(SseEmitter emitter) {
        active.add(emitter);
        emitter.onCompletion(() -> active.remove(emitter));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时，执行清理");
            active.remove(emitter);
            emitter.complete();
        });
        emitter.onError(t -> {
            log.warn("SSE 连接异常: {}", t.getMessage());
            active.remove(emitter);
        });
    }

    /** 手动停止心跳（如提前 completeWithError 前调用） */
    public void stop(SseEmitter emitter) {
        active.remove(emitter);
    }

    private void ping() {
        for (SseEmitter emitter : active) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                // 心跳发送失败说明连接已断开，静默移除（后续发送会触发 onError/onCompletion 清理）
                active.remove(emitter);
            }
        }
    }
}

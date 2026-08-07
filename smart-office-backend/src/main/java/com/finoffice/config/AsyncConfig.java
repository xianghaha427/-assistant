package com.finoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局异步线程池配置
 * 替换 AiController / WritingController 中原先的无界 Executors.newCachedThreadPool()：
 * - 核心 8 / 最大 32 / 有界队列 64，防止突发并发下线程数无限膨胀
 * - CallerRunsPolicy 背压：队列满时由调用线程执行，天然限流不丢任务
 * - daemon 线程：不阻止 JVM 正常退出
 */
@Configuration
public class AsyncConfig {

    /** AI SSE 流式推送共享线程池（供 AiController / WritingController 复用） */
    @Bean(name = "aiSseExecutor", destroyMethod = "shutdown")
    public ExecutorService aiSseExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "ai-sse-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(
                8, 32,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}

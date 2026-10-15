package com.finoffice.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：登录拦截 + 跨域 + 静态资源
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-path:./uploads}")
    private String uploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 基础登录校验
                    StpUtil.checkLogin();
                    String uri = SaHolder.getRequest().getRequestPath();
                    String method = SaHolder.getRequest().getMethod();
                    // 管理后台：仅 ADMIN
                    if (uri.startsWith("/api/admin/")) {
                        StpUtil.checkRole("ADMIN");
                    }
                    // 运维安全中心包含执行和审计能力，仅管理员可访问；AI 内部工具不经过该 HTTP 入口。
                    if (uri.startsWith("/api/security/")) {
                        StpUtil.checkRole("ADMIN");
                    }
                    // 智能体写操作：仅 ADMIN（GET 保持登录即可）
                    if (uri.startsWith("/api/agents/")
                            && ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
                        StpUtil.checkRole("ADMIN");
                    }
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/health", "/error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 音频文件静态资源映射
        registry.addResourceHandler("/api/meetings/audio/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}

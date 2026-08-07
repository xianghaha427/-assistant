package com.finoffice.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 业务异常 + 全局异常处理
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        this(1, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Slf4j
    @RestControllerAdvice
    public static class GlobalExceptionHandler {

        @ExceptionHandler(BizException.class)
        public R<Void> handleBiz(BizException e) {
            return R.error(e.getCode(), e.getMessage());
        }

        @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
        public R<Void> handleNotLogin(cn.dev33.satoken.exception.NotLoginException e) {
            return R.error(401, "未登录或登录已过期");
        }

@ExceptionHandler(cn.dev33.satoken.exception.NotRoleException.class)
        public R<Void> handleNotRole(cn.dev33.satoken.exception.NotRoleException e) {
            return R.error(403, "无权限访问：需要角色 " + e.getRole());
        }

@ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
        public R<Void> handleNotPermission(cn.dev33.satoken.exception.NotPermissionException e) {
            return R.error(403, "无权限执行此操作");
        }

        @ExceptionHandler(Exception.class)
        public R<Void> handleOther(Exception e) {
            log.error("系统异常", e);
            return R.error(500, "系统异常：" + e.getMessage());
        }
    }
}

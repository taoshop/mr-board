package com.mrboard.system.exception;

import lombok.Getter;

/**
 * Git 平台操作异常，携带 HTTP 状态码与平台返回的原始错误详情，便于上层做差异化提示。
 */
@Getter
public class GitPlatformException extends RuntimeException {

    private final int statusCode;
    private final String detail;

    public GitPlatformException(int statusCode, String detail) {
        super(detail);
        this.statusCode = statusCode;
        this.detail = detail;
    }

    public GitPlatformException(int statusCode, String detail, Throwable cause) {
        super(detail, cause);
        this.statusCode = statusCode;
        this.detail = detail;
    }
}

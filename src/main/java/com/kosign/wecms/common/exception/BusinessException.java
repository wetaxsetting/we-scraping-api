package com.kosign.wecms.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final Object[] args;

    public BusinessException(String code) {
        this(code, new Object[0]);
    }

    public BusinessException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }
}

package com.derso.arquitetura.webbase.config;

public class BusinessException extends RuntimeException {
    public BusinessException(String msg) {
        super(msg);
    }
}
package com.derso.arquitetura.config;

public class BusinessException extends RuntimeException {
    public BusinessException(String msg) {
        super(msg);
    }
}
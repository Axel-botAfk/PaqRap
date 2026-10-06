package com.paqrap.api;

import org.springframework.http.HttpStatus;

final class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    ApiException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    HttpStatus status() { return status; }
    String codigo() { return codigo; }
}

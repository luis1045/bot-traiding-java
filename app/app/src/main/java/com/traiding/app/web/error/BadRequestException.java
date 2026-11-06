package com.traiding.app.web.error;

/** Error de petición inválida (HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

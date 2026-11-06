package com.traiding.app.web.error;

/** Recurso no encontrado (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

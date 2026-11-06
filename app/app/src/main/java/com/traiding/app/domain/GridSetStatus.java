package com.traiding.app.domain;

/**
 * Estado de un conjunto de niveles (grilla).
 *
 * <ul>
 *   <li>{@code ACTIVE}    - operando normalmente.</li>
 *   <li>{@code EXHAUSTED} - el precio superó el techo: todo el BTC se convirtió a USDT.
 *       El bot queda a la espera de que el usuario decida continuar o crear un nuevo set.</li>
 *   <li>{@code CLOSED}    - cerrado definitivamente (por ejemplo al crear un nuevo set).</li>
 * </ul>
 */
public enum GridSetStatus {
    ACTIVE,
    EXHAUSTED,
    CLOSED;

    public static GridSetStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return GridSetStatus.valueOf(value.trim().toUpperCase());
    }
}

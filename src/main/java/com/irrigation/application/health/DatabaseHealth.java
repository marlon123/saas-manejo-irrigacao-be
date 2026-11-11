package com.irrigation.application.health;

/**
 * Porta para verificar a conectividade com o banco de dados (Req 9.2).
 * Implementada na camada de infraestrutura.
 */
public interface DatabaseHealth {

    /** Retorna {@code true} se o banco responde; nunca lança exceção. */
    boolean isConnected();
}

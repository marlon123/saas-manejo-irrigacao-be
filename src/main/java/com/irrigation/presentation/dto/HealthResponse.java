package com.irrigation.presentation.dto;

/**
 * Estado de saúde (Req 9.1–9.3).
 *
 * @param status      "healthy" ou "degraded" (banco inacessível)
 * @param application "healthy" ou "unhealthy"
 * @param database    "connected" ou "disconnected"
 */
public record HealthResponse(String status, String application, String database) {
}

package co.edu.uptc.apigateway.exception;

import java.time.LocalDateTime;

/**
 * Common error response contract shared by all services in the project
 * (see "Manejo de errores" section of the technical specification).
 *
 * Example:
 * {
 *   "timestamp": "2026-09-14T15:30:00",
 *   "status": 502,
 *   "error": "Bad Gateway",
 *   "message": "The requested service is currently unavailable",
 *   "path": "/api/students/12"
 * }
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
}

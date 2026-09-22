package co.edu.uptc.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

/**
 * Translates every exception raised while proxying a request (or while no
 * route matches at all) into the team's common error contract, and turns
 * "downstream service unreachable" failures into a 502 Bad Gateway instead
 * of the default 500, per the "Gateway y manejo de fallos" section of the
 * specification.
 *
 * Ordered before Spring Boot's DefaultErrorWebExceptionHandler
 * (Ordered.HIGHEST_PRECEDENCE has the highest priority; -2 runs before the
 * default handler, which is registered at a lower priority).
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                resolveMessage(ex, status),
                exchange.getRequest().getPath().value()
        );

        byte[] bytes = serialize(apiError);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.resolve(rse.getStatusCode().value()) != null
                    ? HttpStatus.resolve(rse.getStatusCode().value())
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (isServiceUnavailable(ex)) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Walks the exception's cause chain looking for the low-level network
     * failures that mean "the downstream service did not respond":
     * connection refused, connect timeout, response timeout or a
     * connection closed prematurely by the peer.
     */
    private boolean isServiceUnavailable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof ConnectTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof PrematureCloseException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveMessage(Throwable ex, HttpStatus status) {
        if (status == HttpStatus.BAD_GATEWAY) {
            return "The requested service is currently unavailable";
        }
        if (ex instanceof NotFoundException) {
            return "No route was found for the requested path";
        }
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return status.getReasonPhrase();
    }

    private byte[] serialize(ApiError apiError) {
        try {
            return objectMapper.writeValueAsBytes(apiError);
        } catch (JsonProcessingException e) {
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }
}

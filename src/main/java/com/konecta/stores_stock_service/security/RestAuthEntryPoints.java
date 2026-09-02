package com.konecta.stores_stock_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konecta.stores_stock_service.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The security filter chain rejects unauthenticated/forbidden requests
 * before they ever reach a controller, so {@code @RestControllerAdvice}
 * never sees them. These write the same {ApiError} envelope directly.
 */
public final class RestAuthEntryPoints {

    private RestAuthEntryPoints() {
    }

    public static AuthenticationEntryPoint unauthenticated(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) ->
                write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                        ApiError.of("UNAUTHENTICATED", "Authentication required", List.of()));
    }

    public static AccessDeniedHandler accessDenied(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) ->
                write(response, objectMapper, HttpStatus.FORBIDDEN,
                        ApiError.of("ACCESS_DENIED", "You do not have access to this resource", List.of()));
    }

    private static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
            ApiError error) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}

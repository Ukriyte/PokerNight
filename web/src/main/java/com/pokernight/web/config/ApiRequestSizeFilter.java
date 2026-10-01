package com.pokernight.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public final class ApiRequestSizeFilter extends OncePerRequestFilter {
    private static final long MAX_API_BYTES = 64 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") && request.getContentLengthLong() > MAX_API_BYTES) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "API request exceeds 64 KiB");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

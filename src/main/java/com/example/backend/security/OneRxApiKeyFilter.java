package com.example.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Filter that enables 3rd-party OneRx integration via a static, secure API Key.
 *
 * Supports both:
 * 1. Header: X-API-KEY: <key>
 * 2. Header: Authorization: Bearer <key>
 *
 * When a valid key is provided on OneRx integration routes (/api/dispense, /api/brands, /api/onerx/**),
 * the request is authenticated with ROLE_ONERX_INTEGRATION without requiring user database lookups.
 */
@Component
public class OneRxApiKeyFilter extends OncePerRequestFilter {

    @Value("${onerx.api.key:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only evaluate API Key for OneRx integration endpoints
        boolean isOneRxPath = path.startsWith("/api/brands")
                || path.startsWith("/api/dispense")
                || path.startsWith("/api/onerx");

        if (isOneRxPath && SecurityContextHolder.getContext().getAuthentication() == null) {
            String apiKey = extractApiKey(request);

            if (isValidApiKey(apiKey)) {
                // Grant authenticated session for OneRx integration
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "OneRx-Integration-Client",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ONERX_INTEGRATION"),
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        // 1. Check primary X-API-KEY header
        String apiKeyHeader = request.getHeader("X-API-KEY");
        if (apiKeyHeader != null && !apiKeyHeader.trim().isBlank()) {
            return apiKeyHeader.trim();
        }

        // 2. Fallback: Check if Authorization header passed the API key directly (Authorization: Bearer <apiKey>)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String candidate = authHeader.substring(7).trim();
            // If the bearer token matches our API key, treat it as API key
            if (isValidApiKey(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean isValidApiKey(String candidate) {
        if (candidate == null || candidate.isBlank() || expectedApiKey == null || expectedApiKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                expectedApiKey.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}

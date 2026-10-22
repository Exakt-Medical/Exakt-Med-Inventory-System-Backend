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

    @Value("${onerx.api.key:onerx_live_sec_7f9b8c2d1e4a}")
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

            if (apiKey != null && apiKey.equals(expectedApiKey)) {
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
            if (candidate.equals(expectedApiKey)) {
                return candidate;
            }
        }

        return null;
    }
}

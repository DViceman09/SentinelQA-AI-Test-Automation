package com.sentinelqa.shoplite;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Browser hardening headers. DEFECT 3 – with defects on they are all missing. */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final boolean defects;

    public SecurityHeadersFilter(@Value("${shoplite.defects}") boolean defects) {
        this.defects = defects;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!defects) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "no-referrer");
            // Swagger UI needs inline styles and data: images; scripts stay same-origin only.
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'");
        }
        chain.doFilter(request, response);
    }
}

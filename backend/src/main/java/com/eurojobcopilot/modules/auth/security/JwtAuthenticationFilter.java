package com.eurojobcopilot.modules.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates Bearer JWT access tokens on incoming requests and populates SecurityContextHolder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (jwt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parseAndValidateAccessToken(jwt);
                String userUuid = claims.getSubject();
                if (userUuid != null && !userUuid.isBlank()) {
                    AuthenticatedUserPrincipal principal = userDetailsService.loadUserByUuid(userUuid);
                    if (principal.isEnabled() && principal.isAccountNonLocked()) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else {
                        log.debug("Rejected JWT for inactive/suspended user uuid={}", userUuid);
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
                log.debug("JWT validation failed for path={}: {}", request.getRequestURI(), ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}


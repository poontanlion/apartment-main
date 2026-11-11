package com.victory.apartment.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP Request Logging Filter for Observability.
 *
 * <p>Logs every incoming HTTP request with method, URI, status code, and duration.
 * Because Spring Micrometer Tracing populates MDC before this filter runs,
 * the structured log pattern (see application.properties) automatically includes
 * the active {@code traceId} and {@code spanId} — enabling Log ↔ Trace correlation
 * in Grafana (Loki → Tempo derived fields).
 *
 * <p>Example log line:
 * <pre>
 *   INFO  [apartment-backend,4f3a1b2c8d9e0f1a,2b3c4d5e] http GET /api/rooms -> 200 (45ms)
 * </pre>
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public OncePerRequestFilter httpRequestLoggingFilter() {
        return new OncePerRequestFilter() {

            private static final Logger log = LoggerFactory.getLogger("http.access");

            // Skip actuator endpoints to avoid log noise from Prometheus scraping
            private static final String ACTUATOR_PREFIX = "/actuator";

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String method = request.getMethod();
                String uri    = request.getRequestURI();

                // Skip actuator/health/prometheus scraping from noisy logs
                if (uri.startsWith(ACTUATOR_PREFIX)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                long start = System.currentTimeMillis();
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    long duration = System.currentTimeMillis() - start;
                    int  status   = response.getStatus();

                    if (status >= 500) {
                        log.error("http {} {} -> {} ({}ms)", method, uri, status, duration);
                    } else if (status >= 400) {
                        log.warn("http {} {} -> {} ({}ms)",  method, uri, status, duration);
                    } else {
                        log.info("http {} {} -> {} ({}ms)",  method, uri, status, duration);
                    }
                }
            }
        };
    }
}

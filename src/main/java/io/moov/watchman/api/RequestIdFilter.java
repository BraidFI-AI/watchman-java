package io.moov.watchman.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds correlation/request ID to all incoming requests for tracing.
 * The request ID is:
 * 1. Added to MDC for inclusion in all log messages
 * 2. Added to response header for client-side correlation
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestIdFilter.class);
    
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_REQUEST_ID_KEY = "requestId";
    public static final String MDC_METHOD_KEY = "method";
    public static final String MDC_PATH_KEY = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Use existing request ID from header or generate new one
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = generateRequestId();
        }
        
        // Add to MDC for logging
        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        MDC.put(MDC_METHOD_KEY, request.getMethod());
        MDC.put(MDC_PATH_KEY, request.getRequestURI());
        
        // Add to response header for client correlation
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Log request completion (skip health checks to reduce noise)
            if (!isHealthCheck(request.getRequestURI())) {
                logger.info("Completed {} {} - status={} duration={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration);
            }
            
            // Clean up MDC
            MDC.remove(MDC_REQUEST_ID_KEY);
            MDC.remove(MDC_METHOD_KEY);
            MDC.remove(MDC_PATH_KEY);
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean isHealthCheck(String uri) {
        return uri.equals("/health") || 
               uri.equals("/v2/health") || 
               uri.equals("/actuator/health");
    }
}

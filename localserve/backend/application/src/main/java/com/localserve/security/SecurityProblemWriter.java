package com.localserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.web.ApiProblem;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper json;
    public SecurityProblemWriter(ObjectMapper json) { this.json = json; }

    @Override public void commence(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException ignored) throws IOException {
        write(request, response, 401, "AUTH.AUTHENTICATION_REQUIRED", "Authentication is required");
    }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response,
                                 AccessDeniedException ignored) throws IOException, ServletException {
        write(request, response, 403, "ACCESS.DENIED", "Permission is denied");
    }
    private void write(HttpServletRequest request, HttpServletResponse response, int status,
                       String code, String detail) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new ApiProblem("https://api.localserve.example/problems/" + code.toLowerCase().replace('.', '-'),
                status == 401 ? "Unauthorized" : "Forbidden", status, code, detail, request.getRequestURI(),
                MDC.get("correlationId"), Instant.now(), Map.of()));
    }
}

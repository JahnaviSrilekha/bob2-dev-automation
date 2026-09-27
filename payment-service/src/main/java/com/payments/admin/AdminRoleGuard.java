package com.payments.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces that only requests with {@code X-User-Role: ADMIN} reach the
 * {@code /v1/admin/**} endpoints. All other callers receive HTTP 403.
 * REQ-F-029 — ST-011-01
 */
@Component
public class AdminRoleGuard implements HandlerInterceptor {

    private static final String ROLE_HEADER = "X-User-Role";
    private static final String ADMIN_ROLE  = "ADMIN";

    private final ObjectMapper objectMapper;

    public AdminRoleGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String role = request.getHeader(ROLE_HEADER);
        if (!ADMIN_ROLE.equalsIgnoreCase(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            ErrorResponse body = new ErrorResponse("FORBIDDEN", "Admin role required", null);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
        return true;
    }
}

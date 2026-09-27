package com.payments.config;

import com.payments.admin.AdminRoleGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration — registers the AdminRoleGuard on the /v1/admin/** path only.
 * ST-011-01
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminRoleGuard adminRoleGuard;

    public WebMvcConfig(AdminRoleGuard adminRoleGuard) {
        this.adminRoleGuard = adminRoleGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminRoleGuard).addPathPatterns("/v1/admin/**");
    }
}

package com.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/**
 * OpenAPI 3.x metadata — title, description, version, server URL, security schemes.
 * The spec is published at GET /v1/openapi.json (REQ-NF-018).
 * Swagger UI is at GET /v1/swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Service API")
                        .description("""
                                Double-entry ledger payment microservice.
                                
                                **Authentication:** All requests must include `X-User-Id` (UUID) injected by the API Gateway.
                                Admin endpoints additionally require `X-User-Role: ADMIN`.
                                
                                **Idempotency:** Transfer requests require a unique `Idempotency-Key` (UUID v4) header.
                                Retrying with the same key returns the cached response.
                                
                                **Monetary amounts:** All amounts are `BigDecimal` strings with up to 4 decimal places (e.g. `"25.0000"`).
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("Platform Engineering")
                                .email("platform@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")))
                .schemaRequirement("AdminRoleHeader",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("Set to `ADMIN` for admin endpoints"));
    }
}

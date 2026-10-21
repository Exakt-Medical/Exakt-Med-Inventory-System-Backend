package com.example.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "Bearer Authentication";

    @Bean
    public OpenAPI exaktMedInventoryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Exakt Med Inventory System API")
                        .description("Comprehensive REST API documentation for Exakt Med Inventory System. " +
                                "Provides endpoints for Authentication, Users, Projects, Facilities, Assignments, " +
                                "Suppliers, Library Medicines, SKUs, Batches, Orders, Restock Requests, " +
                                "Audit Logs, and Real-Time Operational Dashboards.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Exakt Med Support")
                                .email("support@exaktmed.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name(BEARER_AUTH_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT token obtained from `/api/auth/login` (without the 'Bearer ' prefix).")));
    }
}

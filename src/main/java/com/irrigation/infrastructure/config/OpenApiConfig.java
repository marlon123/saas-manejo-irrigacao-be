package com.irrigation.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI (Req 12). A especificação é servida em
 * {@code GET /v3/api-docs} e a interface interativa em {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI irrigationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Irrigation Management API")
                .version("v1")
                .description("API de manejo de irrigação agrícola: áreas, culturas, perfis de solo "
                        + "e histórico de recomendações de irrigação."));
    }
}

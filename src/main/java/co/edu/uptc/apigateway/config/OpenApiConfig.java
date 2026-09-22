package co.edu.uptc.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Each domain service publishes its own independent Swagger UI
 * (see section 28 of the spec). This gateway only documents the endpoints
 * it hosts itself — currently GET /api/students/{id}/detail.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gateway")
                        .description("Single entry point (:8080) routing to students-service (:8081), "
                                + "subjects-service (:8082) and enrollments-service (:8083). "
                                + "Documents the composed /api/students/{id}/detail endpoint owned by the Gateway.")
                        .version("1.0.0"));
    }
}

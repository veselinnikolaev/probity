package me.veselin.probity.common.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.headers.Header;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * OpenAPI configuration for Swagger UI documentation.
 */
public class OpenApiConfiguration {

    @Bean
    public OpenAPI probityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Probity API")
                        .description("Portfolio risk analytics platform — BFF API documentation")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token"))
                        .addHeaders("Idempotency-Key", new Header()
                                .description("Unique key for idempotent requests. Prevents duplicate processing of the same request. Required for POST endpoints that create resources.")
                                .required(false)
                                .schema(new StringSchema())));
    }
}

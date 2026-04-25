package eu.commission.ims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 (Swagger) configuration.
 * Accessible at /swagger-ui.html and /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

        private static final String BASIC_SCHEME_NAME = "basicAuth";

        @Bean
        public OpenAPI interviewManagementOpenAPI() {
                return new OpenAPI()
                                .info(apiInfo())
                                .servers(apiServers())
                                .addSecurityItem(new SecurityRequirement().addList(BASIC_SCHEME_NAME))
                                .components(new Components()
                                                .addSecuritySchemes(BASIC_SCHEME_NAME,
                                                                new SecurityScheme()
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("basic")
                                                                                .description("HTTP Basic Authentication. Default users: admin/admin123 or recruiter/rec123")));
        }

        private Info apiInfo() {
                return new Info()
                                .title("European External Action Service — Interview Management System API")
                                .description("""
                                                REST API managing the full interview lifecycle for the European External Action Service.

                                                **Pipeline:**
                                                `Resume Submission` → `Screening` → `Technical Interview` → `Feedback & Decision`

                                                **DevSecOps Stack:** Jenkins · SonarCloud (SAST) · OWASP ZAP (DAST) · OWASP Dependency-Check (SCA)
                                                """)
                                .version("1.0.0")
                                .contact(new Contact()
                                                .name("EC HR Digital Team")
                                                .email("hr-digital@ec.europa.eu")
                                                .url("https://ec.europa.eu"))
                                .license(new License()
                                                .name("European Union Public Licence v1.2")
                                                .url("https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"));
        }

        private List<Server> apiServers() {
                return List.of(
                                new Server().url("http://localhost:8080").description("Local Development"),
                                new Server().url("https://ims.ec.europa.eu").description("Production"));
        }
}

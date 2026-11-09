package com.eurojobcopilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3 / Swagger documentation configuration.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    private static final List<Map.Entry<String, PathItem.HttpMethod>> PUBLIC_OPERATIONS = List.of(
            Map.entry("/api/v1/health", PathItem.HttpMethod.GET),
            Map.entry("/api/v1/auth/register", PathItem.HttpMethod.POST),
            Map.entry("/api/v1/auth/login", PathItem.HttpMethod.POST),
            Map.entry("/api/v1/auth/refresh", PathItem.HttpMethod.POST)
    );

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EuroJob Copilot REST API")
                        .version("1.0.0")
                        .description("""
                                Production-grade REST API specification for EuroJob Copilot - European Job Discovery, \
                                Sponsorship Intelligence, and AI Career Copilot.

                                **Authentication & Session Model**
                                - Protected endpoints require `Authorization: Bearer <accessToken>` (`HS256` JWT).
                                - `POST /api/v1/auth/register` and `POST /api/v1/auth/login` also issue an `HttpOnly`, \
                                `SameSite=Strict` `refresh_token` cookie scoped to `/api/v1/auth` with automatic rotation \
                                and replay detection.
                                - All client/server failures use the standardized `ApiErrorResponse` envelope.""")
                        .contact(new Contact()
                                .name("EuroJob Copilot Engineering")
                                .email("dev@eurojobcopilot.com")
                                .url("https://eurojobcopilot.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://eurojobcopilot.com/terms")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Environment"),
                        new Server()
                                .url("https://api.eurojobcopilot.com")
                                .description("Production Environment")
                ))
                .tags(List.of(
                        new Tag()
                                .name("Health Check")
                                .description("Operational readiness and service health probe"),
                        new Tag()
                                .name("Authentication")
                                .description("User registration, login, JWT issuance, token rotation, and logout endpoints"),
                        new Tag()
                                .name("User Profile")
                                .description("Authenticated candidate profile, skills, education, and work experience CRUD APIs"),
                        new Tag()
                                .name("Preferences")
                                .description("Job discovery, salary, visa sponsorship, and autopilot search preferences"),
                        new Tag()
                                .name("Resumes")
                                .description("Master resume secure upload, AI parsing, human approval, metadata management, download, and deletion APIs"),
                        new Tag()
                                .name("Tailored Documents")
                                .description("AI-tailored JD-specific resume and cover letter generation, ATS/grounding verification, versioned draft editing, and multi-format PDF/DOCX/TXT export APIs"),
                        new Tag()
                                .name("Applications")
                                .description("Application tracker pipeline, Kanban stage transitions, status timeline history, interview/follow-up scheduling, salary offers, and linked document snapshots"),
                        new Tag()
                                .name("Recruiter CRM")
                                .description("Recruiter contact management, communication log history, follow-up reminder tracking, and AI-assisted editable outreach drafts requiring explicit user approval"),
                        new Tag()
                                .name("Dashboard & Analytics")
                                .description("Executive dashboard KPIs, readiness metrics, pipeline summaries, and European job market analytics"),
                        new Tag()
                                .name("Jobs")
                                .description("Job search, multi-facet filtering, personalized match ranking, and job discovery APIs"),
                        new Tag()
                                .name("AI Application Assistant")
                                .description("Contextual AI copilot on job details for role breakdown, match explanations, skill gaps, salary/sponsorship guidance, editable drafts, and persisted job conversation history"),
                        new Tag()
                                .name("Autopilot & Recommendations")
                                .description("Automated preference-driven job discovery, ranking, run telemetry history, and pause/resume controls"),
                        new Tag()
                                .name("Notifications")
                                .description("In-app high-match, sponsorship, follow-up, and autopilot notification center with unread tracking"),
                        new Tag()
                                .name("Interview Preparation")
                                .description("AI interview prep session generator, interactive practice mode, answer grading, verified company prep topics, and performance tracking"),
                        new Tag()
                                .name("Job Matching")
                                .description("Deterministic rule-based compatibility scoring, AI semantic job matching, and batch match recalculation APIs"),
                        new Tag()
                                .name("AI Intelligence")
                                .description("AI-powered job description extraction, structured validation, and analysis version history APIs"),
                        new Tag()
                                .name("Sponsorship Intelligence")
                                .description("Country-specific European visa sponsorship rules across 14 countries, Netherlands IND register lookup, company verification, and audit history"),
                        new Tag()
                                .name("Companies")
                                .description("European employer intelligence directory, sponsorship verification dossiers, open roles, and target company bookmarks"),
                        new Tag()
                                .name("Job Ingestion Admin")
                                .description("Admin job ingestion triggers, scheduler telemetry, fetch logs, and stale job maintenance"),
                        new Tag()
                                .name("Sponsorship Registry Admin")
                                .description("Admin synchronization and status monitoring for official European sponsor registers (Netherlands IND)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT Bearer access token to authorize protected API calls.")));
    }

    @Bean
    public OpenApiCustomizer openApiSecurityAndMultipartCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            // Explicitly mark public endpoints with empty security list (security: [])
            // so Swagger UI and OpenAPI validators accurately distinguish public vs protected routes.
            for (Map.Entry<String, PathItem.HttpMethod> publicOp : PUBLIC_OPERATIONS) {
                PathItem pathItem = openApi.getPaths().get(publicOp.getKey());
                if (pathItem != null) {
                    Operation operation = switch (publicOp.getValue()) {
                        case GET -> pathItem.getGet();
                        case POST -> pathItem.getPost();
                        case PUT -> pathItem.getPut();
                        case PATCH -> pathItem.getPatch();
                        case DELETE -> pathItem.getDelete();
                        default -> null;
                    };
                    if (operation != null) {
                        operation.setSecurity(Collections.emptyList());
                    }
                }
            }

            // Ensure POST /api/v1/resumes/upload presents a unified multipart/form-data schema
            // containing both binary 'file' and string 'title' form parts (without duplicate query param).
            PathItem uploadPath = openApi.getPaths().get("/api/v1/resumes/upload");
            if (uploadPath != null && uploadPath.getPost() != null) {
                Operation uploadOp = uploadPath.getPost();
                if (uploadOp.getParameters() != null) {
                    List<Parameter> filtered = uploadOp.getParameters().stream()
                            .filter(param -> !"title".equals(param.getName()))
                            .toList();
                    uploadOp.setParameters(filtered.isEmpty() ? null : filtered);
                }

                StringSchema fileSchema = new StringSchema();
                fileSchema.setFormat("binary");
                fileSchema.setDescription("PDF or DOCX resume file (max 10MB)");

                StringSchema titleSchema = new StringSchema();
                titleSchema.setMinLength(1);
                titleSchema.setMaxLength(200);
                titleSchema.setExample("Master Full-Stack Java Resume 2026");
                titleSchema.setDescription("Display title for the uploaded master resume (max 200 characters)");

                ObjectSchema multipartSchema = new ObjectSchema();
                multipartSchema.addProperty("file", fileSchema);
                multipartSchema.addProperty("title", titleSchema);
                multipartSchema.setRequired(List.of("file", "title"));

                uploadOp.setRequestBody(new RequestBody()
                        .required(true)
                        .description("Multipart form payload containing the resume document and display title")
                        .content(new Content().addMediaType(
                                org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE,
                                new MediaType().schema(multipartSchema))));
            }
        };
    }
}


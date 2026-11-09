# EuroJob Copilot – Backend REST API Contracts Specification
**Document Version:** 2.0.0  
**Scope:** 23 Spring Boot Controllers · 83 REST API Paths · 20 OpenAPI Tags  

---

## 1. Global API Conventions & Security Contracts

### 1.1 Base URL & Content Negotiation
- **Base Prefix:** `/api/v1` (except SpringDoc/Actuator operational probes `/v3/api-docs`, `/swagger-ui/**`, `/actuator/health`, `/actuator/info`).
- **Default Media Type:** `application/json` (UTF-8).
- **Multipart Upload:** `multipart/form-data` on `POST /api/v1/resumes/upload` (`file` binary + `title` form part, max 10 MB business cap / 12 MB servlet cap).
- **Binary Exports & Downloads:**
  - `GET /api/v1/resumes/{uuid}/download`: `application/pdf` or `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
  - `GET /api/v1/tailored-documents/resume/{uuid}/export`: `application/pdf` or `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
  - `GET /api/v1/tailored-documents/cover-letter/{uuid}/export`: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, or `text/plain`
  - All binary downloads include `Content-Disposition: attachment; filename="..."`, `X-Content-Type-Options: nosniff`, and `Cache-Control: no-store, must-revalidate`.

### 1.2 Authentication & Authorization Model
- **Public Endpoints (`security: []`):**
  - `GET /api/v1/health`
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
- **Authenticated Endpoints (`BearerAuth`):**
  - Require `Authorization: Bearer <accessToken>` (`HS256` JWT, default 900s expiry).
  - Login & register also set an `HttpOnly`, `SameSite=Strict` `refresh_token` cookie scoped to `/api/v1/auth` with single-use rotation and reuse-triggered family revocation.
- **Admin Endpoints (`ROLE_ADMIN`):**
  - Enforced via `@PreAuthorize("hasRole('ADMIN')")` under `/api/v1/admin/ingestion/**` and `/api/v1/admin/sponsorship/**` -> returns `403 Forbidden` for non-admin users.
- **Resource Ownership:** User-owned resources (profile, preferences, master resumes, tailored CVs/cover letters, applications, recruiters, assistant messages, interview sessions, notifications, autopilot runs) enforce strict owner scoping (`user_id = currentUser.getId()`).

### 1.3 Response Envelopes & Error Format
Resource endpoints return direct typed DTOs (`AuthResponse`, `UserProfileResponse`, `PagedJobsResponse`, etc.). Delete and status-acknowledgment actions return `ApiResponse<Void>`, while all `4xx`/`5xx` failures return standardized `ApiErrorResponse`:

```json
{
  "timestamp": "2026-11-09T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/auth/register",
  "validationErrors": [
    {
      "field": "email",
      "rejectedValue": "bad-email",
      "message": "Must be a well-formed email address"
    }
  ]
}
```

### 1.4 HTTP Rate Limiting Contract (`RateLimitingFilter`)
All `/api/v1/**` endpoints (except `OPTIONS` preflight and `GET /api/v1/health`) enforce bounded-memory fixed-window per-client throttling with three buckets (default `60s` window):
- **`AUTH` bucket (`20` req/min by IP):** `POST /api/v1/auth/login`, `/register`, `/refresh`.
- **`AI` bucket (`30` req/min by authenticated user ID or IP):** AI generation/evaluation `POST` routes (`/resumes/{uuid}/parse`, `/tailored-documents/(resume|cover-letter)/generate`, `/jobs/{id}/intelligence/(analyze-jd|ai-match)`, `/jobs/{jobId}/assistant/respond`, `/recruiters/{id}/outreach/generate`, `/interview-prep/sessions`, `/interview-prep/sessions/{id}/answers`, `/autopilot/run`).
- **`API` bucket (`180` req/min by authenticated user ID or IP):** All remaining `/api/v1/**` endpoints.

Every evaluated response sets `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset` (epoch seconds). Exceeding the bucket returns **`429 Too Many Requests`** with `Retry-After` and an `ApiErrorResponse` JSON body.

---

## 2. Complete Endpoint Catalog (83 Paths / 20 OpenAPI Tags)

### Tag 1: `Health Check` (`HealthCheckController`)
| Method | Path | Auth | Summary | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/health` | Public | Operational readiness, active profile & timestamp | `HealthResponse` (`200`) |

### Tag 2: `Authentication` (`AuthController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | `RegisterRequest` (`email`, strong `password` 8–64 chars w/ upper/lower/digit/special, `firstName`, `lastName`) -> sets `refresh_token` cookie | `AuthResponse` (`201`, `400`, `409`) |
| `POST` | `/api/v1/auth/login` | Public | `LoginRequest` (`email`, `password`) -> returns JWT + sets `refresh_token` cookie | `AuthResponse` (`200`, `401`) |
| `POST` | `/api/v1/auth/refresh` | Public | Rotates `refresh_token` cookie or `RefreshTokenRequest` body | `TokenRefreshResponse` (`200`, `401`) |
| `POST` | `/api/v1/auth/logout` | Bearer | Revokes refresh token (`LogoutRequest` optional) & clears cookie | `ApiResponse<Void>` (`200`, `401`) |
| `GET` | `/api/v1/auth/me` | Bearer | Current authenticated user identity & role summary | `UserSummaryDto` (`200`, `401`) |

> **Note:** Frontend routes `/auth/forgot-password` and `/auth/reset-password` render an informational notice; password-reset email/token APIs require outbound SMTP relay configuration.

### Tag 3: `User Profile` (`UserProfileController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/profile` | Bearer | Fetch owner profile + skills, educations, experiences | `UserProfileResponse` (`200`, `404`) |
| `POST` | `/api/v1/profile` | Bearer | Create candidate profile (`UpsertProfileRequest`) | `UserProfileResponse` (`201`, `400`, `409`) |
| `PUT` | `/api/v1/profile` | Bearer | Update/upsert candidate profile (`UpsertProfileRequest`) | `UserProfileResponse` (`200`, `400`) |
| `DELETE` | `/api/v1/profile` | Bearer | Delete owner profile details | `ApiResponse<Void>` (`200`) |
| `GET` / `POST` | `/api/v1/profile/skills` | Bearer | List or add skills (`UpsertSkillRequest`) | `List<UserSkillDto>` / `UserSkillDto` |
| `GET` / `PUT` / `DELETE` | `/api/v1/profile/skills/{id}` | Bearer | Read, update, or remove owned skill | `UserSkillDto` / `ApiResponse<Void>` |
| `GET` / `POST` | `/api/v1/profile/education` | Bearer | List or add education entries (`UpsertEducationRequest`) | `List<EducationDto>` / `EducationDto` |
| `GET` / `PUT` / `DELETE` | `/api/v1/profile/education/{id}` | Bearer | Read, update, or remove owned education entry | `EducationDto` / `ApiResponse<Void>` |
| `GET` / `POST` | `/api/v1/profile/experience` | Bearer | List or add work experience (`UpsertWorkExperienceRequest`) | `List<WorkExperienceDto>` / `WorkExperienceDto` |
| `GET` / `PUT` / `DELETE` | `/api/v1/profile/experience/{id}` | Bearer | Read, update, or remove owned work experience | `WorkExperienceDto` / `ApiResponse<Void>` |

### Tag 4: `Preferences` (`JobPreferenceController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/preferences` | Bearer | Fetch target titles, EU countries, salary, sponsorship, work arrangement, `minMatchScore`, `autopilotEnabled` | `JobPreferencesResponse` (`200`) |
| `PUT` | `/api/v1/preferences` | Bearer | Update search & autopilot preferences (`UpdateJobPreferencesRequest`) | `JobPreferencesResponse` (`200`, `400`) |

### Tag 5: `Resumes` (`ResumeController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/resumes/upload` | Bearer | `multipart/form-data`: `file` (PDF/DOCX, Tika magic-bytes checked, <= 10 MB) + `title` | `ResumeResponse` (`201`, `400`, `413`) |
| `GET` | `/api/v1/resumes` | Bearer | List all master resume versions owned by user | `List<ResumeResponse>` (`200`) |
| `GET` | `/api/v1/resumes/{uuid}` | Bearer | Get metadata for a specific master resume | `ResumeResponse` (`200`, `404`) |
| `DELETE` | `/api/v1/resumes/{uuid}` | Bearer | Delete master resume (blocked `409` if referenced unless safe) | `ApiResponse<Void>` (`200`, `409`) |
| `GET` | `/api/v1/resumes/{uuid}/download` | Bearer | Stream raw uploaded PDF/DOCX (`application/pdf` / `DOCX` stream) | Binary `Resource` (`200`, `404`) |
| `POST` | `/api/v1/resumes/{uuid}/parse` | Bearer | Query `forceRefresh` (default `true`); extract PDF/DOCX text & parse structured JSON via AI | `ResumeParseResponse` (`200`, `502`, `503`) |
| `GET` | `/api/v1/resumes/{uuid}/parsed` | Bearer | Retrieve persisted structured AI parse payload (`ParsedResumeJsonDto`) | `ResumeParseResponse` (`200`, `404`, `409`) |
| `POST` | `/api/v1/resumes/master/approve` | Bearer | `ApproveMasterResumeRequest` (`resumeUuid`, reviewed `ParsedResumeJsonDto`) -> approves master & syncs profile | `ResumeResponse` (`200`, `400`, `404`) |

### Tag 6: `Tailored Documents` (`TailoredResumeController`, `CoverLetterController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/tailored-documents/resume/generate` | Bearer | `GenerateTailoredResumeRequest` (`jobId`, optional `masterResumeUuid`, `template`) -> AI tailoring grounded against approved facts | `TailoredResumeResponse` (`201`, `400`, `404`, `502`, `503`) |
| `GET` | `/api/v1/tailored-documents/resumes` | Bearer | Optional query `jobId`; list user's tailored resume versions | `List<TailoredResumeSummaryDto>` (`200`) |
| `GET` | `/api/v1/tailored-documents/resume/{uuid}` | Bearer | Full structured tailored CV JSON, ATS keyword coverage & grounding audit | `TailoredResumeResponse` (`200`, `404`) |
| `PUT` | `/api/v1/tailored-documents/resume/{uuid}` | Bearer | Save edited tailored CV sections (`UpdateTailoredResumeRequest`) | `TailoredResumeResponse` (`200`, `400`) |
| `GET` | `/api/v1/tailored-documents/resume/{uuid}/export` | Bearer | Query `format` (`pdf`/`docx`), optional `template`, `regenerate` -> Apache PDFBox PDF or OOXML DOCX | Binary `Resource` (`200`, `400`, `404`) |
| `DELETE` | `/api/v1/tailored-documents/resume/{uuid}` | Bearer | Delete tailored CV draft & unlink applications | `ApiResponse<Void>` (`200`, `404`) |
| `POST` | `/api/v1/tailored-documents/cover-letter/generate` | Bearer | `GenerateCoverLetterRequest` (`jobId`, optional `tailoredResumeUuid`, `tone`, notes) -> grounded draft | `CoverLetterResponse` (`201`, `502`, `503`) |
| `GET` | `/api/v1/tailored-documents/cover-letters` | Bearer | Optional query `jobId`; list user's cover letters | `List<CoverLetterSummaryDto>` (`200`) |
| `GET` | `/api/v1/tailored-documents/cover-letter/{uuid}` | Bearer | Full cover letter text, readiness note & grounding checks | `CoverLetterResponse` (`200`, `404`) |
| `PUT` | `/api/v1/tailored-documents/cover-letter/{uuid}` | Bearer | Save edited cover letter (`UpdateCoverLetterRequest`) | `CoverLetterResponse` (`200`, `400`) |
| `GET` | `/api/v1/tailored-documents/cover-letter/{uuid}/export` | Bearer | Query `format` (`pdf`/`docx`/`txt`) -> European A4 business letter export | Binary `Resource` (`200`, `400`, `404`) |
| `DELETE` | `/api/v1/tailored-documents/cover-letter/{uuid}` | Bearer | Delete cover letter draft & unlink applications | `ApiResponse<Void>` (`200`, `404`) |

### Tag 7: `Jobs` (`JobController`, `JobInteractionController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/jobs` | Bearer | Multi-facet search: `query`, `country`, `city`, `workArrangement`, `employmentType`, `experienceLevel`, `sponsorStatus`, `minMatchScore`, `minSalary`, `savedOnly`, `interestedOnly`, `includeHidden`, `page`, `size`, `sort` | `PagedJobsResponse` (`200`) |
| `GET` | `/api/v1/jobs/{id}` | Bearer | Full job specification, sponsorship breakdown, user interaction flags, match breakdown | `JobDetailDto` (`200`, `404`) |
| `POST` / `DELETE` | `/api/v1/jobs/{id}/save` | Bearer | Save or unsave job bookmark | `JobInteractionResponse` (`200`, `404`, `409`) |
| `POST` / `DELETE` | `/api/v1/jobs/{id}/interested` | Bearer | Mark or clear interested flag | `JobInteractionResponse` (`200`, `404`, `409`) |
| `POST` / `DELETE` | `/api/v1/jobs/{id}/hide` | Bearer | Hide or restore job in discovery feed | `JobInteractionResponse` (`200`, `404`, `409`) |
| `POST` | `/api/v1/jobs/{id}/apply-redirect` | Bearer | Validates external `http/https` application URL & logs redirect audit (`ApplyRedirectRequest` optional) | `ApplyRedirectResponse` (`200`, `404`, `409`) |

### Tag 8: `Job Matching` (`JobMatchingController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/jobs/{id}/intelligence/match-score` | Bearer | Deterministic weighted score (Skill 40%, Exp 20%, Title 15%, Location 10%, Sponsor 10%, Salary 5%) | `JobMatchBreakdownDto` (`200`, `404`) |
| `POST` | `/api/v1/jobs/{id}/intelligence/ai-match` | Bearer | `EvaluateAiMatchRequest` (optional); semantic AI fit rationale + gap coaching | `JobMatchBreakdownDto` (`200`, `502`, `503`) |
| `POST` | `/api/v1/matching/recalculate` | Bearer | Recompute deterministic match scores across active catalog for user | `BatchMatchRecalculationResponse` (`200`) |

### Tag 9: `AI Intelligence` (`JobAiAnalysisController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/jobs/{id}/intelligence/analyze-jd` | Bearer | `AnalyzeJobDescriptionRequest` (optional); AI extraction of stack, seniority, language, sponsorship hints | `JobAiAnalysisDto` (`200`, `502`, `503`) |
| `GET` | `/api/v1/jobs/{id}/intelligence/analyze-jd` | Bearer | Retrieve latest cached JD AI analysis for job | `JobAiAnalysisDto` (`200`, `404`) |
| `GET` | `/api/v1/jobs/{id}/intelligence/analyze-jd/history` | Bearer | Retrieve versioned history of JD analyses for job | `List<JobAiAnalysisDto>` (`200`, `404`) |

### Tag 10: `AI Application Assistant` (`JobAssistantController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/jobs/{jobId}/assistant/history` | Bearer | Retrieve persisted user+assistant thread for job | `List<JobAssistantMessageResponse>` (`200`, `404`) |
| `POST` | `/api/v1/jobs/{jobId}/assistant/respond` | Bearer | `JobAssistantRespondRequest` (`intent` / prompt) -> grounded contextual advice or draft | `JobAssistantMessageResponse` (`201`, `502`, `503`) |
| `PUT` | `/api/v1/jobs/{jobId}/assistant/{messageId}` | Bearer | `UpdateJobAssistantMessageRequest` -> edit & persist user-modified assistant draft | `JobAssistantMessageResponse` (`200`, `404`) |
| `DELETE` | `/api/v1/jobs/{jobId}/assistant/{messageId}` | Bearer | Delete message from user's job assistant thread | `ApiResponse<Void>` (`200`, `404`) |

### Tag 11: `Companies` (`CompanyController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/companies` | Bearer | Filter `query`, `country`, `industry`, `tech`, `verifiedSponsorOnly`, `savedOnly`, `withOpenJobsOnly`, `page`, `size`, `sort` | `PagedCompaniesResponse` (`200`) |
| `GET` | `/api/v1/companies/{id}` | Bearer | Full employer intelligence profile, IND verification evidence, bookmark status | `CompanyDetailDto` (`200`, `404`) |
| `GET` | `/api/v1/companies/{id}/jobs` | Bearer | Paginated active jobs posted by company (`includeHidden`, `page`, `size`, `sort`) | `PagedJobsResponse` (`200`, `404`) |
| `POST` / `DELETE` | `/api/v1/companies/{id}/save` | Bearer | Bookmark or unbookmark target employer (`SaveCompanyRequest` optional notes) | `CompanySaveResponse` (`200`, `404`) |

### Tag 12: `Sponsorship Intelligence` (`SponsorshipController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/sponsorship/countries` | Bearer | 14 European visa rule profiles (Blue Card / skilled worker rules, salary thresholds, languages) | `List<CountrySponsorshipProfileDto>` (`200`) |
| `GET` | `/api/v1/sponsorship/verify` | Bearer | Required `companyName`, `country`, optional `jobId`, `postingText` -> multi-tier evidence check | `SponsorshipVerificationResponseDto` (`200`, `404`) |
| `GET` | `/api/v1/sponsorship/history` | Bearer | Optional `companyName`, `country`, pagination -> verification audit history | `PagedSponsorshipHistoryResponse` (`200`) |
| `GET` | `/api/v1/sponsorship/registry/ind` | Bearer | Search Netherlands IND official recognized sponsor entries (`query`, `page`, `size`) | `PagedIndRegistryResponse` (`200`) |

### Tag 13: `Applications` (`ApplicationController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/applications` | Bearer | Filter `status`, `query`, `dueFollowUpOnly`, `hasInterviewOnly` | `List<JobApplicationDetailResponse>` (`200`) |
| `POST` | `/api/v1/applications` | Bearer | `CreateApplicationRequest` (`jobId`, `status`, linked CV/CL UUIDs, dates, notes) | `JobApplicationDetailResponse` (`201`, `409`) |
| `GET` | `/api/v1/applications/{uuid}` | Bearer | Detail + immutable `statusHistory` transitions | `JobApplicationDetailResponse` (`200`, `404`) |
| `PUT` | `/api/v1/applications/{uuid}` | Bearer | `UpdateApplicationRequest` (dates, salary offer/currency, notes, linked doc UUIDs, optimistic `@Version`) | `JobApplicationDetailResponse` (`200`, `409`) |
| `PATCH` | `/api/v1/applications/{uuid}/status` | Bearer | `UpdateApplicationStatusRequest` (`newStatus` across 10 Kanban stages + `transitionNotes`) | `JobApplicationDetailResponse` (`200`, `404`) |
| `DELETE` | `/api/v1/applications/{uuid}` | Bearer | Deletes tracked application & history while safely detaching recruiter links | `ApiResponse<Void>` (`200`, `404`) |

### Tag 14: `Recruiter CRM` (`RecruiterController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/recruiters` | Bearer | Filter `query`, `companyId`, `page`, `size` | `PagedRecruitersResponse` (`200`) |
| `POST` | `/api/v1/recruiters` | Bearer | Create contact (`CreateRecruiterRequest`) | `RecruiterResponse` (`201`, `404`) |
| `GET` | `/api/v1/recruiters/reminders` | Bearer | Query `includeCompleted` (default `false`); due/overdue follow-ups | `FollowUpRemindersResponse` (`200`) |
| `GET` / `PUT` / `DELETE` | `/api/v1/recruiters/{id}` | Bearer | Read, update (`UpdateRecruiterRequest`), or delete owned contact | `RecruiterResponse` / `ApiResponse<Void>` |
| `POST` | `/api/v1/recruiters/{id}/outreach/generate` | Bearer | `GenerateOutreachRequest` (optional); creates strictly **unsent `DRAFT`** outreach message | `OutreachDraftResponse` (`201`, `502`, `503`) |
| `POST` | `/api/v1/recruiters/{id}/interactions` | Bearer | `CreateRecruiterInteractionRequest` (`markAsSent=true` requires `explicitApprovalConfirmed=true`) | `RecruiterInteractionDto` (`201`, `400`) |
| `PUT` / `DELETE` | `/api/v1/recruiters/{recruiterId}/interactions/{interactionId}` | Bearer | Edit draft, `APPROVE`, or `MARK_SENT` (requires `explicitApprovalConfirmed=true`), or delete interaction | `RecruiterInteractionDto` / `ApiResponse<Void>` |

### Tag 15: `Interview Preparation` (`InterviewPrepController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/interview-prep/sessions` | Bearer | `CreateInterviewPrepSessionRequest` -> AI-generated tailored question set + company prep topics | `InterviewPrepSessionDetailResponse` (`201`, `502`, `503`) |
| `GET` | `/api/v1/interview-prep/sessions` | Bearer | Optional `jobId`; list user's interview prep sessions | `List<InterviewPrepSessionSummaryDto>` (`200`) |
| `GET` / `DELETE` | `/api/v1/interview-prep/sessions/{id}` | Bearer | Full session detail (questions, ideal answers, grades) or delete session | `InterviewPrepSessionDetailResponse` / `ApiResponse<Void>` |
| `POST` | `/api/v1/interview-prep/sessions/{id}/answers` | Bearer | `SubmitInterviewAnswerRequest` -> AI score (0–100), strengths & missing elements | `InterviewPrepSessionDetailResponse` (`200`, `502`, `503`) |
| `GET` | `/api/v1/interview-prep/history` | Bearer | Performance analytics across categories, difficulty levels & chronological trends | `InterviewPerformanceHistoryResponse` (`200`) |

### Tag 16: `Dashboard & Analytics` (`DashboardController`, `MarketAnalyticsController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/dashboard/summary` | Bearer | KPIs (`totalJobs`, `highMatchJobs`, `verifiedSponsorJobs`, `unreadAlerts`, pipeline stages, readiness, recent activity) | `DashboardSummaryResponse` (`200`) |
| `GET` | `/api/v1/analytics/market-insights` | Bearer | Optional `countries`, `days` (`7..180`, default `30`) -> country hiring, remote ratios, median salary, top skills | `MarketInsightsResponse` (`200`) |

### Tag 17: `Autopilot & Recommendations` (`AutopilotController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/autopilot/status` | Bearer | Autopilot enablement/paused state, preferences summary, latest run telemetry | `AutopilotStatusResponse` (`200`) |
| `POST` | `/api/v1/autopilot/run` | Bearer | `RunAutopilotRequest` (optional); manual scan recalculating matches, checking reminders, creating notifications | `AutopilotRunLogDto` (`200`) |
| `POST` | `/api/v1/autopilot/pause` | Bearer | Pause background scheduled autopilot scans | `AutopilotStatusResponse` (`200`) |
| `POST` | `/api/v1/autopilot/resume` | Bearer | Resume background scheduled autopilot scans | `AutopilotStatusResponse` (`200`) |
| `GET` | `/api/v1/autopilot/recommended-jobs` | Bearer | Filter `minMatchScore`, `verifiedSponsorsOnly`, `excludeApplied`, `page`, `size` | `PagedRecommendedJobsResponse` (`200`) |
| `GET` | `/api/v1/autopilot/history` | Bearer | Paginated execution history (`page`, `size`) | `PagedAutopilotHistoryResponse` (`200`) |

### Tag 18: `Notifications` (`NotificationController`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications` | Bearer | Filter `unreadOnly`, `type`, `page`, `size` | `PagedNotificationsResponse` (`200`) |
| `GET` | `/api/v1/notifications/unread-count` | Bearer | Live unread counter for top-bar bell badge | `UnreadCountResponse` (`200`) |
| `PATCH` | `/api/v1/notifications/{id}/read` | Bearer | Mark individual notification as read | `NotificationDto` (`200`, `404`) |
| `POST` | `/api/v1/notifications/mark-all-read` | Bearer | Bulk mark all user notifications as read | `MarkAllReadResponse` (`200`) |
| `DELETE` | `/api/v1/notifications/{id}` | Bearer | Delete individual notification | `ApiResponse<Void>` (`200`, `404`) |

### Tag 19: `Job Ingestion Admin` (`AdminIngestionController` — `ROLE_ADMIN`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/admin/ingestion/trigger` | Admin | Optional query `provider` or body `TriggerIngestionRequest`; runs Arbeitnow/Adzuna sync | `AdminIngestionTriggerResponse` (`200`, `403`, `409`) |
| `GET` | `/api/v1/admin/ingestion/status` | Admin | Provider enablement, scheduler cron, active counts & last fetch status | `IngestionStatusResponse` (`200`, `403`) |
| `GET` | `/api/v1/admin/ingestion/logs` | Admin | Filter `provider`, `status`, `page`, `size` -> paginated connector audit log | `PagedFetchLogsResponse` (`200`, `403`) |
| `POST` | `/api/v1/admin/ingestion/cleanup-stale` | Admin | Optional `staleDays` (`1..365`); marks expired/stale jobs inactive | `StaleJobCleanupResponse` (`200`, `403`) |

### Tag 20: `Sponsorship Registry Admin` (`AdminSponsorshipController` — `ROLE_ADMIN`)
| Method | Path | Auth | Summary / Key Contract | Response DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/admin/sponsorship/ind/sync` | Admin | Synchronizes official Netherlands IND recognized sponsor CSV register | `IndSyncResultDto` (`200`, `403`, `409`) |
| `GET` | `/api/v1/admin/sponsorship/ind/status` | Admin | Reports latest IND sync timestamp, entry count & status | `RegistrySyncStatusDto` (`200`, `403`) |

---

## 3. OpenAPI 3 Verification
- Machine-readable specification: [`docs/api/openapi.yaml`](openapi.yaml)
- Automated contract guard: `OpenApiDocumentationIntegrationTest` validates exact coverage across all **83 paths**, **20 OpenAPI tags**, security schemes (`BearerAuth` vs public routes), multipart schema on `/api/v1/resumes/upload`, binary download/export headers, and `ApiErrorResponse` references.
- In production (`SPRING_PROFILES_ACTIVE=prod`), `/v3/api-docs` and `/swagger-ui/**` default to disabled (`SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`).

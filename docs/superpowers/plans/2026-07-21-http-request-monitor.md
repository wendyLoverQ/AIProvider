# AIProvider HTTP Request Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace misleading Maid LLM request totals with persistent metrics for real AIProvider business API requests.

**Architecture:** A Spring MVC interceptor records one sanitized metric after each included `/api/**` request. MyBatis aggregates the new table for today's cards and 24-hour trends; monitor, health, static, and preflight traffic is excluded at collection time.

**Tech Stack:** Java 8, Spring MVC, Spring Boot, MyBatis, MySQL 8, Flyway, JUnit 5, Mockito

## Global Constraints

- Do not record query parameters, bodies, responses, credentials, prompts, or file content.
- Exclude `/api/monitor/**`, `/api/health`, non-API paths, and `OPTIONS`.
- Keep response fields consumed by `MonitorCenter.jsx` unchanged.
- Run only focused backend tests and compilation.

---

### Task 1: Capture real API request metrics

**Files:**
- Create: `AIProvider-back/src/test/java/com/aiprovider/service/HttpRequestMetricInterceptorTest.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/service/HttpRequestMetricInterceptor.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/config/HttpRequestMetricConfig.java`
- Modify: `AIProvider-back/src/main/java/com/aiprovider/repository/MonitorRepository.java`
- Modify: `AIProvider-back/src/main/java/com/aiprovider/mapper/MonitorMapper.java`

**Interfaces:**
- Produces: `MonitorRepository.recordHttpRequest(String method, String route, int statusCode, long durationMs): int`.

- [ ] Write interceptor tests for included requests, exclusions, normalized route, status, duration, and affected-row mismatch.
- [ ] Run `mvn -Dtest=HttpRequestMetricInterceptorTest test` and verify failure because the interceptor does not exist.
- [ ] Implement the interceptor, MVC registration, and one-row insert with structured success/warning logs.
- [ ] Re-run the focused test and verify it passes.

### Task 2: Aggregate and retain HTTP metrics

**Files:**
- Create: `AIProvider-back/src/main/resources/db/migration/V50__http_request_metrics.sql`
- Modify: `AIProvider-back/src/main/java/com/aiprovider/mapper/MonitorMapper.java`
- Modify: `AIProvider-back/src/main/java/com/aiprovider/repository/MonitorRepository.java`
- Modify: `AIProvider-back/src/main/java/com/aiprovider/service/MonitorRetentionService.java`
- Create: `AIProvider-back/src/test/java/com/aiprovider/mapper/MonitorMapperSqlTest.java`
- Modify: `AIProvider-back/src/test/java/com/aiprovider/service/MonitorServiceTest.java`

**Interfaces:**
- Existing `todayOverview`, `todayP95`, `timeseries`, and `timeseriesP95` signatures remain unchanged.
- Produces: `deleteExpiredHttpRequests(int days): int`.

- [ ] Add failing SQL-contract tests proving service-request queries target `c_HttpRequestMetrics`, not `maid_LlmCallLogs`.
- [ ] Run the focused mapper/service tests and verify the expected failure.
- [ ] Add the migration, switch four aggregation queries, and add structured retention logging.
- [ ] Run the focused tests and backend compile.
- [ ] Review `git diff --check` and the exact scoped diff.

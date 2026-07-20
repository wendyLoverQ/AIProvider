# Platform Account Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one encrypted backend account vault for X, XiaoHongShu, Douyin, and Gemini, migrate existing credentials, and make every existing consumer reference a stable `PlatformAccountId`.

**Architecture:** Flyway creates generic account and secret tables plus consumer foreign keys. An idempotent startup migrator decrypts each legacy format with its owning cipher, re-encrypts with `ContentPlatformSecretCipher`, and backfills consumer IDs; runtime consumers then read only through `PlatformAccountCredentialService`. A new React workspace owns login, secret update, validation, usage display, and account lifecycle.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis, MySQL/Flyway, Playwright 1.60, React 19, Vite 8, Vitest.

## Global Constraints

- Work on `master`; preserve unrelated dirty files and never stage them.
- No credential fallback to legacy fields or another account/secret type.
- No plaintext credential in API responses, logs, exceptions, command arguments, snapshots, or Git.
- Use AES-GCM through `ContentPlatformSecretCipher` for all new secrets.
- All operations log operation, platform, account ID, requested count, affected rows, status, and error code.
- Affected-row mismatch is a failed operation with a warning log.
- Account Center search uses `UiSearchField`; all visible colors use semantic theme variables.
- Run only focused tests and `uiGate.test.js`; do not release unless separately requested.

---

### Task 1: Create the unified schema and mapper

**Files:**
- Create: `AIProvider-back/src/main/resources/db/migration/V51__platform_account_center.sql`
- Create: `AIProvider-back/src/main/java/com/aiprovider/mapper/PlatformAccountMapper.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/repository/PlatformAccountRepository.java`
- Test: `AIProvider-back/src/test/java/com/aiprovider/repository/PlatformAccountRepositoryTest.java`

**Interfaces:**
- Produces `c_PlatformAccounts`, `c_PlatformAccountSecrets`, and nullable `PlatformAccountId` foreign keys on the four consumer tables.
- Mapper provides paged account queries, secret upsert, legacy lookup, usage counts, and archive.

- [ ] Write a failing repository test for numeric IDs, one secret per `(AccountId,SecretType)`, exact affected-row checks, and archive refusal when usages exist.
- [ ] Run `mvn -Dtest=PlatformAccountRepositoryTest test`; expect compilation failure.
- [ ] Add V51 with both generic tables, indexes, legacy source type/ID columns for idempotent migration, and four consumer foreign keys.
- [ ] Implement one batch/atomic SQL operation per repository method; no per-ID loop writes.
- [ ] Run the focused repository test; expect PASS.
- [ ] Commit only Task 1 files with `feat(accounts): add unified account persistence`.

### Task 2: Add account service, credential service, API, and migration

**Files:**
- Create: `AIProvider-back/src/main/java/com/aiprovider/model/dto/PlatformAccountCreateDTO.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/model/dto/PlatformAccountUpdateDTO.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/model/dto/PlatformSecretUpdateDTO.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/model/vo/PlatformAccountVO.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/model/vo/PlatformAccountPageVO.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/model/vo/PlatformAccountUsageVO.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/service/PlatformAccountCredentialService.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/service/PlatformAccountService.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/service/PlatformAccountLegacyMigrationService.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/controller/PlatformAccountController.java`
- Test: corresponding service/controller tests.

**Interfaces:**
- `requireSecret(long accountId,String platform,String secretType)` returns plaintext only in backend memory after all checks.
- `/api/platform-accounts` exposes paged CRUD, secret replacement, validation, login polling, usages, and archive; responses expose hints, never values.

- [ ] Write failing tests for multi-account support, no secret echo, secret version increments, platform/type mismatch, `ACCOUNT_IN_USE`, migration idempotency, and secret-free logging.
- [ ] Run focused tests; expect missing types.
- [ ] Implement DTO/VO validation and stable error codes from the design.
- [ ] Implement credential access with exact account/platform/type matching and no fallback.
- [ ] Implement idempotent legacy migration for Twitter publishing, X collection, XiaoHongShu, and Gemini using their owning legacy ciphers.
- [ ] Implement controller endpoints under `/api/platform-accounts`.
- [ ] Run focused tests; expect PASS.
- [ ] Commit with `feat(accounts): add encrypted account service and migration`.

### Task 3: Switch existing consumers to PlatformAccountId

**Files:**
- Modify: `TwitterMapper.java`, `TwitterPublishingService.java`
- Modify: `ContentOperationsMapper.java`, `ContentOperationsRepository.java`, `ContentSourceService.java`, `XiaohongshuAccountService.java`, `XiaohongshuPublicationService.java`
- Modify: `ContentAiMapper.java`, `ContentAiRepository.java`, `ContentAiConfigService.java`, `ContentGenerationService.java`
- Test: existing focused Twitter/content-operation/Gemini tests plus new no-fallback tests.

**Interfaces:**
- Every consumer resolves credentials only through `PlatformAccountCredentialService`.
- Existing business IDs and publication/source relations remain stable.

- [ ] Add failing tests proving legacy encrypted columns are not selected or decrypted at runtime.
- [ ] Update mapper projections to return `platformAccountId` rather than legacy secret columns.
- [ ] Replace all runtime cipher usage with `requireSecret` calls.
- [ ] Move XiaoHongShu login persistence to the account vault.
- [ ] Move Gemini key update/test to the account vault while retaining model/temperature/Prompt settings in content operations.
- [ ] Run the focused existing and new tests; expect PASS.
- [ ] Commit with `refactor(accounts): switch consumers to unified credentials`.

### Task 4: Implement X, XiaoHongShu, Douyin, and Gemini validation/login adapters

**Files:**
- Create: `AIProvider-back/src/main/java/com/aiprovider/service/PlatformAccountValidationService.java`
- Create: `AIProvider-back/src/main/java/com/aiprovider/service/DouyinWebAdapter.java`
- Reuse/modify: `XiaohongshuWebAdapter.java`, `TwitterWebPublisher.java`, `GeminiContentClient.java`
- Test: adapter and validation service tests.

**Interfaces:**
- Login start returns `{sessionId,status,qrImageDataUrl,message}`.
- Login poll stores `STORAGE_STATE` only after real authenticated state is detected.
- Validation updates account status and stable error code.

- [ ] Write failing state-machine tests for WAITING_SCAN, CONNECTED, LOGIN_TIMEOUT, PLATFORM_RISK_CONTROL, and ADAPTER_UNAVAILABLE.
- [ ] Implement X validation for COOKIE/STORAGE_STATE, XiaoHongShu QR reuse, Gemini model-list validation, and Douyin creator-page QR login.
- [ ] Ensure every Playwright session closes and any temporary state file is deleted and verified.
- [ ] Run adapter tests and one local live login probe where interaction is possible.
- [ ] Commit with `feat(accounts): add platform login validation`.

### Task 5: Build the Account Center workspace

**Files:**
- Create: `AIProvider-front/src/PlatformAccountCenter.jsx`
- Create: `AIProvider-front/src/PlatformAccountCenter.css`
- Create: `AIProvider-front/src/PlatformAccountCenter.test.jsx`
- Modify: `AIProvider-front/src/App.jsx`, `SemanticTheme.css`, `uiGate.test.js`

**Interfaces:**
- Adds navigation key `accounts`, route `/accounts`, and the three approved page sections.
- Consumes only `/api/platform-accounts`; never receives raw credentials.

- [ ] Write failing tests for the three sections, `UiSearchField`, platform-specific forms, no secret echo, QR polling serialization, usage-blocked archive, mobile reachability, and native semantics.
- [ ] Implement paged account loading and 300 ms server-side search.
- [ ] Implement X Cookie/StorageState forms, XiaoHongShu/Douyin QR dialogs, Gemini API form/test, and usage details.
- [ ] Add semantic responsive CSS and focus states.
- [ ] Wire navigation and update UI gates.
- [ ] Run `npm test -- PlatformAccountCenter.test.jsx uiGate.test.js`; expect PASS.
- [ ] Commit with `feat(accounts): add account center workspace`.

### Task 6: Remove credential ownership from Content Operations and Twitter UI

**Files:**
- Modify: `AIProvider-front/src/ContentOperationsCenter.jsx/.css/.test.jsx`
- Modify: `AIProvider-front/src/TwitterPublisher.jsx/.css/.test.jsx`
- Modify: relevant content-operation controllers/VOs so they return account references, not secrets.

**Interfaces:**
- Content Operations retains rules and bindings and links to `/accounts` for credential changes.
- Twitter Publisher selects Account Center X accounts.

- [ ] Add failing tests that old add-account, Cookie, QR, and Gemini-key controls are absent.
- [ ] Replace old account CRUD with account selectors and “前往账号中心”.
- [ ] Keep publish-mode, source binding, model, temperature, and Prompt controls.
- [ ] Run focused frontend/backend tests; expect PASS.
- [ ] Commit with `refactor(content): consume centralized accounts`.

### Task 7: Verify migration, security, UI, and real platform behavior

**Files:**
- No new production files unless a verified defect requires a focused correction.

- [ ] Run targeted Maven tests for account repository/service/controller/migration and modified consumers.
- [ ] Run focused Vitest files and `uiGate.test.js`.
- [ ] Run frontend build.
- [ ] Start local backend/frontend and verify Account Center in the browser at desktop and mobile widths.
- [ ] Inspect API responses and logs for plaintext credentials.
- [ ] Verify migrated X collection, XiaoHongShu login/publication, Gemini connection, and Douyin QR state with real services; report any external blocker exactly.
- [ ] Inspect `git status`, `git diff --check`, and scoped commits; preserve unrelated dirty files.
- [ ] Do not release or delete legacy credential columns without separate user approval and successful real validation.

## Self-Review

- Coverage: schema, migration, credential service, all current consumers, four adapters, Account Center UI, Content Operations split, logs, UI gates, and real verification are assigned.
- No placeholders: each task has exact files, interfaces, failure-first tests, commands, and commit boundaries.
- Type consistency: all consumers use numeric `PlatformAccountId`; only `PlatformAccountCredentialService.requireSecret` returns plaintext internally.

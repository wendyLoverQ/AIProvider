# 统一账号中心设计

日期：2026-07-21

## 目标

在 AIProvider 左侧一级导航中新增“账号中心”，将当前分散在 X 发布、X 采集、小红书内容运营和 Gemini 内容生成中的登录态与 API Key 迁移到唯一的后端凭据主库。内容运营、情报雷达和以后新增的发布或采集模块只能通过稳定数值 `PlatformAccountId` 引用账号中心，不再自行保存或读取平台凭据。

账号中心第一版覆盖 X、小红书、抖音和 Gemini，并允许同一平台保存多个账号。设计必须支持后续新增其他社交平台和 AI API 服务，不为每个平台新建一套账号表。

## 非目标

- 账号中心不保存内容运营的发布模式、发布周期、采集关键词或自动化规则。
- 账号中心不保存 Gemini 模型、温度或业务 Prompt。
- 账号中心不向前端、Bridge 或其他 HTTP 调用方返回原始 Cookie、Token、API Key 或 StorageState。
- 本设计不默认授权生产发布；只有用户明确要求发布后才进入版本递增和部署流程。

## 当前凭据来源

当前存在四类独立凭据来源：

1. `c_TwitterAccounts.EncryptedStorageState`：X 发布浏览器会话。
2. `c_ContentCollectionAccounts.CredentialEncrypted`：X 采集 Cookie 或 Bearer Token。
3. `c_ContentAccounts.SessionEncrypted`：小红书发布浏览器会话。
4. `c_ContentOperationSettings.GeminiApiKeyEncrypted`：Gemini API Key。

账号中心完成切换后，上述字段不得再被运行时代码读取。迁移期间保留旧密文只用于可审计回滚检查，不构成业务兜底。

## 导航与页面

### 导航

左侧“系统”分组新增“账号中心”，路由为 `/accounts`。移动端导航必须保持可达、可滚动和触控安全。

### 页面分区

账号中心包含三个主分区：

1. **平台账号**：X、小红书、抖音等社交平台账号。
2. **API 服务**：Gemini及未来其他 AI API 凭据。
3. **使用关系**：展示账号被哪些业务模块和具体业务记录引用。

账号卡展示平台、显示名称、账号 Handle、适配器、启用状态、连接状态、最近验证时间、脱敏凭据提示和最近错误。账号搜索必须复用 `AIProvider-front/src/UiSearchField.jsx`，平台、类型和状态为实时筛选，不增加搜索提交按钮。

页面必须使用 `uiTheme` 语义变量。所有按钮、链接、标签页、选择器和对话框控件必须使用原生交互元素，具备键盘操作、可见焦点和准确无障碍名称。

## 数据模型

### c_PlatformAccounts

账号主表字段：

- `Id BIGINT AUTO_INCREMENT`：稳定业务主键。
- `Platform VARCHAR(32)`：`X`、`XIAOHONGSHU`、`DOUYIN`、`GEMINI`。
- `AccountKind VARCHAR(24)`：`SOCIAL` 或 `API_SERVICE`。
- `DisplayName VARCHAR(100)`：用户可编辑名称。
- `AccountHandle VARCHAR(200) NULL`：平台用户名或 API账号提示。
- `AdapterType VARCHAR(64)`：固定适配器。
- `PublicConfigJson JSON NULL`：只保存 Base URL 等非敏感适配器配置，禁止保存任何凭据。
- `Enabled BOOLEAN`。
- `ConnectionStatus VARCHAR(32)`：`NOT_CONFIGURED`、`PENDING_LOGIN`、`CONNECTED`、`EXPIRED`、`ERROR`、`DISABLED`。
- `CredentialHint VARCHAR(100) NULL`：非敏感提示。
- `LastValidatedAt DATETIME(6) NULL`。
- `LastConnectedAt DATETIME(6) NULL`。
- `LastErrorCode VARCHAR(80) NULL`。
- `LastErrorMessage VARCHAR(1000) NULL`。
- `ArchivedAt DATETIME(6) NULL`。
- `CreatedAt`、`UpdatedAt`。

索引覆盖 `(Platform,Enabled,ArchivedAt)`、`(AccountKind,Enabled,ArchivedAt)` 和 `(ConnectionStatus,UpdatedAt)`。不对平台和 Handle建立唯一约束，因为同一平台允许保存多个账号，且部分平台无法可靠获得 Handle。

### c_PlatformAccountSecrets

凭据表字段：

- `Id BIGINT AUTO_INCREMENT`。
- `AccountId BIGINT`，外键指向 `c_PlatformAccounts.Id`。
- `SecretType VARCHAR(32)`：`COOKIE`、`STORAGE_STATE`、`BEARER_TOKEN`、`API_KEY`、`PROXY`。
- `EncryptedValue LONGTEXT`。
- `SecretHint VARCHAR(100) NULL`。
- `SecretVersion INT`，首次写入为 1，每次替换递增。
- `LastValidatedAt DATETIME(6) NULL`。
- `CreatedAt`、`UpdatedAt`。

唯一约束为 `(AccountId,SecretType)`。更新凭据使用原位更新并递增版本，不保留可被业务读取的旧明文或旧密文副本。

### 业务引用

现有业务表增加 `PlatformAccountId BIGINT`：

- `c_TwitterAccounts`：X 发布业务档案引用统一 X 账号。
- `c_ContentCollectionAccounts`：X 采集配置引用统一 X 账号。
- `c_ContentAccounts`：小红书发布模式和运营状态引用统一小红书账号。
- `c_ContentOperationSettings`：Gemini业务配置引用统一 Gemini API账号。

这些旧业务表继续保存各自的行为配置和历史业务 ID，避免破坏发布、草稿和采集外键。它们不再保存或读取凭据。

未来的抖音业务配置和情报雷达订阅直接保存 `PlatformAccountId`。账号使用关系由后端对这些明确外键进行联合查询，不建立无外键约束的多态引用表。

## 凭据与安全边界

- 复用 `ContentPlatformSecretCipher` 的 AES-GCM 加密方式和生产环境 `CONTENT_PLATFORM_SECRET_ENCRYPTION_KEY`。
- Gemini API Key迁移后也统一使用 `ContentPlatformSecretCipher`；原 `ContentAiSecretCipher` 不再是账号凭据入口。
- 前端提交新凭据后，后端立即加密再写库。查询 API 只返回 `credentialConfigured`、`credentialHint`、状态和时间。
- 更新表单中凭据留空表示保留现有值；非空才替换。任何接口都不支持“读取原值”。
- 后端业务只能通过 `PlatformAccountCredentialService.requireSecret(accountId,platform,secretType)` 在内存中获取指定凭据。
- 该服务必须校验账号未归档、已启用、平台匹配、凭据类型匹配和密文可解密；不得尝试其他账号或其他凭据类型。
- 原始凭据禁止写入日志、异常消息、命令行参数、普通临时文件、测试快照或 API 响应。
- 如浏览器自动化必须使用 StorageState 文件，使用权限受限的明确临时文件，任务结束立即删除并验证删除结果。

## 平台登录与验证

### X

统一 X 账号可同时拥有：

- `STORAGE_STATE`：供发布浏览器适配器使用。
- `COOKIE`：供网页采集适配器使用。
- `BEARER_TOKEN`：只有明确选择官方 API 适配器时使用。

账号中心复用现有浏览器登录流程，也支持导入 Netscape Cookie。每个业务适配器请求自己唯一需要的凭据类型，缺失时返回 `CREDENTIAL_MISSING`，不切换其他凭据路径。

### 小红书

复用现有扫码登录适配器。扫码成功后将 StorageState 写入 `c_PlatformAccountSecrets`，更新统一账号状态。内容运营不再直接保存扫码结果。

### 抖音

新增独立扫码登录适配器。登录状态包括等待扫码、已连接、超时、会话过期和风控错误。适配器未可用时账号中心显示 `ADAPTER_UNAVAILABLE`，禁止将仅保存了空账号记录显示为已连接。

### Gemini

保存 `API_KEY`，Base URL保存在账号主表 `PublicConfigJson` 的 `apiBaseUrl` 字段。连接验证必须向配置的 Gemini服务执行真实、无内容生成副作用的模型可用性请求。模型、温度和业务 Prompt 继续保存在内容运营等调用模块中。

## 后端 API

统一前缀为 `/api/platform-accounts`：

- `GET /api/platform-accounts`：分页查询，支持 query、platform、accountKind和 status筛选。
- `GET /api/platform-accounts/{id}`：账号详情，不含密文或明文。
- `POST /api/platform-accounts`：创建账号。
- `PUT /api/platform-accounts/{id}`：更新非敏感元数据和启用状态。
- `PUT /api/platform-accounts/{id}/secrets/{secretType}`：设置或替换一种凭据。
- `POST /api/platform-accounts/{id}/validate`：真实验证账号。
- `POST /api/platform-accounts/{id}/login`：启动需要交互的登录。
- `GET /api/platform-accounts/{id}/login/{sessionId}`：轮询登录结果。
- `GET /api/platform-accounts/{id}/usages`：查询全部业务引用。
- `DELETE /api/platform-accounts/{id}`：无引用时归档。

列表和详情响应只返回是否配置了各凭据类型及其脱敏提示，不返回 `EncryptedValue`。

删除账号前必须查询 X 发布、内容采集、小红书运营、Gemini配置、情报雷达和未来已注册消费者的引用。存在任一引用时返回 `ACCOUNT_IN_USE` 和非敏感引用说明，影响行数必须为 0。

## 内容运营与其他消费者调整

- 内容运营页面移除账号 CRUD、X Cookie编辑、小红书扫码入口和 Gemini API Key编辑。
- 内容运营保留发布模式、账号与采集源绑定、模型、温度、Prompt和自动化规则。
- 需要选择账号的地方展示账号中心返回的可用账号，并保存 `PlatformAccountId`。
- 无可用账号时显示“前往账号中心”，通过原生按钮导航到 `/accounts`。
- Twitter发布页面使用统一 X 账号，不再独立创建登录身份。
- 情报雷达实施计划以账号中心 API 和 `PlatformAccountId` 为前置依赖。
- 任一消费者读取账号中心失败时必须明确失败，禁止读取旧凭据字段。

## 迁移方案

迁移分两次 Flyway版本完成。

### 第一阶段：建立与切换

1. 创建 `c_PlatformAccounts` 和 `c_PlatformAccountSecrets`。
2. 给四个现有消费者表添加可空 `PlatformAccountId` 及外键索引。
3. 将现有 X 发布、X 采集、小红书和 Gemini凭据逐行迁入统一表。
4. 为旧业务记录回填 `PlatformAccountId`。
5. 校验每类原记录数、合法去重账号数、迁移账号数、迁移凭据数和回填行数；不一致时迁移失败。
6. 发布只读取统一账号中心的新业务代码。
7. 真实验证 X 发布、X 采集、小红书发布和 Gemini连接。

迁移应尽量复用同一密钥体系。若旧 Gemini密钥使用不同加密组件且无法由纯 SQL 重加密，则在应用启动的受控迁移服务中完成一次性解密和重加密，并以数据库迁移标记保证幂等；失败时禁止启动依赖 Gemini的业务，但不得输出密钥。

### 第二阶段：删除旧凭据

第一阶段真实验证完成并经用户确认后，下一条 Flyway迁移删除：

- `c_TwitterAccounts.EncryptedStorageState`
- `c_ContentCollectionAccounts.CredentialEncrypted` 与 `CredentialHint`
- `c_ContentAccounts.SessionEncrypted` 与 `SessionHint`
- `c_ContentOperationSettings.GeminiApiKeyEncrypted` 与 `GeminiApiKeyHint`

同时删除不再使用的旧加密服务和旧凭据 API。第二阶段不自动随第一阶段执行，避免在真实验证前不可逆删除凭据。

## 错误处理

稳定错误代码：

- `ACCOUNT_NOT_FOUND`
- `ACCOUNT_IN_USE`
- `ACCOUNT_DISABLED`
- `PLATFORM_MISMATCH`
- `CREDENTIAL_MISSING`
- `CREDENTIAL_EXPIRED`
- `LOGIN_REQUIRED`
- `LOGIN_TIMEOUT`
- `PLATFORM_RATE_LIMITED`
- `PLATFORM_RISK_CONTROL`
- `ADAPTER_UNAVAILABLE`
- `SECRET_ENCRYPT_FAILED`
- `SECRET_DECRYPT_FAILED`
- `AFFECTED_ROWS_MISMATCH`

旧数据仍显示在各业务模块中，但凭据或登录验证失败时必须显示当前失败状态和时间，不得把历史成功状态当作当前连接成功。

## 结构化日志

每次新增、更新、替换凭据、验证、登录、迁移和归档记录结构化业务日志，至少包含：

- `operation`
- `platform`
- `accountId`
- `secretType`，如适用
- `requestedCount`
- `affectedRows`
- `resultStatus`
- `errorCode`，如失败

批量迁移日志还需记录整批旧业务 ID和对应新账号 ID。日志禁止记录 Cookie、Token、API Key、StorageState、代理密码、二维码内容、完整响应正文或认证请求头。请求数量与实际影响行数不一致时记录警告并使业务失败。

## 测试与验收

### 后端自动化

- 账号 CRUD、分页和组合筛选测试。
- 多账号同平台测试。
- 每种凭据加密、替换、版本递增和永不回显测试。
- 平台与凭据类型不匹配测试。
- `PlatformAccountCredentialService` 不切换账号或凭据类型测试。
- 删除被引用账号返回 `ACCOUNT_IN_USE` 测试。
- 迁移数量、映射关系、幂等和影响行数不一致测试。
- 结构化日志字段存在且敏感值缺失测试。
- 所有消费者只读取 `PlatformAccountId` 测试。

### 前端自动化

- 平台账号、API服务和使用关系分区测试。
- `UiSearchField`、实时筛选、原生控件和键盘焦点测试。
- 凭据输入留空保持、非空替换和响应不回显测试。
- X Cookie、小红书扫码、抖音扫码和 Gemini验证状态测试。
- 被引用账号无法删除并列出使用关系测试。
- 内容运营不再提供独立凭据编辑入口测试。
- 更新并通过 `uiGate.test.js`，覆盖导航、主题、响应式、搜索和交互语义。

### 真实运行验收

必须分别证明：

1. 现有 X 发布账号迁移后仍可读取真实登录状态并完成既有发布路径的无副作用验证。
2. 现有 X 采集账号迁移后可真实采集。
3. 现有小红书账号迁移后扫码状态和发布链路正常。
4. 现有 Gemini Key迁移后真实连接测试通过。
5. 新增抖音账号完成真实扫码登录；若平台风控阻断，显示准确错误且不得宣称完成。
6. PC和手机访问同一后端时看到一致账号状态。
7. API 响应、浏览器控制台、后端日志和测试输出中不存在原始凭据。
8. 内容运营、Twitter发布和未来情报雷达都只引用统一账号 ID。

构建、单元测试和模拟登录不能代替真实平台验收。

## 实施顺序

1. 新增统一账号表、Mapper、服务、API和加密契约。
2. 实现账号中心前端与 UI 门禁。
3. 迁移 X 发布和 X 采集账号并切换消费者。
4. 迁移小红书账号并移动扫码入口。
5. 迁移 Gemini API账号并移动连接测试入口。
6. 新增抖音扫码适配器。
7. 运行针对性自动化测试和四个平台真实验收。
8. 经用户确认后，单独规划并执行旧凭据字段删除。
9. 重写并执行依赖账号中心的情报雷达实施计划。

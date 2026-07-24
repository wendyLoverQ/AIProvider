# Aimaid 运行与维护

## 架构

WPF 使用只读 SQLite 扫描器检测指定业务表的新增和修改，把未发送记录持久化到 `data-sync-state.json`，断网后指数退避重试。Spring Boot 通过 `(DeviceId, EntityType, EntityId)` 唯一键幂等写入 MySQL。React 由 Nginx 静态托管；WebRTC 视频点对点传输，Java WebSocket 只转发 SDP/ICE 信令。

按用户要求，当前版本**完全没有鉴权**（ComfyUI 控制台与本地 Agent 也不鉴权）。不要直接暴露到不可信公网；必须使用防火墙来源 IP 白名单、VPN 或 Zero Trust 访问策略限制入口。

## ComfyUI 控制台

当前架构是纯本机链路：远程服务器只提供前端静态文件，浏览器调用 `http://127.0.0.1:32145` 的 Local ComfyUI Bridge，Java 后端不接收 Prompt、工作流、参考图、任务状态或生成结果。历史仅保存在浏览器 localStorage，图片保存在本机 ComfyUI/output。

### Windows Agent

1. 安装 .NET 8 Runtime，将 `ComfyUIAgent` 发布到 Windows：`dotnet publish -c Release -r win-x64 --self-contained false`。
2. 将 `appsettings.example.json` 复制为发布目录内的 `appsettings.json`。
3. 配置 ComfyUI 地址、启动脚本、工作目录、API Format 工作流路径、本机随机 Token 和精确允许的前端 Origin。
4. 工作流必须是 ComfyUI “Save (API Format)” 导出的 JSON，不能包含界面格式的 `nodes`、`links`。
5. 启动 `ComfyUIAgent.exe`，再运行 `install-startup.ps1` 安装当前用户的 Windows 开机启动项，并注册 `aiprovider-bridge://start` 浏览器唤起协议。程序只监听 `127.0.0.1:32145`。

Bridge 未运行时，工作台不会反复弹出错误，而会显示“启动本机桥接器”。点击后浏览器询问是否打开 Local ComfyUI Bridge；用户确认后由 Windows 启动已注册的 `ComfyUIAgent.exe`。

Bridge 只允许启动配置好的脚本，不接受网页传入的命令或路径。`/api/comfy/start`、`/api/comfy/stop`、`/api/workflows/*` 和 `/comfy/*` 都要求本机 Token；CORS 只允许配置中的精确 Origin，不能使用通配符。

## 本地启动

1. 复制 `.env.example` 并设置数据库连接环境变量。
2. 后端：Java 17，运行 `mvn spring-boot:run`。
3. 前端：运行 `npm install && npm run dev`。
4. WPF 设置 `AIMAID_SERVER_URL=http://服务器地址` 和可选的 `AIMAID_DEVICE_ID`，然后启动应用。未设置 URL 时同步服务保持关闭。

## 服务器

### CCXT 加密行情网关

AI Provider 通过只监听本机回环地址的 CCXT Node.js 网关统一多个交易所的 Spot 公共行情。Spring Boot 是唯一面向前端的 API 边界；不接受 API Key，不处理账户、下单或私有数据，也不会在 CCXT 不可用时降级到其他行情实现。

部署步骤：

1. 将 `CCXTGateway` 部署到 `/opt/aimaid/ccxt-gateway`，执行 `npm ci --omit=dev --ignore-scripts`。
2. 将 `server-configs/aiprovider-ccxt.service` 安装到 `/etc/systemd/system/`。
3. 在 `/etc/aimaid/aimaid.env` 配置 `CCXT_GATEWAY_URL=http://127.0.0.1:8890`、允许的交易所和超时。
4. 执行 `systemctl daemon-reload && systemctl enable --now aiprovider-ccxt.service`，再重启 Spring Boot 服务。

公开只读接口：

- `GET /api/crypto-market/health`
- `GET /api/crypto-market/exchanges`
- `GET /api/crypto-market/symbols?exchange=okx&quote=USDT&limit=500`
- `GET /api/crypto-market/ticker?exchange=okx&symbol=BTC%2FUSDT`
- `GET /api/crypto-market/klines?exchange=okx&symbol=BTC%2FUSDT&interval=15m&limit=240`
- `GET /api/crypto-market/depth?exchange=okx&symbol=BTC%2FUSDT&limit=20`

Spring Boot 强制 `CCXT_GATEWAY_URL` 使用 `localhost`、`127.0.0.1` 或 `::1`，CCXT 网关还会按 `CCXT_ALLOWED_EXCHANGES` 拒绝任意交易所 ID。健康检查验证网关进程、CCXT 版本和启用的交易所数量。

### Foundry 只读链上工作台

服务器安装 Foundry 后，在 `/etc/aimaid/aimaid.env` 中配置固定的 `FOUNDRY_RPC_URL` 和四个工具的绝对路径。后端只通过 `ProcessBuilder` 参数数组执行白名单内的 `cast block-number`、`cast balance`、`cast code` 与 `cast call`，RPC 地址不接受前端传入。

- `GET /api/foundry/status`
- `GET /api/foundry/block-number`
- `GET /api/foundry/balance?address=0x...`
- `GET /api/foundry/code?address=0x...`
- `POST /api/foundry/call`

该接口明确不提供 `cast send`、私钥参数、`forge script`、任意命令或远程 Anvil 启停。RPC 必须使用 HTTPS；URL 可能包含供应商密钥，因此只能保存在权限为 `600` 的服务器环境文件中。

- JAR：`/opt/aiprovider/backend/app.jar`
- 前端：`/opt/aiprovider/frontend`
- 环境：`/etc/aimaid/aimaid.env`（权限 `600`）
- systemd：`aiprovider-backend.service`
- Nginx：`/etc/nginx/conf.d/aiprovider.conf`

常用命令：`systemctl status aiprovider-backend`、`journalctl -u aiprovider-backend -n 200 --no-pager`、`nginx -t`。

监控中心需要在 `/etc/aimaid/aimaid.env` 中设置 `TENCENT_CLOUD_SECRET_ID`、`TENCENT_CLOUD_SECRET_KEY`、`TENCENT_CLOUD_REGION` 与 `TENCENT_CLOUD_LIGHTHOUSE_INSTANCE_ID`。该文件必须保持 `600` 权限，不得复制到前端目录或提交到 Git。系统资源默认监控应用工作目录所在磁盘，可用 `MONITOR_DISK_PATH` 指定服务器挂载点；AI 调用明细默认保留 30 天，可通过 `MONITOR_DETAIL_RETENTION_DAYS` 增大但不能低于 30 天。

AWS 流量监控通过 CloudWatch API 读取 EC2 `NetworkIn` 与 `NetworkOut`，需要在服务器环境文件中配置 `AWS_REGION`、`AWS_INSTANCE_ID`、`AWS_ACCESS_KEY_ID` 与 `AWS_SECRET_ACCESS_KEY`。监控不会降级成本机网卡累计值；API 不可用时明确返回不可用。页面使用的 100GB 是 AWS 面向所有客户提供的每月免费公网出站额度，按账户跨服务、跨区域合并计算（中国区与 GovCloud 除外），不是单台实例独享额度。

摄像头要求可信 HTTPS。绑定域名后应使用受信任证书，并把 HTTP 重定向到 HTTPS。当前仅有 IP 时，普通 HTTP 页面不能在手机浏览器调用摄像头。跨运营商网络直连失败时还需部署 coturn，并把 TURN 地址和临时凭据加入前端 ICE 配置。

## 数据库迁移

Flyway 迁移位于 `AIProvider-back/src/main/resources/db/migration`。按当前项目约定，发布时直接执行迁移，不自动创建数据库备份；Flyway 会记录已执行版本，不应手工删除 `flyway_schema_history`。

## 远程 Codex 基础对话

左侧“远程 Codex”工作区提供新建、选择、发送与连续回复，不需要单独的访问密钥。服务器环境文件必须配置：

- `REMOTE_CODEX_COMMAND`：Codex CLI 的绝对路径。
- `REMOTE_CODEX_WORKING_DIRECTORY`：允许 Codex 工作的真实目录。

首次使用时在页面点击“开始设备登录”，按显示的 OpenAI 设备授权地址与代码完成一次登录。后端直接运行 Codex CLI；未登录、命令不可用或执行失败时明确返回错误，不切换到其他实现。远程对话按管理员要求使用 `--dangerously-bypass-approvals-and-sandbox`，新对话与续聊都拥有服务器 `ubuntu` 用户的完整文件、终端和网络权限。

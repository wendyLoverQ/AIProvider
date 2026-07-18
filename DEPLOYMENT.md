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
5. 启动 `ComfyUIAgent.exe`，再运行 `install-startup.ps1` 安装当前用户的 Windows 开机启动项。程序只监听 `127.0.0.1:32145`。

Bridge 只允许启动配置好的脚本，不接受网页传入的命令或路径。`/api/comfy/start`、`/api/comfy/stop`、`/api/workflows/*` 和 `/comfy/*` 都要求本机 Token；CORS 只允许配置中的精确 Origin，不能使用通配符。

## 本地启动

1. 复制 `.env.example` 并设置数据库连接环境变量。
2. 后端：Java 17，运行 `mvn spring-boot:run`。
3. 前端：运行 `npm install && npm run dev`。
4. WPF 设置 `AIMAID_SERVER_URL=http://服务器地址` 和可选的 `AIMAID_DEVICE_ID`，然后启动应用。未设置 URL 时同步服务保持关闭。

## 服务器

- JAR：`/opt/aiprovider/backend/app.jar`
- 前端：`/opt/aiprovider/frontend`
- 环境：`/etc/aimaid/aimaid.env`（权限 `600`）
- systemd：`aiprovider-backend.service`
- Nginx：`/etc/nginx/conf.d/aiprovider.conf`

常用命令：`systemctl status aiprovider-backend`、`journalctl -u aiprovider-backend -n 200 --no-pager`、`nginx -t`。

监控中心需要在 `/etc/aimaid/aimaid.env` 中设置 `TENCENT_CLOUD_SECRET_ID`、`TENCENT_CLOUD_SECRET_KEY`、`TENCENT_CLOUD_REGION` 与 `TENCENT_CLOUD_LIGHTHOUSE_INSTANCE_ID`。该文件必须保持 `600` 权限，不得复制到前端目录或提交到 Git。系统资源默认监控应用工作目录所在磁盘，可用 `MONITOR_DISK_PATH` 指定服务器挂载点；AI 调用明细默认保留 30 天，可通过 `MONITOR_DETAIL_RETENTION_DAYS` 增大但不能低于 30 天。

摄像头要求可信 HTTPS。绑定域名后应使用受信任证书，并把 HTTP 重定向到 HTTPS。当前仅有 IP 时，普通 HTTP 页面不能在手机浏览器调用摄像头。跨运营商网络直连失败时还需部署 coturn，并把 TURN 地址和临时凭据加入前端 ICE 配置。

## 备份与恢复

备份：`mysqldump --single-transaction ai_provider | gzip > ai_provider-$(date +%F).sql.gz`。恢复前先停服务，再执行 `gunzip -c backup.sql.gz | mysql ai_provider`。WPF 本地同时备份 `timer.db` 和 `data-sync-state.json`；复制 SQLite 前先退出 Aimaid。

Flyway 迁移位于 `AIProvider-back/src/main/resources/db/migration`。部署前备份数据库；Flyway 会记录已执行版本，不应手工删除 `flyway_schema_history`。

## 远程 Codex 基础对话

左侧“远程 Codex”工作区提供受访问密钥保护的新建、选择、发送与连续回复。服务器环境文件必须配置：

- `REMOTE_CODEX_COMMAND`：Codex CLI 的绝对路径。
- `REMOTE_CODEX_WORKING_DIRECTORY`：允许 Codex 工作的真实目录。
- `REMOTE_CODEX_ACCESS_TOKEN`：至少 16 位的独立访问密钥，只在请求头中传输，不写入前端发布包。

首次使用时在页面点击“开始设备登录”，按显示的 OpenAI 设备授权地址与代码完成一次登录。后端直接运行 Codex CLI；未登录、命令不可用或执行失败时明确返回错误，不切换到其他实现。

远程对话按管理员要求使用 `--dangerously-bypass-approvals-and-sandbox`，新对话与续聊都拥有服务器 `ubuntu` 用户的完整文件、终端和网络权限，因此必须保留独立访问密钥并限制公网入口。页面通过 Codex 官方 `account/rateLimits/read` 接口显示账户类型、真实剩余额度、窗口和重置时间。

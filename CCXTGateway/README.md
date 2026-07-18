# AIProvider CCXT Gateway

只读的本机公共行情网关。它通过 CCXT 统一不同交易所的市场目录、Ticker、OHLCV 和订单簿，再由 Spring Boot 的 `/api/crypto-market/**` 对前端提供统一响应。

## 本地运行

```bash
npm ci --ignore-scripts
npm test
npm start
```

默认监听 `127.0.0.1:8890`。可配置：

- `CCXT_GATEWAY_HOST`：必须保持为回环监听地址，默认 `127.0.0.1`。
- `CCXT_GATEWAY_PORT`：默认 `8890`。
- `CCXT_ALLOWED_EXCHANGES`：逗号分隔的交易所 ID 允许列表。
- `CCXT_EXCHANGE_TIMEOUT_MS`：单次交易所公共 API 超时，默认 10000ms。
- `CCXT_HTTPS_PROXY`：可选的 HTTP/HTTPS 出站代理；未设置时读取系统 `HTTPS_PROXY` 或 `HTTP_PROXY`。

网关不读取 API Key，也不暴露账户、订单或下单接口。

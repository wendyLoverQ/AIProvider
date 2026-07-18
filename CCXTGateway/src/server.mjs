import http from "node:http";
import ccxt from "ccxt";
import { createMarketService, InputError, parseAllowedExchanges, parseHttpsProxy } from "./market-service.mjs";

const host = process.env.CCXT_GATEWAY_HOST || "127.0.0.1";
const port = numberEnv("CCXT_GATEWAY_PORT", 8890, 1, 65535);
const timeout = numberEnv("CCXT_EXCHANGE_TIMEOUT_MS", 10000, 1000, 30000);
const httpsProxy = parseHttpsProxy(process.env.CCXT_HTTPS_PROXY || process.env.HTTPS_PROXY || process.env.HTTP_PROXY);
const service = createMarketService({ ccxt, allowedExchangeIds: parseAllowedExchanges(process.env.CCXT_ALLOWED_EXCHANGES), timeout, httpsProxy });

const server = http.createServer(async (request, response) => {
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.setHeader("Cache-Control", "no-store");
  try {
    if (request.method !== "GET") return send(response, 405, { error: "只允许 GET 请求" });
    const url = new URL(request.url, `http://${request.headers.host || `${host}:${port}`}`);
    let data;
    if (url.pathname === "/health") data = service.health();
    else if (url.pathname === "/exchanges") data = service.exchanges();
    else if (url.pathname === "/markets") data = await service.markets(url.searchParams.get("exchange"), url.searchParams.get("quote"), url.searchParams.get("limit"));
    else if (url.pathname === "/ticker") data = await service.ticker(url.searchParams.get("exchange"), url.searchParams.get("symbol"));
    else if (url.pathname === "/ohlcv") data = await service.ohlcv(url.searchParams.get("exchange"), url.searchParams.get("symbol"), url.searchParams.get("timeframe"), url.searchParams.get("limit"));
    else if (url.pathname === "/order-book") data = await service.orderBook(url.searchParams.get("exchange"), url.searchParams.get("symbol"), url.searchParams.get("limit"));
    else return send(response, 404, { error: "接口不存在" });
    send(response, 200, data);
  } catch (error) {
    const status = error instanceof InputError ? 400 : 502;
    const message = error instanceof InputError ? error.message : "交易所公共行情暂时不可用";
    send(response, status, { error: message, type: error?.constructor?.name || "Error" });
  }
});

server.listen(port, host, () => console.log(`CCXT gateway listening on http://${host}:${port}`));

function send(response, status, body) {
  response.statusCode = status;
  response.end(JSON.stringify(body));
}

function numberEnv(name, fallback, min, max) {
  const value = process.env[name] == null ? fallback : Number(process.env[name]);
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`${name} must be an integer between ${min} and ${max}`);
  return value;
}

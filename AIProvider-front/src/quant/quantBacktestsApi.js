import { readJsonResponse } from "../apiResponse";

const BACKTEST_BASE = "/api/quant/backtests";
const MARKET_DATA_BASE = "/api/quant/market-data";

async function request(base, path, options = {}, signal) {
  const response = await fetch(`${base}${path}`, { ...options, signal, headers: { "Content-Type": "application/json", ...(options.headers || {}) } });
  const payload = await readJsonResponse(response, "回测服务响应异常");
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  return payload.data;
}

const query = (params) => { const search = new URLSearchParams(); Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== "") search.set(key, String(value)); }); return `?${search.toString()}`; };
export function parsePage(data) {
  if (!data || Array.isArray(data) || !Array.isArray(data.records)
    || !Number.isSafeInteger(data.total) || data.total < 0
    || !Number.isSafeInteger(data.page) || data.page < 1
    || !Number.isSafeInteger(data.pageSize) || data.pageSize < 1) {
    throw new Error("回测服务响应格式异常");
  }
  return data;
}
export function parseDatasetList(data) { if (!Array.isArray(data)) throw new Error("历史数据集响应格式异常"); return data; }
export function parseStrategyList(data) { if (!Array.isArray(data) || data.some((item) => !item || typeof item.code !== "string" || typeof item.version !== "string")) throw new Error("策略响应格式异常"); return data; }
export function parseNonTerminalList(data) { if (!Array.isArray(data) || data.some((item) => !item || typeof item.runId !== "string" || typeof item.status !== "string")) throw new Error("非终态任务响应格式异常"); return data; }
export function parseRunDetail(data) { if (!data || typeof data !== "object" || Array.isArray(data) || typeof data.runId !== "string" || typeof data.status !== "string") throw new Error("回测详情响应格式异常"); return data; }
export function parseTradePage(data) { return parsePage(data); }
export function parseEquityResponse(data) { if (!data || typeof data !== "object" || Array.isArray(data) || typeof data.sampled !== "boolean" || !Number.isSafeInteger(data.totalPoints) || data.totalPoints < 0 || !Array.isArray(data.points)) throw new Error("权益曲线响应格式异常"); return data; }
export const fetchStrategies = (signal) => request(BACKTEST_BASE, "/strategies", {}, signal).then(parseStrategyList);
export const fetchDatasets = (signal) => request(MARKET_DATA_BASE, `/datasets${query({ status: "CONTIGUOUS", page: 1, pageSize: 100 })}`, {}, signal).then(parseDatasetList);
export const createBacktestRun = (body, signal) => request(BACKTEST_BASE, "/runs", { method: "POST", body: JSON.stringify(body) }, signal);
export const fetchRuns = (page = 1, pageSize = 20, signal) => request(BACKTEST_BASE, `/runs${query({ page, pageSize })}`, {}, signal).then(parsePage);
export const fetchNonTerminalRuns = (signal) => request(BACKTEST_BASE, "/runs/non-terminal", {}, signal).then(parseNonTerminalList);
export const fetchRunDetail = (runId, signal) => request(BACKTEST_BASE, `/runs/${encodeURIComponent(runId)}`, {}, signal).then(parseRunDetail);
export const fetchTrades = (runId, page = 1, pageSize = 100, signal) => request(BACKTEST_BASE, `/runs/${encodeURIComponent(runId)}/trades${query({ page, pageSize })}`, {}, signal).then(parseTradePage);
export const fetchEquity = (runId, maxPoints = 1200, signal) => request(BACKTEST_BASE, `/runs/${encodeURIComponent(runId)}/equity${query({ maxPoints })}`, {}, signal).then(parseEquityResponse);


import { readJsonResponse } from "../apiResponse";

const BASE = "/api/quant/backtests";

async function request(path, options = {}, signal) {
  const response = await fetch(`${BASE}${path}`, { ...options, signal, headers: { "Content-Type": "application/json", ...(options.headers || {}) } });
  const payload = await readJsonResponse(response, "回测服务响应异常");
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  return payload.data;
}

const query = (params) => { const search = new URLSearchParams(); Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== "") search.set(key, String(value)); }); return `?${search.toString()}`; };
export function parsePage(data) {
  if (!data || Array.isArray(data) || !Array.isArray(data.records)
    || !Number.isFinite(data.total) || data.total < 0
    || !Number.isFinite(data.page) || data.page < 0
    || !Number.isFinite(data.pageSize) || data.pageSize < 0) {
    throw new Error("回测服务响应格式异常");
  }
  return data;
}
export const fetchStrategies = (signal) => request("/strategies", {}, signal);
export const fetchDatasets = (signal) => request(`/market-data/datasets${query({ status: "CONTIGUOUS", page: 1, pageSize: 100 })}`, {}, signal).then(parsePage);
export const createBacktestRun = (body, signal) => request("/runs", { method: "POST", body: JSON.stringify(body) }, signal);
export const fetchRuns = (page = 1, pageSize = 20, signal) => request(`/runs${query({ page, pageSize })}`, {}, signal).then(parsePage);
export const fetchNonTerminalRuns = (signal) => request("/runs/non-terminal", {}, signal);
export const fetchRunDetail = (runId, signal) => request(`/runs/${encodeURIComponent(runId)}`, {}, signal);
export const fetchTrades = (runId, page = 1, pageSize = 100, signal) => request(`/runs/${encodeURIComponent(runId)}/trades${query({ page, pageSize })}`, {}, signal).then(parsePage);
export const fetchEquity = (runId, maxPoints = 1200, signal) => request(`/runs/${encodeURIComponent(runId)}/equity${query({ maxPoints })}`, {}, signal);


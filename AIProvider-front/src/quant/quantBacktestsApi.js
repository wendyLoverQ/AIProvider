import { readJsonResponse } from "../apiResponse";
import { normalizeDecimalString } from "./quantBacktestsFormat";

const BACKTEST_BASE = "/api/quant/backtests";
const MARKET_DATA_BASE = "/api/quant/market-data";
const ENUMS = {
  marketType: new Set(["USDM_PERPETUAL"]),
  executionProfileCode: new Set(["USDM_PERPETUAL_LONG_ONLY_1X_V1"]),
  directionMode: new Set(["LONG_ONLY"]),
  orderSizingMode: new Set(["BASE_QUANTITY"]),
  orderSide: new Set(["BUY", "SELL"]),
  positionSide: new Set(["LONG"]),
  marketFeature: new Set(["OHLCV"]),
  fillModel: new Set(["TA4J_TRADE_ON_NEXT_OPEN"]),
  transactionCostModel: new Set(["LINEAR_FEE_RATE"]),
  holdingCostModel: new Set(["ZERO"]),
  fundingCostModel: new Set(["ZERO_NOT_MODELED"]),
  liquidationModel: new Set(["NONE_NOT_MODELED"]),
  marginModel: new Set(["NONE_NOT_MODELED"]),
};

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
function strictStringArray(value, allowed, message) {
  if (
    !Array.isArray(value) ||
    !value.length ||
    value.some((item) => typeof item !== "string" || !item.trim()) ||
    new Set(value).size !== value.length ||
    value.some((item) => !allowed.has(item))
  )
    throw new Error(message);
  return value;
}
function executionFields(item, message) {
  if (
    !item ||
    typeof item !== "object" ||
    Array.isArray(item) ||
    !ENUMS.executionProfileCode.has(item.executionProfileCode) ||
    !ENUMS.directionMode.has(item.directionMode) ||
    !ENUMS.orderSizingMode.has(item.orderSizingMode)
  )
    throw new Error(message);
  return item;
}
export function parseStrategyList(data) {
  if (!Array.isArray(data)) throw new Error("策略响应格式异常");
  data.forEach((item) => {
    if (
      !item ||
      typeof item !== "object" ||
      Array.isArray(item) ||
      typeof item.code !== "string" ||
      !item.code.trim() ||
      typeof item.version !== "string" ||
      !item.version.trim()
    )
      throw new Error("策略响应格式异常");
    strictStringArray(item.supportedMarketTypes, ENUMS.marketType, "策略响应格式异常");
    strictStringArray(item.supportedExecutionProfileCodes, ENUMS.executionProfileCode, "策略响应格式异常");
    strictStringArray(item.supportedDirectionModes, ENUMS.directionMode, "策略响应格式异常");
    strictStringArray(item.requiredMarketFeatures, ENUMS.marketFeature, "策略响应格式异常");
  });
  return data;
}
export function parseExecutionProfileList(data) {
  if (!Array.isArray(data) || !data.length)
    throw new Error("执行模型数据格式异常");
  const codes = new Set();
  data.forEach((item) => {
    const stringFields = [
      "code", "name", "description", "marketType", "directionMode",
      "orderSizingMode", "entryOrderSide", "exitOrderSide", "positionSide",
      "fillModel", "transactionCostModel", "holdingCostModel",
      "fundingCostModel", "liquidationModel", "marginModel",
    ];
    if (
      !item ||
      typeof item !== "object" ||
      Array.isArray(item) ||
      stringFields.some(
        (key) => typeof item[key] !== "string" || !item[key].trim(),
      ) ||
      codes.has(item.code) ||
      !ENUMS.executionProfileCode.has(item.code) ||
      !ENUMS.marketType.has(item.marketType) ||
      !ENUMS.directionMode.has(item.directionMode) ||
      !ENUMS.orderSizingMode.has(item.orderSizingMode) ||
      !ENUMS.orderSide.has(item.entryOrderSide) ||
      !ENUMS.orderSide.has(item.exitOrderSide) ||
      !ENUMS.positionSide.has(item.positionSide) ||
      !ENUMS.fillModel.has(item.fillModel) ||
      !ENUMS.transactionCostModel.has(item.transactionCostModel) ||
      !ENUMS.holdingCostModel.has(item.holdingCostModel) ||
      !ENUMS.fundingCostModel.has(item.fundingCostModel) ||
      !ENUMS.liquidationModel.has(item.liquidationModel) ||
      !ENUMS.marginModel.has(item.marginModel) ||
      !["string", "number"].includes(typeof item.leverage) ||
      (typeof item.leverage === "string" &&
        normalizeDecimalString(item.leverage) == null) ||
      (typeof item.leverage === "number" && !Number.isFinite(item.leverage)) ||
      Number(item.leverage) <= 0
    )
      throw new Error("执行模型数据格式异常");
    strictStringArray(item.requiredMarketFeatures, ENUMS.marketFeature, "执行模型数据格式异常");
    if (
      !Array.isArray(item.limitations) ||
      !item.limitations.length ||
      item.limitations.some(
        (value) => typeof value !== "string" || !value.trim(),
      ) ||
      new Set(item.limitations).size !== item.limitations.length
    )
      throw new Error("执行模型数据格式异常");
    codes.add(item.code);
  });
  return data;
}
export function parseNonTerminalList(data) {
  if (!Array.isArray(data))
    throw new Error("非终态任务响应格式异常");
  data.forEach((item) => {
    if (typeof item?.runId !== "string" || typeof item?.status !== "string")
      throw new Error("非终态任务响应格式异常");
    executionFields(item, "非终态任务响应格式异常");
  });
  return data;
}
export function parseRunPage(data) {
  const page = parsePage(data);
  page.records.forEach((run) => {
    if (
      typeof run?.runId !== "string" ||
      typeof run?.status !== "string"
    )
      throw new Error("回测任务响应格式异常");
    executionFields(run, "回测任务响应格式异常");
  });
  return page;
}
export function parseRunDetail(data) {
  if (!data || typeof data !== "object" || Array.isArray(data) || typeof data.runId !== "string" || typeof data.status !== "string")
    throw new Error("回测详情响应格式异常");
  [
    data.initialCapital,
    data.finalEquity,
    data.totalPnl,
    data.averageExposureRatio,
    data.maximumExposureRatio,
  ].forEach((value) => {
    if (value != null && normalizeDecimalString(value) == null)
      throw new Error("回测资金字段响应格式异常");
  });
  return executionFields(data, "回测详情响应格式异常");
}
export function parseTradePage(data) {
  const page = parsePage(data);
  page.records.forEach((trade) => {
    if (
      !trade ||
      typeof trade !== "object" ||
      !ENUMS.positionSide.has(trade.positionSide) ||
      !ENUMS.orderSide.has(trade.entryOrderSide) ||
      !ENUMS.orderSide.has(trade.exitOrderSide)
    )
      throw new Error("回测成交响应格式异常");
  });
  return page;
}
export function parseEquityResponse(data) {
  if (!data || typeof data !== "object" || Array.isArray(data) || typeof data.sampled !== "boolean" || !Number.isSafeInteger(data.totalPoints) || data.totalPoints < 0 || !Array.isArray(data.points)) throw new Error("权益曲线响应格式异常");
  const capitalFields = ["equityValue", "availableCapital", "realizedPnl", "unrealizedPnl", "positionQuantity", "positionNotional", "exposureRatio"];
  data.points.forEach((point) => {
    const values = capitalFields.map((field) => point?.[field]);
    const historical = values.every((value) => value == null);
    if (!historical && values.some((value) => value == null || normalizeDecimalString(value) == null))
      throw new Error("权益曲线资金字段响应格式异常");
  });
  return data;
}
export const fetchStrategies = (signal) => request(BACKTEST_BASE, "/strategies", {}, signal).then(parseStrategyList);
export const fetchExecutionProfiles = (signal) => request(BACKTEST_BASE, "/execution-profiles", {}, signal).then(parseExecutionProfileList);
export const fetchDatasets = (signal) => request(MARKET_DATA_BASE, `/datasets${query({ status: "CONTIGUOUS", page: 1, pageSize: 100 })}`, {}, signal).then(parseDatasetList);
export const createBacktestRun = (body, signal) => request(BACKTEST_BASE, "/runs", { method: "POST", body: JSON.stringify(body) }, signal);
export const fetchRuns = (page = 1, pageSize = 20, signal) => request(BACKTEST_BASE, `/runs${query({ page, pageSize })}`, {}, signal).then(parseRunPage);
export const fetchNonTerminalRuns = (signal) => request(BACKTEST_BASE, "/runs/non-terminal", {}, signal).then(parseNonTerminalList);
export const fetchRunDetail = (runId, signal) => request(BACKTEST_BASE, `/runs/${encodeURIComponent(runId)}`, {}, signal).then(parseRunDetail);
export const fetchTrades = (runId, page = 1, pageSize = 100, signal) => request(BACKTEST_BASE, `/runs/${encodeURIComponent(runId)}/trades${query({ page, pageSize })}`, {}, signal).then(parseTradePage);
export const fetchEquity = (runId, maxPoints = 1200, signal) => request(BACKTEST_BASE, `/runs/${encodeURIComponent(runId)}/equity${query({ maxPoints })}`, {}, signal).then(parseEquityResponse);


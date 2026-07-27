import { readJsonResponse } from "../apiResponse";
import { normalizeDecimalString } from "./quantBacktestsFormat";

const EXPERIMENT_BASE = "/api/quant/backtests/experiments";
export const EXPERIMENT_STATUSES = new Set([
  "QUEUED",
  "RUNNING",
  "COMPLETED",
  "COMPLETED_WITH_FAILURES",
  "FAILED",
]);
export const DISPATCH_STATUSES = new Set([
  "PENDING",
  "CLAIMED",
  "DISPATCHED",
  "FAILED",
]);
const SEGMENT_TYPES = new Set(["TRAIN", "VALIDATION"]);
const SEGMENT_STATUSES = new Set([
  "NOT_CREATED",
  "QUEUED",
  "LOADING_SNAPSHOT",
  "RUNNING_ENGINE",
  "PERSISTING",
  "COMPLETED",
  "FAILED",
]);
const TERMINAL_EXPERIMENT_STATUSES = new Set([
  "COMPLETED",
  "COMPLETED_WITH_FAILURES",
  "FAILED",
]);
const EXECUTION_PROFILE_CODES = new Set(["USDM_PERPETUAL_LONG_ONLY_1X_V1"]);
const DIRECTION_MODES = new Set(["LONG_ONLY"]);
const ORDER_SIZING_MODES = new Set(["BASE_QUANTITY"]);

function invalid(message) {
  throw new Error(message);
}

function object(value, message) {
  if (!value || typeof value !== "object" || Array.isArray(value))
    invalid(message);
  return value;
}

function nonEmpty(value, message) {
  if (typeof value !== "string" || !value.trim()) invalid(message);
  return value;
}

function safeCount(value, message) {
  if (!Number.isSafeInteger(value) || value < 0) invalid(message);
  return value;
}

function instant(value, message) {
  if (typeof value !== "string" || !Number.isFinite(new Date(value).getTime()))
    invalid(message);
  return value;
}

function nullableInstant(value, message) {
  return value == null ? null : instant(value, message);
}

function decimal(value, message, { nullable = true } = {}) {
  if (value == null && nullable) return null;
  if (
    !["string", "number"].includes(typeof value) ||
    (typeof value === "number" && !Number.isFinite(value)) ||
    normalizeDecimalString(value) == null
  )
    invalid(message);
  return value;
}

function percent(value, message) {
  const parsed =
    typeof value === "number"
      ? value
      : typeof value === "string" && normalizeDecimalString(value) != null
        ? Number(value)
        : Number.NaN;
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 100) invalid(message);
  return value;
}

function parseIntegerObject(value, message, arrays) {
  const source = object(value, message);
  const entries = Object.entries(source);
  if (
    entries.some(
      ([key, item]) =>
        !key ||
        (arrays
          ? !Array.isArray(item) ||
            item.some((entry) => !Number.isSafeInteger(entry))
          : !Number.isSafeInteger(item)),
    )
  )
    invalid(message);
  return source;
}

export function parseMetrics(value) {
  if (value == null) return null;
  const metrics = object(value, "实验指标响应格式异常");
  [
    "totalReturnRatio",
    "maximumDrawdownRatio",
    "profitFactor",
    "netProfit",
    "winRate",
    "totalFees",
    "buyAndHoldReturnRatio",
    "averageTradeReturnRatio",
  ].forEach((key) => decimal(metrics[key], `实验指标 ${key} 格式异常`));
  if (metrics.tradeCount != null)
    safeCount(metrics.tradeCount, "实验指标 tradeCount 格式异常");
  return metrics;
}

export function parseSegment(value, expectedType) {
  const segment = object(value, "实验区间响应格式异常");
  if (
    !SEGMENT_TYPES.has(segment.segmentType) ||
    segment.segmentType !== expectedType
  )
    invalid("实验区间类型异常");
  nonEmpty(segment.runId, "实验区间缺少 runId");
  if (!SEGMENT_STATUSES.has(segment.status)) invalid("实验区间状态异常");
  percent(segment.progressPercent, "实验区间进度格式异常");
  if (segment.barCount != null)
    safeCount(segment.barCount, "实验区间 barCount 格式异常");
  if (segment.tradeCount != null)
    safeCount(segment.tradeCount, "实验区间 tradeCount 格式异常");
  if (segment.errorCode != null && typeof segment.errorCode !== "string")
    invalid("实验区间错误码格式异常");
  if (segment.errorMessage != null && typeof segment.errorMessage !== "string")
    invalid("实验区间错误信息格式异常");
  nullableInstant(segment.startedAt, "实验区间 startedAt 格式异常");
  nullableInstant(segment.finishedAt, "实验区间 finishedAt 格式异常");
  parseMetrics(segment.metrics);
  return segment;
}

export function parseCandidate(value) {
  const candidate = object(value, "实验候选响应格式异常");
  nonEmpty(candidate.candidateId, "实验候选缺少 candidateId");
  safeCount(candidate.candidateIndex, "实验候选序号格式异常");
  parseIntegerObject(candidate.parameters, "实验候选参数格式异常", false);
  if (!DISPATCH_STATUSES.has(candidate.dispatchStatus))
    invalid("实验候选派发状态异常");
  parseSegment(candidate.training, "TRAIN");
  parseSegment(candidate.validation, "VALIDATION");
  return candidate;
}

function parsePage(value, recordParser, message) {
  const page = object(value, message);
  if (!Array.isArray(page.records)) invalid(message);
  page.records.forEach(recordParser);
  safeCount(page.total, message);
  if (
    !Number.isSafeInteger(page.page) ||
    page.page < 1 ||
    !Number.isSafeInteger(page.pageSize) ||
    page.pageSize < 1
  )
    invalid(message);
  return page;
}

export function parseCandidatePage(value) {
  return parsePage(value, parseCandidate, "实验候选分页响应格式异常");
}

export function parseExperimentSummary(value) {
  const summary = object(value, "参数实验响应格式异常");
  nonEmpty(summary.experimentId, "参数实验缺少 experimentId");
  if (
    !EXECUTION_PROFILE_CODES.has(summary.executionProfileCode) ||
    !DIRECTION_MODES.has(summary.directionMode) ||
    !ORDER_SIZING_MODES.has(summary.orderSizingMode)
  )
    invalid("参数实验执行上下文格式异常");
  safeCount(summary.candidateCount, "参数实验 candidateCount 格式异常");
  [
    "pendingCandidates",
    "activeCandidates",
    "completedCandidates",
    "failedCandidates",
    "completedLegs",
    "failedLegs",
  ].forEach((key) => safeCount(summary[key], `参数实验 ${key} 格式异常`));
  if (!EXPERIMENT_STATUSES.has(summary.status)) invalid("参数实验状态异常");
  percent(summary.progressPercent, "参数实验进度格式异常");
  const progress = Number(summary.progressPercent);
  if (
    TERMINAL_EXPERIMENT_STATUSES.has(summary.status)
      ? progress !== 100
      : progress === 100
  )
    invalid("参数实验状态与进度不一致");
  if (
    [
      summary.pendingCandidates,
      summary.activeCandidates,
      summary.completedCandidates,
      summary.failedCandidates,
    ].some((count) => count > summary.candidateCount)
  )
    invalid("参数实验候选计数越界");
  const maximumLegs = summary.candidateCount * 2;
  if (
    summary.completedLegs > maximumLegs ||
    summary.failedLegs > maximumLegs
  )
    invalid("参数实验任务计数越界");
  parseIntegerObject(
    summary.parameterGrid,
    "参数实验 parameterGrid 格式异常",
    true,
  );
  [
    "trainingStartOpenTimeInclusive",
    "trainingEndOpenTimeExclusive",
    "validationStartOpenTimeInclusive",
    "validationEndOpenTimeExclusive",
  ].forEach((key) => instant(summary[key], `参数实验 ${key} 格式异常`));
  nullableInstant(summary.createdAt, "参数实验 createdAt 格式异常");
  nullableInstant(summary.startedAt, "参数实验 startedAt 格式异常");
  nullableInstant(summary.finishedAt, "参数实验 finishedAt 格式异常");
  if (summary.totalLegs != null)
    safeCount(summary.totalLegs, "参数实验 totalLegs 格式异常");
  return summary;
}

export function parseExperimentPage(value) {
  return parsePage(value, parseExperimentSummary, "参数实验分页响应格式异常");
}

export function parseCreateResponse(value) {
  const response = object(value, "创建参数实验响应格式异常");
  nonEmpty(response.experimentId, "创建参数实验响应缺少 experimentId");
  safeCount(response.candidateCount, "创建参数实验 candidateCount 格式异常");
  safeCount(response.totalLegs, "创建参数实验 totalLegs 格式异常");
  return response;
}

async function request(path, options = {}, signal) {
  const response = await fetch(path, {
    ...options,
    signal,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const payload = await readJsonResponse(response, "参数实验服务响应异常");
  if (!response.ok || payload.code !== 200)
    throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  return payload.data;
}

function query(filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "")
      params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : "";
}

export const createExperiment = (body, signal) =>
  request(
    EXPERIMENT_BASE,
    {
      method: "POST",
      body: JSON.stringify(body),
    },
    signal,
  ).then(parseCreateResponse);

export const fetchExperiments = (filters, signal) =>
  request(`${EXPERIMENT_BASE}${query(filters)}`, {}, signal).then(
    parseExperimentPage,
  );

export const fetchExperiment = (experimentId, signal) =>
  request(
    `${EXPERIMENT_BASE}/${encodeURIComponent(experimentId)}`,
    {},
    signal,
  ).then(parseExperimentSummary);

export const fetchExperimentCandidates = (experimentId, filters, signal) =>
  request(
    `${EXPERIMENT_BASE}/${encodeURIComponent(experimentId)}/candidates${query(filters)}`,
    {},
    signal,
  ).then(parseCandidatePage);

import { readJsonResponse } from "../apiResponse";
import { normalizeDecimalString } from "./quantBacktestsFormat";

const BASE = "/api/quant/backtests/walk-forward-studies";
export const WALK_FORWARD_STUDY_STATUSES = new Set([
  "QUEUED",
  "RUNNING",
  "COMPLETED",
  "COMPLETED_WITH_FAILURES",
  "FAILED",
]);
export const WALK_FORWARD_FOLD_STATUSES = new Set([
  "PENDING",
  "CREATING_EXPERIMENT",
  "WAITING_EXPERIMENT",
  "COMPLETED",
  "FAILED",
]);
export const WALK_FORWARD_SELECTION_METRICS = new Set([
  "TRAIN_TOTAL_RETURN_RATIO",
  "TRAIN_PROFIT_FACTOR",
  "TRAIN_NET_PROFIT",
  "TRAIN_WIN_RATE",
  "TRAIN_MAXIMUM_DRAWDOWN_RATIO",
]);
const EXPERIMENT_STATUSES = new Set([
  "QUEUED",
  "RUNNING",
  "COMPLETED",
  "COMPLETED_WITH_FAILURES",
  "FAILED",
]);
const METRICS = [
  "totalReturnRatio",
  "maximumDrawdownRatio",
  "profitFactor",
  "netProfit",
  "winRate",
  "totalFees",
  "buyAndHoldReturnRatio",
  "averageTradeReturnRatio",
];

function fail(message) { throw new Error(message); }
function object(value, message) {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail(message);
  return value;
}
function string(value, message) {
  if (typeof value !== "string" || !value.trim()) fail(message);
  return value;
}
function required(value, message) { return value === undefined ? fail(message) : value; }
function safeInt(value, message, { positive = false } = {}) {
  if (!Number.isSafeInteger(value) || (positive ? value <= 0 : value < 0)) fail(message);
  return value;
}
function instant(value, message) {
  if (typeof value !== "string" || !Number.isFinite(new Date(value).getTime())) fail(message);
  return value;
}
function nullableInstant(value, message) { return value == null ? null : instant(value, message); }
function decimal(value, message, { nullable = true } = {}) {
  if (value == null && nullable) return null;
  if (!["string", "number"].includes(typeof value) || (typeof value === "number" && !Number.isFinite(value)) || normalizeDecimalString(value) == null) fail(message);
  return value;
}
function percent(value, message) {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n) || n < 0 || n > 100) fail(message);
  return value;
}
function integerObject(value, message) {
  const source = object(value, message);
  Object.entries(source).forEach(([key, values]) => {
    if (!key || !Array.isArray(values) || !values.length || values.some((item) => !Number.isSafeInteger(item))) fail(message);
  });
  return source;
}
function metrics(value, message, { nullable = true } = {}) {
  if (value == null && nullable) return null;
  const source = object(value, message);
  METRICS.forEach((key) => decimal(source[key], `${message}.${key}`));
  if (source.tradeCount != null) safeInt(source.tradeCount, `${message}.tradeCount`);
  return source;
}

function validateSummary(value) {
  const summary = object(value, "Walk-forward Study 响应格式异常");
  string(required(summary.studyId, "Study 缺少 studyId"), "Study 缺少 studyId");
  safeInt(required(summary.datasetId, "Study 缺少 datasetId"), "Study datasetId 格式异常", { positive: true });
  ["provider", "marketType", "dataType", "symbol", "intervalCode", "strategyCode", "strategyVersion"].forEach((key) => string(required(summary[key], `Study 缺少 ${key}`), `Study ${key} 格式异常`));
  if (summary.windowMode !== "ROLLING") fail("Study windowMode 必须为 ROLLING");
  integerObject(summary.parameterGrid, "Study parameterGrid 格式异常");
  ["studyStartOpenTimeInclusive", "studyEndOpenTimeExclusive"].forEach((key) => instant(required(summary[key], `Study 缺少 ${key}`), `Study ${key} 格式异常`));
  if (new Date(summary.studyStartOpenTimeInclusive).getTime() >= new Date(summary.studyEndOpenTimeExclusive).getTime()) fail("Study 时间范围异常");
  ["trainingBars", "validationBars", "stepBars"].forEach((key) => safeInt(required(summary[key], `Study 缺少 ${key}`), `Study ${key} 格式异常`, { positive: true }));
  if (summary.stepBars !== summary.validationBars) fail("Study stepBars 必须等于 validationBars");
  ["foldCount", "candidateCountPerFold", "totalChildRuns"].forEach((key) => safeInt(required(summary[key], `Study 缺少 ${key}`), `Study ${key} 格式异常`, { positive: true }));
  if (!Number.isSafeInteger(summary.foldCount * summary.candidateCountPerFold * 2) || summary.totalChildRuns !== summary.foldCount * summary.candidateCountPerFold * 2) fail("Study totalChildRuns 数量不一致");
  if (!WALK_FORWARD_SELECTION_METRICS.has(summary.selectionMetric)) fail("Study selectionMetric 非法");
  safeInt(required(summary.minimumTrainTrades, "Study 缺少 minimumTrainTrades"), "Study minimumTrainTrades 格式异常");
  decimal(required(summary.orderAmount, "Study 缺少 orderAmount"), "Study orderAmount 格式异常", { nullable: false });
  decimal(required(summary.feeRate, "Study 缺少 feeRate"), "Study feeRate 格式异常", { nullable: false });
  if (summary.forceCloseAtEnd !== true) fail("Study forceCloseAtEnd 必须为 true");
  if (!WALK_FORWARD_STUDY_STATUSES.has(summary.status)) fail("Study status 非法");
  percent(summary.progressPercent, "Study progressPercent 格式异常");
  const terminal = summary.status === "COMPLETED" || summary.status === "COMPLETED_WITH_FAILURES" || summary.status === "FAILED";
  if (terminal ? Number(summary.progressPercent) !== 100 : Number(summary.progressPercent) >= 100) fail("Study 状态与 progressPercent 不一致");
  ["pendingFolds", "activeFolds", "completedFolds", "failedFolds", "selectedParameterChanges", "successfulOosFolds", "totalOosTradeCount"].forEach((key) => { const value = required(summary[key], `Study 缺少 ${key}`); if (value != null) safeInt(value, `Study ${key} 格式异常`); });
  ["pendingFolds", "activeFolds", "completedFolds", "failedFolds"].forEach((key) => { if (summary[key] > summary.foldCount) fail("Study Fold 计数越界"); });
  ["totalOosFees", "totalOosReturnRatio"].forEach((key) => decimal(required(summary[key], `Study 缺少 ${key}`), `Study ${key} 格式异常`));
  const hasOosGaps = required(summary.hasOosGaps, "Study 缺少 hasOosGaps");
  if (hasOosGaps != null && typeof hasOosGaps !== "boolean") fail("Study hasOosGaps 格式异常");
  ["errorCode", "errorMessage"].forEach((key) => { if (summary[key] != null && typeof summary[key] !== "string") fail(`Study ${key} 格式异常`); });
  ["createdAt", "startedAt", "finishedAt", "updatedAt"].forEach((key) => nullableInstant(summary[key], `Study ${key} 格式异常`));
  return summary;
}

export function parseWalkForwardStudySummary(value) { return validateSummary(value); }

export function parseParameterFrequency(value) {
  const item = object(value, "Study 参数频率响应格式异常");
  integerObject(item.parameters, "Study 参数频率 parameters 格式异常");
  safeInt(required(item.selectedCount, "Study 参数频率缺少 selectedCount"), "Study 参数频率 selectedCount 格式异常", { positive: true });
  safeInt(required(item.firstFoldIndex, "Study 参数频率缺少 firstFoldIndex"), "Study 参数频率 firstFoldIndex 格式异常");
  safeInt(required(item.lastFoldIndex, "Study 参数频率缺少 lastFoldIndex"), "Study 参数频率 lastFoldIndex 格式异常");
  if (item.lastFoldIndex < item.firstFoldIndex) fail("Study 参数频率 Fold 顺序异常");
  return item;
}

export function parseWalkForwardStudyDetail(value) {
  const detail = object(value, "Walk-forward Study 详情响应格式异常");
  return { ...detail, summary: parseWalkForwardStudySummary(required(detail.summary, "Study 详情缺少 summary")), parameterFrequencies: (required(detail.parameterFrequencies, "Study 详情缺少 parameterFrequencies")).map(parseParameterFrequency) };
}

export function parseWalkForwardCreateResponse(value) {
  const response = object(value, "Walk-forward 创建响应格式异常");
  string(required(response.studyId, "创建响应缺少 studyId"), "创建响应 studyId 格式异常");
  ["foldCount", "candidateCountPerFold", "totalChildRuns"].forEach((key) => safeInt(required(response[key], `创建响应缺少 ${key}`), `创建响应 ${key} 格式异常`, { positive: true }));
  if (response.totalChildRuns !== response.foldCount * response.candidateCountPerFold * 2) fail("创建响应 totalChildRuns 数量不一致");
  return response;
}

export function parseWalkForwardStudyPage(value) {
  const page = object(value, "Walk-forward Study 分页响应格式异常");
  if (!Array.isArray(page.records)) fail("Walk-forward Study 分页 records 格式异常");
  page.records = page.records.map(parseWalkForwardStudySummary);
  safeInt(required(page.total, "Study 分页缺少 total"), "Study 分页 total 格式异常");
  safeInt(required(page.page, "Study 分页缺少 page"), "Study 分页 page 格式异常", { positive: true });
  safeInt(required(page.pageSize, "Study 分页缺少 pageSize"), "Study 分页 pageSize 格式异常", { positive: true });
  return page;
}

export function parseWalkForwardFold(value) {
  const fold = object(value, "Walk-forward Fold 响应格式异常");
  string(required(fold.foldId, "Fold 缺少 foldId"), "Fold foldId 格式异常");
  safeInt(required(fold.foldIndex, "Fold 缺少 foldIndex"), "Fold foldIndex 格式异常");
  ["trainingStartOpenTimeInclusive", "trainingEndOpenTimeExclusive", "validationStartOpenTimeInclusive", "validationEndOpenTimeExclusive"].forEach((key) => instant(required(fold[key], `Fold 缺少 ${key}`), `Fold ${key} 格式异常`));
  if (new Date(fold.trainingEndOpenTimeExclusive).getTime() !== new Date(fold.validationStartOpenTimeInclusive).getTime()) fail("Fold TRAIN 与 VALIDATION 不相邻");
  if (!(new Date(fold.trainingStartOpenTimeInclusive) < new Date(fold.trainingEndOpenTimeExclusive) && new Date(fold.validationStartOpenTimeInclusive) < new Date(fold.validationEndOpenTimeExclusive))) fail("Fold 时间范围异常");
  string(required(fold.experimentId, "Fold 缺少 experimentId"), "Fold experimentId 格式异常");
  if (!WALK_FORWARD_FOLD_STATUSES.has(fold.status)) fail("Fold status 非法");
  percent(fold.progressPercent, "Fold progressPercent 格式异常");
  if (fold.status === "COMPLETED" ? Number(fold.progressPercent) !== 100 : Number(fold.progressPercent) >= 100) fail("Fold 状态与 progressPercent 不一致");
  if (fold.experimentStatus != null && !EXPERIMENT_STATUSES.has(fold.experimentStatus)) fail("Fold experimentStatus 非法");
  const selected = fold.status === "COMPLETED";
  if (selected) {
    string(required(fold.selectedCandidateId, "已完成 Fold 缺少 selectedCandidateId"), "Fold selectedCandidateId 格式异常");
    integerObject(required(fold.selectedParameters, "已完成 Fold 缺少 selectedParameters"), "Fold selectedParameters 格式异常");
    ["selectedTrainingRunId", "selectedValidationRunId"].forEach((key) => string(required(fold[key], `已完成 Fold 缺少 ${key}`), `Fold ${key} 格式异常`));
    decimal(required(fold.selectionMetricValue, "已完成 Fold 缺少 selectionMetricValue"), "Fold selectionMetricValue 格式异常", { nullable: false });
    metrics(required(fold.trainingMetrics, "已完成 Fold 缺少 trainingMetrics"), "Fold trainingMetrics 格式异常", { nullable: false });
    metrics(required(fold.validationMetrics, "已完成 Fold 缺少 validationMetrics"), "Fold validationMetrics 格式异常", { nullable: false });
  } else if (fold.selectedCandidateId != null || fold.selectedParameters != null || fold.selectedTrainingRunId != null || fold.selectedValidationRunId != null) fail("未完成 Fold 不得携带 selected 字段");
  ["errorCode", "errorMessage"].forEach((key) => { if (fold[key] != null && typeof fold[key] !== "string") fail(`Fold ${key} 格式异常`); });
  ["startedAt", "finishedAt", "updatedAt"].forEach((key) => nullableInstant(fold[key], `Fold ${key} 格式异常`));
  return fold;
}

export function parseWalkForwardFoldPage(value) {
  const page = object(value, "Walk-forward Fold 分页响应格式异常");
  if (!Array.isArray(page.records)) fail("Fold 分页 records 格式异常");
  page.records = page.records.map(parseWalkForwardFold);
  safeInt(required(page.total, "Fold 分页缺少 total"), "Fold 分页 total 格式异常");
  safeInt(required(page.page, "Fold 分页缺少 page"), "Fold 分页 page 格式异常", { positive: true });
  safeInt(required(page.pageSize, "Fold 分页缺少 pageSize"), "Fold 分页 pageSize 格式异常", { positive: true });
  return page;
}

export function parseWalkForwardOosPoint(value) {
  const point = object(value, "OOS 点响应格式异常");
  safeInt(required(point.pointIndex, "OOS 点缺少 pointIndex"), "OOS pointIndex 格式异常");
  safeInt(required(point.foldIndex, "OOS 点缺少 foldIndex"), "OOS foldIndex 格式异常");
  instant(required(point.openTime, "OOS 点缺少 openTime"), "OOS openTime 格式异常");
  const index = decimal(required(point.indexRatio, "OOS 点缺少 indexRatio"), "OOS indexRatio 格式异常", { nullable: false });
  if (Number(index) <= 0) fail("OOS indexRatio 必须大于 0");
  const drawdown = decimal(required(point.drawdownRatio, "OOS 点缺少 drawdownRatio"), "OOS drawdownRatio 格式异常", { nullable: false });
  if (Number(drawdown) < 0 || Number(drawdown) > 1) fail("OOS drawdownRatio 范围异常");
  return point;
}

export function parseWalkForwardOosEquity(value) {
  const equity = object(value, "OOS 指数响应格式异常");
  if (typeof equity.sampled !== "boolean") fail("OOS sampled 格式异常");
  ["totalPoints", "successfulFolds", "missingFolds"].forEach((key) => safeInt(required(equity[key], `OOS 缺少 ${key}`), `OOS ${key} 格式异常`));
  if (typeof equity.hasGaps !== "boolean") fail("OOS hasGaps 格式异常");
  if ((equity.missingFolds > 0) !== equity.hasGaps) fail("OOS hasGaps 与 missingFolds 不一致");
  if (!Array.isArray(equity.points) || equity.points.length > equity.totalPoints) fail("OOS points 格式异常");
  equity.points = equity.points.map(parseWalkForwardOosPoint);
  equity.points.forEach((point, index, points) => { if (index > 0 && (point.pointIndex <= points[index - 1].pointIndex || new Date(point.openTime) <= new Date(points[index - 1].openTime))) fail("OOS 点必须严格递增"); });
  if (!equity.sampled && equity.points.length !== equity.totalPoints) fail("非抽样 OOS 点数量不一致");
  ["totalReturnRatio", "maximumDrawdownRatio"].forEach((key) => decimal(required(equity[key], `OOS 缺少 ${key}`), `OOS ${key} 格式异常`));
  return equity;
}

async function request(path, options = {}, signal) {
  const response = await fetch(path, { ...options, signal, headers: { "Content-Type": "application/json", ...(options.headers || {}) } });
  const payload = await readJsonResponse(response, "Walk-forward 服务响应异常");
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  return payload.data;
}
function query(values) { const params = new URLSearchParams(); Object.entries(values || {}).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== "") params.set(key, String(value)); }); const text = params.toString(); return text ? `?${text}` : ""; }
export const createWalkForwardStudy = (body, signal) => request(BASE, { method: "POST", body: JSON.stringify(body) }, signal).then(parseWalkForwardCreateResponse);
export const fetchWalkForwardStudies = (filters, signal) => request(`${BASE}${query(filters)}`, {}, signal).then(parseWalkForwardStudyPage);
export const fetchWalkForwardStudy = (studyId, signal) => request(`${BASE}/${encodeURIComponent(studyId)}`, {}, signal).then(parseWalkForwardStudyDetail);
export const fetchWalkForwardFolds = (studyId, filters, signal) => request(`${BASE}/${encodeURIComponent(studyId)}/folds${query(filters)}`, {}, signal).then(parseWalkForwardFoldPage);
export const fetchWalkForwardOosEquity = (studyId, limit = 1200, signal) => request(`${BASE}/${encodeURIComponent(studyId)}/oos-equity${query({ limit })}`, {}, signal).then(parseWalkForwardOosEquity);

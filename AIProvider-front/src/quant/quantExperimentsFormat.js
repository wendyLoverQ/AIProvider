import {
  calculateExpectedBars,
  decimalSubtract,
  intervalDurationMs,
  normalizeDecimalString,
  utcInstantToLocalInput,
} from "./quantBacktestsFormat";

export const EXPERIMENT_STATUS_LABELS = {
  QUEUED: "排队中",
  RUNNING: "运行中",
  COMPLETED: "已完成",
  COMPLETED_WITH_FAILURES: "已完成（部分失败）",
  FAILED: "失败",
};

export const DISPATCH_STATUS_LABELS = {
  PENDING: "待派发",
  CLAIMED: "正在派发",
  DISPATCHED: "已派发",
  FAILED: "派发失败",
};

export const CANDIDATE_SORTS = [
  ["CANDIDATE_INDEX", "序号"],
  ["TRAIN_TOTAL_RETURN_RATIO", "TRAIN 总收益率"],
  ["VALIDATION_TOTAL_RETURN_RATIO", "VALIDATION 总收益率"],
  ["TRAIN_MAXIMUM_DRAWDOWN_RATIO", "TRAIN 最大回撤"],
  ["VALIDATION_MAXIMUM_DRAWDOWN_RATIO", "VALIDATION 最大回撤"],
  ["TRAIN_PROFIT_FACTOR", "TRAIN Profit Factor"],
  ["VALIDATION_PROFIT_FACTOR", "VALIDATION Profit Factor"],
  ["TRAIN_NET_PROFIT", "TRAIN 净利润"],
  ["VALIDATION_NET_PROFIT", "VALIDATION 净利润"],
  ["TRAIN_WIN_RATE", "TRAIN 胜率"],
  ["VALIDATION_WIN_RATE", "VALIDATION 胜率"],
  ["TRAIN_TRADE_COUNT", "TRAIN 交易数"],
  ["VALIDATION_TRADE_COUNT", "VALIDATION 交易数"],
];

export function formatExperimentStatus(status) {
  return EXPERIMENT_STATUS_LABELS[status] || "—";
}

export function formatDispatchStatus(status) {
  return DISPATCH_STATUS_LABELS[status] || "—";
}

export function parseIntegerCsv(value, parameter = {}) {
  const tokens = String(value ?? "").split(",");
  if (!tokens.length || tokens.some((token) => token.trim() === ""))
    return { error: "候选值不能包含空项" };
  const values = [];
  const seen = new Set();
  for (const token of tokens) {
    const text = token.trim();
    if (!/^-?(?:0|[1-9]\d*)$/.test(text))
      return { error: "候选值只接受十进制整数" };
    const parsed = Number(text);
    if (!Number.isSafeInteger(parsed))
      return { error: "候选值超出安全整数范围" };
    if (seen.has(parsed)) return { error: "候选值不能重复" };
    if (parameter.minValue != null && parsed < parameter.minValue)
      return { error: `${parameter.name} 不能小于 ${parameter.minValue}` };
    if (parameter.maxValue != null && parsed > parameter.maxValue)
      return { error: `${parameter.name} 不能大于 ${parameter.maxValue}` };
    seen.add(parsed);
    values.push(parsed);
  }
  if (values.length < 1 || values.length > 20)
    return { error: "每个参数需要 1～20 个候选值" };
  return { values };
}

export function calculateCandidateCount(parameterGrid) {
  if (
    !parameterGrid ||
    typeof parameterGrid !== "object" ||
    Array.isArray(parameterGrid)
  )
    return null;
  const groups = Object.values(parameterGrid);
  if (
    !groups.length ||
    groups.some((values) => !Array.isArray(values) || values.length < 1)
  )
    return null;
  let count = 1;
  for (const values of groups) {
    if (count > Number.MAX_SAFE_INTEGER / values.length) return null;
    count *= values.length;
  }
  return Number.isSafeInteger(count) ? count : null;
}

export function validateExperimentRanges(
  ranges,
  dataset,
) {
  const duration = intervalDurationMs(dataset?.interval);
  const values = [
    ranges.trainingStart,
    ranges.trainingEnd,
    ranges.validationStart,
    ranges.validationEnd,
  ].map((value) => new Date(value).getTime());
  if (!duration || values.some((value) => !Number.isFinite(value)))
    return { error: "请完整填写有效的 TRAIN 与 VALIDATION 时间" };
  if (values.some((value) => value % duration !== 0))
    return { error: "四个时间都必须对齐数据集周期" };
  if (!(
    values[0] < values[1] &&
    values[1] <= values[2] &&
    values[2] < values[3]
  ))
    return {
      error:
        "时间必须满足 TRAIN 开始 < TRAIN 结束 ≤ VALIDATION 开始 < VALIDATION 结束",
    };
  const coverageStart = new Date(dataset.earliestOpenTime).getTime();
  const coverageEnd = new Date(dataset.latestOpenTime).getTime() + duration;
  if (values[0] < coverageStart || values[3] > coverageEnd)
    return { error: "TRAIN 与 VALIDATION 必须处于数据集覆盖范围内" };
  const trainingBars = calculateExpectedBars(
    ranges.trainingStart,
    ranges.trainingEnd,
    dataset.interval,
  );
  const validationBars = calculateExpectedBars(
    ranges.validationStart,
    ranges.validationEnd,
    dataset.interval,
  );
  if (trainingBars < 1 || validationBars < 1)
    return { error: "TRAIN 与 VALIDATION 都至少需要 1 根 K 线" };
  return { trainingBars, validationBars };
}

export function splitDataset7030(dataset, minimumRequiredBars = 1) {
  const duration = intervalDurationMs(dataset?.interval);
  const count = Number(dataset?.candleCount);
  const start = new Date(dataset?.earliestOpenTime).getTime();
  if (
    !duration ||
    !Number.isSafeInteger(count) ||
    count < minimumRequiredBars * 2 ||
    !Number.isFinite(start)
  ) {
    return {
      error: `当前数据集无法切分为两个至少 ${minimumRequiredBars} 根 K 线的区间`,
    };
  }
  let trainingCount = Math.floor(count * 0.7);
  trainingCount = Math.max(
    minimumRequiredBars,
    Math.min(trainingCount, count - minimumRequiredBars),
  );
  const boundary = start + trainingCount * duration;
  const end = start + count * duration;
  return {
    trainingStart: utcInstantToLocalInput(new Date(start).toISOString()),
    trainingEnd: utcInstantToLocalInput(new Date(boundary).toISOString()),
    validationStart: utcInstantToLocalInput(new Date(boundary).toISOString()),
    validationEnd: utcInstantToLocalInput(new Date(end).toISOString()),
    trainingBars: trainingCount,
    validationBars: count - trainingCount,
  };
}

export function metricDifference(validation, training) {
  if (
    normalizeDecimalString(validation) == null ||
    normalizeDecimalString(training) == null
  )
    return null;
  return decimalSubtract(validation, training);
}

export function orderedParameterEntries(parameters, strategy) {
  const source = parameters && typeof parameters === "object" ? parameters : {};
  const names = (strategy?.parameters || []).map((parameter) => parameter.name);
  const known = names.filter((name) =>
    Object.prototype.hasOwnProperty.call(source, name),
  );
  const unknown = Object.keys(source)
    .filter((name) => !names.includes(name))
    .sort();
  return [...known, ...unknown].map((name) => [name, source[name]]);
}

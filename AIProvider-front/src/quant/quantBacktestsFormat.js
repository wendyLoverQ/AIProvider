const INTERVAL_MS = {
  "1m": 60_000, "3m": 180_000, "5m": 300_000, "15m": 900_000,
  "30m": 1_800_000, "1h": 3_600_000, "2h": 7_200_000, "4h": 14_400_000,
  "6h": 21_600_000, "12h": 43_200_000, "1d": 86_400_000,
  "1w": 604_800_000,
};

export function intervalDurationMs(interval) {
  return INTERVAL_MS[interval] ?? null;
}

export function calculateExpectedBars(start, end, interval) {
  const duration = intervalDurationMs(interval);
  const startMs = new Date(start).getTime();
  const endMs = new Date(end).getTime();
  if (!duration || !Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) return null;
  return Math.floor((endMs - startMs) / duration);
}

export function toUtcIso(localValue) {
  const date = new Date(localValue);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function formatRatio(value, digits = 2) {
  if (value == null || value === "" || !Number.isFinite(Number(value))) return "—";
  const number = Number(value) * 100;
  return `${number >= 0 ? "+" : ""}${number.toFixed(digits)}%`;
}

export function formatDecimal(value, digits = 4) {
  if (value == null || value === "") return "—";
  const number = Number(value);
  return Number.isFinite(number) ? number.toLocaleString("zh-CN", { maximumFractionDigits: digits }) : "—";
}

export function formatInstant(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("zh-CN", { hour12: false });
}

export const RUN_STATUS_LABELS = {
  QUEUED: "排队中", LOADING_SNAPSHOT: "读取数据", RUNNING_ENGINE: "回测计算",
  PERSISTING: "保存结果", COMPLETED: "已完成", FAILED: "失败",
};

export function formatRunStatus(status) { return RUN_STATUS_LABELS[status] || status || "—"; }

export function intervalLabel(interval) { return interval || "—"; }


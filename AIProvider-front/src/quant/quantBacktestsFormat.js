const INTERVAL_MS = {
  "1m": 60_000, M1: 60_000, "3m": 180_000, M3: 180_000, "5m": 300_000, M5: 300_000,
  "15m": 900_000, M15: 900_000, "30m": 1_800_000, M30: 1_800_000,
  "1h": 3_600_000, H1: 3_600_000, "2h": 7_200_000, H2: 7_200_000,
  "4h": 14_400_000, H4: 14_400_000, "6h": 21_600_000, H6: 21_600_000,
  "12h": 43_200_000, H12: 43_200_000, "1d": 86_400_000, D1: 86_400_000,
  "1w": 604_800_000, W1: 604_800_000,
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

const pad = (value) => String(value).padStart(2, "0");
export function utcInstantToLocalInput(instant) {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function normalizeDecimalString(value, { maxIntegerDigits = 38, maxFractionDigits = 18 } = {}) {
  const text = String(value ?? "");
  if (!/^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(text)) return null;
  const [integer, fraction = ""] = text.split(".");
  if (integer.length > maxIntegerDigits || fraction.length > maxFractionDigits) return null;
  return `${integer}${fraction ? `.${fraction}` : ""}`;
}

export function compareDecimalStrings(left, right) {
  const a = normalizeDecimalString(left); const b = normalizeDecimalString(right);
  if (a == null || b == null) return null;
  const [ai, af = ""] = a.split("."); const [bi, bf = ""] = b.split(".");
  if (ai.length !== bi.length) return ai.length > bi.length ? 1 : -1;
  if (ai !== bi) return ai > bi ? 1 : -1;
  const fractionLength = Math.max(af.length, bf.length);
  const ap = af.padEnd(fractionLength, "0"); const bp = bf.padEnd(fractionLength, "0");
  return ap === bp ? 0 : ap > bp ? 1 : -1;
}

export function isPositiveDecimal(value) { return compareDecimalStrings(value, "0") === 1; }

export function intervalCode(interval) {
  const codes = { M1: "1m", M3: "3m", M5: "5m", M15: "15m", M30: "30m", H1: "1h", H2: "2h", H4: "4h", H6: "6h", H12: "12h", D1: "1d", W1: "1w" };
  return codes[interval] || interval || "—";
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

export function formatRunStatus(status) { return RUN_STATUS_LABELS[status] || (status ? `${status} · 未知状态` : "—"); }

export function intervalLabel(interval) { return intervalCode(interval); }


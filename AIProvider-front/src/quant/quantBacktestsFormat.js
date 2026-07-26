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
  if (!/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(text)) return null;
  const [integer, fraction = ""] = text.split(".");
  if (integer.replace(/^-/, "").length > maxIntegerDigits || fraction.length > maxFractionDigits) return null;
  return `${integer}${fraction ? `.${fraction}` : ""}`;
}

export function decimalSubtract(left, right) {
  const a = normalizeDecimalString(left); const b = normalizeDecimalString(right);
  if (a == null || b == null) return null;
  const [ai, af = ""] = a.split("."); const [bi, bf = ""] = b.split(".");
  const scale = Math.max(af.length, bf.length); const av = BigInt(`${ai}${af.padEnd(scale, "0")}`); const bv = BigInt(`${bi}${bf.padEnd(scale, "0")}`); const result = av - bv; const sign = result < 0n ? "-" : ""; const digits = (result < 0n ? -result : result).toString().padStart(scale + 1, "0");
  return `${sign}${scale ? `${digits.slice(0, -scale)}.${digits.slice(-scale)}` : digits}`.replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "");
}

export function compareDecimalStrings(left, right) {
  const a = normalizeDecimalString(left); const b = normalizeDecimalString(right);
  if (a == null || b == null) return null;
  const as = a.startsWith("-") ? -1 : 1; const bs = b.startsWith("-") ? -1 : 1; if (as !== bs) return as > bs ? 1 : -1;
  const [ai, af = ""] = a.replace(/^-/, "").split("."); const [bi, bf = ""] = b.replace(/^-/, "").split(".");
  if (as < 0) { const absolute = compareDecimalStrings(ai + (af ? `.${af}` : ""), bi + (bf ? `.${bf}` : "")); return absolute == null ? null : -absolute; }
  if (ai.length !== bi.length) return ai.length > bi.length ? 1 : -1;
  if (ai !== bi) return ai > bi ? 1 : -1;
  const fractionLength = Math.max(af.length, bf.length);
  const ap = af.padEnd(fractionLength, "0"); const bp = bf.padEnd(fractionLength, "0");
  return ap === bp ? 0 : ap > bp ? 1 : -1;
}

export function isPositiveDecimal(value) { return compareDecimalStrings(value, "0") === 1; }

function decimalParts(value) { const normalized = normalizeDecimalString(value); if (normalized == null) return null; const negative = normalized.startsWith("-"); const [integer, fraction = ""] = normalized.replace(/^-/, "").split("."); return { negative, integer, fraction }; }
export function formatDecimalString(value, digits = 4) {
  const parts = decimalParts(value); if (!parts) return "—"; const rounded = roundDecimal(parts.integer, parts.fraction, digits); const grouped = rounded.integer.replace(/\B(?=(\d{3})+(?!\d))/g, ","); return `${parts.negative ? "-" : ""}${grouped}${rounded.fraction ? `.${rounded.fraction}` : ""}`;
}
export function formatRatioString(value, digits = 2) { const ratio = decimalSubtract(value, "0"); if (ratio == null) return "—"; const percent = multiplyBy100(ratio); const parts = decimalParts(percent); if (!parts) return "—"; const rounded = roundDecimal(parts.integer, parts.fraction, digits); const grouped = rounded.integer.replace(/\B(?=(\d{3})+(?!\d))/g, ","); const fraction = rounded.fraction.padEnd(digits, "0"); return `${parts.negative ? "-" : "+"}${grouped}${digits > 0 ? `.${fraction}` : ""}%`; }
function multiplyBy100(value) { const parts = decimalParts(value); if (!parts) return null; const absolute = parts.fraction.length <= 2 ? `${parts.integer}${parts.fraction.padEnd(2, "0")}` : `${parts.integer}${parts.fraction.slice(0, 2)}.${parts.fraction.slice(2)}`; const [integer, fraction = ""] = absolute.split("."); const cleanInteger = integer.replace(/^0+(?=\d)/, ""); return `${parts.negative ? "-" : ""}${cleanInteger}${fraction ? `.${fraction}` : ""}`; }
function roundDecimal(integer, fraction, digits) { const negative=integer.startsWith("-"); const absolute=integer.replace(/^-/, ""); if (digits <= 0) return { integer: `${negative ? "-" : ""}${fraction[0] >= "5" ? (BigInt(absolute) + 1n).toString() : absolute}`, fraction: "" }; const kept=fraction.slice(0,digits).padEnd(digits,"0"); if(fraction.length>digits&&fraction[digits]>="5"){const scaled=BigInt(`${absolute}${kept}`)+1n;const text=scaled.toString().padStart(absolute.length+digits,"0");return {integer:`${negative?"-":""}${text.slice(0,-digits)}`,fraction:text.slice(-digits).replace(/0+$/,"")};} return {integer:`${negative?"-":""}${absolute}`,fraction:kept.replace(/0+$/,"")}; }

export function validateEquityResponse(equity) {
  if (!equity || typeof equity.sampled !== "boolean" || !Number.isSafeInteger(equity.totalPoints) || equity.totalPoints < 0 || !Array.isArray(equity.points)) return false;
  const { points, totalPoints } = equity;
  if (!points.length) return totalPoints === 0;
  if (points[0].pointIndex !== 0 || points[points.length - 1].pointIndex !== totalPoints - 1 || points.some((point, index) => !point || !Number.isSafeInteger(point.pointIndex) || point.pointIndex < 0 || (index > 0 && point.pointIndex <= points[index - 1].pointIndex) || !Number.isFinite(new Date(point.openTime).getTime()) || normalizeDecimalString(point.equityRatio) == null || normalizeDecimalString(point.drawdownRatio) == null || compareDecimalStrings(point.drawdownRatio, "0") < 0)) return false;
  if (!equity.sampled) return points.length === totalPoints && points.every((point, index) => point.pointIndex === index);
  return points.length < totalPoints;
}

export function intervalCode(interval) {
  const codes = { M1: "1m", M3: "3m", M5: "5m", M15: "15m", M30: "30m", H1: "1h", H2: "2h", H4: "4h", H6: "6h", H12: "12h", D1: "1d", W1: "1w" };
  return codes[interval] || interval || "—";
}

export function formatRatio(value, digits = 2) {
  return formatRatioString(value, digits);
}

export function formatDecimal(value, digits = 4) {
  return formatDecimalString(value, digits);
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


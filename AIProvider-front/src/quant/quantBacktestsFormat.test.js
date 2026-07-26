import { describe, expect, it } from "vitest";
import { calculateExpectedBars, compareDecimalStrings, formatRatio, formatRunStatus, intervalCode, intervalDurationMs, normalizeDecimalString, toUtcIso, utcInstantToLocalInput } from "./quantBacktestsFormat";
import { parsePage } from "./quantBacktestsApi";

describe("quant backtest formatting", () => {
  it("formats ratios and status without inventing null values", () => {
    expect(formatRatio("0.12")).toBe("+12.00%");
    expect(formatRatio(null)).toBe("—");
    expect(formatRunStatus("RUNNING_ENGINE")).toBe("回测计算");
  });
  it("calculates fixed-period bars and rejects unknown intervals", () => {
    expect(intervalDurationMs("15m")).toBe(900000);
    expect(calculateExpectedBars("2025-01-01T00:00:00Z", "2025-01-01T01:00:00Z", "15m")).toBe(4);
    expect(intervalDurationMs("13m")).toBeNull();
    expect(calculateExpectedBars("2025-01-01T00:00:00Z", "2025-01-01T01:00:00Z", "13m")).toBeNull();
    expect(intervalCode("M15")).toBe("15m");
  });
  it("converts datetime-local input to an absolute ISO instant", () => {
    const result = toUtcIso("2025-01-01T00:00");
    expect(result).toMatch(/^202[45]-\d{2}-\d{2}T\d{2}:\d{2}:00\.000Z$/);
    expect(new Date(result).toISOString()).toBe(result);
    expect(utcInstantToLocalInput("2025-01-01T00:00:00.000Z")).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });
  it("compares bounded decimal strings without floating point conversion", () => {
    expect(normalizeDecimalString("1.000000000000000000")).toBe("1.000000000000000000");
    expect(normalizeDecimalString("1e-3")).toBeNull();
    expect(normalizeDecimalString(`${"9".repeat(39)}`)).toBeNull();
    expect(compareDecimalStrings("0.01", "0.010000")).toBe(0);
    expect(compareDecimalStrings("0.0101", "0.01")).toBe(1);
  });
  it("accepts only the records page protocol", () => {
    expect(parsePage({ records: [], total: 0, page: 1, pageSize: 20 }).records).toEqual([]);
    expect(() => parsePage({ items: [], total: 0, page: 1, pageSize: 20 })).toThrow("回测服务响应格式异常");
  });
});

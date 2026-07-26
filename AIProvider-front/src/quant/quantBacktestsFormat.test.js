import { describe, expect, it } from "vitest";
import { calculateExpectedBars, compareDecimalStrings, decimalSubtract, formatDecimalString, formatRatio, formatRatioString, formatRunStatus, intervalCode, intervalDurationMs, normalizeDecimalString, toUtcIso, utcInstantToLocalInput, validateEquityResponse } from "./quantBacktestsFormat";
import { parsePage } from "./quantBacktestsApi";

describe("quant backtest formatting", () => {
  it("formats ratios and status without inventing null values", () => {
    expect(formatRatio("0.12")).toBe("+12.00%");
    expect(formatRatioString("-0.125")).toBe("-12.50%");
    expect(decimalSubtract("1.12", "1")).toBe("0.12");
    expect(formatDecimalString("12345678901234567890.123456789012345678")).toBe("12,345,678,901,234,567,890.1235");
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
    expect(normalizeDecimalString(`${"9".repeat(20)}.${"1".repeat(18)}`, { maxIntegerDigits: 20, maxFractionDigits: 18 })).not.toBeNull();
    expect(normalizeDecimalString(`${"9".repeat(21)}`, { maxIntegerDigits: 20, maxFractionDigits: 18 })).toBeNull();
    expect(normalizeDecimalString("1e-3", { maxIntegerDigits: 1, maxFractionDigits: 18 })).toBeNull();
  });
  it("accepts sampled equity points with explicit boundary indexes", () => {
    expect(validateEquityResponse({ sampled: true, totalPoints: 100, points: [
      { pointIndex: 0, openTime: "2025-01-01T00:00:00Z", equityRatio: "1", drawdownRatio: "0" },
      { pointIndex: 50, openTime: "2025-01-01T12:30:00Z", equityRatio: "1.12", drawdownRatio: "0.02" },
      { pointIndex: 99, openTime: "2025-01-02T00:00:00Z", equityRatio: "1.1", drawdownRatio: "0.01" },
    ] })).toBe(true);
    expect(validateEquityResponse({ sampled: true, totalPoints: 100, points: [{ pointIndex: 1, openTime: "2025-01-01T00:00:00Z", equityRatio: "1", drawdownRatio: "0" }] })).toBe(false);
  });
  it("accepts only the records page protocol", () => {
    expect(parsePage({ records: [], total: 0, page: 1, pageSize: 20 }).records).toEqual([]);
    expect(() => parsePage({ items: [], total: 0, page: 1, pageSize: 20 })).toThrow("回测服务响应格式异常");
  });
});

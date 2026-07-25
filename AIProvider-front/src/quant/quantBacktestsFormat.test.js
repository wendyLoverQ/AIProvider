import { describe, expect, it } from "vitest";
import { calculateExpectedBars, formatRatio, formatRunStatus, intervalDurationMs, toUtcIso } from "./quantBacktestsFormat";

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
  });
  it("converts datetime-local input to an absolute ISO instant", () => {
    const result = toUtcIso("2025-01-01T00:00");
    expect(result).toMatch(/^202[45]-\d{2}-\d{2}T\d{2}:\d{2}:00\.000Z$/);
    expect(new Date(result).toISOString()).toBe(result);
  });
});

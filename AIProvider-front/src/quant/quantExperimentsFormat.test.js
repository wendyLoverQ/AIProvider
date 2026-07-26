import { describe, expect, it } from "vitest";
import {
  calculateCandidateCount,
  formatDispatchStatus,
  formatExperimentStatus,
  metricDifference,
  orderedParameterEntries,
  parseIntegerCsv,
  splitDataset7030,
  validateExperimentRanges,
} from "./quantExperimentsFormat";

const parameter = { name: "period", minValue: 2, maxValue: 100 };
const dataset = {
  interval: "H1",
  candleCount: 100,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-05T03:00:00Z",
};

describe("parameter grid parsing", () => {
  it("trims CSV integers and preserves order without mutating the source", () => {
    const input = " 21, 7,14 ";
    expect(parseIntegerCsv(input, parameter)).toEqual({ values: [21, 7, 14] });
    expect(input).toBe(" 21, 7,14 ");
  });

  it.each([
    ["7,,14", "空项"],
    ["7,14.5", "整数"],
    ["7,7", "重复"],
    ["1", "不能小于"],
    ["101", "不能大于"],
  ])("rejects %s", (value, message) => {
    expect(parseIntegerCsv(value, parameter).error).toContain(message);
  });

  it("calculates 1, 64, and 65 candidates and rejects safe integer overflow", () => {
    expect(calculateCandidateCount({ a: [1] })).toBe(1);
    expect(
      calculateCandidateCount({ a: Array(8).fill(0), b: Array(8).fill(0) }),
    ).toBe(64);
    expect(
      calculateCandidateCount({ a: Array(13).fill(0), b: Array(5).fill(0) }),
    ).toBe(65);
    const huge = Object.fromEntries(
      Array.from({ length: 14 }, (_, index) => [
        `p${index}`,
        Array(20).fill(0),
      ]),
    );
    expect(calculateCandidateCount(huge)).toBeNull();
  });
});

describe("experiment ranges", () => {
  it("validates ordered, aligned in-coverage ranges", () => {
    expect(
      validateExperimentRanges(
        {
          trainingStart: "2025-01-01T00:00:00Z",
          trainingEnd: "2025-01-03T22:00:00Z",
          validationStart: "2025-01-03T22:00:00Z",
          validationEnd: "2025-01-05T04:00:00Z",
        },
        dataset,
        20,
      ),
    ).toEqual({ trainingBars: 70, validationBars: 30 });
  });

  it("rejects overlap and interval misalignment", () => {
    expect(
      validateExperimentRanges(
        {
          trainingStart: "2025-01-01T00:00:00Z",
          trainingEnd: "2025-01-03T22:00:00Z",
          validationStart: "2025-01-03T21:00:00Z",
          validationEnd: "2025-01-05T04:00:00Z",
        },
        dataset,
        1,
      ).error,
    ).toContain("TRAIN 开始");
    expect(
      validateExperimentRanges(
        {
          trainingStart: "2025-01-01T00:30:00Z",
          trainingEnd: "2025-01-03T22:00:00Z",
          validationStart: "2025-01-03T22:00:00Z",
          validationEnd: "2025-01-05T04:00:00Z",
        },
        dataset,
        1,
      ).error,
    ).toContain("对齐");
  });

  it("splits only when called and refuses insufficient data", () => {
    const split = splitDataset7030(dataset, 20);
    expect(split.trainingBars).toBe(70);
    expect(split.validationBars).toBe(30);
    expect(split.trainingEnd).toBe(split.validationStart);
    expect(
      splitDataset7030({ ...dataset, candleCount: 30 }, 20).error,
    ).toContain("无法切分");
  });

  it("validates manual ranges by known coverage rules instead of claiming the whole grid is valid", () => {
    expect(
      validateExperimentRanges(
        {
          trainingStart: "2025-01-01T00:00:00Z",
          trainingEnd: "2025-01-01T01:00:00Z",
          validationStart: "2025-01-01T01:00:00Z",
          validationEnd: "2025-01-01T02:00:00Z",
        },
        dataset,
        100,
      ),
    ).toEqual({ trainingBars: 1, validationBars: 1 });
  });
});

describe("experiment formatting", () => {
  it("uses decimal string subtraction and frozen status labels", () => {
    expect(metricDifference("0.100000000000000001", "0.1")).toBe(
      "0.000000000000000001",
    );
    expect(formatExperimentStatus("COMPLETED_WITH_FAILURES")).toBe(
      "已完成（部分失败）",
    );
    expect(formatDispatchStatus("CLAIMED")).toBe("正在派发");
  });

  it("orders known strategy parameters first and unknown keys afterward", () => {
    const strategy = { parameters: [{ name: "b" }, { name: "a" }] };
    expect(
      orderedParameterEntries({ a: 1, unknown: 3, b: 2 }, strategy),
    ).toEqual([
      ["b", 2],
      ["a", 1],
      ["unknown", 3],
    ]);
  });
});

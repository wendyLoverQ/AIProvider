import { describe, expect, it } from "vitest";
import { calculateWalkForwardWindow } from "./quantExperimentsFormat";

const dataset = {
  interval: "1h",
  earliestOpenTime: "2024-01-01T00:00:00.000Z",
  latestOpenTime: "2024-01-10T23:00:00.000Z",
};

describe("calculateWalkForwardWindow", () => {
  it("calculates complete rolling folds without truncation", () => {
    const result = calculateWalkForwardWindow({
      dataset,
      studyStart: "2024-01-01T00:00:00.000Z",
      studyEnd: "2024-01-10T00:00:00.000Z",
      trainingBars: 48,
      validationBars: 24,
      candidateCount: 4,
    });
    expect(result).toMatchObject({ totalBars: 216, foldCount: 7, stepBars: 24, totalChildRuns: 56 });
  });

  it("rejects a remainder that would be silently truncated", () => {
    const result = calculateWalkForwardWindow({ dataset, studyStart: "2024-01-01T00:00:00.000Z", studyEnd: "2024-01-10T00:00:00.000Z", trainingBars: 50, validationBars: 24, candidateCount: 4 });
    expect(result.error).toContain("整除");
  });
});

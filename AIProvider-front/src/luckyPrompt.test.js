import { describe, expect, it } from "vitest";
import { buildLuckyPrompts } from "./luckyPrompt";

describe("buildLuckyPrompts", () => {
  it("uses weighted asset fragments and removes duplicates", () => {
    const values = [0, 0, 0, 0, 0, 0]; let index = 0;
    const result = buildLuckyPrompts("masterpiece", "bad anatomy", [
      { prompt: "masterpiece, black pantyhose, bedroom", negativePrompt: "bad anatomy, extra fingers", weight: 8 },
      { prompt: "soft lighting", negativePrompt: "watermark", weight: 1 },
    ], () => values[index++] ?? 0);
    expect(result.positivePrompt.split(", ").filter((item) => item === "masterpiece")).toHaveLength(1);
    expect(result.positivePrompt).toContain("black pantyhose");
    expect(result.negativePrompt.split(", ").filter((item) => item === "bad anatomy")).toHaveLength(1);
  });

  it("rejects an empty asset Prompt pool", () => {
    expect(() => buildLuckyPrompts("base", "", [])).toThrow(/还没有/);
  });
});

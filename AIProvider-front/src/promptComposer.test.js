import { describe, expect, it } from "vitest";
import { buildPromptTranslations, composePrompts, emptySelectedOptions, extractNegativeExtra, extractPositiveExtra, matchSelectedOptionsFromPrompt, normalizePrompt, normalizeSelectedOptions, relatedNegativePromptsForPositive, restorePromptFromChinese, translatePromptToChinese } from "./promptComposer";

describe("structured Prompt composer", () => {
  const options = [
    { id: "quality", category: "Quality", allowMultiple: true, positivePrompt: "best quality, detailed", negativePrompt: "low quality" },
    { id: "solo", category: "Character", allowMultiple: true, positivePrompt: "solo", negativePrompt: "crowd" },
    { id: "girl", category: "Character", allowMultiple: true, positivePrompt: "1girl", negativePrompt: "male" },
    { id: "center", category: "Composition", allowMultiple: true, positivePrompt: "centered composition", negativePrompt: "bad composition" },
  ];

  it("uses the fixed positive and negative order and removes repeated terms", () => {
    const selected = { ...emptySelectedOptions(), Character: ["solo", "girl"], Composition: ["center"], Quality: ["quality"] };
    expect(composePrompts(selected, options, "low quality, blurry", "detailed, rim light", "blurry, jpeg artifacts")).toEqual({
      positivePrompt: "solo, 1girl, centered composition, best quality, detailed, rim light",
      negativePrompt: "low quality, blurry, crowd, male, bad composition, jpeg artifacts",
    });
  });

  it("normalizes commas, spaces, duplicate case and single selections", () => {
    expect(normalizePrompt(" A ,,  b ", "a, C")).toBe("A, b, C");
    expect(normalizeSelectedOptions({ Expression: ["smile", "cry"], Quality: ["x", "x", null] })).toEqual({
      ...emptySelectedOptions(), Expression: ["smile", "cry"], Quality: ["x"],
    });
  });

  it("maps only catalog terms that have related negative prompts", () => {
    expect(relatedNegativePromptsForPositive("white, standing", [
      { positivePrompt: "white", negativePrompt: "dark" },
      { positivePrompt: "standing", negativePrompt: "" },
    ])).toEqual(["dark"]);
  });

  it("moves unmatched manual comma terms into positive extra", () => {
    expect(extractPositiveExtra("best quality, detailed, standing, abc", { Quality: ["quality"] }, options)).toBe("standing, abc");
  });

  it("moves unmatched negative terms into negative extra", () => {
    expect(extractNegativeExtra("low quality, blurry, custom defect", { Quality: ["quality"] }, options, "low quality, blurry")).toBe("custom defect");
  });

  it("rebuilds structured selections from exact comma-separated prompt terms", () => {
    expect(matchSelectedOptionsFromPrompt("best quality, detailed, solo, custom", options)).toMatchObject({ Quality: ["quality"], Character: ["solo"] });
  });

  it("shows mapped terms in Chinese and restores canonical English", () => {
    const translations = buildPromptTranslations([{ name: "站立", positivePrompt: "standing" }, { name: "白色服装", positivePrompt: "white clothes" }]);
    const display = translatePromptToChinese("standing, custom term, white clothes", translations);
    expect(display).toBe("站立, custom term, 白色服装");
    expect(restorePromptFromChinese(display, translations)).toBe("standing, custom term, white clothes");
  });

});

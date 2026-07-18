// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { applyUiTheme, DEFAULT_UI_THEME, isUiThemePreset, readUiTheme, saveUiTheme, UI_THEME_PRESETS, UI_THEME_STORAGE_KEY } from "./uiTheme";

const rgb = (hex) => [1, 3, 5].map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16));
const themeDistance = (left, right) => {
  const keys = ["--bg-page", "--bg-card", "--accent-primary"];
  return Math.sqrt(keys.flatMap((key) => rgb(left[key]).map((value, index) => value - rgb(right[key])[index])).reduce((sum, value) => sum + value ** 2, 0));
};
const luminance = (hex) => rgb(hex).map((value) => value / 255).map((value) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4).reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
const contrast = (foreground, background) => {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (lighter + 0.05) / (darker + 0.05);
};

describe("global UI theme", () => {
  afterEach(() => { localStorage.clear(); document.documentElement.removeAttribute("style"); });

  it("merges saved values with the global defaults", () => {
    localStorage.setItem(UI_THEME_STORAGE_KEY, JSON.stringify({ "--accent-primary": "#abcdef" }));
    expect(readUiTheme()).toMatchObject({ "--accent-primary": "#abcdef", "--bg-page": DEFAULT_UI_THEME["--bg-page"] });
  });

  it("persists and applies changes to the document root", () => {
    const theme = { ...DEFAULT_UI_THEME, "--bg-page": "#101010", "--card-radius": "20px" };
    saveUiTheme(theme);
    expect(JSON.parse(localStorage.getItem(UI_THEME_STORAGE_KEY))["--card-radius"]).toBe("20px");
    expect(document.documentElement.style.getPropertyValue("--bg-page")).toBe("#101010");
    applyUiTheme(DEFAULT_UI_THEME);
    expect(document.documentElement.style.getPropertyValue("--bg-page")).toBe(DEFAULT_UI_THEME["--bg-page"]);
  });

  it("provides ten complete, distinct and readable selectable presets", () => {
    expect(UI_THEME_PRESETS).toHaveLength(10);
    for (const preset of UI_THEME_PRESETS) {
      expect(Object.keys(preset.theme).sort()).toEqual(Object.keys(DEFAULT_UI_THEME).sort());
      expect(isUiThemePreset({ ...preset.theme }, preset)).toBe(true);
      expect(isUiThemePreset({ ...preset.theme, "--bg-page": "#000000" }, preset)).toBe(preset.theme["--bg-page"] === "#000000");
      expect(contrast(preset.theme["--text-muted"], preset.theme["--bg-card"])).toBeGreaterThanOrEqual(4.5);
    }
    for (let index = 0; index < UI_THEME_PRESETS.length; index += 1) {
      for (let peer = index + 1; peer < UI_THEME_PRESETS.length; peer += 1) {
        expect(themeDistance(UI_THEME_PRESETS[index].theme, UI_THEME_PRESETS[peer].theme)).toBeGreaterThanOrEqual(50);
      }
    }
  });
});

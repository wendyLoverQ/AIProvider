// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { applyUiTheme, DEFAULT_UI_THEME, isUiThemePreset, readUiTheme, saveUiTheme, UI_THEME_PRESETS, UI_THEME_STORAGE_KEY } from "./uiTheme";

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

  it("provides seven complete selectable presets", () => {
    expect(UI_THEME_PRESETS).toHaveLength(15);
    for (const preset of UI_THEME_PRESETS) {
      expect(Object.keys(preset.theme).sort()).toEqual(Object.keys(DEFAULT_UI_THEME).sort());
      expect(isUiThemePreset({ ...preset.theme }, preset)).toBe(true);
      expect(isUiThemePreset({ ...preset.theme, "--bg-page": "#000000" }, preset)).toBe(preset.theme["--bg-page"] === "#000000");
    }
  });
});

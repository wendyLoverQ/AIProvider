import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const srcDir = path.dirname(fileURLToPath(import.meta.url));
const read = (name) => readFileSync(path.join(srcDir, name), "utf8");
const jsxFiles = readdirSync(srcDir).filter((name) => name.endsWith(".jsx"));

describe("UI release gate", () => {
  it("routes every search input through UiSearchField", () => {
    const violations = jsxFiles.flatMap((name) => {
      if (name === "UiSearchField.jsx") return [];
      const source = read(name);
      return source.split(/\r?\n/).flatMap((line, index) =>
        /<input\b[^>]*(?:aria-label|placeholder)="[^"]*搜索/i.test(line)
          ? [`${name}:${index + 1}`]
          : []);
    });
    expect(violations, `搜索框必须复用 UiSearchField：${violations.join(", ")}`).toEqual([]);
    expect(read("UiSearchField.jsx")).toMatch(/<input[\s\S]*<MagnifyingGlass/);
    expect(read("UiSearchField.css")).toMatch(/\.ui-search-field\s*>\s*svg[\s\S]*right:/);
    expect(read("CryptoMarket.jsx")).not.toContain('type="submit">搜索');
  });

  it("keeps every primary workspace on semantic theme tokens", () => {
    const theme = read("SemanticTheme.css");
    ["video-editor-shell", "foundry-workbench", "system-settings-shell", "twitter-publisher", "content-operations-center", "prompt-scheme-list", "maid-panel", "universe-toolbar"].forEach((root) => {
      expect(theme, `${root} 未接入全局语义主题`).toContain(root);
    });
    const copy = read("UiControl.jsx");
    ["视频编辑", "我的女仆", "链上工具", "Twitter", "系统设置"].forEach((label) => expect(copy).toContain(label));
  });

  it("keeps mobile navigation reachable and touch-safe", () => {
    const app = read("App.jsx");
    const css = read("App.css");
    expect(app).toContain('const MOBILE_NAV = [{ key: "home"');
    expect(app).toContain('scrollIntoView({ behavior: "smooth"');
    expect(css).toMatch(/\.bottom-nav[\s\S]*overflow-x:\s*auto/);
    expect(css).toMatch(/\.bottom-nav \.nav-button[\s\S]*min-height:\s*44px/);
  });

  it("does not nest the Prompt favorite action inside another button", () => {
    const prompt = read("PromptManager.jsx");
    expect(prompt).toContain('className={`prompt-scheme-row');
    expect(prompt).not.toContain('<span className={`prompt-default-star');
    expect(prompt).toContain('<button type="button" className={`prompt-default-star');
  });

  it("keeps Remote Codex reachable from the primary navigation", () => {
    const app = read("App.jsx");
    const remoteCodex = read("RemoteCodex.jsx");
    expect(app).toContain('{ key: "remoteCodex"');
    expect(app).toContain('remoteCodex: "/remote-codex"');
    expect(remoteCodex).toContain('aria-label="新建远程 Codex 对话"');
    expect(remoteCodex).toMatch(/aria-label=\{[\s\S]*"插话"\s*:\s*"发送消息"[\s\S]*\}/);
    expect(remoteCodex).not.toMatch(/<div[^>]+onClick=/);
  });
});

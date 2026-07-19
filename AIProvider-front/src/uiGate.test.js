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
    const tokens = read("uiTheme.js");
    ["video-editor-shell", "foundry-workbench", "system-settings-shell", "file-transfer-page", "twitter-publisher", "content-operations-center", "prompt-scheme-list", "maid-panel", "universe-toolbar"].forEach((root) => {
      expect(theme, `${root} 未接入全局语义主题`).toContain(root);
    });
    const copy = read("UiControl.jsx");
    ["视频编辑", "我的女仆", "链上工具", "Twitter", "系统设置"].forEach((label) => expect(copy).toContain(label));
    expect(tokens).toContain('"--text-muted-readable"');
    expect(tokens).toContain('"--border-interactive"');
  });

  it("keeps file transfer reachable, semantic, and horizontally contained", () => {
    const app = read("App.jsx");
    const page = read("FileTransfer.jsx");
    const css = read("FileTransfer.css");
    expect(app).toContain('{ key: "fileTransfer"');
    expect(app).toContain('fileTransfer: "/file-transfer"');
    expect(app).toContain("<FileTransfer />");
    expect(page).toContain('type="file"');
    expect(page).toContain('<progress max="100"');
    expect(page).toContain('type="checkbox"');
    expect(page).toContain('/download-batch');
    expect(page).toContain('/preview/');
    expect(page).toContain('aria-label="中转文本"');
    expect(page).toContain('type="submit"');
    expect(page).not.toMatch(/<div[^>]+onClick=/);
    expect(css).toContain("var(--bg-surface)");
    expect(css).toMatch(/\.file-transfer-page\{[^}]*min-width:0[^}]*overflow:hidden/);
    expect(css).toMatch(/\.file-transfer-table-wrap\{[^}]*overflow:auto/);
    expect(css).not.toMatch(/td:first-child\{[^}]*display:flex/);
    expect(css).toMatch(/\.file-transfer-file-cell\{[^}]*display:flex/);
    expect(css).toMatch(/\.file-transfer-text-card\{[^}]*grid-template-columns/);
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
    expect(prompt).toContain('className="prompt-scheme-select"');
    expect(prompt).not.toContain('role="button"');
    expect(prompt).not.toContain('<span className={`prompt-default-star');
    expect(prompt).toContain('<button type="button" className={`prompt-default-star');
  });

  it("keeps the desktop shell labeled, grouped, and workshop-safe", () => {
    const app = read("App.jsx");
    const shell = read("DesktopShell.css");
    const codexTheme = read("CodexTheme.css");
    expect(app).toContain('const NAV_GROUPS = [');
    expect(app).toContain('aria-label="一级工作区"');
    expect(app).toContain('aria-current={active ? "page" : undefined}');
    expect(app).toContain('<div className="neural-shell shell-expanded">');
    expect(app).toContain('<aside className="rail rail-expanded">');
    expect(app).not.toContain('const compactShell = view === "workshop"');
    expect(app).toContain('RELEASE_VERSION.frontend');
    expect(app).toContain('RELEASE_VERSION.backend');
    expect(shell).toContain('.rail-expanded .nav-button > span');
    expect(shell).toContain('.workspace-expanded-shell');
    expect(shell).toContain('--workspace-inline-gutter: 12px');
    expect(shell).toContain('padding: 0 var(--workspace-inline-gutter) 28px !important');
    expect(shell).not.toMatch(/\.workspace-workshop\.workspace-expanded-shell\s*\{[^}]*padding/);
    expect(codexTheme).toMatch(/\.system-settings-view\s*\{[^}]*width:\s*100%[^}]*max-width:\s*none[^}]*margin:\s*0/);
    expect(codexTheme).not.toMatch(/\.system-settings-view\s*\{[^}]*max-width:\s*1120px/);
    expect(shell).not.toMatch(/\.comfy-local-workbench|\.workflow-panel/);
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

  it("keeps content operation dialogs inside the desktop viewport", () => {
    const css = read("ContentOperationsCenter.css");
    const shell = read("DesktopShell.css");
    expect(css).toMatch(/\.content-ops-dialog\{[^}]*max-height:\s*calc\(100vh\s*-\s*32px\)/);
    expect(css).toMatch(/\.content-ops-dialog\{[^}]*overflow-y:\s*auto/);
    expect(shell).toMatch(/\.workspace-contentOperations \.content-operations-center\s*\{[^}]*height:\s*calc\(100vh\s*-\s*68px\)/);
    expect(shell).toMatch(/\.workspace-contentOperations \.content-ops-error\s*\{[^}]*position:\s*sticky/);
  });

  it("keeps image-workshop detail actions grouped and keyboard accessible", () => {
    const workbench = read("ComfyLocalWorkbench.jsx");
    const workbenchCss = read("ComfyLocalWorkbench.css");
    const workflowCss = read("WorkflowPanel.css");
    expect(workbench).toContain('className="detail-header-actions"');
    expect(workbench).toContain('aria-label="关闭任务详情"');
    expect(workbench).toContain('aria-label="关闭图片详情"');
    expect(workbench).toContain('aria-modal="true"');
    expect(workbenchCss).toMatch(/\.detail-header-actions\{[^}]*display:flex/);
    expect(workbenchCss).toMatch(/\.detail-close-button:focus-visible/);
    expect(workflowCss).toMatch(/\.workflow-panel__main-model\{grid-column:1\/-1\}/);
  });

  it("keeps Bridge task cards native, non-nested interactions", () => {
    const workbench = read("ComfyLocalWorkbench.jsx");
    const workbenchCss = read("ComfyLocalWorkbench.css");
    expect(workbench).toContain('<article\n                  key={task.id}\n                  className={`queue-pill');
    expect(workbench).toContain('className="queue-pill__detail"');
    expect(workbench).not.toContain('className={`queue-pill ${task.state.toLowerCase()}`}\n                  role="button"');
    expect(workbenchCss).toMatch(/\.queue-pill__detail:focus-visible/);
    expect(workbench).toContain('<button type="button" className="task-cancel-all"');
  });
});

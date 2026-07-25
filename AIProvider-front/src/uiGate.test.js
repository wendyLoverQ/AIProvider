import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const srcDir = path.dirname(fileURLToPath(import.meta.url));
const read = (name) => readFileSync(path.join(srcDir, name), "utf8");
// 递归收集 src 下所有 JSX 文件，使门禁覆盖子目录（如 src/quant/）。
function collectJsxFiles(dir) {
  const result = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) {
      result.push(...collectJsxFiles(full));
    } else if (entry.endsWith(".jsx")) {
      result.push(path.relative(srcDir, full).split(path.sep).join("/"));
    }
  }
  return result;
}
const jsxFiles = collectJsxFiles(srcDir);

describe("UI release gate", () => {
  it("keeps monitor capacity cards compact instead of stretching to the viewport", () => {
    const css=read("MonitorCenterEnhancements.css");
    expect(css).toMatch(/\.workspace-monitor \.cloud-monitor\{[^}]*grid-template-rows:auto auto auto auto auto/);
    expect(css).toMatch(/\.workspace-monitor \.cloud-capacity-grid\{[^}]*align-items:start/);
  });

  it("mounts the persistent image workshop only after its first visit", () => {
    const app = read("App.jsx");
    expect(app).toContain('const [workshopMounted, setWorkshopMounted] = useState(() => viewFromPath() === "workshop")');
    expect(app).toContain('if (view === "workshop") setWorkshopMounted(true)');
    expect(app).toContain('{workshopMounted && <div className={`tool-home compact-home persistent-workshop');
  });

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
    ["favorite-library", "video-editor-shell", "foundry-workbench", "system-settings-shell", "file-transfer-page", "twitter-publisher", "content-operations-center", "platform-account-center", "asr-records-page", "prompt-scheme-list", "maid-panel", "universe-toolbar", "quant-page"].forEach((root) => {
      expect(theme, `${root} 未接入全局语义主题`).toContain(root);
    });
    const copy = read("UiControl.jsx");
    ["视频编辑", "我的女仆", "链上工具", "Twitter", "系统设置"].forEach((label) => expect(copy).toContain(label));
    expect(tokens).toContain('"--text-muted-readable"');
    expect(tokens).toContain('"--border-interactive"');
  });

  it("keeps Account Center reachable, searchable, semantic, and native",()=>{
    const app=read("App.jsx"),page=read("PlatformAccountCenter.jsx"),css=read("PlatformAccountCenter.css");
    expect(app).toContain('{ key: "accounts"');
    expect(app).toContain('accounts: "/accounts"');
    expect(app).toContain("<PlatformAccountCenter />");
    expect(page).toContain('import UiSearchField from "./UiSearchField"');
    expect(page).toContain('aria-label="搜索账号"');
    expect(page).not.toMatch(/<div[^>]+onClick=/);
    expect(css).toContain("var(--bg-surface)");
    expect(css).toContain(":focus-visible");
  });

  it("keeps ASR records reachable, searchable, semantic, and native",()=>{
    const app=read("App.jsx"),page=read("AsrRecords.jsx"),css=read("AsrRecords.css");
    expect(app).toContain('{ key: "asrRecords"');
    expect(app).toContain('asrRecords: "/admin/asr"');
    expect(app).toContain("<AsrRecords />");
    expect(page).toContain('import UiSearchField from "./UiSearchField"');
    expect(page).toContain('aria-label="搜索识别文字"');
    expect(page).toContain("<audio controls");
    expect(page).toContain("/correction");
    expect(page).not.toMatch(/<div[^>]+onClick=/);
    expect(css).toContain("var(--bg-surface)");
    expect(css).toContain(":focus-visible");
  });

  it("keeps My Maid aligned with the current role-card and LLM business schema", () => {
    const app = read("App.jsx");
    const maidView = app.slice(app.indexOf("function MaidView"), app.indexOf("function KawaiiPageAtmosphere"));
    ["主动判断", "主动回应", "语音播放", "角色卡迭代", "最近业务", "TemplateCardGenerationStatus", "SourceName"].forEach((field) => {
      expect(maidView).toContain(field);
    });
    ["好感度", "心情", "陪伴时间"].forEach((deprecatedLabel) => {
      expect(maidView).not.toContain(deprecatedLabel);
    });
  });

  it("keeps My Favorites server-backed, searchable, semantic, and native", () => {
    const app = read("App.jsx");
    const page = read("FavoriteMediaLibrary.jsx");
    const css = read("FavoriteMediaLibrary.css");
    expect(app.indexOf('{ key: "favorites"')).toBeLessThan(app.indexOf('{ key: "workshop"'));
    expect(app).toContain('favorites: "/favorites"');
    expect(page).toContain('import UiSearchField from "./UiSearchField"');
    expect(page).toContain('import UiToast from "./UiToast"');
    expect(page).toContain('fetch("/api/favorites"');
    expect(page).toContain('/api/wallpaper/monitors');
    expect(page).toContain('renderWallpaper');
    expect(page).toContain('window.addEventListener("dragenter"');
    expect(page).toContain('window.addEventListener("drop"');
    expect(page).toContain('aria-label="拖放上传区域"');
    expect(page).toContain('aria-label="确认拖放上传"');
    expect(page).toContain('new XMLHttpRequest()');
    expect(page).toContain('<progress max=');
    expect(page).toContain('body: JSON.stringify({ ids: deleteIds })');
    expect(page).toContain('<Trash />批量删除');
    expect(page).toContain('aria-label="按内容类型筛选"');
    expect(page).toContain("saveToDevice(menu.item)");
    expect(page).toContain("navigator.canShare");
    expect(page).toContain('aria-label={mobileSave.isVideo ? "保存视频到手机" : "保存图片到相册"}');
    expect(page).not.toMatch(/<div[^>]+onClick=/);
    expect(css).toContain("var(--bg-surface)");
    expect(css).toContain("var(--text-primary)");
  });

  it("keeps Workshop and My Favorites on one compact shared media viewer", () => {
    const workshop = read("ComfyLocalWorkbench.jsx");
    const favorites = read("FavoriteMediaLibrary.jsx");
    const viewer = read("MediaViewer.jsx");
    const viewerCss = read("MediaViewer.css");
    expect(workshop).toContain('import MediaViewer from "./MediaViewer"');
    expect(favorites).toContain('import MediaViewer from "./MediaViewer"');
    expect(workshop).toContain("<MediaViewer");
    expect(favorites).toContain("<MediaViewer");
    expect(viewer).toContain("<TransformWrapper");
    expect(viewerCss).toMatch(/\.media-viewer-panel\{[^}]*grid-template-rows:44px minmax\(0,1fr\) auto/);
    expect(viewerCss).toMatch(/\.media-viewer-tools button,\.media-viewer-actions button\{[^}]*width:30px!important;height:30px!important;min-height:0!important/);
    expect(viewerCss).toContain("var(--bg-surface)");
    expect(viewerCss).toContain("var(--text-primary)");
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
    expect(page).toContain('import UiToast from "./UiToast"');
    expect(page).toContain("<UiToast message={error || notice}");
    expect(page).not.toContain("file-transfer-message");
    expect(page).not.toMatch(/<div[^>]+onClick=/);
    expect(css).toContain("var(--bg-surface)");
    expect(css).toMatch(/\.file-transfer-page\{[^}]*min-width:0[^}]*overflow:hidden/);
    expect(css).toMatch(/\.file-transfer-table-wrap\{[^}]*overflow:auto/);
    expect(css).not.toMatch(/td:first-child\{[^}]*display:flex/);
    expect(css).toMatch(/\.file-transfer-file-cell\{[^}]*display:flex/);
    expect(css).toMatch(/\.file-transfer-text-card\{[^}]*display:grid[^}]*align-self:start/);
  });

  it("routes transient operation feedback through the unified compact toast", () => {
    const toast = read("UiToast.jsx");
    const toastCss = read("UiToast.css");
    const customToastFiles = jsxFiles.filter((name) => name !== "UiToast.jsx" && /className=[^\n>]*toast/i.test(read(name)));
    expect(customToastFiles, `禁止功能页自造 Toast：${customToastFiles.join(", ")}`).toEqual([]);
    expect(toast).toContain('role={tone === "error" ? "alert" : "status"}');
    expect(toast).toContain('aria-label="关闭消息"');
    expect(toastCss).toMatch(/\.ui-toast\{[^}]*position:fixed[^}]*max-width:min\(360px,calc\(100vw - 24px\)\)/);
    expect(toastCss).toContain("var(--bg-surface)");
    expect(toastCss).not.toMatch(/width:\s*100%/);
    const operations = read("ContentOperationsCenter.jsx");
    expect(operations).toContain('import UiToast from "./UiToast"');
    expect(operations).toContain("<UiToast message={error||notice}");
    expect(operations).not.toContain("content-ops-error");
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
    expect(prompt).not.toContain('request("/api/prompt-catalog")');
    expect(prompt).toContain('/api/prompt-options/resolve');
    expect(prompt).toContain('category, status: "enabled"');
    expect(prompt).toContain('className={`prompt-scheme-row');
    expect(prompt).toContain('className="prompt-scheme-select"');
    expect(prompt).not.toContain('role="button"');
    expect(prompt).not.toContain('<span className={`prompt-default-star');
    expect(prompt).toContain('<button type="button" className={`prompt-default-star');
    expect(prompt).toContain('name="promptMode"');
    expect(prompt).toContain('aria-label="长文正向描述"');
    expect(prompt).toContain('/api/prompt-translations/prose');
    expect(prompt).not.toContain('role="radio"');
    expect(read("App.jsx")).toContain('prompts: "管理可复用的标签式与长文式提示词方案"');
    const workbench = read("ComfyLocalWorkbench.jsx");
    expect(workbench).toContain('const promptMode = mode === "overwrite" ? selected.promptMode : (form.promptMode === "prose" ? "prose" : "tags")');
    expect(workbench).toContain('promptMode: task?.promptMode || form.promptMode || "tags"');
    expect(workbench).toContain('promptMode !== "tags" && promptMode !== "prose"');
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

  it("keeps Quant as an independent nav group with 8 independent pages and routes", () => {
    const app = read("App.jsx");
    const overview = read("quant/QuantOverview.jsx");
    const scaffold = read("quant/QuantPageScaffold.jsx");
    const pagesCss = read("quant/QuantPages.css");
    const theme = read("SemanticTheme.css");

    // 量化独立分组，顺序为 operate → quant → publish。
    expect(app).toContain('{ key: "quant", label: "量化" }');
    const groupsText = app.slice(app.indexOf("const NAV_GROUPS"), app.indexOf("const PAGE_DESCRIPTIONS"));
    expect(groupsText.indexOf('key: "operate"')).toBeLessThan(groupsText.indexOf('key: "quant"'));
    expect(groupsText.indexOf('key: "quant"')).toBeLessThan(groupsText.indexOf('key: "publish"'));

    // 8 个菜单项均属于 quant 分组。
    expect(app.match(/group: "quant"/g).length).toBe(8);
    ["quantOverview", "market", "quantStrategies", "quantBacktests", "quantRisk", "quantPortfolio", "quantOrders", "quantLogs"].forEach((key) => {
      expect(app, `缺少量化菜单项 ${key}`).toContain(`key: "${key}"`);
    });
    // 合约行情归属量化分组，且不再属于运营与工具。
    expect(app).toContain('{ key: "market", label: "合约行情", icon: ChartLineUp, group: "quant"');
    // 不再存在旧的单一“量化交易”菜单。
    expect(app).not.toContain('{ key: "quant", label: "量化交易"');

    // 7 个量化独立路径（/market 已存在，单独验证）。
    expect(app).toContain('"/quant": "quantOverview"');
    expect(app).toContain('"/quant/strategies": "quantStrategies"');
    expect(app).toContain('"/quant/backtests": "quantBacktests"');
    expect(app).toContain('"/quant/risk": "quantRisk"');
    expect(app).toContain('"/quant/portfolio": "quantPortfolio"');
    expect(app).toContain('"/quant/orders": "quantOrders"');
    expect(app).toContain('"/quant/logs": "quantLogs"');
    // 反向映射也存在。
    expect(app).toContain('quantOverview: "/quant"');
    expect(app).toContain('quantLogs: "/quant/logs"');

    // 7 个独立页面组件已 import 和挂载。
    const components = ["QuantOverview", "QuantStrategies", "QuantBacktests", "QuantRisk", "QuantPortfolio", "QuantOrders", "QuantLogs"];
    components.forEach((component) => {
      expect(app, `未导入 ${component}`).toContain(`import ${component} from "./quant/${component}"`);
      const viewKey = component[0].toLowerCase() + component.slice(1);
      expect(app, `未挂载 ${component}`).toContain(`{view === "${viewKey}" && <${component} />}`);
    });

    // QuantOverview 请求真实接口，不伪造数据。
    expect(overview).toContain("/api/quant/overview");

    // 不再存在单页面 Tab 结构与旧文件。
    expect(app).not.toContain("QuantWorkbench");
    expect(overview).not.toContain("const WORKSPACES");
    expect(scaffold).not.toContain("const WORKSPACES");
    expect(pagesCss).not.toContain(".quant-tabs");
    expect(pagesCss).not.toContain(".quant-tab");

    // 各页面无 clickable div，使用语义变量，:focus-visible。
    expect(overview).not.toMatch(/<div[^>]+onClick=/);
    expect(scaffold).not.toMatch(/<div[^>]+onClick=/);
    expect(pagesCss).toContain("var(--bg-surface)");
    expect(pagesCss).toContain("var(--text-primary)");
    expect(pagesCss).toContain(":focus-visible");
    expect(pagesCss).toMatch(/\.quant-page\{[^}]*min-width:0/);
    expect(pagesCss).toMatch(/@media \(max-width:\s*720px\)/);
    expect(theme).toContain(".quant-page");

    // 移动端仍使用原有 MOBILE_NAV 与 NavButton。
    expect(app).toContain('const MOBILE_NAV = [{ key: "home"');
    expect(app).toContain('scrollIntoView({ behavior: "smooth"');

    // CryptoMarket 业务代码与 API 路径未被修改（旧文件保留但不再被 /market 挂载）。
    expect(read("CryptoMarket.jsx")).toContain("/api/crypto-market");

    // /market 页面已替换为 QuantMarket，不再挂载 CryptoMarket。
    expect(app).not.toContain('import CryptoMarket from "./CryptoMarket"');
    expect(app).not.toContain("<CryptoMarket />");
    expect(app).toContain('import QuantMarket from "./quant/QuantMarket"');
    expect(app).toContain('<QuantMarket />');
    // 菜单标签改为合约行情。
    expect(app).toContain('{ key: "market", label: "合约行情"');
    expect(app).not.toContain('{ key: "market", label: "市场行情"');
  });

  it("keeps QuantMarket on Binance public market without CCXT or API Key", () => {
    const page = read("quant/QuantMarket.jsx");
    const css = read("quant/QuantMarket.css");
    const theme = read("SemanticTheme.css");

    // 只调用 /api/quant/market/**，不调用旧 CCXT 链路。
    expect(page).toContain("/api/quant/market");
    expect(page).not.toContain("/api/crypto-market");

    // 复用 UiSearchField 和 readJsonResponse。
    expect(page).toContain('import UiSearchField from "../UiSearchField"');
    expect(page).toContain('import { readJsonResponse } from "../apiResponse"');

    // 使用 QuantPageScaffold。
    expect(page).toContain('import QuantPageScaffold from "./QuantPageScaffold"');

    // 明确标注公共只读和不经过 CCXT。
    expect(page).toContain("公共只读");
    expect(page).toContain("不经过 CCXT");

    // BTCUSDT 只作为默认选择逻辑，不是唯一合约数据。
    expect(page).toContain('DEFAULT_SYMBOL = "BTCUSDT"');
    expect(page).toContain("contracts.find");
    expect(page).toContain("cs[0]");

    // 无 API Key 输入框。
    expect(page).not.toMatch(/api[_-]?key/i);

    // 无下单按钮（允许说明文字"不提供下单"）。
    expect(page).not.toMatch(/<button[^>]*>[^<]*下单/);
    expect(page).not.toMatch(/买入|卖出|place.?order/i);

    // 无 clickable div。
    expect(page).not.toMatch(/<div[^>]+onClick=/);

    // 原生按钮和 select。
    expect(page).toContain("<button type=\"button\"");
    expect(page).toContain("<select");

    // CSS 使用语义变量和 :focus-visible。
    expect(css).toContain("var(--bg-surface)");
    expect(css).toContain("var(--text-primary)");
    expect(css).toContain(":focus-visible");
    expect(css).toContain("var(--border-normal)");

    // 响应式。
    expect(css).toMatch(/@media \(max-width:\s*900px\)/);
    expect(css).toMatch(/@media \(max-width:\s*600px\)/);

    // SemanticTheme 接入 quant-market-page。
    expect(theme).toContain(".quant-market-page");

    // K 线表格允许横向滚动。
    expect(css).toMatch(/\.quant-market-table-wrap\{[^}]*overflow:auto/);
  });

  it("keeps content operation dialogs inside the desktop viewport", () => {
    const css = read("ContentOperationsCenter.css");
    const page = read("ContentOperationsCenter.jsx");
    const shell = read("DesktopShell.css");
    expect(css).toMatch(/\.content-ops-dialog\{[^}]*max-height:\s*calc\(100vh\s*-\s*32px\)/);
    expect(css).toMatch(/\.content-ops-dialog\{[^}]*overflow-y:\s*auto/);
    expect(shell).toMatch(/\.workspace-contentOperations \.content-operations-center\s*\{[^}]*height:\s*calc\(100vh\s*-\s*68px\)/);
    expect(css).toMatch(/\.settings-inline\{[^}]*grid-template-columns:/);
    expect(css).toMatch(/\.collection-history-row\{[^}]*display:grid/);
    expect(css).toMatch(/\.publication-row\{[^}]*width:100%/);
    expect(css).toMatch(/\.settings-inline\{[^}]*grid-template-columns:max-content 150px 150px 72px/);
    expect(css).toMatch(/\.settings-inline \.automation-toggle\{[^}]*display:inline-flex!important[^}]*align-items:center/);
    expect(css).toMatch(/\.settings-inline>button\{[^}]*height:32px[^}]*min-height:32px/);
    expect(css).toMatch(/\.number-with-unit\{[^}]*grid-template-columns:76px 42px/);
    expect(css).toMatch(/\.account-source-rules article\{[^}]*grid-template-columns:minmax\(130px,1fr\) 210px 96px/);
    expect(css).not.toMatch(/\.settings-inline\{[^}]*(?:\.8fr|\.85fr|1fr)/);
    expect(page).not.toMatch(/<div[^>]+onClick=/);
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
    expect(workflowCss).toMatch(/\.workflow-panel__dimension-field\{[^}]*grid-template-columns:auto minmax\(0,1fr\)/);
  });

  it("keeps Bridge task cards native, non-nested interactions", () => {
    const workbench = read("ComfyLocalWorkbench.jsx");
    const workbenchCss = read("ComfyLocalWorkbench.css");
    expect(workbench).toContain('<article\n                  key={task.id}\n                  className={`queue-pill');
    expect(workbench).toContain('className="queue-pill__detail"');
    expect(workbench).not.toContain('className={`queue-pill ${task.state.toLowerCase()}`}\n                  role="button"');
    expect(workbenchCss).toMatch(/\.queue-pill__detail:focus-visible/);
    expect(workbench).toContain('<button type="button" className="task-cancel-all"');
    expect(workbench).toContain('取消全部生成任务（${cancelableTaskCount}）');
    expect(workbenchCss).toMatch(/\.task-queue-overview \.task-cancel-all/);
    expect(workbench).not.toContain('className="task-queue-controls"');
    expect(workbenchCss).toMatch(/\.compact-home \.comfy-form,\.compact-home \.comfy-history\{height:100%!important;min-height:0!important;max-height:100%!important\}/);
    expect(workbenchCss).not.toContain("height:calc(100vh - 158px)!important");
  });

  it("hides image/video editor nav entries and fully removes the Twitter frontend page", () => {
    const app = read("App.jsx");

    // manualEditor 和 videoEditor 仍存在于 NAV，且都设置 hidden: true。
    expect(app).toContain('{ key: "manualEditor"');
    expect(app).toContain('{ key: "videoEditor"');
    const manualEditorLine = app.match(/\{ key: "manualEditor"[^}]*\}/)[0];
    const videoEditorLine = app.match(/\{ key: "videoEditor"[^}]*\}/)[0];
    expect(manualEditorLine).toContain("hidden: true");
    expect(videoEditorLine).toContain("hidden: true");

    // /manual-editor 与 /video-editor 路由映射仍然存在。
    expect(app).toContain('"/manual-editor": "manualEditor"');
    expect(app).toContain('"/video-editor": "videoEditor"');
    // 反向映射仍然存在。
    expect(app).toContain("manualEditor: \"/manual-editor\"");
    expect(app).toContain("videoEditor: \"/video-editor\"");

    // ManualImageEditor 与 VideoEditor 仍被 import 和挂载。
    expect(app).toContain('import ManualImageEditor from "./ManualImageEditor"');
    expect(app).toContain('import VideoEditor from "./VideoEditor"');
    expect(app).toContain('{view === "manualEditor" && <ManualImageEditor />}');
    expect(app).toContain('{view === "videoEditor" && <VideoEditor />}');

    // PAGE_DESCRIPTIONS 仍保留。
    expect(app).toContain("manualEditor:");
    expect(app).toContain("videoEditor:");

    // twitter 不再存在于 NAV。
    expect(app).not.toContain('{ key: "twitter"');
    // /twitter 路由映射、反向映射和页面挂载已经删除。
    expect(app).not.toContain('"/twitter": "twitter"');
    expect(app).not.toContain('twitter: "/twitter"');
    expect(app).not.toContain('{view === "twitter"');
    // TwitterPublisher 不再被 import 或挂载。
    expect(app).not.toContain("TwitterPublisher");
    // XLogo 不再被 import。
    expect(app).not.toMatch(/^\s*XLogo,?\s*$/m);

    // Twitter 前端文件已删除。
    expect(existsSync(path.join(srcDir, "TwitterPublisher.jsx"))).toBe(false);
    expect(existsSync(path.join(srcDir, "TwitterPublisher.css"))).toBe(false);
    expect(existsSync(path.join(srcDir, "TwitterPublisher.test.jsx"))).toBe(false);

    // 整个 src 中不存在其他对 TwitterPublisher 的正式引用。
    const staleRefs = jsxFiles.filter((name) => read(name).includes("TwitterPublisher"));
    expect(staleRefs, `仍存在 TwitterPublisher 引用: ${staleRefs.join(", ")}`).toEqual([]);

    // 图像工坊、内容运营、账号中心 仍然存在。
    expect(app).toContain('{ key: "workshop"');
    expect(app).toContain('{ key: "contentOperations"');
    expect(app).toContain('{ key: "accounts"');
  });

  it("keeps QuantMarket on candlestick chart with WebSocket and Chinese labels", () => {
    const page = read("quant/QuantMarket.jsx");
    const chart = read("quant/QuantCandlestickChart.jsx");
    const chartCss = read("quant/QuantCandlestickChart.css");
    const socket = read("quant/useQuantMarketSocket.js");
    const labels = read("quant/quantMarketLabels.js");
    const app = read("App.jsx");
    const pkg = JSON.parse(readFileSync(path.join(srcDir, "..", "package.json"), "utf8"));

    // 不再使用 Recharts AreaChart/Area。
    expect(page).not.toContain("AreaChart");
    expect(page).not.toContain("Area");
    expect(page).not.toMatch(/from\s+["']recharts["']/);

    // 使用 QuantCandlestickChart。
    expect(page).toContain("QuantCandlestickChart");

    // lightweight-charts 版本精确为 5.2.0。
    expect(pkg.dependencies["lightweight-charts"]).toBe("5.2.0");

    // 使用本机后端 WebSocket 代理 /ws/quant/market，不直连 fstream.binance.com。
    expect(socket).toContain("/ws/quant/market");
    expect(socket).not.toContain("fstream.binance.com");

    // 浏览器代码中不存在 fstream.binance.com。
    const quantJsFiles = readdirSync(path.join(srcDir, "quant"))
      .filter((f) => f.endsWith(".js"))
      .map((f) => `quant/${f}`);
    const allCheckFiles = [...jsxFiles, ...quantJsFiles];
    const binanceDirectRefs = allCheckFiles.filter((name) => read(name).includes("fstream.binance.com"));
    expect(binanceDirectRefs, `浏览器代码不得直连 fstream.binance.com: ${binanceDirectRefs.join(", ")}`).toEqual([]);

    // 包含中文标签。
    expect(page).toContain("实时行情");
    expect(labels).toContain("合约类型");
    expect(labels).toContain("交易状态");
    expect(labels).toContain("价格最小变动单位");

    // 无 clickable div。
    expect(page).not.toMatch(/<div[^>]+onClick=/);
    expect(chart).not.toMatch(/<div[^>]+onClick=/);

    // :focus-visible 存在于 QuantCandlestickChart.css。
    expect(chartCss).toContain(":focus-visible");

    // manualEditor 和 videoEditor 仍 hidden。
    const manualEditorLine = app.match(/\{ key: "manualEditor"[^}]*\}/)[0];
    const videoEditorLine = app.match(/\{ key: "videoEditor"[^}]*\}/)[0];
    expect(manualEditorLine).toContain("hidden: true");
    expect(videoEditorLine).toContain("hidden: true");

    // Twitter 前端已删除。
    expect(app).not.toContain("TwitterPublisher");
    const staleRefs = jsxFiles.filter((name) => read(name).includes("TwitterPublisher"));
    expect(staleRefs, `仍存在 TwitterPublisher 引用: ${staleRefs.join(", ")}`).toEqual([]);
  });
});

// @vitest-environment jsdom
import React from "react";
import { cleanup, configure, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ComfyLocalWorkbench from "./ComfyLocalWorkbench";

configure({ asyncUtilTimeout: 5000 });

const PROMPT_ID = "11111111-1111-1111-1111-111111111111";

const workflow = {
  id: "futa01",
  name: "Futa 01 · 竖版文生图",
  fields: ["positivePrompt", "negativePrompt", "width", "height", "batchSize", "seed", "steps", "cfg", "sampler", "scheduler", "denoise", "secondPassSteps", "secondPassDenoise", "secondPassSeed"],
  defaults: { positivePrompt: "portrait", negativePrompt: "bad", width: 1080, height: 1920, batchSize: 1, seed: 11, steps: 30, cfg: 5, sampler: "uni_pc", scheduler: "normal", denoise: 1, secondPassSteps: 22, secondPassDenoise: 0.28, secondPassSeed: 12 },
  capabilities: { styleReference: false, poseReference: false, controlNet: false },
  definition: {
    "4": { class_type: "KSampler", inputs: { positive: ["28", 0], latent_image: ["5", 0], steps: 30, cfg: 5, seed: 1 } },
    "5": { class_type: "EmptyLatentImage", inputs: { width: 1080, height: 1920 } },
    "7": { class_type: "SaveImage", _meta: { title: "最终输出" }, inputs: {} },
    "28": { inputs: { text: "portrait" } },
  },
  binding: { fields: {}, outputNode: { title: "最终输出" } },
};
const workflow2 = { ...workflow, id: "futa02", name: "Futa 02 · 透明双输出", definition: { ...workflow.definition, "99": { class_type: "WorkflowTwoMarker", inputs: {} } }, fields: undefined, binding: { ...workflow.binding, fields: { ...Object.fromEntries(workflow.fields.map((key) => [key, {}])), node_4_control_after_generate: { nodeId: "4", input: "control_after_generate", label: "KSampler 4 · control_after_generate" } } }, defaults: { ...workflow.defaults, positivePrompt: "changed by workflow", width: 832, height: 1216, batchSize: 3, seed: 99, steps: 42, cfg: 7, sampler: "euler", scheduler: "karras", node_4_control_after_generate: false } };
const cutoutWorkflow = {
  id: "local-a4f23acdd681e785", name: "BiRefNet_一键抠图_透明PNG", relativePath: "BiRefNet_一键抠图_透明PNG.json",
  fields: ["sourceImage", "filenamePrefix"], defaults: { sourceImage: "", filenamePrefix: "BiRefNet_一键抠图", randomSeed: true },
  capabilities: { inputImage: true, autoCutout: true, generationAndCutout: false },
  definition: { "1": { class_type: "LoadImage", inputs: { image: "" } }, "8": { class_type: "SaveImage", inputs: { filename_prefix: "BiRefNet_一键抠图" } } },
  binding: { fields: { sourceImage: { nodeId: "1", input: "image" }, filenamePrefix: { nodeId: "8", input: "filename_prefix" } }, outputNode: { nodeId: "8", title: "保存透明 PNG" } },
};
const interactiveWorkflow = {
  id: "local-sam2", name: "BiRefNet_SAM2_点选删除物体_透明PNG",
  fields: ["sourceImage", "filenamePrefix", "node_5_editor_data", "node_5_default_radius"],
  defaults: { sourceImage: "", filenamePrefix: "SAM2_Result", node_5_editor_data: '{"points":[],"bboxes":[]}', node_5_default_radius: 12 },
  capabilities: { inputImage: true },
  definition: { "1": { class_type: "LoadImage", inputs: { image: "" } }, "5": { class_type: "MaskEditMEC", inputs: { editor_data: '{"points":[],"bboxes":[]}', default_radius: 12 } }, "13": { class_type: "SaveImage", inputs: { filename_prefix: "SAM2_Result" } } },
  binding: { fields: {
    sourceImage: { nodeId: "1", nodeType: "LoadImage", input: "image" },
    filenamePrefix: { nodeId: "13", nodeType: "SaveImage", input: "filename_prefix" },
    node_5_editor_data: { nodeId: "5", nodeType: "MaskEditMEC", input: "editor_data", label: "MaskEditMEC 5 · editor_data" },
    node_5_default_radius: { nodeId: "5", nodeType: "MaskEditMEC", input: "default_radius", label: "MaskEditMEC 5 · default_radius" },
  }, outputNode: { nodeId: "13", title: "最终输出" } },
};

const completed = {
  prompt: [0, 0, workflow.definition],
  outputs: { "7": { images: [{ filename: "done.png", subfolder: "aimaid", type: "output" }] } },
  status: { messages: [["execution_start", { timestamp: Date.now() }]] },
};
const incrementalCompleted = {
  ...completed,
  outputs: { "7": { images: [{ filename: "incremental.png", subfolder: "aimaid", type: "output" }] } },
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
}

describe("Comfy image generation flow", () => {
  let submitted;
  let moved;
  let galleryRequests;
  let galleryRequestUrls;
  let localGalleryPages;
  let assetRequests;
  let externalRun;
  let externalQueuePoll;
  let externalGalleryReady;
  let submittedEditorData;
  let savedTwitterTask;
  let presetIsDefault;
  let presetPositivePrompt;
  let cancelledTask;
  let cancelAllRequests;
  let multiImageGallery;
  let submittedWorkflow;
  let generateRequests;
  let submittedBatchSizes;
  let customQueue;
  let progressFails;
  let deletedPaths;
  let incrementalHistoryRun;
  let incrementalHistoryAlreadyPresent;
  let existingHistoryAlreadyPresent;
  let recentHistoryPoll;
  let bridgeRequests;
  let registeredAssetStatus;
  let transferredFileName;

  beforeEach(() => {
    submitted = false;
    moved = false;
    galleryRequests = 0;
    galleryRequestUrls = [];
    localGalleryPages = 1;
    assetRequests = 0;
    externalRun = false;
    externalQueuePoll = 0;
    externalGalleryReady = false;
    submittedEditorData = null;
    savedTwitterTask = null;
    presetIsDefault = false;
    presetPositivePrompt = "preset prompt";
    cancelledTask = null;
    cancelAllRequests = 0;
    multiImageGallery = false;
    submittedWorkflow = null;
    generateRequests = 0;
    submittedBatchSizes = [];
    customQueue = null;
    progressFails = false;
    deletedPaths = [];
    incrementalHistoryRun = false;
    incrementalHistoryAlreadyPresent = false;
    existingHistoryAlreadyPresent = false;
    recentHistoryPoll = 0;
    bridgeRequests = [];
    registeredAssetStatus = null;
    transferredFileName = null;
    vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
    vi.stubGlobal("confirm", vi.fn(() => true));
    vi.stubGlobal("ClipboardItem", class { constructor(items) { this.items = items; } });
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { write: vi.fn(async () => {}), writeText: vi.fn(async () => {}) } });
    vi.stubGlobal("fetch", vi.fn(async (input, options = {}) => {
      const url = String(input);
      if (url.startsWith("http://127.0.0.1:32145")) bridgeRequests.push(url);
      if (url.endsWith("/api/config")) return json({ token: "local-token", platform: "Windows", configured: true });
      if (url.endsWith("/api/comfy/status")) return json({ running: true, platform: "Windows", configured: true });
      if (url.endsWith("/api/local-workflows/settings")) return json({ directory: "F:\\ComfyUI\\user\\default\\workflows", exists: true });
      if (url.endsWith("/api/local-workflows")) return json({ directory: "F:\\ComfyUI\\user\\default\\workflows", workflows: [workflow, workflow2, cutoutWorkflow, interactiveWorkflow], rejected: [] });
      if (url.endsWith("/api/lora-models")) return json({ models: [] });
      if (url.includes("/api/comfy-presets")) return json({ code: 200, data: [{ id: 2, name: "扶她0", isDefault: presetIsDefault, selectedOptions: { characterCount: [], characterTypes: [], relationships: [], actions: [], clothing: [], expression: [], pose: [], cameraAngle: [], shotType: [], scene: [], composition: [], quality: [] }, positiveExtra: "", negativeExtra: "", positivePrompt: presetPositivePrompt, negativePrompt: "preset negative", remark: "" }] });
      if (url.startsWith("/api/assets?")) {
        assetRequests += 1;
        return json({ code: 200, data: { page: 1, pages: 1, total: 1, items: [{ id: 12, platform: "Windows", localPath: "C:\\assets\\saved.png", localUrl: "http://127.0.0.1:32145/api/assets/file?path=saved.png", fileName: "saved.png", fileSize: 8 }] } });
      }
      if (url.startsWith("/api/assets/prompt-pool?")) return json({ code: 200, data: [{ prompt: "black pantyhose, soft lighting, bedroom", negativePrompt: "watermark, extra fingers", weight: 5 }] });
      if (url.endsWith("/api/twitter/accounts")) return json({ code: 200, data: [{ id: 2, username: "tester", sessionStatus: "CONNECTED" }] });
      if (url.endsWith("/api/twitter/posts/local-scheduled") && options.method === "POST") {
        savedTwitterTask = options.body;
        return json({ code: 200, data: { id: 88 } });
      }
      if (url === "blob:done") return new Response(new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])], { type: "image/png" }), { headers: { "Content-Type": "image/png" } });
      if (url.endsWith("/api/folders")) return json({ folders: ["aimaid", "favorites"] });
      if (url.endsWith("/api/migration/settings")) return json({ directory: "C:\\Users\\49213\\Desktop\\A\\ai成品" });
      if (url.includes("/api/tasks/") && url.endsWith("/state")) {
        const promptId = decodeURIComponent(url.split("/api/tasks/")[1].replace("/state", ""));
        if (promptId === PROMPT_ID) return json({ success: true, state: submitted ? "SUCCEEDED" : "QUEUED", tracked: true });
        if (promptId === "external-prompt") return json({ success: true, state: externalGalleryReady ? "COMPLETED" : "RUNNING", tracked: true });
        return json({ success: true, state: "UNKNOWN", tracked: false });
      }
      if (url.endsWith("/api/file-transfer/upload") && options.method === "POST") {
        transferredFileName = options.body.get("file").name;
        return json({ code: 200, data: { fileName: transferredFileName } });
      }
      if (url.includes("/api/tasks/") && url.endsWith("/cancel") && options.method === "POST") {
        cancelledTask = decodeURIComponent(url.split("/api/tasks/")[1].replace("/cancel", ""));
        return json({ success: true, cancelled: true, promptId: cancelledTask });
      }
      if (url.endsWith("/api/tasks/cancel-all") && options.method === "POST") {
        cancelAllRequests += 1;
        return json({ success: true, total: 1, cancelled: 0, cancellationRequested: 1, promptIds: [PROMPT_ID] }, 202);
      }
      if (url.endsWith("/api/gallery/delete") && options.method === "POST") {
        deletedPaths.push(...JSON.parse(options.body).paths);
        return json({ success: true, deleted: deletedPaths.length });
      }
      if (url.endsWith("/api/gallery/trash") && options.method === "POST") {
        deletedPaths.push(...JSON.parse(options.body).paths);
        return json({ success: true, trashed: deletedPaths.length });
      }
      if (url.includes("/api/gallery/trash?")) return json({ success: true, total: 0, items: [] });
      if (url.endsWith("/api/gallery/restore") && options.method === "POST") return json({ success: true, restored: 1 });
      if (["/api/assets/trash", "/api/assets/restore", "/api/assets/delete"].some((path) => url.endsWith(path)) && options.method === "POST") return json({ code: 200, data: { updated: 1 } });
      if (url.endsWith("/api/assets/status") && options.method === "PUT") return json({ code: 200, data: { updated: 1 } });
      if (url.endsWith("/comfy/queue")) {
        if (customQueue) return json(customQueue);
        if (externalRun && externalQueuePoll++ === 0) return json({ queue_running: [[0, "external-prompt", workflow.definition, {}, ["7"]]], queue_pending: [] });
        if (externalRun) externalGalleryReady = true;
        return json({ queue_running: [], queue_pending: [] });
      }
      if (url.endsWith("/api/logs/client") && options.method === "POST") return json({ success: true });
      if (url.endsWith("/api/gallery/complete") && options.method === "POST") return json({ success: true });
      if (url.endsWith("/api/local-generated-images/batch") && options.method === "POST") return json({ code: 200, data: { saved: 1 } });
      if (url.endsWith("/comfy/aiprovider/progress") && progressFails) return json({ message: "progress extension unavailable" }, 404);
      if (url.endsWith("/comfy/aiprovider/progress")) return externalRun
        ? json({ promptId: "external-prompt", nodes: { "5": { state: "finished" }, "4": { state: "running", value: 5, max: 10 } } })
        : json({ promptId: "", nodes: {} });
      if (url.endsWith("/api/gallery/move") && options.method === "POST") {
        const body = JSON.parse(options.body);
        expect(body).toEqual({ paths: ["aimaid/done.png"] });
        moved = true;
        return json({ moved: 1, folder: "C:\\Users\\49213\\Desktop\\A\\ai成品", manifest: "aiprovider-migration-test.json", platform: "Windows", assets: [{ localPath: "C:\\Users\\49213\\Desktop\\A\\ai成品\\done.png", localUrl: "http://127.0.0.1:32145/api/assets/file?path=done.png", fileName: "done.png" }] });
      }
      if (url.endsWith("/api/assets/batch") && options.method === "POST") {
        const item = JSON.parse(options.body).items[0];
        registeredAssetStatus = item.status;
        return json({ code: 200, data: { saved: 1, items: [{ id: 13, platform: "Windows", ...item }] } });
      }
      if (url.includes("/api/gallery?")) {
        galleryRequests += 1;
        galleryRequestUrls.push(url);
        const items = multiImageGallery ? [{
          id: PROMPT_ID,
          promptId: PROMPT_ID,
          prompt: "two images",
          createdAt: new Date().toISOString(),
          images: [
            { filename: "done.png", path: "aimaid/done.png" },
            { filename: "done-2.png", path: "aimaid/done-2.png" },
          ],
        }] : externalGalleryReady ? [{ id: "external-prompt", prompt: "external", images: [{ filename: "external.png", path: "aimaid/external.png" }] }] : submitted && !moved ? [{
        id: PROMPT_ID,
        promptId: PROMPT_ID,
        prompt: "portrait",
        createdAt: new Date().toISOString(),
        images: [{ filename: "done.png", path: "aimaid/done.png" }],
        }] : [];
        const page = Number(url.match(/[?&]page=(\d+)/)?.[1] || 1);
        const currentImages = items.reduce((sum, item) => sum + item.images.length, 0);
        return json({ items, page, pages: localGalleryPages, total: currentImages + (localGalleryPages - 1) * 100 });
      }
      if (url.includes("/api/gallery/file?")) return new Response(new Blob(["image"], { type: "image/png" }), { headers: { "Content-Type": "image/png" } });
      if (url.includes("/api/assets/file?")) return new Response(new Blob(["image"], { type: "image/png" }), { headers: { "Content-Type": "image/png" } });
      if (url.includes("/comfy/history?")) {
        if (existingHistoryAlreadyPresent) return json({ "existing-prompt": completed });
        if (incrementalHistoryAlreadyPresent) return json({ "incremental-prompt": incrementalCompleted });
        if (incrementalHistoryRun) return json(recentHistoryPoll++ === 0 ? {} : { "incremental-prompt": incrementalCompleted });
        return json(externalGalleryReady ? { "external-prompt": completed } : {});
      }
      if (url.includes("/comfy/history/external-prompt")) return json({ "external-prompt": completed });
      if (url.includes(`/comfy/history/${PROMPT_ID}`)) return json({ [PROMPT_ID]: completed });
      if (url.includes("/comfy/view?")) return new Response(new Blob(["image"], { type: "image/png" }));
      if (url.endsWith("/api/generate") && options.method === "POST") {
        submitted = true;
        generateRequests += 1;
        submittedBatchSizes.push(options.body.get("batchSize"));
        submittedWorkflow = {
          id: options.body.get("workflowId"),
          name: options.body.get("workflowName"),
          definition: options.body.get("workflowDefinition"),
          positivePrompt: options.body.get("positivePrompt"),
          negativePrompt: options.body.get("negativePrompt"),
        };
        submittedEditorData = options.body.get("node_5_editor_data");
        expect(options.body.get("workflowDefinition")).toContain("SaveImage");
        expect(options.body.get("workflowBinding")).toContain("outputNode");
        return json({ promptId: PROMPT_ID, finalOutputNodeId: "7", actualSeed: 42 });
      }
      throw new Error(`Unexpected request: ${url}`);
    }));
    vi.stubGlobal("URL", { ...URL, createObjectURL: vi.fn(() => "blob:done"), revokeObjectURL: vi.fn() });
  });

  afterEach(() => { cleanup(); vi.unstubAllGlobals(); localStorage.clear(); });

  it("offers the registered app launch action without an alert when the local bridge is missing", async () => {
    const availableFetch = fetch.getMockImplementation();
    fetch.mockImplementation((input, options) => {
      const url = String(input);
      if (url.startsWith("http://127.0.0.1:32145")) return Promise.reject(new TypeError("Failed to fetch"));
      if (url === "/api/prompt-catalog") return Promise.resolve(json({ code: 200, data: { options: [], negativeOptions: [], generalNegativePrompt: "" } }));
      return availableFetch(input, options);
    });
    render(<ComfyLocalWorkbench />);

    const launch = await screen.findByRole("link", { name: "启动本机桥接器" });
    expect(launch.getAttribute("href")).toBe("aiprovider-bridge://start");
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByText("未检测到")).toBeTruthy();
  });

  it("loads the local workflow, submits it locally, polls and renders the completed image", async () => {
    render(<ComfyLocalWorkbench />);
    const generate = await screen.findByRole("button", { name: "开始生成" });
    await waitFor(() => expect(generate.disabled).toBe(false));
    expect(screen.getByRole("combobox", { name: "当前生成工作流" }).value).toBe("futa01");
    fireEvent.click(generate);
    const image = await screen.findByAltText("历史生成结果", {}, { timeout: 10000 });
    expect(image.getAttribute("src")).toBe("blob:done");
    expect(screen.getByText("1 张")).toBeTruthy();
  }, 15000);

  it("cancels a queued generation from its compact close button", async () => {
    render(<ComfyLocalWorkbench />);
    const generate = await screen.findByRole("button", { name: "开始生成" });
    await waitFor(() => expect(generate.disabled).toBe(false));
    fireEvent.click(generate);
    const cancel = await screen.findByRole("button", { name: `取消任务 ${PROMPT_ID}` });
    fireEvent.click(cancel);
    await waitFor(() => expect(cancelledTask).toBe(PROMPT_ID));
    expect(screen.queryByRole("button", { name: `取消任务 ${PROMPT_ID}` })).toBeNull();
  });

  it("edits workflow dimensions and cuts selected results into a chosen folder", async () => {
    render(<ComfyLocalWorkbench />);
    const generate = await screen.findByRole("button", { name: "开始生成" });
    await waitFor(() => expect(generate.disabled).toBe(false));

    fireEvent.change(screen.getByRole("combobox", { name: "最终输出尺寸" }), { target: { value: "custom" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "宽度" }), { target: { value: "960" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "高度" }), { target: { value: "1600" } });

    fireEvent.click(generate);
    const image = await screen.findByAltText("历史生成结果", {}, { timeout: 10000 });
    fireEvent.click(screen.getByRole("button", { name: "选择" }));
    fireEvent.click(image.closest("button"));
    fireEvent.click(screen.getByRole("button", { name: "加入待处理 1" }));
    await waitFor(() => expect(moved).toBe(true));
    await waitFor(() => expect(screen.queryByAltText("历史生成结果")).toBeNull());
  }, 15000);

  it("selects individual images independently when one task contains multiple outputs", async () => {
    multiImageGallery = true;
    render(<ComfyLocalWorkbench />);
    const images = await screen.findAllByAltText("历史生成结果");
    const first = images[0].closest("button");
    const second = images[1].closest("button");
    fireEvent.click(screen.getByRole("button", { name: "选择" }));

    fireEvent.click(first);
    expect(first.dataset.selected).toBe("true");
    expect(second.dataset.selected).toBe("false");
    expect(screen.getByRole("button", { name: "删除 1" })).toBeTruthy();

    fireEvent.click(second);
    expect(first.dataset.selected).toBe("true");
    expect(second.dataset.selected).toBe("true");
    expect(screen.getByRole("button", { name: "删除 2" })).toBeTruthy();

    fireEvent.click(first);
    expect(first.dataset.selected).toBe("false");
    expect(second.dataset.selected).toBe("true");
    expect(screen.getByRole("button", { name: "删除 1" })).toBeTruthy();
  });

  it("shows and loads workflow parameters immediately when switching workflows without refreshing the gallery", async () => {
    render(<ComfyLocalWorkbench />);
    const workflowSelect = await screen.findByRole("combobox", { name: "当前生成工作流" });
    await waitFor(() => expect(workflowSelect.value).toBe("futa01"));
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    const prompt = screen.getByRole("textbox", { name: "正向提示词" });
    expect(workflowSelect.compareDocumentPosition(prompt) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    fireEvent.change(prompt, { target: { value: "keep this prompt" } });
    const requestsBeforeSwitch = galleryRequests;
    fireEvent.change(workflowSelect, { target: { value: "futa02" } });
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    const currentPrompt = screen.getByRole("textbox", { name: "正向提示词" });
    expect(currentPrompt.value).toBe("changed by workflow");
    const advancedPanel = screen.getByText("高级选项").closest("details");
    expect(advancedPanel.open).toBe(false);
    expect(advancedPanel.contains(screen.getByRole("spinbutton", { name: "Steps" }))).toBe(true);
    expect(advancedPanel.contains(currentPrompt)).toBe(false);
    expect(screen.getByRole("spinbutton", { name: "生成数量" }).value).toBe("3");
    fireEvent.click(screen.getByRole("button", { name: "固定种子" }));
    expect(screen.getByRole("spinbutton", { name: "Seed" }).value).toBe("99");
    expect(screen.getByRole("spinbutton", { name: "Steps" }).value).toBe("42");
    expect(screen.getByRole("spinbutton", { name: "CFG" }).value).toBe("7");
    expect(screen.getByRole("combobox", { name: "Sampler" }).value).toBe("euler");
    expect(screen.getByRole("combobox", { name: "Scheduler" }).value).toBe("karras");
    expect(screen.getByRole("spinbutton", { name: "denoise" }).value).toBe("1");
    expect(screen.getByRole("spinbutton", { name: "secondPassSteps" }).value).toBe("22");
    expect(screen.getByRole("spinbutton", { name: "secondPassDenoise" }).value).toBe("0.28");
    expect(screen.getByRole("spinbutton", { name: "secondPassSeed" }).value).toBe("12");
    expect(screen.queryByRole("textbox", { name: "新方案名称" })).toBeNull();
    expect(screen.queryByRole("button", { name: "保存当前配置" })).toBeNull();
    expect(galleryRequests).toBe(requestsBeforeSwitch);
  });

  it("loads each gallery once and reuses its image addresses when switching tabs", async () => {
    submitted = true;
    render(<ComfyLocalWorkbench />);
    await screen.findByAltText("历史生成结果");
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "我的资产" }));
    await waitFor(() => expect(assetRequests).toBe(1));
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(2));

    fireEvent.click(screen.getByRole("button", { name: "本机图片" }));
    await waitFor(() => expect(screen.getByAltText("历史生成结果")).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "我的资产" }));
    await waitFor(() => expect(screen.getByAltText("历史生成结果")).toBeTruthy());

    expect(galleryRequests).toBe(1);
    expect(assetRequests).toBe(1);
    expect(galleryRequestUrls).toEqual(["http://127.0.0.1:32145/api/gallery?page=1&pageSize=100"]);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(2);
  });

  it("removes migration from asset actions and toggles right-click select all", async () => {
    render(<ComfyLocalWorkbench />);
    fireEvent.click(await screen.findByRole("button", { name: "我的资产" }));
    const image = await screen.findByAltText("历史生成结果");

    fireEvent.contextMenu(image.closest("button"));
    expect(screen.queryByRole("button", { name: "迁移" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "全选" }));
    expect(image.closest("button").dataset.selected).toBe("true");

    fireEvent.contextMenu(image.closest("button"));
    fireEvent.click(within(document.querySelector(".image-context-menu")).getByRole("button", { name: "取消全选" }));
    expect(image.closest("button").dataset.selected).toBe("false");

    fireEvent.contextMenu(image.closest("button"));
    fireEvent.click(screen.getByRole("button", { name: "详细" }));
    const promptFields = screen.getAllByRole("textbox");
    await waitFor(() => {
      const readOnly = promptFields.filter((field) => field.readOnly).map((field) => field.value);
      expect(readOnly).toHaveLength(2);
      readOnly.forEach((v) => expect(typeof v).toBe("string"));
    });
  });

  it("paginates local images in pages of 100", async () => {
    submitted = true;
    localGalleryPages = 2;
    render(<ComfyLocalWorkbench />);

    expect(await screen.findByText("第 1 / 2 页 · 每页 100 张")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "下一页" }));

    await waitFor(() => expect(galleryRequests).toBe(2));
    expect(screen.getByText("第 2 / 2 页 · 每页 100 张")).toBeTruthy();
    expect(galleryRequestUrls).toEqual([
      "http://127.0.0.1:32145/api/gallery?page=1&pageSize=100",
      "http://127.0.0.1:32145/api/gallery?page=2&pageSize=100",
    ]);
  });

  it("submits the workflow explicitly selected by the user", async () => {
    render(<ComfyLocalWorkbench />);
    const workflowSelect = await screen.findByRole("combobox", { name: "当前生成工作流" });
    fireEvent.change(workflowSelect, { target: { value: "futa02" } });
    fireEvent.click(screen.getByRole("button", { name: "开始生成" }));
    await waitFor(() => expect(submittedWorkflow?.id).toBe("futa02"));
    expect(submittedWorkflow.name).toBe("Futa 02 · 透明双输出");
    expect(submittedWorkflow.definition).toContain("WorkflowTwoMarker");
  });

  it("loads Prompt schemes by stable backend id", async () => {
    render(<ComfyLocalWorkbench />);
    const schemes = await screen.findByRole("combobox", { name: "Prompt 方案" });
    const chooser = screen.getByRole("region", { name: "选择工作流" });
    const parameters = screen.getByRole("region", { name: "工作流参数" });
    expect(chooser.contains(schemes)).toBe(true);
    expect(chooser.compareDocumentPosition(parameters) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByRole("button", { name: "另存为方案" })).toBeTruthy();
    expect(screen.queryByRole("textbox", { name: "新 Prompt 方案名称" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "另存为方案" }));
    expect(screen.getByRole("textbox", { name: "新 Prompt 方案名称" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "关闭另存为方案" }));
    expect(screen.getByRole("button", { name: "覆盖方案" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "重新加载当前方案" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "编辑当前方案" })).toBeTruthy();
    expect(screen.queryByLabelText("正向提示词")).toBeNull();
    expect(screen.queryByLabelText("反向提示词")).toBeNull();
    const btns = screen.getAllByRole("button", { name: "手动编辑" });
    fireEvent.click(btns[0]);
    fireEvent.click(btns[1]);
    expect(screen.getByRole("textbox", { name: "正向提示词" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "反向提示词" })).toBeTruthy();
    expect(screen.getByRole("option", { name: "扶她0" }).value).toBe("2");
    fireEvent.change(schemes, { target: { value: "2" } });
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    const positivePrompt = screen.getByRole("textbox", { name: "正向提示词" });
    expect(positivePrompt.value).toBe("preset prompt");
    fireEvent.change(positivePrompt, { target: { value: "manual draft" } });
    presetPositivePrompt = "updated preset prompt";
    fireEvent.click(screen.getByRole("button", { name: "重新加载当前方案" }));
    await waitFor(() => expect(screen.getByRole("textbox", { name: "正向提示词" }).value).toBe("updated preset prompt"));
  });

  it("keeps every Prompt scheme available after switching workflows", async () => {
    render(<ComfyLocalWorkbench />);
    let schemes = await screen.findByRole("combobox", { name: "Prompt 方案" });
    expect(Array.from(schemes.options).some((option) => option.text === "扶她0")).toBe(true);
    fireEvent.change(screen.getByRole("combobox", { name: "当前生成工作流" }), { target: { value: "futa02" } });
    schemes = screen.getByRole("combobox", { name: "Prompt 方案" });
    expect(Array.from(schemes.options).some((option) => option.text === "扶她0")).toBe(true);
  });

  it("automatically applies the scheme marked as default", async () => {
    presetIsDefault = true;
    render(<ComfyLocalWorkbench />);
    await waitFor(() => expect(screen.getByRole("combobox", { name: "Prompt 方案" }).value).toBe("2"));
    await waitFor(() => {
      const toggle = screen.getAllByRole("button", { name: "手动编辑" })[0];
      if (toggle.getAttribute("aria-expanded") === "false") fireEvent.click(toggle);
      expect(screen.getByRole("textbox", { name: "正向提示词" }).value).toBe("preset prompt");
    });
  });

  it("appends an external ComfyUI result without recreating its cached image", async () => {
    externalRun = true;
    render(<ComfyLocalWorkbench />);
    const image = await screen.findByAltText("历史生成结果", {}, { timeout: 12000 });
    expect(image.getAttribute("src")).toBe("blob:done");
    expect(galleryRequests).toBeGreaterThanOrEqual(1);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  }, 15000);

  it("keeps a completed result in the local gallery even while assets are open", async () => {
    externalRun = true;
    render(<ComfyLocalWorkbench />);
    fireEvent.click(await screen.findByRole("button", { name: "我的资产" }));
    await screen.findByAltText("历史生成结果");

    await waitFor(() => expect(URL.createObjectURL.mock.calls.length).toBeGreaterThanOrEqual(2), { timeout: 12000 });
    expect(galleryRequests).toBe(1);
    fireEvent.click(screen.getByRole("button", { name: "本机图片" }));

    expect(await screen.findByAltText("历史生成结果")).toBeTruthy();
  }, 15000);

  it("inserts only the new bridge history result without reloading the gallery", async () => {
    submitted = true;
    incrementalHistoryRun = true;
    render(<ComfyLocalWorkbench />);

    expect(await screen.findByText("1 张")).toBeTruthy();
    await waitFor(() => expect(screen.getAllByAltText("历史生成结果")).toHaveLength(2), { timeout: 12000 });

    const recentHistoryRequests = bridgeRequests.filter((url) => url.includes("/comfy/history?max_items=20"));
    const incrementalImageRequests = bridgeRequests.filter((url) => url.includes("/comfy/view?") && url.includes("incremental.png"));
    expect(recentHistoryRequests.length).toBeGreaterThanOrEqual(2);
    expect(incrementalImageRequests).toHaveLength(1);
    expect(galleryRequests).toBe(1);
    expect(galleryRequestUrls).toEqual(["http://127.0.0.1:32145/api/gallery?page=1&pageSize=100"]);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(2);
    expect(screen.getByText("2 张")).toBeTruthy();
    const tiles = Array.from(document.querySelectorAll(".local-image-tile"));
    expect(tiles).toHaveLength(2);
    expect(tiles[0].dataset.galleryEntryId).toBe("incremental-prompt");
    expect(tiles[0].dataset.imagePath).toBe("aimaid/incremental.png");
    expect(tiles[1].dataset.galleryEntryId).toBe(PROMPT_ID);
    expect(tiles[1].dataset.imagePath).toBe("aimaid/done.png");
  }, 15000);

  it("reconciles a completed bridge result already present on the first history poll", async () => {
    submitted = true;
    incrementalHistoryAlreadyPresent = true;
    render(<ComfyLocalWorkbench />);

    await waitFor(() => {
      const incrementalTile = document.querySelector('[data-gallery-entry-id="incremental-prompt"]');
      expect(incrementalTile).not.toBeNull();
      expect(incrementalTile.dataset.imagePath).toBe("aimaid/incremental.png");
    }, { timeout: 12000 });

    const tiles = Array.from(document.querySelectorAll(".local-image-tile"));
    expect(tiles).toHaveLength(2);
    expect(tiles[0].dataset.galleryEntryId).toBe("incremental-prompt");
    expect(tiles[1].dataset.galleryEntryId).toBe(PROMPT_ID);
    expect(galleryRequests).toBe(1);
    expect(bridgeRequests.filter((url) => url.includes("/comfy/view?") && url.includes("incremental.png"))).toHaveLength(1);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(2);
    expect(screen.getByText("2 张")).toBeTruthy();
  }, 15000);

  it("does not duplicate a first-poll bridge result whose image address is already loaded", async () => {
    submitted = true;
    existingHistoryAlreadyPresent = true;
    render(<ComfyLocalWorkbench />);

    await waitFor(() => expect(
      bridgeRequests.filter((url) => url.includes("/comfy/history?max_items=20")).length,
    ).toBeGreaterThanOrEqual(2), { timeout: 12000 });

    const tiles = Array.from(document.querySelectorAll(".local-image-tile"));
    expect(tiles).toHaveLength(1);
    expect(tiles[0].dataset.galleryEntryId).toBe(PROMPT_ID);
    expect(tiles[0].dataset.imagePath).toBe("aimaid/done.png");
    expect(bridgeRequests.filter((url) => url.includes("/comfy/view?"))).toHaveLength(0);
    expect(galleryRequests).toBe(1);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(screen.getByText("1 张")).toBeTruthy();
  }, 15000);

  it("shows every active task in ComfyUI execution order even when progress lookup fails", async () => {
    progressFails = true;
    customQueue = {
      queue_running: [[90, "running-task", workflow.definition, {}, ["7"]]],
      // ComfyUI exposes its heap here; only the numeric priority is authoritative.
      queue_pending: [
        [30, "queued-third", workflow.definition, {}, ["7"]],
        [10, "queued-first", workflow.definition, {}, ["7"]],
        [20, "queued-second", workflow.definition, {}, ["7"]],
      ],
    };
    render(<ComfyLocalWorkbench />);
    await screen.findByText("当前 4 个任务");
    const taskIds = Array.from(document.querySelectorAll(".queue-pill")).map((item) => item.title.split(" · ").pop());
    expect(taskIds).toEqual(["running-task", "queued-first", "queued-second", "queued-third"]);
    expect(screen.getByRole("button", { name: "开始生成" }).disabled).toBe(false);
    expect(screen.getByText("读取中")).toBeTruthy();
    expect(screen.queryByText(/查询当前任务失败/)).toBeNull();
  });

  it("splits a large requested quantity into single-image queue submissions", async () => {
    render(<ComfyLocalWorkbench />);
    const quantity = await screen.findByRole("spinbutton", { name: "生成数量" });
    fireEvent.change(quantity, { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: "开始生成" }));
    await waitFor(() => expect(generateRequests).toBe(3));
    expect(submittedBatchSizes).toEqual(["1", "1", "1"]);
  });

  it("builds a weighted prompt from image assets before lucky generation", async () => {
    render(<ComfyLocalWorkbench />);
    await screen.findByRole("button", { name: "手气不错" });
    fireEvent.click(screen.getByRole("button", { name: "手气不错" }));
    await waitFor(() => expect(generateRequests).toBe(1));
    expect(submittedWorkflow.positivePrompt).toContain("black pantyhose");
    expect(submittedWorkflow.negativePrompt).toContain("watermark");
  });

  it("advances atomically to the next image after deleting the current large image", async () => {
    multiImageGallery = true;
    render(<ComfyLocalWorkbench />);
    const images = await screen.findAllByAltText("历史生成结果");
    fireEvent.click(images[0].closest("button"));
    expect(screen.getByText("1 / 2")).toBeTruthy();
    expect(screen.getByText("done.png")).toBeTruthy();

    fireEvent.contextMenu(document.querySelector(".history-lightbox"));
    fireEvent.click(screen.getByRole("button", { name: "删除" }));

    await waitFor(() => expect(deletedPaths).toEqual(["aimaid/done.png"]));
    expect(screen.queryByText("确认删除")).toBeNull();
    expect(screen.getByText("done-2.png")).toBeTruthy();
    expect(screen.getByText("1 / 1")).toBeTruthy();
    expect(galleryRequests).toBe(1);
  });

  it("falls back to the previous image after deleting the last large image", async () => {
    multiImageGallery = true;
    render(<ComfyLocalWorkbench />);
    const images = await screen.findAllByAltText("历史生成结果");
    fireEvent.click(images[0].closest("button"));
    fireEvent.click(screen.getByRole("button", { name: "下一张图片" }));
    expect(screen.getByText("2 / 2")).toBeTruthy();

    fireEvent.contextMenu(document.querySelector(".history-lightbox"));
    fireEvent.click(screen.getByRole("button", { name: "删除" }));

    await waitFor(() => expect(deletedPaths).toEqual(["aimaid/done-2.png"]));
    expect(screen.getByText("done.png")).toBeTruthy();
    expect(screen.getByText("1 / 1")).toBeTruthy();
  });

  it("copies and deletes the current large image from buttons, shortcuts, and the context menu", async () => {
    multiImageGallery = true;
    render(<ComfyLocalWorkbench />);
    const images = await screen.findAllByAltText("历史生成结果");
    fireEvent.click(images[0].closest("button"));

    fireEvent.click(screen.getByRole("button", { name: "复制当前图片" }));
    await waitFor(() => expect(navigator.clipboard.write).toHaveBeenCalledTimes(1));

    fireEvent.keyDown(window, { key: "c", ctrlKey: true });
    await waitFor(() => expect(navigator.clipboard.write).toHaveBeenCalledTimes(2));

    fireEvent.keyDown(window, { key: "c", metaKey: true });
    await waitFor(() => expect(navigator.clipboard.write).toHaveBeenCalledTimes(3));

    fireEvent.contextMenu(document.querySelector(".history-lightbox"));
    fireEvent.click(screen.getByRole("button", { name: "复制图片" }));
    await waitFor(() => expect(navigator.clipboard.write).toHaveBeenCalledTimes(4));

    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    fireEvent.keyDown(screen.getByRole("textbox", { name: "正向提示词" }), { key: "Delete" });
    expect(screen.queryByText("只删除当前这张本机图片？此操作不可恢复。")).toBeNull();

    fireEvent.keyDown(window, { key: "Delete" });
    await waitFor(() => expect(deletedPaths).toEqual(["aimaid/done.png"]));
  });

  it("transfers an image to the server folder from its right-click menu", async () => {
    render(<ComfyLocalWorkbench />);
    fireEvent.click(await screen.findByRole("button", { name: "我的资产" }));
    const image = await screen.findByAltText("历史生成结果");
    fireEvent.contextMenu(image.closest("button"));
    fireEvent.click(screen.getByRole("button", { name: "转到文件中转站" }));
    await waitFor(() => expect(transferredFileName).toBe("saved.png"));
    expect(screen.getByText("已转到文件中转站：saved.png")).toBeTruthy();
  }, 15000);

  it("asks Bridge to cancel every active generation from one native button", async () => {
    render(<ComfyLocalWorkbench />);
    const generate = await screen.findByRole("button", { name: "开始生成" });
    await waitFor(() => expect(generate.disabled).toBe(false));
    fireEvent.click(generate);
    const cancelAll = await screen.findByRole("button", { name: "取消全部 1" });
    fireEvent.click(cancelAll);
    await waitFor(() => expect(cancelAllRequests).toBe(1));
    expect(screen.getByText("Bridge 已接收全部 1 个任务的取消请求")).toBeTruthy();
  });

  it("uses End to send the current local image to pending without a dialog", async () => {
    multiImageGallery = true;
    render(<ComfyLocalWorkbench />);
    const [image] = await screen.findAllByAltText("历史生成结果");
    fireEvent.click(image.closest("button"));
    fireEvent.keyDown(window, { key: "End" });
    await waitFor(() => expect(registeredAssetStatus).toBe("PENDING"));
    expect(screen.queryByText("确认删除")).toBeNull();
  });

  it("uses PageDown to send the current local image directly to assets", async () => {
    multiImageGallery = true;
    render(<ComfyLocalWorkbench />);
    const [image] = await screen.findAllByAltText("历史生成结果");
    fireEvent.click(image.closest("button"));
    fireEvent.keyDown(window, { key: "PageDown" });
    await waitFor(() => expect(registeredAssetStatus).toBe("ACTIVE"));
  });

  it("shows a unified recycle bin and confirms only permanent deletion there", async () => {
    render(<ComfyLocalWorkbench />);
    fireEvent.click(await screen.findByRole("button", { name: "回收站" }));
    const image = await screen.findByAltText("历史生成结果");
    fireEvent.contextMenu(image.closest("button"));
    fireEvent.click(screen.getByRole("button", { name: "永久删除" }));
    expect(screen.getByText("永久删除当前图片？此操作不可恢复。")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(screen.queryByAltText("历史生成结果")).toBeNull());
  });

  it("shows node details to the right of the local and asset tabs", async () => {
    externalRun = true;
    render(<ComfyLocalWorkbench />);
    const progress = await screen.findByRole("status");
    expect(progress.textContent).toContain("节点 4");
    expect(progress.textContent).toContain("节点 5/10");
    expect(progress.textContent).toContain("总进度");
    expect(progress.closest(".gallery-source-row")?.querySelector(".gallery-source-tabs")).not.toBeNull();
  });

  it("uploads an asset-backed Twitter task with its asset id", async () => {
    render(<ComfyLocalWorkbench />);
    fireEvent.click(await screen.findByRole("button", { name: "我的资产" }));
    const image = await screen.findByAltText("历史生成结果");
    fireEvent.click(screen.getByRole("button", { name: "选择" }));
    fireEvent.click(image.closest("button"));
    fireEvent.click(screen.getByRole("button", { name: /添加到 Twitter 任务 1/ }));
    fireEvent.click(await screen.findByRole("button", { name: "保存发布任务" }));
    await waitFor(() => expect(savedTwitterTask).toBeInstanceOf(FormData));
    expect(savedTwitterTask.get("assetIds")).toBe("12");
    expect(savedTwitterTask.get("images").name).toBe("saved.png");
    expect(savedTwitterTask.get("delayMinutes")).toBe("15");
  });

  it("renders the real BiRefNet cutout workflow required parameters immediately", async () => {
    render(<ComfyLocalWorkbench />);
    const workflowSelect = await screen.findByRole("combobox", { name: "当前生成工作流" });
    await waitFor(() => expect(workflowSelect.value).toBe("futa01"));
    fireEvent.change(workflowSelect, { target: { value: "local-a4f23acdd681e785" } });
    expect(screen.queryByRole("combobox", { name: "Prompt 方案" })).toBeNull();
    const source = screen.getByLabelText("待处理原图");
    expect(screen.getByText("高级选项").closest("details").contains(source)).toBe(false);
    const image = new File(["png"], "person.png", { type: "image/png" });
    fireEvent.change(source, { target: { files: [image] } });
    expect(screen.getByText("person.png")).toBeTruthy();
    const filenamePrefix = screen.getByRole("textbox", { name: "filenamePrefix" });
    expect(filenamePrefix.value).toBe("BiRefNet_一键抠图");
    expect(screen.getByText("高级选项").closest("details").contains(filenamePrefix)).toBe(true);
    expect(screen.queryByRole("spinbutton", { name: "Steps" })).toBeNull();
  });

  it("renders a visual mask editor for interactive workflow nodes and submits painted points", async () => {
    const context = { clearRect: vi.fn(), drawImage: vi.fn(), beginPath: vi.fn(), arc: vi.fn(), fill: vi.fn(), stroke: vi.fn() };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(context);
    render(<ComfyLocalWorkbench />);
    const workflowSelect = await screen.findByRole("combobox", { name: "当前生成工作流" });
    fireEvent.change(workflowSelect, { target: { value: "local-sam2" } });
    expect(screen.getByText("区域编辑")).toBeTruthy();
    expect(screen.queryByRole("textbox", { name: "MaskEditMEC 5 · editor_data" })).toBeNull();
    const imageFile = new File(["png"], "person.png", { type: "image/png" });
    fireEvent.change(screen.getByLabelText("待处理原图"), { target: { files: [imageFile] } });
    const editorImage = await screen.findByAltText("区域编辑原图");
    Object.defineProperties(editorImage, { naturalWidth: { value: 512 }, naturalHeight: { value: 512 } });
    fireEvent.load(editorImage);
    const canvas = screen.getByLabelText("涂抹删除区域");
    vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({ left: 0, top: 0, width: 512, height: 512, right: 512, bottom: 512, x: 0, y: 0, toJSON() {} });
    fireEvent.pointerDown(canvas, { clientX: 200, clientY: 220, pointerId: 1 });
    fireEvent.pointerUp(canvas, { pointerId: 1 });
    fireEvent.click(screen.getByRole("button", { name: "开始生成" }));
    await waitFor(() => expect(submittedEditorData).toContain('"label":1'));
  });
});

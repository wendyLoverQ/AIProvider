import { useEffect, useRef, useState } from "react";
import {
  CheckCircle,
  Copy,
  CaretLeft,
  CaretRight,
  ArrowCounterClockwise,
  ArrowClockwise,
  ImageSquare,
  Info,
  FolderOpen,
  MagnifyingGlassMinus,
  MagnifyingGlassPlus,
  Play,
  PaperPlaneTilt,
  Power,
  SpinnerGap,
  Trash,
  Warning,
  X,
} from "@phosphor-icons/react";
import { TransformComponent, TransformWrapper } from "react-zoom-pan-pinch";
import "./ComfyLocalWorkbench.css";
import WorkflowPanel from "./WorkflowPanel";
import DynamicShowcase from "./DynamicShowcase";
import { generateId } from "./utils/generateId";
import { applySchemeToWorkflow, createComfyProgressPlan, createWorkflowForm, describeComfyProgress, FALLBACK_FORM, findFinalOutput, getWorkflowFieldKeys, getWorkflowRevision, normalizeFolder, refreshWorkflowForm } from "./comfy/workbench";

const BRIDGE = "http://127.0.0.1:32145";
const initial = FALLBACK_FORM;
const VIEWER_ZOOM_STEP = 0.2;
const imageSelectionKey = (item, image) =>
  `${item.id}::${image.path || image.url || image.filename || "image"}`;
const fileSize = (value) => {
  const bytes = Number(value || 0);
  if (!bytes) return "-";
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
};
const parseLoras = (value) => {
  if (Array.isArray(value)) return value;
  if (!value) return [];
  try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed : []; }
  catch { return []; }
};
const loraDisplayName = (value) => String(value || "").replace(/\\/g, "/").split("/").pop().replace(/\.(safetensors|ckpt|pt)$/i, "");
const deadline = (ms = 8000) => AbortSignal.timeout(ms);
async function readJson(response, label) {
  const text = await response.text();
  if (!text.trim())
    throw new Error(`${label}返回空响应（HTTP ${response.status}）`);
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`${label}返回了无效 JSON（HTTP ${response.status}）`);
  }
}
async function mapLimit(items, limit, mapper) {
  const results = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await mapper(items[index], index);
    }
  });
  await Promise.all(workers);
  return results;
}
async function detectImageTransparency(url) {
  const blob = await (await fetch(url)).blob();
  if (/jpe?g/i.test(blob.type)) return false;
  const bitmap = await createImageBitmap(blob);
  try {
    const scale = Math.min(1, 192 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext("2d", { willReadFrequently: true });
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    for (let index = 3; index < pixels.length; index += 4) if (pixels[index] < 250) return true;
    return false;
  } finally {
    bitmap.close?.();
  }
}

export default function ComfyLocalWorkbench({ mode = "workbench", active = true }) {
  const [token, setToken] = useState(""),
    [launcher, setLauncher] = useState("checking"),
    [running, setRunning] = useState(false),
    [platform, setPlatform] = useState("Windows"),
    [platformConfigured, setPlatformConfigured] = useState(true),
    [expectedComfyDirectory, setExpectedComfyDirectory] = useState("");
  const [form, setForm] = useState(initial),
    [referenceFiles, setReferenceFiles] = useState({}),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [controlAction, setControlAction] = useState("");
  const [tasks, setTasks] = useState([]),
    [results, setResults] = useState([]),
    [cancelingTask, setCancelingTask] = useState("");
  const [history, setHistory] = useState([]);
  const [galleryMode, setGalleryMode] = useState("output");
  const [galleryWorkflowFilter, setGalleryWorkflowFilter] = useState("all");
  const [galleryTransparencyFilter, setGalleryTransparencyFilter] = useState("all");
  const [assetPage, setAssetPage] = useState({ page: 1, pages: 0, total: 0 });
  const [detail, setDetail] = useState(null);
  const [detailIndex, setDetailIndex] = useState(0);
  const [infoDetail, setInfoDetail] = useState(null);
  const [imageMenu, setImageMenu] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedImages, setSelectedImages] = useState(() => new Set());
  const [presets, setPresets] = useState([]),
    [selectedPresetId, setSelectedPresetId] = useState(""),
    [appliedPresetTitle, setAppliedPresetTitle] = useState("");
  const [presetQuery, setPresetQuery] = useState("");
  const [presetSaveName, setPresetSaveName] = useState("");
  const [presetSaving, setPresetSaving] = useState(false);
  const [folders, setFolders] = useState([]),
    [folder, setFolder] = useState(() => localStorage.getItem("comfy_output_folder") || "aimaid"),
    [migrationNewFolder, setMigrationNewFolder] = useState(""),
    [migrationDirectory, setMigrationDirectory] = useState("C:\\Users\\49213\\Desktop\\A\\ai成品");
  const [workflows, setWorkflows] = useState([]);
  const [loraModels, setLoraModels] = useState([]);
  const [loraModelsLoading, setLoraModelsLoading] = useState(false);
  const [workflowDirectory, setWorkflowDirectory] = useState("F:\\AI\\ComfyUI_windows_portable_nvidia\\ComfyUI_windows_portable\\ComfyUI\\user\\default\\workflows");
  const [workflowRejected, setWorkflowRejected] = useState([]);
  const [workflowLoading, setWorkflowLoading] = useState(false);
  const [moveDialog, setMoveDialog] = useState(false),
    [moveFolder, setMoveFolder] = useState(""),
    [moveNewFolder, setMoveNewFolder] = useState(""),
    [directMove, setDirectMove] = useState(null);
  const [twitterDialog, setTwitterDialog] = useState(false);
  const [twitterAccounts, setTwitterAccounts] = useState([]);
  const [twitterTask, setTwitterTask] = useState({ accountId: "", content: "", delayMinutes: 15 });
  const [twitterSubmitting, setTwitterSubmitting] = useState(false);
  const polling = useRef(new Map()),
    externalTaskIds = useRef(new Set()),
    externalGalleryRefresh = useRef(null),
    draggedHistoryImage = useRef(null),
    selectedWorkflowIdRef = useRef(""),
    historyLoaded = useRef(false),
    activeLoaded = useRef(false),
    workflowsLoaded = useRef(false),
    workflowVersions = useRef(new Map()),
    workflowRefreshInFlight = useRef(false),
    taskSyncInFlight = useRef(false),
    loadWorkflowsRef = useRef(null),
    defaultPresetApplied = useRef(""),
    viewerTransform = useRef(null);
  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(""), 3000);
    return () => clearTimeout(timer);
  }, [notice]);
  useEffect(() => {
    if (!imageMenu) return undefined;
    const close = () => setImageMenu(null);
    window.addEventListener("click", close);
    window.addEventListener("scroll", close, true);
    return () => { window.removeEventListener("click", close); window.removeEventListener("scroll", close, true); };
  }, [imageMenu]);
  const call = (path, options = {}, ms = 10000, authToken = token) =>
    fetch(`${BRIDGE}${path}`, {
      ...options,
      signal: deadline(ms),
      headers: { "X-Local-Token": authToken, ...(options.headers || {}) },
    });
  const reportLocalError = (scope, exception, details = {}, authToken = token) => {
    if (!authToken) return;
    fetch(`${BRIDGE}/api/logs/client`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Local-Token": authToken },
      body: JSON.stringify({ scope, message: exception?.message || String(exception), ...details }),
      signal: deadline(3000),
    }).catch(() => {});
  };
  const clearActiveTasks = () => {
    polling.current.forEach((timer) => clearInterval(timer));
    polling.current.clear();
    setTasks([]);
    localStorage.removeItem("comfy_active_tasks");
    localStorage.removeItem("comfy_active_task");
  };
  const removeActiveTask = (promptId) => {
    const timer = polling.current.get(promptId);
    if (timer) clearInterval(timer);
    polling.current.delete(promptId);
    setTasks((current) => {
      const next = current.filter((task) => task.id !== promptId);
      localStorage.setItem("comfy_active_tasks", JSON.stringify(next));
      return next;
    });
  };
  const cancelTask = async (task) => {
    const promptId = String(task.id);
    setCancelingTask(promptId);
    setError("");
    try {
      const response = await call(`/api/tasks/${encodeURIComponent(promptId)}/cancel`, { method: "POST" }, 45000);
      const data = await readJson(response, "取消本机任务接口");
      if (!response.ok || !data.success) throw new Error(data.message || `取消任务失败（HTTP ${response.status}）`);
      removeActiveTask(promptId);
      externalTaskIds.current.delete(promptId);
      setNotice(data.cancelled ? "任务已取消" : "任务已结束，已从列表移除");
    } catch (e) {
      setError(`取消任务失败：${e.message}`);
    } finally {
      setCancelingTask("");
    }
  };

  const check = async () => {
    try {
      const configResponse = await fetch(`${BRIDGE}/api/config`, {
        signal: deadline(),
      });
      const statusResponse = await fetch(`${BRIDGE}/api/comfy/status`, {
        signal: deadline(),
      });
      const config = await readJson(configResponse, "本机配置接口"),
        status = await readJson(statusResponse, "本机状态接口");
      setToken(config.token);
      setPlatform(config.platform || status.platform || "Windows");
      setPlatformConfigured(config.configured !== false && status.configured !== false);
      setExpectedComfyDirectory(config.expectedComfyDirectory || status.expectedComfyDirectory || "");
      setLauncher("ready");
      setRunning(Boolean(status.running));
      setError("");
      if (!status.running) {
        clearActiveTasks();
      }
      if (!historyLoaded.current) {
        historyLoaded.current = true;
        loadHistory(config.token).catch((e) => {
          historyLoaded.current = false;
          setError(`读取本机历史失败：${e.message}`);
        });
      }
      if (!activeLoaded.current) {
        activeLoaded.current = true;
        if (status.running) resumeActive(config.token);
        loadFolders(config.token);
        loadMigrationSettings(config.token);
        loadWorkflowSettings(config.token);
        loadPresets();
      }
      if (status.running && !workflowsLoaded.current) {
        workflowsLoaded.current = true;
        loadWorkflows(config.token).catch((e) => {
          workflowsLoaded.current = false;
          setError(`读取本机工作流失败：${e.message}`);
        });
      }
    } catch {
      setLauncher("missing");
      setRunning(false);
      setError(
        "未检测到本机桥接器，请确认 Local ComfyUI Bridge 已随 Windows 启动。",
      );
    }
  };
  useEffect(() => {
    check();
    const id = setInterval(check, 5000);
    const activePolling = polling.current;
    return () => {
      clearInterval(id);
      activePolling.forEach((timer) => clearInterval(timer));
      if (externalGalleryRefresh.current) clearTimeout(externalGalleryRefresh.current);
    };
  }, []);

  const control = async (action) => {
    setBusy(true);
    setControlAction(action);
    setError("");
    setNotice("");
    try {
      const response = await call(
        `/api/comfy/${action}`,
        { method: "POST" },
        130000,
      );
      const data = await readJson(response, `ComfyUI ${action}`);
      if (!response.ok)
        throw new Error(
          data.message || data.detail || `HTTP ${response.status}`,
        );
      setRunning(data.running);
      if (action === "start" || action === "restart") await loadWorkflows(token);
      setNotice(action === "start" ? "ComfyUI 已启动" : action === "restart" ? "ComfyUI 已重启" : "ComfyUI 已停止");
      window.setTimeout(() => setNotice(""), 3500);
    } catch (e) {
      setError(e.name === "TimeoutError" ? "本机启动器响应超时。" : e.message);
    } finally {
      setBusy(false);
      setControlAction("");
    }
  };
  const applyReferenceFile = (key, file) => {
    if (!file) return;
    setReferenceFiles((current) => ({ ...current, [key]: file }));
    if (key === "sourceImage") {
      const editorKeys = activeWorkflowFields.filter((fieldKey) =>
        activeWorkflow?.binding?.fields?.[fieldKey]?.nodeType === "MaskEditMEC" &&
        activeWorkflow?.binding?.fields?.[fieldKey]?.input === "editor_data",
      );
      if (editorKeys.length) setForm((current) => ({ ...current, ...Object.fromEntries(editorKeys.map((fieldKey) => [fieldKey, '{"points":[],"bboxes":[]}'])) }));
    }
  };
  const chooseReference = (key, event) => applyReferenceFile(key, event.target.files?.[0]);
  const startHistoryImageDrag = (event, item, image) => {
    draggedHistoryImage.current = { item, image };
    event.dataTransfer.effectAllowed = "copy";
    event.dataTransfer.setData("text/plain", image.filename || image.path || "history-image");
  };
  const dropHistoryImageAsReference = async (key, event) => {
    event.preventDefault();
    const dragged = draggedHistoryImage.current;
    draggedHistoryImage.current = null;
    if (key !== "sourceImage") return;
    try {
      const droppedFile = [...(event.dataTransfer.files || [])].find((file) =>
        file.type.startsWith("image/") || /\.(png|jpe?g|webp|gif|bmp|avif)$/i.test(file.name),
      );
      if (droppedFile) {
        applyReferenceFile(key, droppedFile);
        setNotice(`已选择待处理原图：${droppedFile.name}`);
        return;
      }
      const externalUrl = (event.dataTransfer.getData("text/uri-list") || "")
        .split(/\r?\n/).find((line) => line && !line.startsWith("#"));
      const imageUrl = dragged?.image?.url || externalUrl;
      if (!imageUrl) throw new Error("没有识别到可用的图片文件");
      const response = await fetch(imageUrl);
      if (!response.ok) throw new Error(`读取图片失败（HTTP ${response.status}）`);
      const blob = await response.blob();
      const filename = dragged?.image?.filename || dragged?.image?.path?.split(/[\\/]/).pop() || decodeURIComponent(externalUrl?.split("/").pop() || "reference.png");
      applyReferenceFile(key, new File([blob], filename, { type: blob.type || "image/png", lastModified: Date.now() }));
      setNotice(`已选择待处理原图：${filename}`);
    } catch (e) {
      setError(`拖入图片失败：${e.message}`);
    }
  };
  const loadPresets = async () => {
    try {
      const response = await fetch("/api/comfy-presets");
      const data = await readJson(response, "参数方案接口");
      setPresets(data.data || []);
    } catch (e) {
      setError(`读取参数方案失败：${e.message}`);
    }
  };
  useEffect(() => {
    if (mode === "workbench" && active) loadPresets();
  }, [active, mode]);
  useEffect(() => {
    if (mode !== "workbench" || !active) return;
    const preset = presets.find((item) => item.defaultPreset);
    const workflow = workflows.find((item) => item.id === form.workflowId);
    if (!preset || !workflow) return;
    const applicationKey = `${preset.id}:${workflow.id}`;
    if (defaultPresetApplied.current === applicationKey) return;
    defaultPresetApplied.current = applicationKey;
    setForm((current) => applySchemeToWorkflow(current, preset, workflow));
    setFolder(normalizeFolder(preset.outputFolder));
    setPresetQuery(String(preset.id));
    setSelectedPresetId(String(preset.id));
    setAppliedPresetTitle(preset.title);
  }, [active, form.workflowId, mode, presets, workflows]);
  const applyPreset = (preset) => {
    setForm((current) => {
      const workflow = workflows.find((item) => item.id === current.workflowId);
      const next = applySchemeToWorkflow(current, preset, workflow);
      return next;
    });
    setFolder(normalizeFolder(preset.outputFolder));
  };
  const applySelectedPreset = () => {
    const preset = presets.find(
      (item) => String(item.id) === selectedPresetId,
    );
    if (!preset) {
      setError("请先选择要应用的参数方案");
      return;
    }
    applyPreset(preset);
    setAppliedPresetTitle(preset.title);
    setError("");
  };
  const choosePreset = (value) => {
    setPresetQuery(String(value || ""));
    const preset = presets.find((item) => String(item.id) === String(value));
    if (!preset) return;
    setSelectedPresetId(String(preset.id));
    applyPreset(preset);
    setAppliedPresetTitle(preset.title);
    setPresetSaveName("");
    setError("");
  };
  const savePromptPreset = async (mode) => {
    const selected = presets.find((item) => String(item.id) === String(presetQuery));
    if (mode === "overwrite" && !selected) { setError("请先选择要覆盖的 Prompt 方案"); return; }
    const title = mode === "new" ? presetSaveName.trim() : selected.title;
    if (!title) { setError("请填写新 Prompt 方案名称"); return; }
    setPresetSaving(true);
    setError("");
    try {
      const response = await fetch(mode === "new" ? "/api/comfy-presets" : `/api/comfy-presets/${selected.id}`, {
        method: mode === "new" ? "POST" : "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title,
          outputFolder: selected?.outputFolder || "",
          notes: selected?.notes || "",
          parameters: {
            positivePrompt: form.positivePrompt ?? "",
            negativePrompt: form.negativePrompt ?? "",
          },
        }),
      });
      const data = await readJson(response, mode === "new" ? "新建 Prompt 方案接口" : "覆盖 Prompt 方案接口");
      if (!response.ok || data.code !== 200) throw new Error(data.message || `HTTP ${response.status}`);
      const savedId = mode === "new" ? data.data?.id : selected.id;
      await loadPresets();
      setPresetQuery(String(savedId));
      setSelectedPresetId(String(savedId));
      setAppliedPresetTitle(title);
      setPresetSaveName("");
      setNotice(mode === "new" ? `已保存新 Prompt 方案：${title}` : `已覆盖 Prompt 方案：${title}`);
    } catch (e) {
      setError(`保存 Prompt 方案失败：${e.message}`);
    } finally {
      setPresetSaving(false);
    }
  };
  const removePreset = async (preset) => {
    const id = preset.id;
    if (!window.confirm("删除这个参数方案？")) return;
    await fetch(`/api/comfy-presets/${id}`, { method: "DELETE" });
    await loadPresets();
  };
  const loadFolders = async (authToken = token) => {
    try {
      const response = await call("/api/folders", {}, 10000, authToken);
      const data = await readJson(response, "本机文件夹接口");
      setFolders(data.folders || []);
    } catch (e) {
      setError(`读取本机文件夹失败：${e.message}`);
    }
  };
  const loadMigrationSettings = async (authToken = token) => {
    const response = await call("/api/migration/settings", {}, 10000, authToken);
    const data = await readJson(response, "迁移目录接口");
    if (response.ok && data.directory) setMigrationDirectory(data.directory);
  };
  const saveMigrationSettings = async () => {
    const response = await call("/api/migration/settings", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ directory: migrationDirectory }),
    });
    const data = await readJson(response, "迁移目录保存接口");
    if (!response.ok) throw new Error(data.message || "保存迁移目录失败");
    setMigrationDirectory(data.directory);
    setNotice(`迁移目录已保存：${data.directory}`);
  };
  const loadWorkflowSettings = async (authToken = token) => {
    const response = await call("/api/local-workflows/settings", {}, 10000, authToken);
    const data = await readJson(response, "本机工作流目录接口");
    if (data.directory) setWorkflowDirectory(data.directory);
  };
  const loadWorkflows = async (authToken = token) => {
    if (workflowRefreshInFlight.current) return;
    workflowRefreshInFlight.current = true;
    setWorkflowLoading(true);
    try {
      const response = await call("/api/local-workflows", {}, 120000, authToken);
      const data = await readJson(response, "本机工作流接口");
      if (!response.ok) throw new Error(data.message || "读取本机工作流失败");
      const next = data.workflows || [];
      setWorkflowDirectory(data.directory || workflowDirectory);
      setWorkflowRejected(data.rejected || []);
      setWorkflows(next);
      workflowsLoaded.current = true;
      if (next.length) setForm((current) => {
        const requestedId = selectedWorkflowIdRef.current || current.workflowId;
        const selected = next.find((item) => item.id === requestedId);
        if (!selected && selectedWorkflowIdRef.current) return current;
        const resolved = selected || next[0];
        selectedWorkflowIdRef.current = resolved.id;
        return refreshWorkflowForm(current, resolved, workflowVersions.current.get(resolved.id));
      });
      workflowVersions.current = new Map(next.map((workflow) => [workflow.id, getWorkflowRevision(workflow)]));
    } catch (e) {
      workflowsLoaded.current = false;
      throw e;
    } finally {
      workflowRefreshInFlight.current = false;
      setWorkflowLoading(false);
    }
  };
  loadWorkflowsRef.current = loadWorkflows;
  useEffect(() => {
    if (mode !== "workbench" || !active || !token || launcher !== "ready" || !running) return undefined;
    const timer = window.setInterval(() => {
      loadWorkflowsRef.current?.(token).catch(() => { /* A later scan retries automatically. */ });
    }, 10000);
    return () => window.clearInterval(timer);
  }, [active, launcher, mode, running, token]);
  useEffect(() => {
    if (mode !== "workbench" || !active || !token || launcher !== "ready" || !running) return undefined;
    let cancelled = false;
    setLoraModelsLoading(true);
    call("/api/lora-models", {}, 30000, token)
      .then((response) => readJson(response, "LoRA 模型接口").then((data) => {
        if (!response.ok) throw new Error(data.message || `HTTP ${response.status}`);
        if (!cancelled) setLoraModels(data.models || []);
      }))
      .catch((exception) => { if (!cancelled) setError(`读取 LoRA 模型失败：${exception.message}`); })
      .finally(() => { if (!cancelled) setLoraModelsLoading(false); });
    return () => { cancelled = true; };
  }, [active, launcher, mode, running, token]);
  const saveWorkflowDirectory = async () => {
    const response = await call("/api/local-workflows/settings", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ directory: workflowDirectory }),
    });
    const data = await readJson(response, "本机工作流目录保存接口");
    if (!response.ok) throw new Error(data.message || "保存工作流目录失败");
    setWorkflowDirectory(data.directory);
    await loadWorkflows(token);
    setNotice("已切换本机工作流目录");
  };
  const createMigrationFolder = async () => {
    if (!migrationNewFolder.trim()) return;
    const response = await call("/api/migration/folders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: migrationNewFolder.trim() }),
    });
    const data = await readJson(response, "新建迁移文件夹接口");
    if (!response.ok) throw new Error(data.message || "新建迁移文件夹失败");
    setMigrationDirectory(data.directory);
    setMigrationNewFolder("");
    setNotice(`已新建并切换迁移文件夹：${data.directory}`);
  };
  const openFolder = async () => {
    const response = await call("/api/folders/open", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: folder }),
    });
    const data = await readJson(response, "打开文件夹接口");
    if (!response.ok) throw new Error(data.message);
  };
  const resumeActive = async (authToken) => {
    const raw = JSON.parse(
      localStorage.getItem("comfy_active_tasks") ||
        localStorage.getItem("comfy_active_task") ||
        "[]",
    );
    const saved = Array.isArray(raw) ? raw : raw?.id ? [raw] : [];
    if (!saved.length) return;
    if (saved[0].form) setForm(saved[0].form);
    setTasks(
      saved.map((item) => ({
        ...item,
        state: item.state || "RUNNING",
        progress: null,
      })),
    );
    saved.forEach((item) => poll(item.id, authToken, item.finalOutputNodeId, item.progressPlan));
    localStorage.removeItem("comfy_active_task");
  };
  const loadHistory = async (authToken, mode = galleryMode, page = 1) => {
    const assets = mode === "assets";
    const response = assets
      ? await fetch(`/api/assets?platform=${encodeURIComponent(platform)}&page=${page}&pageSize=100`, { signal: deadline(30000) })
      : await call("/api/gallery?maxItems=1000", {}, 30000, authToken);
    const data = await readJson(response, assets ? "后端资产目录" : "本机图片目录");
    if (!response.ok || (assets && data.code !== 200)) throw new Error(data.message || `HTTP ${response.status}`);
    const payload = assets ? data.data || {} : data;
    if (assets) setAssetPage({ page: payload.page || page, pages: payload.pages || 0, total: payload.total || 0 });
    const sourceItems = assets ? (payload.items || []).map((item) => ({
      id: `asset-${item.id}`, assetId: item.id, source: "asset", platform: item.platform,
      prompt: item.prompt, negativePrompt: item.negativePrompt, loras: parseLoras(item.lorasJson), seed: item.seed, steps: item.steps,
      cfg: item.cfg, sampler: item.sampler, scheduler: item.scheduler, workflowId: item.workflowId,
      width: item.width, height: item.height, createdAt: item.generatedAt || item.createdAt,
      images: [{ path: item.localPath, localUrl: item.localUrl, filename: item.fileName, sizeBytes: item.fileSize, width: item.width, height: item.height }],
    })) : payload.items || [];
    const entries = await mapLimit(
      sourceItems, 6, async (item) => {
          const settledImages = await Promise.allSettled(
            (item.images || []).map(async (image) => {
              const query = new URLSearchParams({ path: image.path });
              const response = await call(
                `${assets ? "/api/assets/file" : "/api/gallery/file"}?${query}`,
                {},
                30000,
                authToken,
              );
              if (!response.ok) throw new Error("missing image");
              const blob = await response.blob();
              const recordedTransparency = item.form?.generateTransparent ?? item.generateTransparent;
              return { ...image, url: URL.createObjectURL(blob), transparent: typeof recordedTransparency === "boolean" ? recordedTransparency : null };
            }),
          );
          const loadedImages = settledImages
            .filter((result) => result.status === "fulfilled")
            .map((result) => result.value);
          const imageUrl = loadedImages[0]?.url || null;
          return {
            ...item,
            prompt: item.prompt || "",
            count: loadedImages.length,
            imageUrl,
            images: loadedImages,
          };
        },
    );
    setHistory((previous) => {
      previous.forEach((item) =>
        (item.images || []).forEach((image) => URL.revokeObjectURL(image.url)),
      );
      return entries.filter((item) => item.images.length > 0);
    });
  };
  const switchGallery = async (mode) => {
    setGalleryMode(mode);
    setSelectionMode(false);
    setSelectedImages(new Set());
    setGalleryWorkflowFilter("all");
    setGalleryTransparencyFilter("all");
    await loadHistory(token, mode, 1);
  };
  const syncComfyTasks = async (authToken = token) => {
    if (taskSyncInFlight.current) return;
    taskSyncInFlight.current = true;
    try {
    const queueResponse = await call("/comfy/queue", {}, 10000, authToken);
    const queue = await readJson(queueResponse, "ComfyUI queue");
    if (!queueResponse.ok) return;
    const progressResponse = await call("/comfy/aiprovider/progress", {}, 5000, authToken);
    const liveProgress = await readJson(progressResponse, "ComfyUI 实时进度接口");
    if (!progressResponse.ok) throw new Error(liveProgress.message || `ComfyUI 实时进度接口失败（HTTP ${progressResponse.status}）`);
    const rows = [
      ...(queue.queue_running || []).map((row) => ({ row, state: "RUNNING" })),
      ...(queue.queue_pending || []).map((row) => ({ row, state: "QUEUED" })),
    ];
    const nextExternalIds = new Set(rows.filter(({ row }) => Array.isArray(row) && row[1]).map(({ row }) => String(row[1])));
    const completedExternally = [...externalTaskIds.current].some((id) => !nextExternalIds.has(id));
    externalTaskIds.current = nextExternalIds;
    setTasks((current) => {
      const ownTasks = current.filter((task) => !task.external);
      const ownIds = new Set(ownTasks.map((task) => String(task.id)));
      const existingExternal = new Map(current.filter((task) => task.external).map((task) => [String(task.id), task]));
      const externalTasks = rows
        .filter(({ row }) => Array.isArray(row) && row[1] && !ownIds.has(String(row[1])))
        .map(({ row, state }) => {
          const promptId = String(row[1]);
          const progressPlan = createComfyProgressPlan(row[2], row[4]);
          const progressDetail = state === "QUEUED" ? null : describeComfyProgress(liveProgress, promptId, progressPlan);
          const existing = existingExternal.get(promptId);
          return { ...existing, id: promptId, state, progress: progressDetail?.totalPercent ?? 0, progressDetail, progressPlan, external: true, createdAt: existing?.createdAt || new Date().toISOString() };
        });
      return [...ownTasks, ...externalTasks].slice(0, 12);
    });
    if (completedExternally && galleryMode === "output") {
      if (externalGalleryRefresh.current) clearTimeout(externalGalleryRefresh.current);
      externalGalleryRefresh.current = setTimeout(() => {
        loadHistory(authToken, "output").catch(() => {});
      }, 700);
    }
    } finally {
      taskSyncInFlight.current = false;
    }
  };
  useEffect(() => {
    if (!token || !running) return undefined;
    const sync = () => syncComfyTasks(token).catch((e) => {
      reportLocalError("task-sync", e, { path: "/comfy/queue + /comfy/aiprovider/progress" }, token);
      setError(`读取 ComfyUI 实时进度失败：${e.message}`);
    });
    sync();
    const id = setInterval(sync, 1500);
    return () => clearInterval(id);
  }, [token, running]);
  const generate = async () => {
    setBusy(true);
    setError("");
    results.forEach((x) => URL.revokeObjectURL(x.url));
    setResults([]);
    try {
      const body = new FormData();
      Object.entries(form).forEach(([key, value]) => body.append(key, key === "loras" ? JSON.stringify(Array.isArray(value) ? value : []) : String(value)));
      const selectedWorkflowId = selectedWorkflowIdRef.current || form.workflowId;
      const active = workflows.find((item) => item.id === selectedWorkflowId);
      if (!active) throw new Error("工作流尚未从后端加载完成");
      body.set("workflowId", active.id);
      body.set("workflowName", active.name || active.id);
      body.append("workflowDefinition", JSON.stringify(active.definition));
      body.append("workflowBinding", JSON.stringify(active.binding));
      const referenceKeys = [];
      if (active?.capabilities?.inputImage) referenceKeys.push("sourceImage");
      if (active?.capabilities?.styleReference) referenceKeys.push("styleReference1", "styleReference2", "styleReference3", "styleReference4");
      if (active?.capabilities?.poseReference) referenceKeys.push("poseReference");
      const missing = referenceKeys.find((key) => !referenceFiles[key]);
      if (missing) throw new Error(missing === "sourceImage" ? "当前工作流需要先选择待处理原图" : "当前工作流需要先选择全部参考图片");
      const interactiveEditor = activeWorkflowFields.find((fieldKey) =>
        active?.binding?.fields?.[fieldKey]?.nodeType === "MaskEditMEC" &&
        active?.binding?.fields?.[fieldKey]?.input === "editor_data",
      );
      if (interactiveEditor) {
        let editorData;
        try { editorData = JSON.parse(form[interactiveEditor] || "{}"); }
        catch { throw new Error("区域编辑数据无效，请清空后重新涂抹"); }
        if (!(editorData.points?.length || editorData.bboxes?.length)) throw new Error("请先在原图上涂抹需要删除的区域");
      }
      referenceKeys.forEach((key) => body.append(key, referenceFiles[key]));
      body.append("folder", folder);
      body.append("clientId", generateId());
      const response = await call(
          "/api/generate",
          {
            method: "POST",
            body,
          },
          120000,
        );
      const data = await readJson(response, "本机生成接口");
      if (!response.ok || !data.promptId)
        throw new Error(
          data.error?.message ||
            data.message ||
            `ComfyUI 提交失败（HTTP ${response.status}）`,
        );
      const progressPlan = createComfyProgressPlan(active.definition, [data.finalOutputNodeId]);
      const nextTask = {
        id: data.promptId,
        state: "QUEUED",
        progress: 0,
        form: { ...form, workflowId: active.id },
        workflowId: active.id,
        workflowName: active.name || active.id,
        finalOutputNodeId: data.finalOutputNodeId,
        progressPlan,
        progressDetail: null,
        actualSeed: data.actualSeed,
        folder,
        createdAt: new Date().toISOString(),
      };
      setTasks((current) => {
        const next = [nextTask, ...current].slice(0, 8);
        localStorage.setItem(
          "comfy_active_tasks",
          JSON.stringify(
            next.filter(
              (item) => !["SUCCEEDED", "FAILED"].includes(item.state),
            ),
          ),
        );
        return next;
      });
      poll(data.promptId, token, data.finalOutputNodeId, progressPlan);
    } catch (e) {
      reportLocalError("generate", e, { path: "/api/generate" });
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const poll = (promptId, authToken = token, finalOutputNodeId, progressPlan) => {
    if (polling.current.has(promptId)) return;
    const timer = setInterval(async () => {
      try {
        const historyResponse = await call(
            `/comfy/history/${encodeURIComponent(promptId)}`,
            {},
            10000,
            authToken,
          ),
          data = await readJson(historyResponse, "ComfyUI history"),
          item = data[promptId];
        if (!item) {
          const queueResponse = await call("/comfy/queue", {}, 10000, authToken),
            queue = await readJson(queueResponse, "ComfyUI queue"),
            contains = (rows) =>
              Array.isArray(rows) &&
              rows.some(
                (row) => Array.isArray(row) && String(row[1]) === promptId,
              ),
            queued = contains(queue.queue_pending),
            executing = contains(queue.queue_running);
          if (!queued && !executing) {
            removeActiveTask(promptId);
            return;
          }
          let progress = 0;
          let progressDetail = null;
          if (executing) {
            const progressResponse = await call("/comfy/aiprovider/progress", {}, 5000, authToken);
            const liveProgress = await readJson(progressResponse, "ComfyUI 实时进度接口");
            if (!progressResponse.ok) throw new Error(liveProgress.message || `ComfyUI 实时进度接口失败（HTTP ${progressResponse.status}）`);
            progressDetail = describeComfyProgress(liveProgress, promptId, progressPlan);
            progress = progressDetail?.totalPercent ?? null;
            if (progress === null) throw new Error(`实时进度未包含当前任务 ${promptId}`);
          }
          setTasks((current) =>
            current.map((task) =>
              task.id === promptId
                ? {
                    ...task,
                    state: executing ? "RUNNING" : "QUEUED",
                    progress,
                    progressDetail,
                  }
                : task,
            ),
          );
          return;
        }
        clearInterval(timer);
        polling.current.delete(promptId);
        const finalOutput = findFinalOutput(item, finalOutputNodeId);
        if (!finalOutput) throw new Error("历史结果中没有可展示的图片输出");
        const images = finalOutput.images || [];
        const loaded = await Promise.all(
          images.map(async (image) => {
            const query = new URLSearchParams({
              filename: image.filename,
              subfolder: image.subfolder || "",
              type: image.type || "output",
            });
            const blob = await (
              await call(`/comfy/view?${query}`, {}, 30000)
            ).blob();
            return { ...image, url: URL.createObjectURL(blob) };
          }),
        );
        setResults(loaded);
        setTasks((current) => {
          const next = current.map((task) =>
            task.id === promptId
              ? { ...task, state: "SUCCEEDED", progress: 100, progressDetail: null }
              : task,
          );
          localStorage.setItem(
            "comfy_active_tasks",
            JSON.stringify(
              next.filter(
                (task) => !["SUCCEEDED", "FAILED"].includes(task.state),
              ),
            ),
          );
          return next;
        });
        await loadHistory(authToken);
      } catch (e) {
        reportLocalError("task-poll", e, { promptId, path: `/comfy/history/${promptId}` }, authToken);
        try {
          const statusResponse = await fetch(`${BRIDGE}/api/comfy/status`, {
            signal: deadline(),
          });
          const status = await readJson(statusResponse, "本机状态接口");
          if (!status.running) {
            removeActiveTask(promptId);
            setError("");
            return;
          }
        } catch {
          // Bridge 本身不可用时保留任务，避免误删仍在运行的生成。
        }
        setError(`查询本机任务失败：${e.message}`);
      }
    }, 2000);
    polling.current.set(promptId, timer);
  };
  const openHistory = (item, image) => {
    const gallery = history.flatMap((entry) =>
      (entry.images || []).map((candidate) => ({ ...candidate, task: entry })),
    );
    setDetail({ images: gallery });
    setDetailIndex(
      Math.max(
        0,
        gallery.findIndex(
          (candidate) =>
            candidate.task.id === item.id &&
            candidate.filename === image.filename,
        ),
      ),
    );
  };
  const openImageInfo = (item, image) => {
    setImageMenu(null);
    setInfoDetail({ item, image });
  };
  const copyInfo = async (value, label) => {
    if (!value) { setNotice(`${label}未记录`); return; }
    try {
      await navigator.clipboard.writeText(String(value));
      setNotice(`${label}已复制`);
    } catch {
      setNotice(`请选择文本后复制${label}`);
    }
  };
  const closeDetail = () => {
    const shouldRefresh = detail?.refreshOnClose;
    setDetail(null);
    if (shouldRefresh) loadHistory(token).catch((e) => setError(`刷新本机图片失败：${e.message}`));
  };
  const advanceDetailAfterAction = (item, image) => {
    const removedKey = imageSelectionKey(item, image);
    setDetail((current) => {
      if (!current) return current;
      const images = current.images.filter(
        (candidate) => imageSelectionKey(candidate.task, candidate) !== removedKey,
      );
      if (!images.length) {
        setDetailIndex(0);
        return null;
      }
      setDetailIndex((index) => Math.min(index, images.length - 1));
      return { ...current, images, refreshOnClose: true };
    });
  };
  const galleryImages = history.flatMap((item) =>
    (item.images || []).map((image) => ({
      item,
      image,
      key: imageSelectionKey(item, image),
    })),
  );
  const galleryWorkflowOptions = [...new Map(history
    .filter((item) => item.workflowId)
    .map((item) => [item.workflowId, item.workflowName || workflows.find((workflow) => workflow.id === item.workflowId)?.name || item.workflowId])).entries()];
  const filteredGalleryImages = galleryImages.filter(({ item, image }) =>
    (galleryWorkflowFilter === "all" || item.workflowId === galleryWorkflowFilter) &&
    (galleryTransparencyFilter === "all" || (galleryTransparencyFilter === "transparent" ? image.transparent === true : image.transparent === false))
  );
  const transparencyScanKey = galleryTransparencyFilter === "all" ? "" : galleryImages.filter(({ image }) => image.transparent == null).map(({ key }) => key).join("|");
  useEffect(() => {
    if (!transparencyScanKey) return undefined;
    let cancelled = false;
    const pending = galleryImages.filter(({ image }) => image.transparent == null);
    mapLimit(pending, 3, async ({ key, image }) => ({ key, transparent: await detectImageTransparency(image.url).catch(() => false) }))
      .then((detected) => {
        if (cancelled) return;
        const values = new Map(detected.map((entry) => [entry.key, entry.transparent]));
        setHistory((current) => current.map((item) => ({ ...item, images: (item.images || []).map((image) => {
          const value = values.get(imageSelectionKey(item, image));
          return value === undefined ? image : { ...image, transparent: value };
        }) })));
      });
    return () => { cancelled = true; };
  }, [transparencyScanKey]);
  const selectedGalleryImages = () =>
    galleryImages.filter((entry) => selectedImages.has(entry.key));
  const toggleSelected = (item, image) =>
    setSelectedImages((current) => {
      const next = new Set(current);
      const key = imageSelectionKey(item, image);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  const twitterImagesForEntries = (entries) => entries
    .map(({ item, image }) => ({
      path: image.path,
      url: image.url,
      assetId: item.assetId || null,
      source: item.source === "asset" || galleryMode === "assets" ? "asset" : "output",
      fileName: image.filename || image.path?.split(/[\\/]/).pop() || "image",
      contentType: image.filename?.toLowerCase().endsWith(".png") ? "image/png"
        : image.filename?.toLowerCase().endsWith(".webp") ? "image/webp"
          : image.filename?.toLowerCase().endsWith(".gif") ? "image/gif" : "image/jpeg",
      fileSize: Number(image.sizeBytes || 0),
    }));
  const selectedTwitterImages = () => twitterImagesForEntries(selectedGalleryImages());
  const openTwitterTask = async (entries = selectedGalleryImages()) => {
    const images = twitterImagesForEntries(entries);
    if (!images.length) return;
    if (images.length > 4) return setError("Twitter 一条帖子最多选择 4 张图片");
    setError("");
    const response = await fetch("/api/twitter/accounts");
    const result = await readJson(response, "Twitter 账号接口");
    if (!response.ok || result.code !== 200) throw new Error(result.message || "读取 Twitter 账号失败");
    const connected = (result.data || []).filter((account) => account.sessionStatus === "CONNECTED");
    if (!connected.length) throw new Error("请先到 Twitter 发布页面连接当前 Chrome 的 X 账号");
    setTwitterAccounts(connected);
    setTwitterTask((current) => ({ ...current, accountId: String(connected.some((item) => String(item.id) === current.accountId) ? current.accountId : connected[0].id) }));
    setTwitterDialog(true);
  };
  const submitTwitterTask = async () => {
    const images = selectedTwitterImages();
    if (!twitterTask.accountId) return setError("请选择发布账号");
    if (!images.length || images.length > 4) return setError("请选择 1 至 4 张图片");
    setTwitterSubmitting(true);
    setError("");
    try {
      const body = new FormData();
      body.append("accountId", String(Number(twitterTask.accountId)));
      body.append("content", twitterTask.content.trim());
      body.append("delayMinutes", String(Number(twitterTask.delayMinutes)));
      for (const image of images) {
        const imageResponse = await fetch(image.url);
        if (!imageResponse.ok) throw new Error(`读取待保存图片失败：${image.fileName}`);
        body.append("images", await imageResponse.blob(), image.fileName);
        body.append("assetIds", String(image.assetId || 0));
      }
      const response = await fetch("/api/twitter/posts/local-scheduled", {
        method: "POST",
        body,
      });
      const result = await readJson(response, "Twitter 定时任务接口");
      if (!response.ok || result.code !== 200) throw new Error(result.message || "创建 Twitter 任务失败");
      setTwitterDialog(false);
      setSelectionMode(false);
      setSelectedImages(new Set());
      setTwitterTask((current) => ({ ...current, content: "" }));
      setNotice(`Twitter 任务 #${result.data.id} 已保存，${twitterTask.delayMinutes} 分钟后进入发布队列`);
    } catch (e) {
      setError(`创建 Twitter 任务失败：${e.message}`);
    } finally {
      setTwitterSubmitting(false);
    }
  };
  const performDeleteSelected = async () => {
    const deletingAssets = galleryMode === "assets";
    if (!selectedImages.size) return;
    setBusy(true);
    setError("");
    try {
      const selectedEntries = selectedGalleryImages();
      const paths = selectedEntries.map(({ image }) => image.path);
      const response = await call(deletingAssets ? "/api/assets/delete" : "/api/gallery/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ paths }),
      }, 30000);
      const data = await readJson(response, deletingAssets ? "迁移资产删除接口" : "本机图片删除接口");
      if (!response.ok) throw new Error(data.message || "删除失败");
      if (deletingAssets) {
        const backend = await fetch("/api/assets/delete", {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            platform,
            ids: [...new Set(selectedEntries.map(({ item }) => item.assetId).filter(Boolean))],
          }),
        });
        const backendData = await readJson(backend, "后端资产删除接口");
        if (!backend.ok || backendData.code !== 200) throw new Error(backendData.message || "后端资产记录删除失败");
      }
      setSelectedImages(new Set());
      setSelectionMode(false);
      await loadHistory(token);
    } catch (e) {
      setError(`批量删除失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const deleteSelected = () => {
    if (!selectedImages.size) return;
    setDeleteConfirm({
      kind: "selected",
      message: galleryMode === "assets"
        ? `永久删除选中的 ${selectedImages.size} 张资产图片？此操作不可恢复。`
        : `删除选中的 ${selectedImages.size} 张本机图片？`,
    });
  };
  const openMoveDialog = () => {
    if (!selectedImages.size) return;
    moveSelected();
  };
  const createMoveFolder = async () => {
    const name = moveNewFolder.trim();
    if (!name) return;
    const response = await call("/api/folders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
    const data = await readJson(response, "新建迁移文件夹接口");
    if (!response.ok) throw new Error(data.message || "新建文件夹失败");
    await loadFolders();
    setMoveFolder(data.name);
    setMoveNewFolder("");
  };
  const migratePaths = async (paths, viewerEntry = null) => {
    if (!paths.length) return;
    setBusy(true);
    setError("");
    try {
      const response = await call("/api/gallery/move", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ paths }),
      }, 120000);
      const data = await readJson(response, "批量迁移接口");
      if (!response.ok) throw new Error(data.message || "批量迁移失败");
      if (!Array.isArray(data.assets) || !data.assets.length) throw new Error("本机 Agent 版本过旧，图片已迁移但未返回资产登记信息；请更新并重启 Agent 后重试");
      const registerResponse = await fetch("/api/assets/batch", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ platform: data.platform || platform, items: data.assets || [] }),
      });
      const registerData = await readJson(registerResponse, "后端资产保存接口");
      if (!registerResponse.ok || registerData.code !== 200) throw new Error(`图片已迁移，但后端资产保存失败：${registerData.message || registerResponse.status}`);
      setMoveDialog(false);
      setDirectMove(null);
      setSelectedImages(new Set());
      setSelectionMode(false);
      setNotice("迁移成功");
      if (viewerEntry) advanceDetailAfterAction(viewerEntry.item, viewerEntry.image);
      await loadHistory(token);
      await loadFolders(token);
    } catch (e) {
      setError(`批量迁移失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const migrateAssetEntries = async (entries, viewerEntry = null) => {
    if (!entries.length) return;
    setBusy(true);
    setError("");
    try {
      const response = await call("/api/assets/move", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ paths: entries.map(({ image }) => image.path) }),
      }, 120000);
      const data = await readJson(response, "资产迁移接口");
      if (!response.ok || !data.success) throw new Error(data.message || "资产迁移失败");
      const movedAssets = (data.assets || []).map((asset) => {
        const source = entries.find(({ image }) => String(image.path).toLowerCase() === String(asset.oldPath).toLowerCase());
        return {
          ...asset,
          prompt: source?.item.prompt, negativePrompt: source?.item.negativePrompt,
          lorasJson: JSON.stringify(parseLoras(source?.item.loras)), seed: source?.item.seed,
          steps: source?.item.steps, cfg: source?.item.cfg, sampler: source?.item.sampler,
          scheduler: source?.item.scheduler, workflowId: source?.item.workflowId,
          generatedAt: source?.item.createdAt,
        };
      });
      const changed = movedAssets.filter((asset) => String(asset.oldPath).toLowerCase() !== String(asset.localPath).toLowerCase());
      if (changed.length) {
        const registerResponse = await fetch("/api/assets/batch", {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ platform: data.platform || platform, items: changed }),
        });
        const registerData = await readJson(registerResponse, "后端资产保存接口");
        if (!registerResponse.ok || registerData.code !== 200) throw new Error(registerData.message || "迁移后的资产保存失败");
        const movedOldPaths = new Set(changed.map((asset) => String(asset.oldPath).toLowerCase()));
        const ids = entries.filter(({ image }) => movedOldPaths.has(String(image.path).toLowerCase())).map(({ item }) => item.assetId).filter(Boolean);
        if (ids.length) {
          const deleteResponse = await fetch("/api/assets/delete", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ platform, ids }),
          });
          const deleteData = await readJson(deleteResponse, "旧资产记录删除接口");
          if (!deleteResponse.ok || deleteData.code !== 200) throw new Error(deleteData.message || "旧资产记录删除失败");
        }
      }
      setSelectedImages(new Set());
      setSelectionMode(false);
      setNotice(changed.length ? `已迁移 ${changed.length} 张资产图片` : "所选图片已在当前迁移目录");
      if (viewerEntry) advanceDetailAfterAction(viewerEntry.item, viewerEntry.image);
      await loadHistory(token, "assets", assetPage.page);
    } catch (e) {
      setError(`资产迁移失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const moveSelected = async () => {
    const entries = selectedGalleryImages();
    return galleryMode === "assets" ? migrateAssetEntries(entries) : migratePaths(entries.map(({ image }) => image.path));
  };
  const contextEntry = () => imageMenu ? { item: imageMenu.item, image: imageMenu.image, key: imageSelectionKey(imageMenu.item, imageMenu.image) } : null;
  const contextDelete = () => {
    const entry = contextEntry();
    if (!entry) return;
    const fromViewer = imageMenu.viewer;
    setImageMenu(null);
    if (fromViewer) {
      setDeleteConfirm({ kind: "item", item: entry.item, image: entry.image, message: entry.item.source === "asset" ? "永久删除这张资产图片？此操作不可恢复。" : "只删除当前这张本机图片？此操作不可恢复。" });
      return;
    }
    setSelectedImages(new Set([entry.key]));
    setSelectionMode(true);
    setDeleteConfirm({ kind: "selected", message: entry.item.source === "asset" ? "永久删除这张资产图片？此操作不可恢复。" : "删除这张本机图片？此操作不可恢复。" });
  };
  const contextMigrate = () => {
    const entry = contextEntry();
    if (!entry) return;
    const fromViewer = imageMenu.viewer;
    setImageMenu(null);
    if (entry.item.source === "asset") migrateAssetEntries([entry], fromViewer ? entry : null);
    else migratePaths([entry.image.path], fromViewer ? entry : null);
  };
  const contextSelectAll = () => {
    setSelectedImages(new Set(filteredGalleryImages.map((entry) => entry.key)));
    setSelectionMode(true);
    setImageMenu(null);
  };
  const contextTwitter = async () => {
    const entry = contextEntry();
    if (!entry) return;
    setSelectedImages(new Set([entry.key]));
    setSelectionMode(true);
    setImageMenu(null);
    await openTwitterTask([entry]);
  };
  const performDeleteImage = async (item, image) => {
    const deletingAsset = item.source === "asset";
    setBusy(true);
    setError("");
    try {
      const response = await call(deletingAsset ? "/api/assets/delete" : "/api/gallery/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ paths: [image.path] }),
      }, 30000);
      const data = await readJson(response, "本机历史删除接口");
      if (!response.ok)
        throw new Error(data.message || `HTTP ${response.status}`);
      if (deletingAsset) {
        const backend = await fetch("/api/assets/delete", {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ platform, ids: [item.assetId] }),
        });
        const backendData = await readJson(backend, "后端资产删除接口");
        if (!backend.ok || backendData.code !== 200) throw new Error(backendData.message || "后端资产记录删除失败");
      }
      advanceDetailAfterAction(item, image);
      await loadHistory(token);
    } catch (e) {
      setError(`删除失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const confirmDelete = () => {
    const request = deleteConfirm;
    setDeleteConfirm(null);
    if (request?.kind === "selected") performDeleteSelected();
    if (request?.kind === "item") performDeleteImage(request.item, request.image);
  };
  const openImageFolder = async (item, image) => {
    const response = await call(item.source === "asset" ? "/api/assets/open-folder" : "/api/gallery/open-folder", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ path: image.path }),
    });
    const data = await readJson(response, "打开资产所在文件夹接口");
    if (!response.ok) throw new Error(data.message || "打开文件夹失败");
  };
  const set = (key, value) => setForm((x) => ({ ...x, [key]: value }));
  const selectWorkflow = (workflowId) => {
    const workflow = workflows.find((item) => item.id === workflowId);
    if (!workflow) return;
    selectedWorkflowIdRef.current = workflow.id;
    setReferenceFiles({});
    const next = createWorkflowForm(workflow);
    setForm(next);
    setPresetQuery("");
    setSelectedPresetId("");
    setAppliedPresetTitle("");
  };
  const activeWorkflow = workflows.find((item) => item.id === form.workflowId);
  const activeWorkflowFields = getWorkflowFieldKeys(activeWorkflow);
  const exportParameters = () => {
    const payload = {
      format: "aimaid-comfy-parameters",
      version: 1,
      exportedAt: new Date().toISOString(),
      parameters: form,
      outputFolder: folder,
      referenceFileNames: Object.fromEntries(
        Object.entries(referenceFiles).map(([key, file]) => [key, file.name]),
      ),
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: "application/json;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `comfy-parameters-${new Date().toISOString().replace(/[:.]/g, "-")}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };
  const importParameters = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    try {
      const payload = JSON.parse(await file.text());
      const workflowNodes = Object.values(payload).filter(
        (node) => node?.class_type && node?.inputs,
      );
      const looksLikeWorkflow = workflowNodes.length > 0;
      let parameters = payload.parameters;
      if (looksLikeWorkflow) {
        const byTitle = (title) =>
          workflowNodes.find((node) => node?._meta?.title === title);
        const positive = byTitle("正向提示词") || byTitle("正向描述"),
          negative = byTitle("负向提示词") || byTitle("反向描述"),
          style = byTitle("IP-Adapter 日漫画风"),
          pose = byTitle("OpenPose 强度"),
          depth = byTitle("Depth 强度"),
          size = byTitle("生成尺寸") || byTitle("基础尺寸") ||
            workflowNodes.find((node) => node.class_type === "EmptyLatentImage"),
          first = byTitle("采样参数") || byTitle("第一遍完整生成"),
          second = byTitle("第二遍高清细化"),
          face = byTitle("FaceDetailer 脸部二次修复");
        if (!positive || !negative || !size || !first)
          throw new Error("工作流缺少正向描述、反向描述、基础尺寸或第一遍完整生成节点");
        const weightType = style?.inputs?.weight_type;
        parameters = {
          positivePrompt: positive.inputs.text,
          negativePrompt: negative.inputs.text,
          seed: Number(first.inputs.seed),
          randomSeed: false,
          width: Number(size.inputs.width),
          height: Number(size.inputs.height),
          steps: Number(first.inputs.steps),
          cfg: Number(first.inputs.cfg),
          sampler: first.inputs.sampler_name,
          scheduler: first.inputs.scheduler,
          loras: byTitle("LoRA 加载器")?.inputs?.text ?? "",
          batchSize: Number(size.inputs.batch_size ?? 1),
          denoise: Number(first.inputs.denoise ?? 1),
          controlMode: pose && depth ? "openpose_depth" : pose ? "openpose" : depth ? "depth" : "none",
          styleStrength: Number(style?.inputs?.weight ?? initial.styleStrength),
          styleWeightType: weightType === "linear" ? "linear" : "style transfer (SDXL)",
          styleEndAt: Number(style?.inputs?.end_at ?? initial.styleEndAt),
          combineEmbeds: style?.inputs?.combine_embeds ?? initial.combineEmbeds,
          openPoseStrength: Number(pose?.inputs?.strength ?? initial.openPoseStrength),
          depthStrength: Number(depth?.inputs?.strength ?? initial.depthStrength),
          secondPassSteps: Number(second?.inputs?.steps ?? initial.secondPassSteps),
          secondPassDenoise: Number(second?.inputs?.denoise ?? initial.secondPassDenoise),
          faceDetailerSteps: Number(face?.inputs?.steps ?? initial.faceDetailerSteps),
          faceDetailerDenoise: Number(face?.inputs?.denoise ?? initial.faceDetailerDenoise),
        };
      } else if (payload.format !== "aimaid-comfy-parameters" || !parameters) {
        throw new Error("不是有效的工作台参数文件或 ComfyUI API 工作流");
      }
      const allowed = Object.keys(initial);
      const imported = Object.fromEntries(
        Object.entries(parameters).filter(([key]) => allowed.includes(key)),
      );
      if (!imported.positivePrompt || !imported.negativePrompt)
        throw new Error("参数文件缺少提示词");
      setForm((current) => ({ ...current, ...imported }));
      if (typeof payload.outputFolder === "string" && payload.outputFolder)
        setFolder(payload.outputFolder);
      setError("");
    } catch (e) {
      setError(`导入参数失败：${e.message}`);
    }
  };

  const allHistorySelected = filteredGalleryImages.length > 0 && filteredGalleryImages.every((entry) => selectedImages.has(entry.key));
  const sortedTasks = [...tasks].sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0));
  const completedTaskCount = tasks.filter((task) => task.state === "SUCCEEDED").length;
  const runningTaskCount = tasks.filter((task) => task.state === "RUNNING").length;
  const queuedTaskCount = tasks.filter((task) => task.state === "QUEUED").length;
  const activeTask = tasks.find((task) => task.state === "RUNNING") || tasks.find((task) => task.state === "QUEUED") || null;
  const activeTaskWorkflowName = activeTask
    ? activeTask.workflowName || workflows.find((workflow) => workflow.id === (activeTask.workflowId || activeTask.form?.workflowId))?.name || (activeTask.external ? "外部 ComfyUI 工作流" : "当前工作流")
    : "";

  if (mode === "settings") {
    return (
      <div className="comfy-shell embedded local-comfy system-settings-shell">
        <div className="comfy-section-title">
          <div><span>本机图像服务配置</span><h2>文件夹设置</h2></div>
          <small>集中管理工作流、输出与迁移目录</small>
        </div>
        {error && <div className="local-bridge-error" role="alert"><Warning /><span>{error}</span><button type="button" onClick={() => setError("")} aria-label="关闭错误提示"><X /></button></div>}
        {notice && <div className="local-action-notice" role="status"><CheckCircle /><span>{notice}</span><button type="button" onClick={() => setNotice("")} aria-label="关闭操作提示"><X /></button></div>}
        <section className="settings-folder-grid">
          <div className="local-tool-block workflow-source-panel">
            <div className="local-tool-title"><strong>本机工作流目录</strong><small>递归读取，不上传服务器</small></div>
            <div className="local-inline workflow-directory-row">
              <input value={workflowDirectory} onChange={(e) => setWorkflowDirectory(e.target.value)} aria-label="本机工作流目录" />
              <button type="button" disabled={workflowLoading || launcher !== "ready"} onClick={() => saveWorkflowDirectory().catch((e) => setError(`切换工作流目录失败：${e.message}`))}>保存并读取</button>
              <button type="button" disabled={workflowLoading || !running} onClick={() => loadWorkflows(token).catch((e) => setError(`刷新本机工作流失败：${e.message}`))}>{workflowLoading ? "读取中…" : "刷新"}</button>
            </div>
            <div className="workflow-source-status" title={workflowRejected.map((item) => `${item.path}：${item.message}`).join("\n")}>
              <span>已识别 {workflows.length} 个可运行工作流</span>
              {workflowRejected.length > 0 && <span className="workflow-rejected">{workflowRejected.length} 个文件未适配（悬停查看）</span>}
            </div>
          </div>
          <div className="local-tool-block folder-panel">
            <div className="local-tool-title"><strong>默认输出文件夹</strong><small>本机 ComfyUI/output</small></div>
            <div className="local-inline">
              <select value={folder} onChange={(e) => { setFolder(e.target.value); localStorage.setItem("comfy_output_folder", e.target.value); setNotice("默认输出文件夹已保存"); }}>
                <option value="aimaid">aimaid</option>
                {folders.filter((name) => name !== "aimaid").map((name) => <option key={name} value={name}>{name}</option>)}
              </select>
              <button type="button" disabled={launcher !== "ready"} onClick={() => openFolder().catch((e) => setError(e.message))}>打开</button>
            </div>
          </div>
          <div className="local-tool-block migration-panel">
            <div className="local-tool-title"><strong>迁移文件夹</strong><small>生成资产的集中迁移目录</small></div>
            <div className="local-inline">
              <input value={migrationDirectory} onChange={(e) => setMigrationDirectory(e.target.value)} aria-label="迁移文件夹路径" />
              <button type="button" disabled={launcher !== "ready"} onClick={() => saveMigrationSettings().catch((e) => setError(e.message))}>保存</button>
            </div>
            <div className="local-inline">
              <input value={migrationNewFolder} onChange={(e) => setMigrationNewFolder(e.target.value)} placeholder="在当前迁移目录中新建文件夹" />
              <button type="button" disabled={launcher !== "ready"} onClick={() => createMigrationFolder().catch((e) => setError(e.message))}>新建并切换</button>
            </div>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className={`comfy-shell embedded local-comfy workflow-${form.workflowId}`}>
      <div className="comfy-section-title">
        <div>
          <span>本机图像生成工作台</span>
          <h2>图像工坊</h2>
        </div>
        <div className="workshop-title-side">
          <DynamicShowcase variant="workshop" />
          <small>提示词与图片只在这台电脑处理</small>
        </div>
      </div>
      <section className="comfy-status">
        <div>
          <span
            className={
              launcher === "ready" ? "status-dot online" : "status-dot offline"
            }
          />
          <small>本机启动器</small>
          <strong>{launcher === "ready" ? `${platform} · 已就绪` : "未检测到"}</strong>
        </div>
        <div>
          <span className={running ? "status-dot online" : "status-dot offline"} />
          <small>图像生成服务</small>
          <strong>{running ? "运行中" : platformConfigured ? "已停止" : "路径待配置"}</strong>
        </div>
        <div className="comfy-actions">
          <button
            onClick={() => control("start")}
            disabled={busy || launcher !== "ready" || running || !platformConfigured}
          >
            <Play />
            {controlAction === "start" ? "启动中…" : "启动"}
          </button>
          <button onClick={() => control("stop")} disabled={busy || !running}>
            <Power />
            {controlAction === "stop" ? "停止中…" : "停止"}
          </button>
          <button onClick={() => control("restart")} disabled={busy || launcher !== "ready" || !running || !platformConfigured}>
            <ArrowClockwise className={controlAction === "restart" ? "spin" : ""} />
            {controlAction === "restart" ? "重启中…" : "重启"}
          </button>
        </div>
      </section>
      {!platformConfigured && launcher === "ready" && (
        <div className="platform-placeholder" role="status">
          <strong>{platform} 尚未配置 ComfyUI</strong>
          <span>请将 ComfyUI 放到 {expectedComfyDirectory || "配置文件指定目录"}，再补全本机 appsettings.json。</span>
        </div>
      )}
      {error && (
        <div className="local-bridge-error" role="alert" aria-live="assertive">
          <Warning />
          <span>{error}</span>
          <button type="button" onClick={() => setError("")} aria-label="关闭错误提示">
            <X />
          </button>
        </div>
      )}
      {notice && (
        <div className="local-action-notice" role="status" aria-live="polite">
          <CheckCircle />
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice("")} aria-label="关闭操作提示"><X /></button>
        </div>
      )}
      <div className="comfy-grid">
        <section className="comfy-form">
          <h2>生成参数</h2>
          <WorkflowPanel
            workflows={workflows} loading={workflowLoading} workflow={activeWorkflow}
            fieldKeys={activeWorkflowFields} fieldSpecs={activeWorkflow?.binding?.fields || {}} values={form} onFieldChange={set}
            onWorkflowChange={selectWorkflow} referenceFiles={referenceFiles} onReference={chooseReference}
            onReferenceDrop={dropHistoryImageAsReference}
            loraModels={loraModels} loraModelsLoading={loraModelsLoading}
            presets={presets} presetQuery={presetQuery} onPresetChange={choosePreset}
            appliedPresetTitle={appliedPresetTitle} presetSaveName={presetSaveName} onPresetSaveNameChange={setPresetSaveName}
            onSavePreset={savePromptPreset} presetSaving={presetSaving} onGenerate={generate}
            disabled={{ blocked: busy || !running || !token || !activeWorkflow, busy }}
          />
        </section>
        <section className="comfy-history">
          <div className="gallery-head">
            <div className="gallery-source-row">
              <div className="gallery-source-tabs">
                <button className={galleryMode === "output" ? "active" : ""} onClick={() => switchGallery("output").catch((e) => setError(e.message))}>本机图片</button>
                <button className={galleryMode === "assets" ? "active" : ""} onClick={() => switchGallery("assets").catch((e) => setError(e.message))}>我的资产</button>
              </div>
              <div className="gallery-filters">
                <select aria-label="按工作流筛选图片" value={galleryWorkflowFilter} onChange={(event) => { setGalleryWorkflowFilter(event.target.value); setSelectedImages(new Set()); }}>
                  <option value="all">全部工作流</option>
                  {galleryWorkflowOptions.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
                </select>
                <select aria-label="按透明背景筛选图片" value={galleryTransparencyFilter} onChange={(event) => { setGalleryTransparencyFilter(event.target.value); setSelectedImages(new Set()); }}>
                  <option value="all">全部图片</option>
                  <option value="transparent">透明图片</option>
                  <option value="opaque">非透明图片</option>
                </select>
              </div>
              {activeTask && <div className="gallery-live-progress" role="status" aria-live="polite">
                <strong>{activeTaskWorkflowName} · {activeTask.state === "QUEUED" ? "等待执行" : activeTask.progressDetail?.currentNode?.name || "正在读取当前节点"}</strong>
                <span>
                  {activeTask.progressDetail?.currentNode?.max
                    ? `节点 ${activeTask.progressDetail.currentNode.value}/${activeTask.progressDetail.currentNode.max} · `
                    : ""}
                  {activeTask.progressDetail ? `已完成 ${activeTask.progressDetail.completedNodes}/${activeTask.progressDetail.totalNodes} · ` : ""}
                  总进度 {activeTask.progress ?? 0}%
                </span>
              </div>}
            </div>
            <div className="gallery-actions">
              <span>
                {galleryMode === "assets" ? assetPage.total : history.reduce(
                  (sum, item) => sum + (item.images?.length || 0),
                  0,
                )}{" "}
                张
              </span>
              {selectionMode ? (
                <>
                  <button
                    onClick={() =>
                      setSelectedImages(allHistorySelected ? new Set() : new Set(filteredGalleryImages.map((entry) => entry.key)))
                    }
                  >
                    {allHistorySelected ? "取消全选" : "全选"}
                  </button>
                  <button
                    className="danger"
                    disabled={!selectedImages.size}
                    onClick={deleteSelected}
                  >
                    删除 {selectedImages.size || ""}
                  </button>
                  <button
                    className="twitter-task-action"
                    disabled={!selectedImages.size || selectedTwitterImages().length > 4}
                    onClick={() => openTwitterTask().catch((e) => setError(e.message))}
                  >
                    <PaperPlaneTilt /> 添加到 Twitter 任务 {selectedTwitterImages().length || ""}
                  </button>
                  <button
                      className="move-action"
                      disabled={!selectedImages.size}
                      onClick={openMoveDialog}
                    >
                      迁移 {selectedImages.size || ""}
                    </button>
                  <button
                    onClick={() => {
                      setSelectionMode(false);
                      setSelectedImages(new Set());
                    }}
                  >
                    取消
                  </button>
                </>
              ) : (
                <button onClick={() => setSelectionMode(true)}>选择</button>
              )}
            </div>
          </div>
          {tasks.length > 0 && (
            <div className="task-queue-area">
              <div className="task-queue-overview"><strong>当前 {tasks.length} 个任务</strong><span>完成 {completedTaskCount}</span><span>执行中 {runningTaskCount}</span><span>排队 {queuedTaskCount}</span></div>
              <div className="task-queue-strip">
              {sortedTasks.map((task) => (
                <div
                  key={task.id}
                  className={`queue-pill ${task.state.toLowerCase()}`}
                  title={`${task.workflowName || workflows.find((workflow) => workflow.id === (task.workflowId || task.form?.workflowId))?.name || (task.external ? "外部 ComfyUI 工作流" : "当前工作流")} · ${task.id}`}
                >
                  <div className="queue-pill__info">
                    <span>
                      {task.external
                        ? task.state === "QUEUED" ? "ComfyUI 排队" : "ComfyUI 运行"
                        : task.state === "QUEUED"
                        ? "排队"
                        : task.state === "RUNNING"
                          ? "生成中"
                          : "完成"}
                    </span>
                    <small>{task.workflowName || workflows.find((workflow) => workflow.id === (task.workflowId || task.form?.workflowId))?.name || (task.external ? "外部 ComfyUI 工作流" : "当前工作流")}</small>
                  </div>
                  <strong>{task.progress === null ? "进度错误" : `${task.progress}%`}</strong>
                  {["QUEUED", "RUNNING"].includes(task.state) && <button className="queue-pill__cancel" type="button" aria-label={`取消任务 ${task.id}`} title="取消任务" disabled={cancelingTask === String(task.id)} onClick={() => cancelTask(task)}><X /></button>}
                  <i style={{ width: `${task.progress ?? 0}%` }} />
                </div>
              ))}
              </div>
            </div>
          )}
          {history.length === 0 && (
            <div className="empty-mini">
              <ImageSquare size={38} />
              <span>{galleryMode === "assets" ? `当前 ${platform} 暂无已登记资产` : "生成结果只保存在本机"}</span>
            </div>
          )}
          {history.length > 0 && filteredGalleryImages.length === 0 && (
            <div className="empty-mini"><ImageSquare size={38} /><span>{transparencyScanKey ? "正在识别图片透明背景…" : "当前筛选条件下没有图片"}</span></div>
          )}
          {filteredGalleryImages.length > 0 && (
            <div className="local-image-wall">
              {filteredGalleryImages.map(({ item, image }) => {
                  const selectionKey = imageSelectionKey(item, image);
                  return (
                  <button
                    key={selectionKey}
                    className="local-image-tile"
                    onClick={() =>
                      selectionMode
                        ? toggleSelected(item, image)
                        : openHistory(item, image)
                    }
                    title={item.prompt}
                    data-selected={selectedImages.has(selectionKey)}
                    draggable
                    onDragStart={(event) => startHistoryImageDrag(event, item, image)}
                    onDragEnd={() => { draggedHistoryImage.current = null; }}
                    onContextMenu={(event) => {
                      event.preventDefault();
                      setImageMenu({ x: Math.min(event.clientX, window.innerWidth - 190), y: Math.min(event.clientY, window.innerHeight - 230), item, image });
                    }}
                  >
                    <img src={image.url} alt="历史生成结果" draggable="false" />
                    {item.source === "asset" && <span className="asset-platform-badge">{item.platform}</span>}
                    {image.transparent === true && <span className="transparent-image-badge">透明</span>}
                    {selectionMode && (
                      <i className="selection-mark">
                        {selectedImages.has(selectionKey) ? "✓" : ""}
                      </i>
                    )}
                  </button>
                  );
                })}
            </div>
          )}
          {galleryMode === "assets" && assetPage.pages > 1 && <div className="asset-pagination">
            <button disabled={assetPage.page <= 1 || busy} onClick={() => loadHistory(token, "assets", assetPage.page - 1).catch((e) => setError(e.message))}>上一页</button>
            <span>第 {assetPage.page} / {assetPage.pages} 页 · 每页 100 张</span>
            <button disabled={assetPage.page >= assetPage.pages || busy} onClick={() => loadHistory(token, "assets", assetPage.page + 1).catch((e) => setError(e.message))}>下一页</button>
          </div>}
        </section>
      </div>
      {moveDialog && (
        <div className="history-modal move-modal" onMouseDown={(e) => e.target === e.currentTarget && setMoveDialog(false)}>
          <div className="move-modal-panel">
            <header>
              <div><span>{directMove ? "单图迁移" : "批量迁移"}</span><h3>{directMove ? `迁移原图：${directMove.image.filename}` : `剪切 ${selectedImages.size} 张图片`}</h3></div>
              <button onClick={() => setMoveDialog(false)} aria-label="关闭迁移弹框"><X /></button>
            </header>
            <p>图片会移动到 ComfyUI/output 下的目标文件夹；原任务历史将删除，完整任务参数会保存为目标文件夹内的迁移清单 JSON。</p>
            <label>目标文件夹
              <select value={moveFolder} onChange={(e) => setMoveFolder(e.target.value)}>
                <option value="">请选择文件夹</option>
                {folders.map((name) => <option key={name} value={name}>{name}</option>)}
              </select>
            </label>
            <div className="local-inline">
              <input value={moveNewFolder} onChange={(e) => setMoveNewFolder(e.target.value)} placeholder="或新建保存文件夹" />
              <button onClick={() => createMoveFolder().catch((e) => setError(e.message))}>新建</button>
            </div>
            <footer>
              <button onClick={() => setMoveDialog(false)}>取消</button>
              <button className="confirm-move" disabled={!moveFolder || busy} onClick={moveSelected}>{busy ? "迁移中…" : "确认剪切迁移"}</button>
            </footer>
          </div>
        </div>
      )}
      {twitterDialog && (
        <div className="history-modal twitter-task-modal" onMouseDown={(e) => e.target === e.currentTarget && !twitterSubmitting && setTwitterDialog(false)}>
          <div className="twitter-task-panel">
            <header>
              <div><span>TWITTER SCHEDULE</span><h3>添加到 Twitter 任务</h3></div>
              <button onClick={() => setTwitterDialog(false)} disabled={twitterSubmitting} aria-label="关闭"><X /></button>
            </header>
            <div className="twitter-task-fields">
              <label><span>发布账号</span><select value={twitterTask.accountId} onChange={(e) => setTwitterTask((current) => ({ ...current, accountId: e.target.value }))}>{twitterAccounts.map((account) => <option key={account.id} value={account.id}>@{account.username}</option>)}</select></label>
              <label><span>多久后发布</span><select value={twitterTask.delayMinutes} onChange={(e) => setTwitterTask((current) => ({ ...current, delayMinutes: Number(e.target.value) }))}>{[1, 5, 10, 15, 30].map((minutes) => <option key={minutes} value={minutes}>{minutes} 分钟后</option>)}</select></label>
              <label className="twitter-task-content"><span>文字（可不填，只发图片）</span><textarea maxLength={1000} rows={5} value={twitterTask.content} onChange={(e) => setTwitterTask((current) => ({ ...current, content: e.target.value }))} placeholder="留空将发布仅图片帖子" /><small>{twitterTask.content.length} / 1000</small></label>
            </div>
            <div className="twitter-task-files"><strong>已选择 {selectedTwitterImages().length} 张本机图片</strong>{selectedTwitterImages().map((image) => <div key={`${image.source}-${image.path}`}><ImageSquare /><span>{image.fileName}<small>{image.path}</small></span></div>)}</div>
            <p>服务器会保存图片副本和媒体 ID；资产图片同时关联资产 ID。到点后 Chrome 扩展直接按媒体 ID 读取并发布到 X。</p>
            <footer><button onClick={() => setTwitterDialog(false)} disabled={twitterSubmitting}>取消</button><button className="confirm-twitter-task" onClick={submitTwitterTask} disabled={twitterSubmitting}>{twitterSubmitting ? <SpinnerGap className="spin" /> : <PaperPlaneTilt weight="fill" />}{twitterSubmitting ? "正在保存…" : "保存发布任务"}</button></footer>
          </div>
        </div>
      )}
      {imageMenu && <div className="image-context-menu" style={{ left: imageMenu.x, top: imageMenu.y }} onClick={(event) => event.stopPropagation()}>
        <button className="danger" onClick={contextDelete}><Trash />删除</button>
        <button onClick={contextMigrate}><FolderOpen />迁移</button>
        <button onClick={() => openImageInfo(imageMenu.item, imageMenu.image)}><Info />详细</button>
        {!imageMenu.viewer && <button onClick={contextSelectAll}><CheckCircle />全选</button>}
        {!imageMenu.viewer && <button onClick={() => contextTwitter().catch((e) => setError(e.message))}><PaperPlaneTilt />添加到 Twitter 任务</button>}
      </div>}
      {deleteConfirm && <div className="history-modal delete-confirm-modal" onMouseDown={(event) => event.target === event.currentTarget && setDeleteConfirm(null)}>
        <div className="delete-confirm-panel">
          <Warning />
          <div><h3>确认删除</h3><p>{deleteConfirm.message}</p></div>
          <footer><button onClick={() => setDeleteConfirm(null)}>取消</button><button className="danger" onClick={confirmDelete}>确认删除</button></footer>
        </div>
      </div>}
      {infoDetail && <div className="history-modal image-info-modal" onMouseDown={(event) => event.target === event.currentTarget && setInfoDetail(null)}>
        <div className="image-info-panel">
          <header><div><span>{infoDetail.item.source === "asset" ? "资产详情" : "本机图片详情"}</span><h3>{infoDetail.image.filename || "图片信息"}</h3></div><button onClick={() => setInfoDetail(null)}><X /></button></header>
          <div className="image-info-summary">
            <span><small>工作流</small>{infoDetail.item.workflowName || workflows.find((entry) => entry.id === infoDetail.item.workflowId)?.name || infoDetail.item.workflowId || "未记录"}</span>
            <span><small>分辨率</small>{infoDetail.image.width || infoDetail.item.width || "-"} × {infoDetail.image.height || infoDetail.item.height || "-"}</span>
            <span><small>生成平台</small>{infoDetail.item.platform || platform || "未记录"}</span>
            <span><small>创建时间</small>{infoDetail.item.createdAt ? new Date(infoDetail.item.createdAt).toLocaleString("zh-CN", { hour12: false }) : "未记录"}</span>
            <span><small>Seed / Steps / CFG</small>{infoDetail.item.seed ?? "-"} / {infoDetail.item.steps ?? "-"} / {infoDetail.item.cfg ?? "-"}</span>
            <span><small>采样器 / 调度器</small>{infoDetail.item.sampler || "-"} / {infoDetail.item.scheduler || "-"}</span>
          </div>
          <section className="image-info-loras">
            <header><strong>使用的 LoRA</strong><small>{parseLoras(infoDetail.item.loras).length} 个</small></header>
            {parseLoras(infoDetail.item.loras).length ? <div>{parseLoras(infoDetail.item.loras).map((lora, index) => <article key={`${lora.name}-${index}`} title={lora.name}>
              <b>{index + 1}</b><span>{loraDisplayName(lora.name)}</span><small>模型 {Number(lora.modelStrength ?? 1).toFixed(2)}</small><small>CLIP {Number(lora.clipStrength ?? 1).toFixed(2)}</small>
            </article>)}</div> : <p>该图片没有记录 LoRA</p>}
          </section>
          <label className="image-info-prompt"><span>正向 Prompt <button onClick={() => copyInfo(infoDetail.item.prompt, "正向 Prompt")}><Copy />复制</button></span><textarea readOnly value={infoDetail.item.prompt || "未记录"} /></label>
          <label className="image-info-prompt"><span>反向 Prompt <button onClick={() => copyInfo(infoDetail.item.negativePrompt, "反向 Prompt")}><Copy />复制</button></span><textarea readOnly value={infoDetail.item.negativePrompt || "未记录"} /></label>
          <div className="image-info-path"><small>本机路径</small><code>{infoDetail.image.fullPath || infoDetail.image.path || "未记录"}</code><button onClick={() => copyInfo(infoDetail.image.fullPath || infoDetail.image.path, "本机路径")}><Copy />复制</button></div>
          <footer><button onClick={() => openImageFolder(infoDetail.item, infoDetail.image).catch((e) => setError(e.message))}><FolderOpen />打开所在文件夹</button><button onClick={() => setInfoDetail(null)}>关闭</button></footer>
        </div>
      </div>}
      {detail && (
        <div
          className="history-modal"
          onMouseDown={(e) => e.target === e.currentTarget && closeDetail()}
        >
          <div className="history-modal-panel">
            <header className="viewer-header">
              <div className="viewer-header-tools">
                <button onClick={() => viewerTransform.current?.zoomOut(VIEWER_ZOOM_STEP, 120, "easeOut")} title="缩小"><MagnifyingGlassMinus /></button>
                <button onClick={() => viewerTransform.current?.resetTransform(140, "easeOut")} title="恢复原位"><ArrowCounterClockwise /></button>
                <button onClick={() => viewerTransform.current?.zoomIn(VIEWER_ZOOM_STEP, 120, "easeOut")} title="放大"><MagnifyingGlassPlus /></button>
                <small>每次 20% · 滚轮同档</small>
              </div>
              <div className="viewer-file-title" title={detail.images[detailIndex].filename || detail.images[detailIndex].path || "图片"}>
                <strong>{detail.images[detailIndex].filename || detail.images[detailIndex].path?.split(/[\\/]/).pop() || "图片"}</strong>
                <span>{detailIndex + 1} / {detail.images.length}</span>
              </div>
              <div className="viewer-header-actions">
                <button onClick={() => openImageInfo(detail.images[detailIndex].task, detail.images[detailIndex])} title="查看详细信息"><Info /></button>
                <button onClick={closeDetail} title="关闭大图"><X /></button>
              </div>
            </header>
            <div className="history-lightbox" onContextMenu={(event) => {
              event.preventDefault();
              const image = detail.images[detailIndex];
              setImageMenu({ x: Math.min(event.clientX, window.innerWidth - 190), y: Math.min(event.clientY, window.innerHeight - 155), item: image.task, image, viewer: true });
            }}>
              <TransformWrapper
                ref={viewerTransform}
                initialScale={1}
                minScale={1}
                maxScale={8}
                centerOnInit
                centerZoomedOut
                limitToBounds
                disablePadding
                smooth={false}
                wheel={{ step: VIEWER_ZOOM_STEP }}
                doubleClick={{ mode: "toggle", step: 1, animationTime: 180, animationType: "easeOut" }}
                zoomAnimation={{ animationTime: 160, animationType: "easeOut" }}
                autoAlignment={{ animationTime: 140, velocityAlignmentTime: 220, animationType: "easeOut" }}
                panning={{ velocityDisabled: false }}
              >
                {() => (
                  <>
                    <TransformComponent wrapperClass="zoom-viewer" contentClass="zoom-content">
                      <img src={detail.images[detailIndex].url} alt="历史生成结果" />
                    </TransformComponent>
                    <button className="lightbox-nav prev" onClick={() => setDetailIndex((detailIndex - 1 + detail.images.length) % detail.images.length)}><CaretLeft /></button>
                    <button className="lightbox-nav next" onClick={() => setDetailIndex((detailIndex + 1) % detail.images.length)}><CaretRight /></button>
                  </>
                )}
              </TransformWrapper>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

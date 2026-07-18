import { useEffect, useMemo, useRef, useState } from "react";
import {
  CheckCircle,
  Copy,
  CaretLeft,
  CaretRight,
  ArrowCounterClockwise,
  ArrowClockwise,
  ArrowsLeftRight,
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
import { applySchemeToWorkflow, createComfyProgressPlan, createWorkflowForm, describeComfyProgress, FALLBACK_FORM, findFinalOutput, getWorkflowFieldKeys, getWorkflowRevision, refreshWorkflowForm } from "./comfy/workbench";
import { buildPromptCategories, extractNegativeExtra, extractPositiveExtra, matchSelectedOptionsFromPrompt, normalizePrompt } from "./promptComposer";
import { buildLuckyPrompts } from "./luckyPrompt";

const BRIDGE = "http://127.0.0.1:32145";
const BRIDGE_LAUNCH_URL = "aiprovider-bridge://start";
const initial = FALLBACK_FORM;
const VIEWER_ZOOM_STEP = 0.2;
const taskStateRank = { RUNNING: 0, QUEUED: 1 };
function PromptViewToggle({ value, onChange }) {
  return <div className="prompt-view-toggle" aria-label="Prompt 显示方式">
    <button type="button" aria-pressed={value === "zh"} onClick={() => onChange("zh")}>中文映射</button>
    <button type="button" aria-pressed={value === "raw"} onClick={() => onChange("raw")}>原始 Prompt</button>
  </div>;
}
const sortActiveTasks = (items) => [...items].sort((left, right) => {
  const stateDifference = (taskStateRank[left.state] ?? 9) - (taskStateRank[right.state] ?? 9);
  if (stateDifference) return stateDifference;
  const leftNumber = Number(left.queueNumber);
  const rightNumber = Number(right.queueNumber);
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber) && leftNumber !== rightNumber)
    return leftNumber - rightNumber;
  const leftOrder = Number.isFinite(left.queueOrder) ? left.queueOrder : Number.MAX_SAFE_INTEGER;
  const rightOrder = Number.isFinite(right.queueOrder) ? right.queueOrder : Number.MAX_SAFE_INTEGER;
  if (leftOrder !== rightOrder) return leftOrder - rightOrder;
  return new Date(left.createdAt || 0) - new Date(right.createdAt || 0);
});
const workflowStructureKey = (definition) => JSON.stringify(
  Object.entries(definition || {})
    .sort(([left], [right]) => left.localeCompare(right, undefined, { numeric: true }))
    .map(([id, node]) => [
      id,
      node?.class_type || "",
      Object.entries(node?.inputs || {})
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([name, value]) => [name, Array.isArray(value) ? value.slice(0, 2) : typeof value]),
    ]),
);
const imageSelectionKey = (item, image) =>
  `${item.id}::${image.path || image.url || image.filename || "image"}`;
const limitGalleryImages = (entries, limit = 100) => {
  let remaining = limit;
  return entries.flatMap((entry) => {
    if (remaining <= 0) return [];
    const images = (entry.images || []).slice(0, remaining);
    remaining -= images.length;
    return images.length ? [{ ...entry, images, count: images.length, imageUrl: images[0]?.url || null }] : [];
  });
};
const createGallerySource = () => ({
  serverEntries: [],
  recentEntries: [],
  page: 1,
  serverPage: 0,
  pages: 0,
  total: 0,
  status: "idle",
  loadedAt: 0,
});
const galleryImageAddress = (mode, image) => {
  const address = String(image.path || image.localUrl || image.filename || "").replace(/\\/g, "/").toLowerCase();
  return `${mode}::${address}`;
};
const galleryEntryUrls = (entries) => entries.flatMap((item) =>
  (item.images || []).map((image) => image.url).filter(Boolean));
const mergeGalleryEntries = (mode, preferredEntries, fallbackEntries, limit = 100) => {
  const seen = new Set();
  let remaining = limit;
  return [...preferredEntries, ...fallbackEntries].flatMap((entry) => {
    if (remaining <= 0) return [];
    const images = (entry.images || []).filter((image) => {
      const address = galleryImageAddress(mode, image);
      if (seen.has(address)) return false;
      seen.add(address);
      return true;
    }).slice(0, remaining);
    remaining -= images.length;
    return images.length ? [{ ...entry, images, count: images.length, imageUrl: images[0]?.url || null }] : [];
  });
};
const visibleGalleryEntries = (mode, source) => {
  const serverEntries = source.serverPage === source.page ? source.serverEntries : [];
  return source.page === 1
    ? mergeGalleryEntries(mode, source.recentEntries, serverEntries)
    : serverEntries;
};
const mapGalleryEntries = (entries, mapper) => entries.map((item) => {
  const images = (item.images || []).map((image) => mapper(image, item)).filter(Boolean);
  return { ...item, images, count: images.length, imageUrl: images[0]?.url || null };
}).filter((item) => item.images.length > 0);
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
const assetRecordToGalleryEntry = (item) => ({
  id: `asset-${item.id}`, assetId: item.id, source: "asset", platform: item.platform,
  prompt: item.prompt, negativePrompt: item.negativePrompt, loras: parseLoras(item.lorasJson), seed: item.seed, steps: item.steps,
  cfg: item.cfg, sampler: item.sampler, scheduler: item.scheduler, workflowId: item.workflowId,
  width: item.width, height: item.height, createdAt: item.generatedAt || item.createdAt,
  generationCompletedAt: item.generationCompletedAt, generationDurationMs: item.generationDurationMs,
  images: [{ path: item.localPath, localUrl: item.localUrl, filename: item.fileName, sizeBytes: item.fileSize, width: item.width, height: item.height }],
});
const normalizeHistoryTimestamp = (value) => {
  if (value == null || value === "") return null;
  const numeric = Number(value);
  if (Number.isFinite(numeric)) return numeric < 1e12 ? numeric * 1000 : numeric;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
};
const readGenerationTiming = (historyItem) => {
  const messages = Array.isArray(historyItem?.status?.messages) ? historyItem.status.messages : [];
  const timestamps = (eventName) => messages
    .filter((message) => Array.isArray(message) && message[0] === eventName)
    .map((message) => normalizeHistoryTimestamp(message[1]?.timestamp))
    .filter(Number.isFinite);
  const started = timestamps("execution_start");
  const completed = timestamps("execution_success");
  const startedAt = started.length ? Math.min(...started) : null;
  const completedAt = completed.length ? Math.max(...completed) : null;
  return {
    generationStartedAt: startedAt ? new Date(startedAt).toISOString() : null,
    generationCompletedAt: completedAt ? new Date(completedAt).toISOString() : null,
    generationDurationMs: startedAt && completedAt && completedAt >= startedAt ? completedAt - startedAt : null,
  };
};
const readTaskFailure = (historyItem) => {
  const status = historyItem?.status;
  const messages = Array.isArray(status?.messages) ? status.messages : [];
  const error = messages.find((message) => Array.isArray(message) && message[0] === "execution_error");
  if (!error && String(status?.status_str || "").toLowerCase() !== "error") return "";
  return error?.[1]?.exception_message || error?.[1]?.exception_type || "ComfyUI 执行失败";
};
const formatGenerationDuration = (value) => {
  if (value == null || value === "") return "未记录";
  const milliseconds = Number(value);
  if (!Number.isFinite(milliseconds) || milliseconds < 0) return "未记录";
  const totalSeconds = milliseconds / 1000;
  if (totalSeconds < 60) return `${totalSeconds.toFixed(totalSeconds < 10 ? 1 : 0)} 秒`;
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = Math.round(totalSeconds % 60);
  return hours ? `${hours} 小时 ${minutes} 分 ${seconds} 秒` : `${minutes} 分 ${seconds} 秒`;
};
const formatTaskElapsed = (task, now = Date.now()) => {
  const started = normalizeHistoryTimestamp(task?.generationStartedAt || task?.startedAt || task?.createdAt);
  if (!started) return "未记录";
  return formatGenerationDuration(Math.max(0, now - started));
};
const sha256Blob = async (blob) => Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", await blob.arrayBuffer()))).map((byte) => byte.toString(16).padStart(2, "0")).join("");
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
async function convertImageBlobToPng(blob) {
  if (blob.type === "image/png") return blob;
  if (typeof createImageBitmap !== "function") throw new Error("当前浏览器不支持图片格式转换");
  const bitmap = await createImageBitmap(blob);
  try {
    const canvas = document.createElement("canvas");
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("无法创建图片复制画布");
    context.drawImage(bitmap, 0, 0);
    return await new Promise((resolve, reject) => canvas.toBlob(
      (png) => png ? resolve(png) : reject(new Error("图片转换为 PNG 失败")),
      "image/png",
    ));
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
  const [taskClock, setTaskClock] = useState(() => Date.now());
  const [taskDetail, setTaskDetail] = useState(null);
  const [batchOperation, setBatchOperation] = useState({ open: false, workflowId: "", busy: false });
  const [batchScheduling, setBatchScheduling] = useState(false);
  const [batchProgress, setBatchProgress] = useState(null);
  const [luckyLoading, setLuckyLoading] = useState(false);
  const [galleryMode, setGalleryMode] = useState("output");
  const [gallerySources, setGallerySources] = useState(() => ({
    output: createGallerySource(),
    pending: createGallerySource(),
    assets: createGallerySource(),
  }));
  const [galleryWorkflowFilter, setGalleryWorkflowFilter] = useState("all");
  const [galleryTransparencyFilter, setGalleryTransparencyFilter] = useState("all");
  const [detail, setDetail] = useState(null);
  const [infoDetail, setInfoDetail] = useState(null);
  const [detailPromptView, setDetailPromptView] = useState("zh");
  const [detailPromptTranslation, setDetailPromptTranslation] = useState({ key: "", positivePrompt: "", negativePrompt: "", loading: false, error: "" });
  const [viewerOrientation, setViewerOrientation] = useState({ rotation: 0, mirrored: false });
  const [imageMenu, setImageMenu] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedImages, setSelectedImages] = useState(() => new Set());
  const [presets, setPresets] = useState([]),
    [selectedPresetId, setSelectedPresetId] = useState(""),
    [appliedPresetTitle, setAppliedPresetTitle] = useState("");
  const [promptOptions, setPromptOptions] = useState([]);
  const [negativePromptOptions, setNegativePromptOptions] = useState([]);
  const allPromptOptions = useMemo(() => [...promptOptions, ...negativePromptOptions], [promptOptions, negativePromptOptions]);
  const [generalNegativePrompt, setGeneralNegativePrompt] = useState("");
  const [presetQuery, setPresetQuery] = useState("");
  const [presetSaveName, setPresetSaveName] = useState("");
  const [presetSaving, setPresetSaving] = useState(false);
  const [presetReloading, setPresetReloading] = useState(false);
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
    tasksRef = useRef([]),
    draggedHistoryImage = useRef(null),
    selectedWorkflowIdRef = useRef(""),
    historyLoaded = useRef(false),
    activeLoaded = useRef(false),
    workflowsLoaded = useRef(false),
    workflowVersions = useRef(new Map()),
    workflowRefreshInFlight = useRef(false),
    taskSyncInFlight = useRef(false),
    taskMissingChecks = useRef(new Map()),
    galleryModeRef = useRef("output"),
    gallerySourcesRef = useRef(gallerySources),
    galleryRequestVersions = useRef({ output: 0, pending: 0, assets: 0 }),
    pendingGenerationSourceIds = useRef(new Set()),
    pendingSourceImageRef = useRef(false),
    comfyHistoryIds = useRef(new Set()),
    comfyHistoryLoadingIds = useRef(new Set()),
    galleryCompletionRegisteredIds = useRef(new Set()),
    comfyHistoryInitialized = useRef(false),
    comfyHistorySyncInFlight = useRef(false),
    historyTimingCache = useRef(new Map()),
    loadWorkflowsRef = useRef(null),
    bridgeInstanceIdRef = useRef(""),
    bridgeExitIntent = useRef(""),
    defaultPresetApplied = useRef(""),
    batchStopRequested = useRef(false),
    viewerTransform = useRef(null);
  gallerySourcesRef.current = gallerySources;
  const activeGallerySource = gallerySources[galleryMode];
  const history = visibleGalleryEntries(galleryMode, activeGallerySource);
  useEffect(() => { tasksRef.current = tasks; }, [tasks]);
  useEffect(() => {
    if (!tasks.length) return undefined;
    const timer = window.setInterval(() => setTaskClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [tasks.length]);
  useEffect(() => () => { batchStopRequested.current = true; }, []);
  useEffect(() => {
    if (!active) batchStopRequested.current = true;
  }, [active]);
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
  useEffect(() => {
    setViewerOrientation({ rotation: 0, mirrored: false });
  }, [detail?.index]);
  useEffect(() => {
    if (detailPromptView !== "zh" || (!taskDetail && !infoDetail)) return undefined;
    const positivePrompt = String(taskDetail?.form?.positivePrompt ?? infoDetail?.item?.prompt ?? "");
    const negativePrompt = String(taskDetail?.form?.negativePrompt ?? infoDetail?.item?.negativePrompt ?? "");
    const key = JSON.stringify([positivePrompt, negativePrompt]);
    const controller = new AbortController();
    setDetailPromptTranslation({ key, positivePrompt: "", negativePrompt: "", loading: true, error: "" });
    fetch("/api/prompt-translations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ positivePrompt, negativePrompt }),
      signal: controller.signal,
    }).then(async (response) => {
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload.code !== 200) throw new Error(payload.message || `HTTP ${response.status}`);
      setDetailPromptTranslation({ key, positivePrompt: payload.data?.positivePrompt || "", negativePrompt: payload.data?.negativePrompt || "", loading: false, error: "" });
    }).catch((exception) => {
      if (exception.name !== "AbortError")
        setDetailPromptTranslation({ key, positivePrompt: "", negativePrompt: "", loading: false, error: exception.message || "请求失败" });
    });
    return () => controller.abort();
  }, [detailPromptView, taskDetail, infoDetail]);
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
    taskMissingChecks.current.delete(String(promptId));
    setTasks((current) => {
      const next = current.filter((task) => task.id !== promptId);
      localStorage.setItem("comfy_active_tasks", JSON.stringify(next));
      return next;
    });
  };
  const failActiveTask = (promptId, message) => {
    const id = String(promptId);
    const timer = polling.current.get(id);
    if (timer) clearInterval(timer);
    polling.current.delete(id);
    taskMissingChecks.current.delete(id);
    setTasks((current) => current.map((task) => String(task.id) === id ? { ...task, state: "FAILED", failureMessage: message, reconciling: false } : task));
    const saved = JSON.parse(localStorage.getItem("comfy_active_tasks") || "[]");
    localStorage.setItem("comfy_active_tasks", JSON.stringify((Array.isArray(saved) ? saved : []).filter((task) => String(task.id) !== id)));
    setError(`任务失败：${message}`);
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
      bridgeInstanceIdRef.current = config.instanceId || bridgeInstanceIdRef.current;
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
        loadGalleryPage(config.token, "output", 1).catch((e) => {
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
      if (bridgeExitIntent.current) return;
      setError("");
    }
  };
  useEffect(() => {
    check();
    const id = setInterval(check, 5000);
    const activePolling = polling.current;
    return () => {
      clearInterval(id);
      activePolling.forEach((timer) => clearInterval(timer));
      const urls = new Set(Object.values(gallerySourcesRef.current).flatMap((source) => [
        ...galleryEntryUrls(source.serverEntries),
        ...galleryEntryUrls(source.recentEntries),
      ]));
      urls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, []);

  const waitForBridgeRestart = async (previousInstanceId) => {
    if (!previousInstanceId) throw new Error("无法确认 Bridge 当前实例，已取消重启确认。");
    for (let attempt = 0; attempt < 80; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 250));
      try {
        const configResponse = await fetch(`${BRIDGE}/api/config`, { signal: deadline(2000) });
        const config = await readJson(configResponse, "Bridge 重启确认");
        if (!configResponse.ok || !config.instanceId || config.instanceId === previousInstanceId) continue;
        const statusResponse = await fetch(`${BRIDGE}/api/comfy/status`, { signal: deadline(2000) });
        const status = await readJson(statusResponse, "ComfyUI 重启确认");
        if (!statusResponse.ok || !status.running) continue;
        setToken(config.token);
        bridgeInstanceIdRef.current = config.instanceId;
        setPlatform(config.platform || status.platform || "Windows");
        setPlatformConfigured(config.configured !== false && status.configured !== false);
        setExpectedComfyDirectory(config.expectedComfyDirectory || status.expectedComfyDirectory || "");
        setLauncher("ready");
        setRunning(true);
        bridgeExitIntent.current = "";
        return config.token;
      } catch {
        // The old Bridge must release the port before the new instance can listen.
      }
    }
    throw new Error("Bridge 未能在 20 秒内完成重启。");
  };

  const control = async (action) => {
    setBusy(true);
    setControlAction(action);
    setError("");
    setNotice("");
    const controlsBridgeLifecycle = action === "stop" || action === "restart";
    if (controlsBridgeLifecycle) bridgeExitIntent.current = action;
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
      if (action === "restart") {
        const restartedToken = await waitForBridgeRestart(bridgeInstanceIdRef.current);
        await loadWorkflows(restartedToken);
        setNotice("ComfyUI 与 Bridge 已重启");
      } else if (action === "stop") {
        setRunning(false);
        setLauncher("missing");
        clearActiveTasks();
        setNotice("ComfyUI 与 Bridge 已停止");
      } else {
        setRunning(data.running);
        await loadWorkflows(token);
        setNotice("ComfyUI 已启动");
      }
      window.setTimeout(() => setNotice(""), 3500);
    } catch (e) {
      bridgeExitIntent.current = "";
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
  const chooseReference = (key, event) => {
    if (key === "sourceImage") pendingSourceImageRef.current = false;
    applyReferenceFile(key, event.target.files?.[0]);
  };
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
    // Track if source image is from pending gallery
    pendingSourceImageRef.current = dragged?.item?.source === "asset" && galleryModeRef.current === "pending";
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
  const loadPromptOptions = async () => {
    const response = await fetch("/api/prompt-catalog");
    const data = await readJson(response, "Prompt 词条接口");
    if (!response.ok) throw new Error(data.message || "读取 Prompt 词条失败");
    setPromptOptions(data.data?.options || []);
    setNegativePromptOptions(data.data?.negativeOptions || []);
    setGeneralNegativePrompt(data.data?.generalNegativePrompt || "");
  };
  useEffect(() => {
    if (mode === "workbench" && active) { loadPresets(); loadPromptOptions().catch((e) => setError(e.message)); }
  }, [active, mode]);
  useEffect(() => {
    if (mode !== "workbench" || !active) return;
    const preset = presets.find((item) => item.isDefault);
    const workflow = workflows.find((item) => item.id === form.workflowId);
    if (!preset || !workflow) return;
    const applicationKey = `${preset.id}:${workflow.id}`;
    if (defaultPresetApplied.current === applicationKey) return;
    defaultPresetApplied.current = applicationKey;
    setForm((current) => applySchemeToWorkflow(current, preset, workflow));
    setPresetQuery(String(preset.id));
    setSelectedPresetId(String(preset.id));
    setAppliedPresetTitle(preset.name);
  }, [active, form.workflowId, mode, presets, workflows]);
  const applyPreset = (preset) => {
    setForm((current) => {
      const workflow = workflows.find((item) => item.id === current.workflowId);
      const next = applySchemeToWorkflow(current, preset, workflow);
      return next;
    });
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
    setAppliedPresetTitle(preset.name);
    setError("");
  };
  const choosePreset = (value) => {
    setPresetQuery(String(value || ""));
    const preset = presets.find((item) => String(item.id) === String(value));
    if (!preset) return;
    setSelectedPresetId(String(preset.id));
    applyPreset(preset);
    setAppliedPresetTitle(preset.name);
    setPresetSaveName("");
    setError("");
  };
  const reloadCurrentPreset = async () => {
    if (!presetQuery) { setError("请先选择要重新加载的 Prompt 方案"); return; }
    setPresetReloading(true);
    setError("");
    try {
      const response = await fetch("/api/comfy-presets");
      const data = await readJson(response, "Prompt 方案接口");
      if (!response.ok || data.code !== 200) throw new Error(data.message || `HTTP ${response.status}`);
      const latestPresets = data.data || [];
      const latest = latestPresets.find((item) => String(item.id) === String(presetQuery));
      if (!latest) throw new Error("当前方案已不存在，请重新选择");
      setPresets(latestPresets);
      applyPreset(latest);
      setSelectedPresetId(String(latest.id));
      setAppliedPresetTitle(latest.name);
      setNotice(`已重新加载当前方案：${latest.name}`);
    } catch (e) {
      setError(`重新加载 Prompt 方案失败：${e.message}`);
    } finally {
      setPresetReloading(false);
    }
  };
  const savePromptPreset = async (mode) => {
    const selected = presets.find((item) => String(item.id) === String(presetQuery));
    if (mode === "overwrite" && !selected) { setError("请先选择要覆盖的 Prompt 方案"); return; }
    const title = mode === "new" ? presetSaveName.trim() : selected.name;
    if (!title) { setError("请填写新 Prompt 方案名称"); return; }
    setPresetSaving(true);
    setError("");
    try {
      if (!promptOptions.length) throw new Error("Prompt 词条尚未加载完成");
      const definitions = buildPromptCategories(promptOptions);
      const mappedSelections = matchSelectedOptionsFromPrompt(form.positivePrompt, promptOptions, definitions);
      const mappedPositiveExtra = extractPositiveExtra(form.positivePrompt, mappedSelections, promptOptions);
      const mappedNegativeExtra = extractNegativeExtra(form.negativePrompt, mappedSelections, promptOptions, generalNegativePrompt);
      const response = await fetch(mode === "new" ? "/api/comfy-presets" : `/api/comfy-presets/${selected.id}`, {
        method: mode === "new" ? "POST" : "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: title,
          selectedOptions: mappedSelections,
          positiveExtra: mappedPositiveExtra,
          negativeExtra: mappedNegativeExtra,
          positivePrompt: form.positivePrompt ?? "",
          negativePrompt: form.negativePrompt ?? "",
          remark: "",
          isDefault: mode === "overwrite" && Boolean(selected?.isDefault),
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
      setNotice(`Prompt 方案已保存：${title}`);
      return true;
    } catch (e) {
      setError(`保存 Prompt 方案失败：${e.message}`);
      return false;
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
        sourceType: item.sourceType || (item.external ? "COMFYUI" : "AIPROVIDER"),
        state: item.state || "RUNNING",
        progress: null,
      })),
    );
    saved.forEach((item) => poll(item.id, authToken, item.finalOutputNodeId, item.progressPlan, item));
    localStorage.removeItem("comfy_active_task");
  };
  const editCurrentPreset = () => {
    if (!presetQuery) { setError("请先选择要编辑的 Prompt 方案"); return; }
    window.history.pushState({}, "", `/prompts?edit=${encodeURIComponent(presetQuery)}`);
    window.dispatchEvent(new PopStateEvent("popstate"));
  };
  const replaceGallerySource = (mode, updater) => {
    const currentSources = gallerySourcesRef.current;
    const nextSource = updater(currentSources[mode]);
    const nextSources = { ...currentSources, [mode]: nextSource };
    gallerySourcesRef.current = nextSources;
    setGallerySources(nextSources);
    return nextSource;
  };
  const releaseGalleryUrlsNotRetained = (previousEntries, retainedEntries) => {
    const retainedUrls = new Set(galleryEntryUrls(retainedEntries));
    new Set(galleryEntryUrls(previousEntries)).forEach((url) => {
      if (!retainedUrls.has(url)) URL.revokeObjectURL(url);
    });
  };
  const hydrateGalleryEntries = async (entries, mode, authToken, cachedEntries = []) => {
    const assets = mode === "assets" || mode === "pending";
    const cachedImages = new Map(
      cachedEntries.flatMap((item) => (item.images || [])
        .filter((image) => image.url)
        .map((image) => [galleryImageAddress(mode, image), image])),
    );
    return mapLimit(
      entries, 6, async (item) => {
        const settledImages = await Promise.allSettled(
          (item.images || []).map(async (image) => {
            const cachedImage = cachedImages.get(galleryImageAddress(mode, image));
            const recordedTransparency = item.form?.generateTransparent ?? item.generateTransparent;
            if (cachedImage?.url) {
              return {
                ...image,
                url: cachedImage.url,
                blob: cachedImage.blob,
                transparent: typeof recordedTransparency === "boolean"
                  ? recordedTransparency
                  : image.transparent ?? cachedImage.transparent ?? null,
              };
            }
            const query = new URLSearchParams({ path: image.path });
            const response = await call(`${assets ? "/api/assets/file" : "/api/gallery/file"}?${query}`, {}, 30000, authToken);
            if (!response.ok) throw new Error("missing image");
            const blob = await response.blob();
            return { ...image, blob, url: URL.createObjectURL(blob), transparent: typeof recordedTransparency === "boolean" ? recordedTransparency : image.transparent ?? null };
          }),
        );
        const loadedImages = settledImages.filter((result) => result.status === "fulfilled").map((result) => result.value);
        return { ...item, prompt: item.prompt || "", count: loadedImages.length, imageUrl: loadedImages[0]?.url || null, images: loadedImages };
      },
    );
  };
  const addRecentAssetRecords = async (records, authToken = token, targetMode = "assets") => {
    if (!records.length) return;
    const current = gallerySourcesRef.current[targetMode];
    const hydrated = await hydrateGalleryEntries(
      records.map(assetRecordToGalleryEntry),
      targetMode,
      authToken,
      [...current.serverEntries, ...current.recentEntries],
    );
    replaceGallerySource(targetMode, (source) => {
      const known = new Set([...source.serverEntries, ...source.recentEntries].flatMap((item) =>
        (item.images || []).map((image) => galleryImageAddress(targetMode, image))));
      const added = hydrated.reduce((count, item) => count + (item.images || []).filter((image) =>
        !known.has(galleryImageAddress(targetMode, image))).length, 0);
      const total = source.total + added;
      return {
        ...source,
        recentEntries: mergeGalleryEntries(targetMode, hydrated, source.recentEntries),
        total,
        pages: total ? Math.ceil(total / 100) : 0,
      };
    });
  };
  const reconcileGalleryUrls = (entries, mode, cachedEntries) => {
    const cachedImages = new Map(cachedEntries.flatMap((item) => (item.images || [])
      .filter((image) => image.url)
      .map((image) => [galleryImageAddress(mode, image), image])));
    return mapGalleryEntries(entries, (image) => {
      const cached = cachedImages.get(galleryImageAddress(mode, image));
      if (!cached?.url || cached.url === image.url) return image;
      if (image.url) URL.revokeObjectURL(image.url);
      return { ...image, url: cached.url, transparent: image.transparent ?? cached.transparent ?? null };
    });
  };
  const loadGalleryPage = async (authToken, mode = galleryModeRef.current, page = 1) => {
    const requestVersion = ++galleryRequestVersions.current[mode];
    const sourceBeforeRequest = replaceGallerySource(mode, (source) => ({
      ...source,
      status: source.serverEntries.length ? "refreshing" : "loading",
    }));
    const assets = mode === "assets" || mode === "pending";
    const statusParam = mode === "pending" ? "&status=PENDING" : mode === "assets" ? "&status=ACTIVE" : "";
    try {
      const response = assets
        ? await fetch(`/api/assets?platform=${encodeURIComponent(platform)}&page=${page}&pageSize=100${statusParam}`, { signal: deadline(30000) })
        : await call(`/api/gallery?page=${page}&pageSize=100`, {}, 30000, authToken);
      const data = await readJson(response, assets ? "后端资产目录" : "本机图片目录");
      if (!response.ok || (assets && data.code !== 200)) throw new Error(data.message || `HTTP ${response.status}`);
      const payload = assets ? data.data || {} : data;
      const sourceItems = assets ? (payload.items || []).map(assetRecordToGalleryEntry) : payload.items || [];
      const cachedAtStart = [...sourceBeforeRequest.serverEntries, ...sourceBeforeRequest.recentEntries];
      const hydrated = await hydrateGalleryEntries(limitGalleryImages(sourceItems), mode, authToken, cachedAtStart);
      if (galleryRequestVersions.current[mode] !== requestVersion) {
        releaseGalleryUrlsNotRetained(hydrated, [
          ...gallerySourcesRef.current[mode].serverEntries,
          ...gallerySourcesRef.current[mode].recentEntries,
        ]);
        return;
      }
      const current = gallerySourcesRef.current[mode];
      const serverEntries = reconcileGalleryUrls(hydrated, mode, [...current.serverEntries, ...current.recentEntries]);
      const confirmedAddresses = new Set(serverEntries.flatMap((item) =>
        (item.images || []).map((image) => galleryImageAddress(mode, image))));
      const recentEntries = Number(payload.page || page) === 1
        ? mapGalleryEntries(current.recentEntries, (image) =>
          confirmedAddresses.has(galleryImageAddress(mode, image)) ? null : image)
        : current.recentEntries;
      releaseGalleryUrlsNotRetained(
        [...current.serverEntries, ...current.recentEntries],
        [...serverEntries, ...recentEntries],
      );
      const payloadTotal = Number(payload.total || 0);
      const total = recentEntries.length ? Math.max(payloadTotal, current.total) : payloadTotal;
      replaceGallerySource(mode, () => ({
        serverEntries,
        recentEntries,
        page: Number(payload.page || page),
        serverPage: Number(payload.page || page),
        pages: total ? Math.ceil(total / 100) : 0,
        total,
        status: "ready",
        loadedAt: Date.now(),
      }));
    } catch (exception) {
      if (galleryRequestVersions.current[mode] === requestVersion)
        replaceGallerySource(mode, (source) => ({ ...source, status: "error" }));
      throw exception;
    }
  };
  const mapGalleryImages = (mode, mapper) => replaceGallerySource(mode, (source) => ({
    ...source,
    serverEntries: mapGalleryEntries(source.serverEntries, mapper),
    recentEntries: mapGalleryEntries(source.recentEntries, mapper),
  }));
  const removeGalleryImages = (mode, images) => {
    const addresses = new Set(images.map((image) => galleryImageAddress(mode, image)));
    if (!addresses.size) return;
    replaceGallerySource(mode, (source) => {
      const allEntries = [...source.serverEntries, ...source.recentEntries];
      const existingAddresses = new Set(allEntries.flatMap((item) => (item.images || [])
        .map((image) => galleryImageAddress(mode, image))
        .filter((address) => addresses.has(address))));
      const removedUrls = new Set(allEntries.flatMap((item) => (item.images || [])
        .filter((image) => addresses.has(galleryImageAddress(mode, image)))
        .map((image) => image.url).filter(Boolean)));
      const keep = (image) => addresses.has(galleryImageAddress(mode, image)) ? null : image;
      const serverEntries = mapGalleryEntries(source.serverEntries, keep);
      const recentEntries = mapGalleryEntries(source.recentEntries, keep);
      const retainedUrls = new Set([...galleryEntryUrls(serverEntries), ...galleryEntryUrls(recentEntries)]);
      removedUrls.forEach((url) => { if (!retainedUrls.has(url)) URL.revokeObjectURL(url); });
      const total = Math.max(0, source.total - existingAddresses.size);
      return { ...source, serverEntries, recentEntries, total, pages: total ? Math.ceil(total / 100) : 0 };
    });
  };
  const switchGallery = async (mode) => {
    if (mode === galleryModeRef.current) return;
    galleryModeRef.current = mode;
    setGalleryMode(mode);
    setSelectionMode(false);
    setSelectedImages(new Set());
    setGalleryWorkflowFilter("all");
    setGalleryTransparencyFilter("all");
    const source = gallerySourcesRef.current[mode];
    if (!source.loadedAt) await loadGalleryPage(token, mode, source.page || 1);
  };
  const loadOutputImages = async (images, authToken, includeResultUrl = false) => Promise.all(
    (images || []).map(async (image) => {
      const query = new URLSearchParams({
        filename: image.filename,
        subfolder: image.subfolder || "",
        type: image.type || "output",
      });
      const response = await call(`/comfy/view?${query}`, {}, 30000, authToken);
      if (!response.ok) throw new Error(`读取生成图片失败（HTTP ${response.status}）`);
      const blob = await response.blob();
      const path = image.path || [image.subfolder, image.filename].filter(Boolean).join("/");
      return { ...image, path, blob, url: includeResultUrl ? URL.createObjectURL(blob) : null };
    }),
  );
  const addRecentOutputEntry = (entry) => replaceGallerySource("output", (source) => {
    const knownAddresses = new Set([...source.serverEntries, ...source.recentEntries].flatMap((item) =>
      (item.images || []).map((image) => galleryImageAddress("output", image))));
    const newAddresses = new Set((entry.images || [])
      .map((image) => galleryImageAddress("output", image))
      .filter((address) => !knownAddresses.has(address)));
    const recentEntries = limitGalleryImages([
      entry,
      ...source.recentEntries.filter((item) => String(item.id) !== String(entry.id)),
    ]);
    const recentAddresses = new Set(recentEntries.flatMap((item) =>
      (item.images || []).map((image) => galleryImageAddress("output", image))));
    const recentImageCount = recentAddresses.size;
    const serverEntries = source.serverPage === 1
      ? limitGalleryImages(
        mapGalleryEntries(source.serverEntries, (image) =>
          recentAddresses.has(galleryImageAddress("output", image)) ? null : image),
        Math.max(0, 100 - recentImageCount),
      )
      : [];
    releaseGalleryUrlsNotRetained(
      [...source.serverEntries, ...source.recentEntries],
      [...serverEntries, ...recentEntries],
    );
    const total = source.total + newAddresses.size;
    return {
      ...source,
      serverEntries,
      recentEntries,
      page: 1,
      total,
      pages: total ? Math.ceil(total / 100) : 0,
      loadedAt: Date.now(),
    };
  });
  const recordLocalGeneratedImages = async (entry) => {
    const items = (entry.images || []).map((image) => ({
      promptId: String(entry.promptId),
      imagePath: image.path,
      fileName: image.filename,
      workflowId: entry.workflowId,
      workflowName: entry.workflowName,
      promptSchemeName: entry.promptSchemeName,
      prompt: entry.prompt,
      negativePrompt: entry.negativePrompt,
      lorasJson: JSON.stringify(parseLoras(entry.loras)),
      seed: entry.seed,
      steps: entry.steps,
      cfg: entry.cfg,
      sampler: entry.sampler,
      scheduler: entry.scheduler,
      width: image.width || entry.width,
      height: image.height || entry.height,
      taskCreatedAt: entry.createdAt,
      generationCompletedAt: entry.generationCompletedAt,
      generationDurationMs: entry.generationDurationMs,
    }));
    if (!items.length) throw new Error("本机生成记录没有精确图片路径");
    const response = await fetch("/api/local-generated-images/batch", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ platform, items }),
    });
    const data = await readJson(response, "本机生成图片异步记录接口");
    if (!response.ok || data.code !== 200) throw new Error(data.message || `HTTP ${response.status}`);
  };
  const appendTaskToHistory = (promptId, task, loadedImages, authToken = token, historyItem = null) => {
    if (!loadedImages.length) return;
    comfyHistoryIds.current.add(String(promptId));
    const images = loadedImages.map(({ blob, ...image }) => ({ ...image, blob, url: URL.createObjectURL(blob) }));
    const form = task?.form || {};
    const timing = historyItem ? readGenerationTiming(historyItem) : {};
    if (timing.generationCompletedAt) historyTimingCache.current.set(String(promptId), timing);
    const entry = {
      id: promptId,
      promptId,
      prompt: form.positivePrompt || "",
      negativePrompt: form.negativePrompt || "",
      loras: form.loras || [],
      seed: task?.actualSeed ?? form.seed,
      steps: form.steps,
      cfg: form.cfg,
      sampler: form.sampler,
      scheduler: form.scheduler,
      workflowId: task?.workflowId || form.workflowId,
      workflowName: task?.workflowName,
      promptSchemeName: task?.promptSchemeName || form.promptSchemeName || "",
      width: form.width,
      height: form.height,
      createdAt: task?.createdAt || new Date().toISOString(),
      ...timing,
      images,
    };
    addRecentOutputEntry(entry);
    recordLocalGeneratedImages(entry).catch((exception) => reportLocalError(
      "local-generated-image-record",
      exception,
      { promptId: String(promptId), path: "/api/local-generated-images/batch" },
      authToken,
    ));
  };
  const registerGalleryCompletion = async (promptId, historyItem, task, authToken) => {
    const id = String(promptId);
    if (galleryCompletionRegisteredIds.current.has(id)) return false;
    const finalOutput = findFinalOutput(historyItem, task?.finalOutputNodeId);
    const files = (finalOutput?.images || []).map((image) => image.path || [image.subfolder, image.filename].filter(Boolean).join("/")).filter(Boolean);
    if (!files.length) return false;
    const timing = readGenerationTiming(historyItem);
    const response = await call("/api/gallery/complete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        promptId: id,
        files,
        generationCompletedAt: timing.generationCompletedAt,
        generationDurationMs: timing.generationDurationMs,
      }),
    }, 10000, authToken);
    if (!response.ok) return false;
    fetch(`/api/comfy-tasks/${encodeURIComponent(id)}/complete`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ paths: files }) }).catch((exception) => reportLocalError("comfy-task-complete", exception, { promptId: id }));
    galleryCompletionRegisteredIds.current.add(id);
    return true;
  };
  const appendCompletedHistoryItem = async (promptId, task, historyItem, authToken) => {
    const id = String(promptId);
    if (comfyHistoryLoadingIds.current.has(id)) return false;
    comfyHistoryLoadingIds.current.add(id);
    try {
      const finalOutput = findFinalOutput(historyItem, task.finalOutputNodeId);
      if (!finalOutput) return true;
      await registerGalleryCompletion(id, historyItem, task, authToken);
      const images = await loadOutputImages(finalOutput.images, authToken);
      appendTaskToHistory(id, task, images, authToken, historyItem);
      return true;
    } finally {
      comfyHistoryLoadingIds.current.delete(id);
    }
  };
  const appendCompletedExternalTask = async (task, authToken) => {
    const promptId = String(task.id);
    const response = await call(`/comfy/history/${encodeURIComponent(promptId)}`, {}, 10000, authToken);
    const data = await readJson(response, "ComfyUI history");
    if (!response.ok || !data[promptId]) return;
    await appendCompletedHistoryItem(promptId, task, data[promptId], authToken);
  };
  const syncRecentComfyHistory = async (authToken = token) => {
    if (comfyHistorySyncInFlight.current || gallerySourcesRef.current.output.status !== "ready") return;
    comfyHistorySyncInFlight.current = true;
    try {
      const response = await call("/comfy/history?max_items=20", {}, 10000, authToken);
      const data = await readJson(response, "ComfyUI 最近历史");
      if (!response.ok || !data || Array.isArray(data)) throw new Error(data.message || `HTTP ${response.status}`);
      const ids = Object.keys(data);
      await Promise.allSettled(ids.map((id) => registerGalleryCompletion(
        id,
        data[id],
        tasksRef.current.find((candidate) => String(candidate.id) === String(id)),
        authToken,
      )));
      let newIds;
      if (!comfyHistoryInitialized.current) {
        const knownAddresses = new Set([
          ...gallerySourcesRef.current.output.serverEntries,
          ...gallerySourcesRef.current.output.recentEntries,
        ].flatMap((item) => (item.images || []).map((image) => galleryImageAddress("output", image))));
        newIds = ids.filter((id) => {
          const task = tasksRef.current.find((candidate) => String(candidate.id) === String(id));
          const finalOutput = findFinalOutput(data[id], task?.finalOutputNodeId);
          return finalOutput?.images?.some((image) => {
            const path = image.path || [image.subfolder, image.filename].filter(Boolean).join("/");
            return !knownAddresses.has(galleryImageAddress("output", { ...image, path }));
          });
        });
        const missingIds = new Set(newIds);
        comfyHistoryIds.current = new Set(ids.filter((id) => !missingIds.has(id)));
        comfyHistoryInitialized.current = true;
      } else {
        newIds = ids.filter((id) => !comfyHistoryIds.current.has(id));
      }
      await Promise.allSettled(newIds.map((id) => {
        const task = tasksRef.current.find((candidate) => String(candidate.id) === String(id)) || {
          id,
          external: true,
          createdAt: new Date().toISOString(),
        };
        return appendCompletedHistoryItem(id, task, data[id], authToken)
          .then((processed) => { if (processed) comfyHistoryIds.current.add(id); });
      }));
    } finally {
      comfyHistorySyncInFlight.current = false;
    }
  };
  const syncComfyTasks = async (authToken = token) => {
    if (taskSyncInFlight.current) return;
    taskSyncInFlight.current = true;
    try {
    const queueResponse = await call("/comfy/queue", {}, 10000, authToken);
    const queue = await readJson(queueResponse, "ComfyUI queue");
    if (!queueResponse.ok) throw new Error(queue.message || `ComfyUI 队列接口失败（HTTP ${queueResponse.status}）`);
    const rows = [
      ...(Array.isArray(queue.queue_running) ? queue.queue_running : []).map((row, queueOrder) => ({ row, state: "RUNNING", queueOrder })),
      ...(Array.isArray(queue.queue_pending) ? queue.queue_pending : []).map((row, queueOrder) => ({ row, state: "QUEUED", queueOrder })),
    ];
    let liveProgress = null;
    if (rows.some(({ state }) => state === "RUNNING")) {
      try {
        const progressResponse = await call("/comfy/aiprovider/progress", {}, 5000, authToken);
        const progressData = await readJson(progressResponse, "ComfyUI 实时进度接口");
        if (!progressResponse.ok) throw new Error(progressData.message || `HTTP ${progressResponse.status}`);
        liveProgress = progressData;
      } catch (exception) {
        // Queue state is authoritative. A missing optional progress extension must
        // not make active tasks disappear or turn queue refresh into an error.
        reportLocalError("task-progress", exception, { path: "/comfy/aiprovider/progress" }, authToken);
      }
    }
    const nextExternalIds = new Set(rows.filter(({ row }) => Array.isArray(row) && row[1]).map(({ row }) => String(row[1])));
    const previousTasks = new Map(tasksRef.current.map((task) => [String(task.id), task]));
    const completedExternalTasks = [...externalTaskIds.current]
      .filter((id) => !nextExternalIds.has(id))
      .map((id) => previousTasks.get(id))
      .filter((task) => task?.external);
    externalTaskIds.current = nextExternalIds;
    setTasks((current) => {
      const existingTasks = new Map(current.map((task) => [String(task.id), task]));
      const activeTasks = rows
        .filter(({ row }) => Array.isArray(row) && row[1])
        .map(({ row, state, queueOrder }) => {
          const promptId = String(row[1]);
          const existing = existingTasks.get(promptId);
          const rowStructure = workflowStructureKey(row[2]);
          const matchedWorkflow = existing?.workflowName
            ? null
            : workflows.find((workflow) => workflowStructureKey(workflow.definition) === rowStructure);
          const progressPlan = existing?.progressPlan || createComfyProgressPlan(row[2], row[4]);
          const progressDetail = state === "RUNNING" && liveProgress
            ? describeComfyProgress(liveProgress, promptId, progressPlan)
            : null;
          const previousProgress = Number.isFinite(existing?.progress) ? existing.progress : null;
          return {
            ...existing,
            id: promptId,
            state,
            progress: state === "QUEUED" ? 0 : progressDetail?.totalPercent ?? previousProgress,
            progressDetail,
            progressPlan,
            workflowId: existing?.workflowId || matchedWorkflow?.id,
            workflowName: existing?.workflowName || matchedWorkflow?.name,
            external: existing ? Boolean(existing.external) : true,
            sourceType: existing?.sourceType || (existing && !existing.external ? "AIPROVIDER" : "COMFYUI"),
            queueOrder,
            queueNumber: row[0],
            createdAt: existing?.createdAt || new Date().toISOString(),
          };
        });
      const activeIds = new Set(activeTasks.map((task) => String(task.id)));
      const retainedLocalTasks = current.filter((task) => !task.external && !activeIds.has(String(task.id)) && task.state !== "SUCCEEDED");
      const next = sortActiveTasks([...activeTasks, ...retainedLocalTasks]);
      localStorage.setItem("comfy_active_tasks", JSON.stringify(next.filter((task) => !task.external && !["SUCCEEDED", "FAILED"].includes(task.state))));
      return next;
    });
    if (completedExternalTasks.length)
      await Promise.allSettled(completedExternalTasks.map((task) => appendCompletedExternalTask(task, authToken)));
    } finally {
      taskSyncInFlight.current = false;
    }
  };
  useEffect(() => {
    if (!token || !running) return undefined;
    const sync = () => syncComfyTasks(token).catch((e) => {
      reportLocalError("task-sync", e, { path: "/comfy/queue + /comfy/aiprovider/progress" }, token);
    });
    sync();
    const id = setInterval(sync, 1500);
    return () => clearInterval(id);
  }, [token, running]);
  useEffect(() => {
    if (!token || !running || !active) return undefined;
    const sync = () => syncRecentComfyHistory(token)
      .catch((exception) => reportLocalError("comfy-history-sync", exception, { path: "/comfy/history?max_items=20" }, token));
    sync();
    const id = setInterval(sync, 2000);
    return () => clearInterval(id);
  }, [token, running, active]);
  const submitGeneration = async (submissionForm, batchRunId = null, submissionReferences = referenceFiles, inputSha256 = null) => {
      const body = new FormData();
      Object.entries(submissionForm).forEach(([key, value]) => body.append(key, key === "loras" ? JSON.stringify(Array.isArray(value) ? value : []) : String(value)));
      const selectedWorkflowId = submissionForm.workflowId || selectedWorkflowIdRef.current;
      const active = workflows.find((item) => item.id === selectedWorkflowId);
      if (!active) throw new Error("工作流尚未从后端加载完成");
      body.set("workflowId", active.id);
      body.set("workflowName", active.name || active.id);
      body.set("promptSchemeName", appliedPresetTitle || "");
      body.append("workflowDefinition", JSON.stringify(active.definition));
      body.append("workflowBinding", JSON.stringify(active.binding));
      const referenceKeys = [];
      if (active?.capabilities?.inputImage) referenceKeys.push("sourceImage");
      if (active?.capabilities?.styleReference) referenceKeys.push("styleReference1", "styleReference2", "styleReference3", "styleReference4");
      if (active?.capabilities?.poseReference) referenceKeys.push("poseReference");
      const missing = referenceKeys.find((key) => !submissionReferences[key]);
      if (missing) throw new Error(missing === "sourceImage" ? "当前工作流需要先选择待处理原图" : "当前工作流需要先选择全部参考图片");
      const interactiveEditor = activeWorkflowFields.find((fieldKey) =>
        active?.binding?.fields?.[fieldKey]?.nodeType === "MaskEditMEC" &&
        active?.binding?.fields?.[fieldKey]?.input === "editor_data",
      );
      if (interactiveEditor) {
        let editorData;
        try { editorData = JSON.parse(submissionForm[interactiveEditor] || "{}"); }
        catch { throw new Error("区域编辑数据无效，请清空后重新涂抹"); }
        if (!(editorData.points?.length || editorData.bboxes?.length)) throw new Error("请先在原图上涂抹需要删除的区域");
      }
      referenceKeys.forEach((key) => body.append(key, submissionReferences[key]));
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
      // Track if this generation was from a pending source image
      if (pendingSourceImageRef.current) {
        pendingGenerationSourceIds.current.add(String(data.promptId));
        pendingSourceImageRef.current = false;
      }
      const progressPlan = createComfyProgressPlan(active.definition, [data.finalOutputNodeId]);
      const nextTask = {
        id: data.promptId,
        sourceType: "AIPROVIDER",
        external: false,
        state: "QUEUED",
        progress: 0,
        form: { ...submissionForm, workflowId: active.id },
        workflowId: active.id,
        workflowName: active.name || active.id,
        promptSchemeName: appliedPresetTitle || "",
        inputSha256,
        inputImages: Object.entries(submissionReferences).filter(([, file]) => file instanceof File).map(([key, file]) => ({ key, name: file.name, url: URL.createObjectURL(file) })),
        finalOutputNodeId: data.finalOutputNodeId,
        progressPlan,
        progressDetail: null,
        actualSeed: data.actualSeed,
        batchRunId,
        folder,
        createdAt: new Date().toISOString(),
      };
      setTasks((current) => {
        const next = [nextTask, ...current].slice(0, 2000);
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
      fetch("/api/comfy-tasks", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ promptId: nextTask.id, workflowId: nextTask.workflowId, workflowName: nextTask.workflowName, promptSchemeName: nextTask.promptSchemeName, positivePrompt: submissionForm.positivePrompt || "", negativePrompt: submissionForm.negativePrompt || "", parametersJson: JSON.stringify(submissionForm), inputFile: nextTask.inputImages?.[0]?.name || null, inputFileName: nextTask.inputImages?.[0]?.name || null, inputSha256, status: "QUEUED" }) }).catch((exception) => reportLocalError("comfy-task-record", exception, { promptId: nextTask.id }));
      poll(data.promptId, token, data.finalOutputNodeId, progressPlan, nextTask);
      return nextTask;
  };
  const scheduleGenerations = async (total, buildSubmissionForm, completionLabel = "任务") => {
    if (total < 1 || total > 10000) { setError("生成数量必须在 1 到 10000 之间"); return; }
    const batchRunId = generateId();
    batchStopRequested.current = false;
    setBatchScheduling(true);
    setBatchProgress({ total, submitted: 0 });
    setError("");
    results.forEach((result) => URL.revokeObjectURL(result.url));
    setResults([]);
    try {
      let submitted = 0;
      while (submitted < total && !batchStopRequested.current) {
        await submitGeneration({ ...buildSubmissionForm(submitted), batchSize: 1 }, batchRunId);
        submitted += 1;
        setBatchProgress({ total, submitted });
      }
      setNotice(batchStopRequested.current ? `已停止加入 Bridge 队列，共提交 ${submitted} / ${total} 个任务` : `已将 ${submitted} 个${completionLabel}交给 Bridge 队列`);
    } catch (e) {
      reportLocalError("generate", e, { path: "/api/generate" });
      setError(e.message);
    } finally {
      setBatchScheduling(false);
    }
  };
  const runGeneration = async (generationForm) => {
    const total = Math.floor(Number(generationForm.batchSize) || 1);
    return scheduleGenerations(total, () => generationForm);
  };
  const generate = async () => runGeneration(form);
  const batchPromptGenerate = async (options) => {
    const selected = Array.isArray(options) ? options : [];
    if (!selected.length) { setError("请至少选择一个批量生成词条"); return; }
    const baseForm = { ...form, batchSize: 1 };
    await scheduleGenerations(selected.length, (index) => {
      const option = selected[index];
      const negative = option.type === "negative";
      const target = negative ? "negativePrompt" : "positivePrompt";
      const term = option.prompt || (negative ? option.negativePrompt : option.positivePrompt);
      return { ...baseForm, [target]: normalizePrompt(baseForm[target], term) };
    }, "批量 Prompt 词条");
  };
  const luckyGenerate = async () => {
    setLuckyLoading(true); setError("");
    try {
      const response = await fetch(`/api/assets/prompt-pool?platform=${encodeURIComponent(platform)}`);
      const data = await readJson(response, "资产 Prompt 池接口");
      if (!response.ok || data.code !== 200) throw new Error(data.message || `HTTP ${response.status}`);
      const lucky = buildLuckyPrompts(form.positivePrompt, form.negativePrompt, data.data || []);
      const luckyForm = { ...form, positivePrompt: lucky.positivePrompt, negativePrompt: lucky.negativePrompt };
      setForm(luckyForm);
      setNotice(`手气不错：加入 ${lucky.positiveAdditions.length} 个正向词和 ${lucky.negativeAdditions.length} 个反向词`);
      await runGeneration(luckyForm);
    } catch (e) { setError(`手气不错失败：${e.message}`); }
    finally { setLuckyLoading(false); }
  };
  const poll = (promptId, authToken = token, finalOutputNodeId, progressPlan, taskHint = null) => {
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
            const stateResponse = await call(`/api/tasks/${encodeURIComponent(promptId)}/state`, {}, 10000, authToken);
            const bridgeState = await readJson(stateResponse, "Bridge 任务状态接口");
            if (!stateResponse.ok) throw new Error(bridgeState.message || `Bridge 任务状态查询失败（HTTP ${stateResponse.status}）`);
            if (bridgeState.state === "FAILED") {
              failActiveTask(promptId, bridgeState.message || "Bridge 或 ComfyUI 已明确报告任务执行失败");
              return;
            }
            if (bridgeState.state === "QUEUED") {
              taskMissingChecks.current.delete(String(promptId));
              setTasks((current) => current.map((task) => String(task.id) === String(promptId) ? { ...task, state: "QUEUED", progress: 0, reconciling: false } : task));
              return;
            }
            if (["TRACKED", "COMPLETED"].includes(bridgeState.state)) {
              taskMissingChecks.current.delete(String(promptId));
              setTasks((current) => current.map((task) => String(task.id) === String(promptId) ? { ...task, reconciling: true } : task));
              return;
            }
            const missingChecks = (taskMissingChecks.current.get(String(promptId)) || 0) + 1;
            taskMissingChecks.current.set(String(promptId), missingChecks);
            if (missingChecks < 10) return;
            failActiveTask(promptId, "Bridge 与 ComfyUI 连续确认后均找不到该任务");
            return;
          }
          taskMissingChecks.current.delete(String(promptId));
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
                    reconciling: false,
                  }
                : task,
            ),
          );
          return;
        }
        const explicitFailure = readTaskFailure(item);
        if (explicitFailure) {
          failActiveTask(promptId, explicitFailure);
          return;
        }
        const finalOutput = findFinalOutput(item, finalOutputNodeId);
        if (!finalOutput) {
          if (item?.status?.completed || String(item?.status?.status_str || "").toLowerCase() === "success")
            failActiveTask(promptId, "任务已结束，但没有找到可展示的图片输出");
          return;
        }
        clearInterval(timer);
        polling.current.delete(promptId);
        taskMissingChecks.current.delete(String(promptId));
        await registerGalleryCompletion(promptId, item, taskHint || tasksRef.current.find((task) => String(task.id) === String(promptId)), authToken);
        const loaded = await loadOutputImages(finalOutput.images, authToken, true);
        setResults(loaded.map(({ blob, ...image }) => image));
        appendTaskToHistory(promptId, taskHint || tasksRef.current.find((task) => String(task.id) === String(promptId)), loaded, authToken, item);
        removeActiveTask(promptId);
      } catch (e) {
        reportLocalError("task-poll", e, { promptId, path: `/comfy/history/${promptId}` }, authToken);
        setTasks((current) => current.map((task) => String(task.id) === String(promptId) ? { ...task, reconciling: true } : task));
      }
    }, 2000);
    polling.current.set(promptId, timer);
  };
  const loadGenerationTiming = async (item) => {
    const promptId = item.promptId;
    if (item.source === "asset" || !promptId) return null;
    if (item.generationCompletedAt) return {
      generationStartedAt: item.generationStartedAt,
      generationCompletedAt: item.generationCompletedAt,
      generationDurationMs: item.generationDurationMs,
    };
    const cached = historyTimingCache.current.get(String(promptId));
    if (cached) return cached;
    try {
      const response = await call(`/comfy/history/${encodeURIComponent(promptId)}`, {}, 10000, token);
      const data = await readJson(response, "ComfyUI history");
      const timing = readGenerationTiming(data?.[promptId]);
      if (!timing.generationCompletedAt) return null;
      historyTimingCache.current.set(String(promptId), timing);
      return timing;
    } catch (exception) {
      reportLocalError("image-generation-timing", exception, { promptId, path: `/comfy/history/${promptId}` }, token);
      return null;
    }
  };
  const openHistory = async (item, image) => {
    const gallery = history.flatMap((entry) =>
      (entry.images || []).map((candidate) => ({ ...candidate, task: entry })),
    );
    const selectedKey = imageSelectionKey(item, image);
    const index = gallery.findIndex((candidate) => imageSelectionKey(candidate.task, candidate) === selectedKey);
    setDetail({ images: gallery, index: Math.max(0, index) });
    const timing = await loadGenerationTiming(item);
    if (!timing) return;
    setDetail((current) => current ? {
      ...current,
      images: current.images.map((candidate) => item.promptId && String(candidate.task.promptId) === String(item.promptId)
        ? { ...candidate, task: { ...candidate.task, ...timing } }
        : candidate),
    } : current);
  };
  const openImageInfo = async (item, image) => {
    setImageMenu(null);
    const cachedTiming = item.promptId ? historyTimingCache.current.get(String(item.promptId)) : null;
    setInfoDetail({ item: cachedTiming ? { ...item, ...cachedTiming } : item, image });
    const timing = await loadGenerationTiming(item);
    if (!timing) return;
    setInfoDetail((current) => current && item.promptId && String(current.item.promptId) === String(item.promptId)
      ? { ...current, item: { ...current.item, ...timing } }
      : current);
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
  const copyGalleryImage = async (image) => {
    try {
      if (!navigator.clipboard?.write || typeof ClipboardItem !== "function") throw new Error("当前浏览器不支持图片剪贴板");
      let blob = image.blob;
      if (!blob) {
        const response = await fetch(image.url);
        if (!response.ok) throw new Error(`读取图片失败（HTTP ${response.status}）`);
        blob = await response.blob();
        const responseType = response.headers.get("content-type")?.split(";")[0]?.trim();
        if (!blob.type && responseType?.startsWith("image/")) blob = blob.slice(0, blob.size, responseType);
      }
      const png = await convertImageBlobToPng(blob);
      await navigator.clipboard.write([new ClipboardItem({ "image/png": png })]);
      setNotice("图片已复制");
    } catch (exception) {
      setError(`复制图片失败：${exception.message}`);
    }
  };
  const requestViewerDelete = (item, image) => {
    performDeleteImage(item, image);
  };
  const closeDetail = () => {
    setDetail(null);
  };
  const advanceDetailAfterAction = (item, image) => {
    const removedKey = imageSelectionKey(item, image);
    setSelectedImages((current) => {
      if (!current.has(removedKey)) return current;
      const next = new Set(current);
      next.delete(removedKey);
      return next;
    });
    removeGalleryImages(item.source === "asset" ? "assets" : "output", [image]);
    setDetail((current) => {
      if (!current) return current;
      const removedIndex = current.images.findIndex(
        (candidate) => imageSelectionKey(candidate.task, candidate) === removedKey,
      );
      if (removedIndex < 0) return current;
      const images = current.images.filter((_, index) => index !== removedIndex);
      if (!images.length) return null;
      const index = current.index > removedIndex
        ? current.index - 1
        : current.index === removedIndex
          ? Math.min(removedIndex, images.length - 1)
          : current.index;
      return { ...current, images, index };
    });
  };
  const navigateDetail = (offset) => {
    setDetail((current) => current?.images.length
      ? { ...current, index: (current.index + offset + current.images.length) % current.images.length }
      : current);
  };
  useEffect(() => {
    if (!detail) return undefined;
    const handleViewerKey = (event) => {
      const target = event.target;
      if (target instanceof HTMLElement && (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))) return;
      if (event.key === "Escape") {
        if (infoDetail || deleteConfirm) return;
        event.preventDefault();
        if (imageMenu) setImageMenu(null);
        else closeDetail();
        return;
      }
      const currentImage = detail.images[detail.index];
      if ((event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey && event.key.toLowerCase() === "c") {
        event.preventDefault();
        copyGalleryImage(currentImage);
        return;
      }
      if (event.key === "Delete" && !event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
        event.preventDefault();
        requestViewerDelete(currentImage.task, currentImage);
        return;
      }
      if (!["ArrowLeft", "ArrowRight"].includes(event.key) || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
      event.preventDefault();
      setDetail((current) => current?.images.length
        ? { ...current, index: (current.index + (event.key === "ArrowLeft" ? -1 : 1) + current.images.length) % current.images.length }
        : current);
    };
    window.addEventListener("keydown", handleViewerKey);
    return () => window.removeEventListener("keydown", handleViewerKey);
  }, [detail, infoDetail, deleteConfirm, imageMenu]);
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
    const scanMode = galleryMode;
    const pending = galleryImages.filter(({ image }) => image.transparent == null);
    mapLimit(pending, 3, async ({ key, image }) => ({ key, transparent: await detectImageTransparency(image.url).catch(() => false) }))
      .then((detected) => {
        if (cancelled) return;
        const values = new Map(detected.map((entry) => [entry.key, entry.transparent]));
        mapGalleryImages(scanMode, (image, item) => {
          const value = values.get(imageSelectionKey(item, image));
          return value === undefined ? image : { ...image, transparent: value };
        });
      });
    return () => { cancelled = true; };
  }, [transparencyScanKey]);
  const selectedGalleryImages = () =>
    galleryImages.filter((entry) => selectedImages.has(entry.key));
  const batchInputWorkflows = workflows.filter((workflow) => workflow?.capabilities?.inputImage);
  const openBatchOperation = () => {
    if (!selectedImages.size) return;
    setBatchOperation({ open: true, workflowId: batchInputWorkflows[0]?.id || "", busy: false });
  };
  const runBatchOperation = async () => {
    const workflow = batchInputWorkflows.find((item) => item.id === batchOperation.workflowId);
    const entries = selectedGalleryImages();
    if (!workflow || !entries.length) return;
    setBatchOperation((current) => ({ ...current, busy: true }));
    try {
      const prepared = [];
      for (const { image } of entries) {
        const blob = image.blob || await (await fetch(image.url)).blob();
        const file = new File([blob], image.filename || image.path?.split(/[\\/]/).pop() || "input.png", { type: blob.type || "image/png" });
        const hash = await sha256Blob(blob);
        const response = await fetch(`/api/comfy-tasks/duplicate?workflowId=${encodeURIComponent(workflow.id)}&inputSha256=${hash}`);
        const result = await readJson(response, "重复任务检查接口");
        if (!response.ok || result.code !== 200) throw new Error(result.message || "重复任务检查失败");
        prepared.push({ file, hash, duplicate: result.data && Object.keys(result.data).length > 0 });
      }
      const duplicateCount = prepared.filter((item) => item.duplicate).length;
      if (duplicateCount && !window.confirm(`其中 ${duplicateCount} 张图片之前已经使用同一工作流生成过，是否仍然继续？`)) return;
      let submitted = 0;
      for (const input of prepared) {
        const batchForm = { ...initial, ...(workflow.defaults || {}), workflowId: workflow.id, batchSize: 1 };
        await submitGeneration(batchForm, generateId(), { sourceImage: input.file }, input.hash);
        submitted += 1;
      }
      setNotice(`批量操作已向 Bridge 队列提交 ${submitted} 个任务`);
      setBatchOperation({ open: false, workflowId: "", busy: false });
      setSelectionMode(false); setSelectedImages(new Set());
    } catch (exception) {
      setError(`批量操作失败：${exception.message}`);
      setBatchOperation((current) => ({ ...current, busy: false }));
    }
  };
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
      source: item.source === "asset" || galleryMode === "assets" || galleryMode === "pending" ? "asset" : "output",
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
    const deletingAssets = galleryMode === "assets" || galleryMode === "pending";
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
      removeGalleryImages(deletingAssets ? (galleryMode === "pending" ? "pending" : "assets") : "output", selectedEntries.map(({ image }) => image));
      setSelectedImages(new Set());
      setSelectionMode(false);
    } catch (e) {
      setError(`批量删除失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const deleteSelected = () => {
    if (!selectedImages.size) return;
    if (galleryMode === "output" && selectedImages.size === 1) {
      performDeleteSelected();
      return;
    }
    setDeleteConfirm({
      kind: "selected",
      message: galleryMode === "assets" || galleryMode === "pending"
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
  const migratePaths = async (paths, viewerEntry = null, assetStatus = "ACTIVE") => {
    if (!paths.length) return;
    setBusy(true);
    setError("");
    try {
      const sourceByPath = new Map();
      for (const path of paths) {
        const source = history.find((item) => (item.images || []).some((image) => String(image.path).toLowerCase() === String(path).toLowerCase()));
        if (!source) continue;
        const timing = await loadGenerationTiming(source);
        sourceByPath.set(String(path).toLowerCase(), { ...source, ...(timing || {}) });
      }
      const response = await call("/api/gallery/move", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ paths }),
      }, 120000);
      const data = await readJson(response, "批量迁移接口");
      if (!response.ok) throw new Error(data.message || "批量迁移失败");
      if (!Array.isArray(data.assets) || !data.assets.length) throw new Error("本机 Agent 版本过旧，图片已迁移但未返回资产登记信息；请更新并重启 Agent 后重试");
      const assets = data.assets.map((asset) => {
        const source = sourceByPath.get(String(asset.oldPath || "").toLowerCase());
        const sourceStatus = source?.id && pendingGenerationSourceIds.current.has(String(source.id)) ? "PENDING" : assetStatus;
        return {
          ...asset,
          status: sourceStatus,
          generationCompletedAt: source?.generationCompletedAt,
          generationDurationMs: source?.generationDurationMs,
        };
      });
      const registerResponse = await fetch("/api/assets/batch", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ platform: data.platform || platform, items: assets }),
      });
      const registerData = await readJson(registerResponse, "后端资产保存接口");
      if (!registerResponse.ok || registerData.code !== 200) throw new Error(`图片已迁移，但后端资产保存失败：${registerData.message || registerResponse.status}`);
      const registeredAssets = registerData.data?.items || [];
      if (!registeredAssets.length) throw new Error("图片已迁移并写入后端，但后端没有返回新资产记录");
      await addRecentAssetRecords(registeredAssets, token, assetStatus === "PENDING" ? "pending" : "assets");
      // Clean up pendingGenerationSourceIds for migrated items
      for (const source of sourceByPath.values()) {
        if (source?.id) pendingGenerationSourceIds.current.delete(String(source.id));
      }
      setMoveDialog(false);
      setDirectMove(null);
      setSelectedImages(new Set());
      setSelectionMode(false);
      setNotice(assetStatus === "PENDING" ? "已加入待处理" : "转成资产成功");
      if (viewerEntry) advanceDetailAfterAction(viewerEntry.item, viewerEntry.image);
      else {
        removeGalleryImages("output", paths.map((path) => ({ path })));
      }
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
          generationCompletedAt: source?.item.generationCompletedAt,
          generationDurationMs: source?.item.generationDurationMs,
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
        const registeredAssets = registerData.data?.items || [];
        if (!registeredAssets.length) throw new Error("迁移后的资产已保存，但后端没有返回资产记录");
        await addRecentAssetRecords(registeredAssets, token);
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
      else if (changed.length) {
        const movedImages = entries
          .filter(({ image }) => changed.some((asset) => String(asset.oldPath).toLowerCase() === String(image.path).toLowerCase()))
          .map(({ image }) => image);
        removeGalleryImages("assets", movedImages);
      }
    } catch (e) {
      setError(`资产迁移失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const migratePendingToActive = async (entries) => {
    if (!entries.length) return;
    setBusy(true);
    setError("");
    try {
      const ids = entries.map(({ item }) => item.assetId).filter(Boolean);
      if (!ids.length) throw new Error("未找到待处理资产的 ID");
      const response = await fetch("/api/assets/status", {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ platform, ids, status: "ACTIVE" }),
      });
      const data = await readJson(response, "资产状态更新接口");
      if (!response.ok || data.code !== 200) throw new Error(data.message || "状态更新失败");
      const updatedCount = data.data?.updated || 0;
      // Remove from pending gallery
      const movedImages = entries.map(({ image }) => image);
      removeGalleryImages("pending", movedImages);
      // Add to assets gallery
      const assetRecords = entries.map(({ item }) => ({
        ...item, status: "ACTIVE",
      }));
      await addRecentAssetRecords(assetRecords, token, "assets");
      setSelectedImages(new Set());
      setSelectionMode(false);
      setNotice(updatedCount ? `已转成资产 ${updatedCount} 张图片` : "所选图片已转成资产");
    } catch (e) {
      setError(`转成资产失败：${e.message}`);
    } finally {
      setBusy(false);
    }
  };
  const directToAsset = async () => {
    const entries = selectedGalleryImages();
    return migratePaths(entries.map(({ image }) => image.path), null, "ACTIVE");
  };
  const moveSelected = async () => {
    const entries = selectedGalleryImages();
    if (galleryMode === "pending") return migratePendingToActive(entries);
    if (galleryMode === "assets") return migrateAssetEntries(entries);
    return migratePaths(entries.map(({ image }) => image.path), null, "PENDING");
  };
  const contextEntry = () => imageMenu ? { item: imageMenu.item, image: imageMenu.image, key: imageSelectionKey(imageMenu.item, imageMenu.image) } : null;
  const contextDelete = () => {
    const entry = contextEntry();
    if (!entry) return;
    const fromViewer = imageMenu.viewer;
    setImageMenu(null);
    performDeleteImage(entry.item, entry.image);
  };
  const contextCopy = async () => {
    const entry = contextEntry();
    if (!entry) return;
    setImageMenu(null);
    await copyGalleryImage(entry.image);
  };
  const contextOpenFolder = async () => {
    const entry = contextEntry();
    if (!entry) return;
    setImageMenu(null);
    try {
      await openImageFolder(entry.item, entry.image);
    } catch (exception) {
      setError(`打开所在文件夹失败：${exception.message}`);
    }
  };
  const contextMigrate = () => {
    const entry = contextEntry();
    if (!entry) return;
    const fromViewer = imageMenu.viewer;
    setImageMenu(null);
    if (entry.item.source === "asset") {
      if (galleryMode === "pending") migratePendingToActive([entry]);
      else migrateAssetEntries([entry], fromViewer ? entry : null);
    } else {
      migratePaths([entry.image.path], fromViewer ? entry : null, "PENDING");
    }
  };
  const contextSelectAll = () => {
    setSelectedImages(allHistorySelected ? new Set() : new Set(filteredGalleryImages.map((entry) => entry.key)));
    setSelectionMode(!allHistorySelected);
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
  const sortedTasks = sortActiveTasks(tasks);
  const runningTaskCount = tasks.filter((task) => task.state === "RUNNING").length;
  const queuedTaskCount = tasks.filter((task) => task.state === "QUEUED").length;
  const activeTask = sortedTasks[0] || null;
  const activeTaskWorkflowName = activeTask
    ? activeTask.workflowName || workflows.find((workflow) => workflow.id === (activeTask.workflowId || activeTask.form?.workflowId))?.name || (activeTask.external ? "外部 ComfyUI 工作流" : "当前工作流")
    : "";
  const detailPromptText = (value, field) => {
    if (detailPromptView === "raw") return value || "";
    if (detailPromptTranslation.loading) return "正在读取中文映射…";
    if (detailPromptTranslation.error) return `中文映射失败：${detailPromptTranslation.error}`;
    return detailPromptTranslation[field] || "";
  };
  const activeGalleryPage = activeGallerySource;

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
        <div />
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
          {launcher === "missing" && (
            <a
              className="bridge-launch-action"
              href={BRIDGE_LAUNCH_URL}
              onClick={() => {
                setNotice("请在浏览器提示中确认打开 Local ComfyUI Bridge");
                window.setTimeout(() => setNotice(""), 5000);
              }}
            >
              <Play />
              启动本机桥接器
            </a>
          )}
          <button
            onClick={() => control("start")}
            disabled={busy || launcher !== "ready" || running || !platformConfigured}
          >
            <Play />
            {controlAction === "start" ? "启动中…" : "启动"}
          </button>
          <button onClick={() => control("stop")} disabled={busy || launcher !== "ready"}>
            <Power />
            {controlAction === "stop" ? "停止中…" : "停止"}
          </button>
          <button onClick={() => control("restart")} disabled={busy || launcher !== "ready" || !platformConfigured}>
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
            promptOptions={allPromptOptions}
            onPromptOptionsReload={loadPromptOptions}
            presets={presets} presetQuery={presetQuery} onPresetChange={choosePreset}
            appliedPresetTitle={appliedPresetTitle} presetSaveName={presetSaveName} onPresetSaveNameChange={setPresetSaveName}
            onSavePreset={savePromptPreset} onReloadPreset={reloadCurrentPreset} onEditPreset={editCurrentPreset} presetSaving={presetSaving} presetReloading={presetReloading}
            onLuckyGenerate={luckyGenerate} luckyLoading={luckyLoading} onBatchGenerate={batchPromptGenerate} onGenerate={generate}
            disabled={{ blocked: busy || batchScheduling || !running || !token || !activeWorkflow, busy: busy || batchScheduling }}
          />
        </section>
        <section className="comfy-history">
          <div className="gallery-head">
            <div className="gallery-source-row">
              <div className="gallery-source-tabs">
                <button className={galleryMode === "output" ? "active" : ""} onClick={() => switchGallery("output").catch((e) => setError(e.message))}>本机图片</button>
                <button className={galleryMode === "pending" ? "active" : ""} onClick={() => switchGallery("pending").catch((e) => setError(e.message))}>待处理</button>
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
                {activeGalleryPage.total}{" "}
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
                  <button disabled={!selectedImages.size || !batchInputWorkflows.length} onClick={openBatchOperation}>批量操作 {selectedImages.size || ""}</button>
                  {galleryMode === "output" && <>
                    <button
                      className="move-action"
                      disabled={!selectedImages.size}
                      onClick={openMoveDialog}
                    >
                      加入待处理 {selectedImages.size || ""}
                    </button>
                    <button
                      className="move-action"
                      disabled={!selectedImages.size}
                      onClick={directToAsset}
                    >
                      转成资产 {selectedImages.size || ""}
                    </button>
                  </>}
                  {galleryMode === "pending" && <button
                      className="move-action"
                      disabled={!selectedImages.size}
                      onClick={openMoveDialog}
                    >
                      转成资产 {selectedImages.size || ""}
                    </button>}
                  <button
                    className="twitter-task-action"
                    disabled={!selectedImages.size || selectedTwitterImages().length > 4}
                    onClick={() => openTwitterTask().catch((e) => setError(e.message))}
                  >
                    <PaperPlaneTilt /> 添加到 Twitter 任务 {selectedTwitterImages().length || ""}
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
              <div className="task-queue-overview"><strong>当前 {tasks.length} 个任务</strong><span>执行中 {runningTaskCount}</span><span>排队 {queuedTaskCount}</span>{batchProgress && <span>Bridge 已接收 {batchProgress.submitted} / {batchProgress.total}</span>}{batchScheduling && <button type="button" onClick={() => { batchStopRequested.current = true; }}>停止加入 Bridge</button>}</div>
              <div className="task-queue-strip">
              {sortedTasks.map((task) => (
                <div
                  key={task.id}
                  className={`queue-pill ${task.state.toLowerCase()}`}
                  role="button"
                  tabIndex={0}
                  onClick={() => setTaskDetail(task)}
                  onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setTaskDetail(task); } }}
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
                          : task.state === "FAILED" ? "失败" : "完成"}
                    </span>
                    <small>{task.workflowName || workflows.find((workflow) => workflow.id === (task.workflowId || task.form?.workflowId))?.name || (task.external ? "外部 ComfyUI 工作流" : "当前工作流")}</small>
                    <em>已运行 {formatTaskElapsed(task, taskClock)}</em>
                  </div>
                  <strong>{task.progress === null ? "读取中" : `${task.progress}%`}</strong>
                  {["QUEUED", "RUNNING"].includes(task.state) && <button className="queue-pill__cancel" type="button" aria-label={`取消任务 ${task.id}`} title="取消任务" disabled={cancelingTask === String(task.id)} onClick={(event) => { event.stopPropagation(); cancelTask(task); }}><X /></button>}
                  <i style={{ width: `${task.progress ?? 0}%` }} />
                </div>
              ))}
              </div>
            </div>
          )}
          {history.length === 0 && (
            <div className="empty-mini">
              {activeGalleryPage.status === "loading" ? <SpinnerGap className="spin" size={38} /> : <ImageSquare size={38} />}
              <span>{activeGalleryPage.status === "loading"
                ? "正在读取图片…"
                : galleryMode === "assets" ? `当前 ${platform} 暂无已登记资产` : galleryMode === "pending" ? "暂无待处理图片" : "生成结果只保存在本机"}</span>
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
                    data-gallery-entry-id={item.promptId || item.id}
                    data-image-path={image.path}
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
          {activeGalleryPage.pages > 1 && <div className="asset-pagination">
            <button disabled={activeGalleryPage.page <= 1 || busy || activeGalleryPage.status === "loading"} onClick={() => loadGalleryPage(token, galleryMode, activeGalleryPage.page - 1).catch((e) => setError(e.message))}>上一页</button>
            <span>第 {activeGalleryPage.page} / {activeGalleryPage.pages} 页 · 每页 100 张</span>
            <button disabled={activeGalleryPage.page >= activeGalleryPage.pages || busy || activeGalleryPage.status === "loading"} onClick={() => loadGalleryPage(token, galleryMode, activeGalleryPage.page + 1).catch((e) => setError(e.message))}>下一页</button>
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
            <p>图片会直接移动到 ComfyUI/output 下的目标文件夹；迁移完成后原任务历史将删除。</p>
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
        <button onClick={() => contextCopy()}><Copy />复制图片</button>
        <button onClick={() => contextOpenFolder()}><FolderOpen />打开所在文件夹</button>
        <button className="danger" onClick={contextDelete}><Trash />删除</button>
        {imageMenu.item.source !== "asset" && <button onClick={contextMigrate}><FolderOpen />加入待处理</button>}
        {imageMenu.item.source !== "asset" && <button onClick={() => { const entry = contextEntry(); if (!entry) return; const fromViewer = imageMenu.viewer; setImageMenu(null); migratePaths([entry.image.path], fromViewer ? entry : null, "ACTIVE"); }}><FolderOpen />转成资产</button>}
        {imageMenu.item.source === "asset" && galleryMode === "pending" && <button onClick={contextMigrate}><FolderOpen />转成资产</button>}
        <button onClick={() => openImageInfo(imageMenu.item, imageMenu.image)}><Info />详细</button>
        {!imageMenu.viewer && <button onClick={contextSelectAll}><CheckCircle />{allHistorySelected ? "取消全选" : "全选"}</button>}
        {!imageMenu.viewer && <button onClick={() => contextTwitter().catch((e) => setError(e.message))}><PaperPlaneTilt />添加到 Twitter 任务</button>}
      </div>}
      {batchOperation.open && <div className="history-modal batch-operation-modal" onMouseDown={(event) => event.target === event.currentTarget && !batchOperation.busy && setBatchOperation({ open: false, workflowId: "", busy: false })}>
        <div className="batch-operation-panel"><header><div><span>批量操作</span><h3>为 {selectedImages.size} 张图片创建任务</h3></div><button type="button" disabled={batchOperation.busy} onClick={() => setBatchOperation({ open: false, workflowId: "", busy: false })}><X /></button></header><label><span>选择输入图片工作流</span><select value={batchOperation.workflowId} onChange={(event) => setBatchOperation((current) => ({ ...current, workflowId: event.target.value }))}>{batchInputWorkflows.map((workflow) => <option key={workflow.id} value={workflow.id}>{workflow.name || workflow.id}</option>)}</select></label><p>每张图片会建立一个独立任务；提交前会使用图片 SHA-256 和工作流检查是否已经生成过。</p><footer><button disabled={batchOperation.busy} onClick={() => setBatchOperation({ open: false, workflowId: "", busy: false })}>取消</button><button className="confirm-batch-operation" disabled={batchOperation.busy || !batchOperation.workflowId} onClick={runBatchOperation}>{batchOperation.busy ? "检查并提交中…" : "开始批量操作"}</button></footer></div>
      </div>}
      {taskDetail && <div className="history-modal task-detail-modal" onMouseDown={(event) => event.target === event.currentTarget && setTaskDetail(null)}>
        <div className="task-detail-panel">
          <header><div><span>任务详情</span><h3>{taskDetail.workflowName || "当前工作流"}</h3></div><PromptViewToggle value={detailPromptView} onChange={setDetailPromptView} /><button type="button" onClick={() => setTaskDetail(null)} aria-label="关闭任务详情"><X /></button></header>
          <div className="task-detail-summary">
            <span><small>状态</small>{taskDetail.state === "RUNNING" ? "生成中" : taskDetail.state === "QUEUED" ? "排队中" : taskDetail.state === "FAILED" ? "失败" : taskDetail.state}</span>
            <span><small>任务类型</small>{taskDetail.sourceType === "COMFYUI" || taskDetail.external ? "ComfyUI 外部任务" : "AIProvider 创建任务"}</span>
            <span><small>已运行</small>{formatTaskElapsed(taskDetail, taskClock)}</span>
            <span><small>进度</small>{taskDetail.progress == null ? "读取中" : `${Math.round(taskDetail.progress)}%`}</span>
            <span><small>Prompt 方案</small>{taskDetail.promptSchemeName || taskDetail.form?.promptSchemeName || "未使用方案"}</span>
            <span><small>任务 ID</small>{taskDetail.id}</span>
          </div>
          {taskDetail.progressDetail && <div className="task-detail-progress"><strong>{taskDetail.progressDetail.currentNode?.name || "当前节点"}</strong><span>{taskDetail.progressDetail.completedNodes || 0} / {taskDetail.progressDetail.totalNodes || 0} 个节点</span></div>}
          {taskDetail.inputImages?.length > 0 && <section className="task-detail-inputs"><strong>输入图片</strong><div>{taskDetail.inputImages.map((image) => <figure key={`${image.key}-${image.name}`}><img src={image.url} alt={image.name} /><figcaption>{image.name}</figcaption></figure>)}</div></section>}
          <label><span>正向 Prompt</span><textarea readOnly value={detailPromptText(taskDetail.form?.positivePrompt, "positivePrompt")} /></label>
          <label><span>反向 Prompt</span><textarea readOnly value={detailPromptText(taskDetail.form?.negativePrompt, "negativePrompt")} /></label>
          <div className="task-detail-meta"><span><small>Seed</small>{taskDetail.actualSeed ?? taskDetail.form?.seed ?? "-"}</span><span><small>Steps</small>{taskDetail.form?.steps ?? "-"}</span><span><small>CFG</small>{taskDetail.form?.cfg ?? "-"}</span><span><small>LoRA</small>{Array.isArray(taskDetail.form?.loras) ? taskDetail.form.loras.length : 0} 个</span></div>
        </div>
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
          <header><div><span>{infoDetail.item.source === "asset" ? "资产详情" : "本机图片详情"}</span><h3>{infoDetail.image.filename || "图片信息"}</h3></div><PromptViewToggle value={detailPromptView} onChange={setDetailPromptView} /><button onClick={() => setInfoDetail(null)}><X /></button></header>
          <div className="image-info-summary">
            <span><small>工作流</small>{infoDetail.item.workflowName || workflows.find((entry) => entry.id === infoDetail.item.workflowId)?.name || infoDetail.item.workflowId || "未记录"}</span>
            <span><small>Prompt 方案</small>{infoDetail.item.promptSchemeName || "未使用方案"}</span>
            <span><small>分辨率</small>{infoDetail.image.width || infoDetail.item.width || "-"} × {infoDetail.image.height || infoDetail.item.height || "-"}</span>
            <span><small>生成平台</small>{infoDetail.item.platform || platform || "未记录"}</span>
            <span><small>任务创建时间</small>{infoDetail.item.createdAt ? new Date(infoDetail.item.createdAt).toLocaleString("zh-CN", { hour12: false }) : "未记录"}</span>
            <span><small>生成完成时间</small>{infoDetail.item.generationCompletedAt ? new Date(infoDetail.item.generationCompletedAt).toLocaleString("zh-CN", { hour12: false }) : "未记录"}</span>
            <span><small>生成时间</small>{formatGenerationDuration(infoDetail.item.generationDurationMs)}</span>
            <span><small>Seed / Steps / CFG</small>{infoDetail.item.seed ?? "-"} / {infoDetail.item.steps ?? "-"} / {infoDetail.item.cfg ?? "-"}</span>
            <span><small>采样器 / 调度器</small>{infoDetail.item.sampler || "-"} / {infoDetail.item.scheduler || "-"}</span>
          </div>
          <section className="image-info-loras">
            <header><strong>使用的 LoRA</strong><small>{parseLoras(infoDetail.item.loras).length} 个</small></header>
            {parseLoras(infoDetail.item.loras).length ? <div>{parseLoras(infoDetail.item.loras).map((lora, index) => <article key={`${lora.name}-${index}`} title={lora.name}>
              <b>{index + 1}</b><span>{loraDisplayName(lora.name)}</span><small>模型 {Number(lora.modelStrength ?? 1).toFixed(2)}</small><small>CLIP {Number(lora.clipStrength ?? 1).toFixed(2)}</small>
            </article>)}</div> : <p>该图片没有记录 LoRA</p>}
          </section>
          <label className="image-info-prompt"><span>正向 Prompt <button disabled={!infoDetail.item.prompt} onClick={() => copyInfo(detailPromptText(infoDetail.item.prompt, "positivePrompt"), "正向 Prompt")}><Copy />复制</button></span><textarea readOnly value={detailPromptText(infoDetail.item.prompt, "positivePrompt")} /></label>
          <label className="image-info-prompt"><span>反向 Prompt <button disabled={!infoDetail.item.negativePrompt} onClick={() => copyInfo(detailPromptText(infoDetail.item.negativePrompt, "negativePrompt"), "反向 Prompt")}><Copy />复制</button></span><textarea readOnly value={detailPromptText(infoDetail.item.negativePrompt, "negativePrompt")} /></label>
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
                <button type="button" onClick={() => setViewerOrientation((current) => ({ ...current, rotation: (current.rotation + 90) % 360 }))} title="顺时针旋转 90°" aria-label="顺时针旋转图片 90 度"><ArrowClockwise /></button>
                <button type="button" aria-pressed={viewerOrientation.mirrored} onClick={() => setViewerOrientation((current) => ({ ...current, mirrored: !current.mirrored }))} title="水平镜像" aria-label="水平镜像图片"><ArrowsLeftRight /></button>
                <small>每次 20% · 滚轮同档</small>
              </div>
              <div className="viewer-file-title" title={detail.images[detail.index].filename || detail.images[detail.index].path || "图片"}>
                <strong>{detail.images[detail.index].filename || detail.images[detail.index].path?.split(/[\\/]/).pop() || "图片"}</strong>
                <span>{detail.index + 1} / {detail.images.length}</span>
                <small>
                  完成 {detail.images[detail.index].task.generationCompletedAt
                    ? new Date(detail.images[detail.index].task.generationCompletedAt).toLocaleString("zh-CN", { hour12: false })
                    : "未记录"}
                  {" · "}生成 {formatGenerationDuration(detail.images[detail.index].task.generationDurationMs)}
                </small>
              </div>
              <div className="viewer-header-actions">
                <button onClick={() => copyGalleryImage(detail.images[detail.index])} title="复制图片" aria-label="复制当前图片"><Copy /></button>
                <button onClick={() => openImageInfo(detail.images[detail.index].task, detail.images[detail.index])} title="查看详细信息"><Info /></button>
                <button onClick={closeDetail} title="关闭大图"><X /></button>
              </div>
            </header>
            <div className="history-lightbox" onContextMenu={(event) => {
              event.preventDefault();
              const image = detail.images[detail.index];
              setImageMenu({ x: Math.min(event.clientX, window.innerWidth - 190), y: Math.min(event.clientY, window.innerHeight - 205), item: image.task, image, viewer: true });
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
                      <img
                        className={`viewer-oriented-image ${viewerOrientation.rotation % 180 ? "is-quarter-turn" : ""}`}
                        style={{ transform: `rotate(${viewerOrientation.rotation}deg) scaleX(${viewerOrientation.mirrored ? -1 : 1})` }}
                        src={detail.images[detail.index].url}
                        alt="历史生成结果"
                      />
                    </TransformComponent>
                    <button className="lightbox-nav prev" aria-label="上一张图片" onClick={() => navigateDetail(-1)}><CaretLeft /></button>
                    <button className="lightbox-nav next" aria-label="下一张图片" onClick={() => navigateDetail(1)}><CaretRight /></button>
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

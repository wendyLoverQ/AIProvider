import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowClockwise,
  ArrowCounterClockwise,
  ArrowsOutSimple,
  DownloadSimple,
  Eraser,
  FlipHorizontal,
  FlipVertical,
  Hand,
  ImageSquare,
  MagicWand,
  Minus,
  PaintBrush,
  Polygon,
  Plus,
  Selection,
  SpinnerGap,
  Trash,
  UploadSimple,
  X,
} from "@phosphor-icons/react";
import "./ManualImageEditor.css";
import { distance, isSimplePolygon, nearestAnchor, nearestSegment } from "./manualCutout";

const EMPTY_WIDTH = 1200;
const EMPTY_HEIGHT = 800;
const HISTORY_LIMIT = 40;
const HISTORY_BYTE_LIMIT = 128 * 1024 * 1024;
const COMFY_BRIDGE = "http://127.0.0.1:32145";
const AI_POLL_INTERVAL = 1500;
const AI_TIMEOUT_MS = 10 * 60 * 1000;
const DEFAULT_AI_SETTINGS = Object.freeze({
  positivePrompt: "",
  negativePrompt: "low quality, blurry, artifacts, deformed",
  denoise: 0.65,
  steps: 28,
  cfg: 6,
  seed: 0,
  randomSeed: true,
  expandPercent: 25,
});

function trimHistory(snapshots) {
  const retained = [];
  let bytes = 0;
  for (let index = snapshots.length - 1; index >= 0 && retained.length < HISTORY_LIMIT; index -= 1) {
    const snapshot = snapshots[index];
    const snapshotBytes = (snapshot.pixels?.data?.byteLength || 0) + (snapshot.maskPixels?.data?.byteLength || 0);
    if (retained.length && bytes + snapshotBytes > HISTORY_BYTE_LIMIT) break;
    retained.unshift(snapshot);
    bytes += snapshotBytes;
  }
  return retained;
}
const DEFAULT_ADJUSTMENTS = Object.freeze({ brightness: 100, contrast: 100, saturation: 100, grayscale: 0, blur: 0 });
const FILTER_FIELDS = [
  ["brightness", "亮度", 0, 200, "%"],
  ["contrast", "对比度", 0, 200, "%"],
  ["saturation", "饱和度", 0, 200, "%"],
  ["grayscale", "黑白", 0, 100, "%"],
  ["blur", "模糊", 0, 20, "px"],
];

function buildFilter(adjustments) {
  return `brightness(${adjustments.brightness}%) contrast(${adjustments.contrast}%) saturate(${adjustments.saturation}%) grayscale(${adjustments.grayscale}%) blur(${adjustments.blur}px)`;
}

function canvasToBlob(canvas, type = "image/png", quality) {
  return new Promise((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error("无法创建图片数据")), type, quality));
}

async function responseMessage(response, fallback) {
  try {
    const payload = await response.json();
    return payload.message || payload.error || fallback;
  } catch {
    return fallback;
  }
}

const wait = (milliseconds) => new Promise((resolve) => window.setTimeout(resolve, milliseconds));
const localErrorMessage = (error, fallback) => error?.message === "Failed to fetch"
  ? "未检测到本机 ComfyUI Agent，请先启动 Agent 和 ComfyUI"
  : (error?.message || fallback);

function ToolButton({ active, icon: Icon, label, shortcut, onClick }) {
  return (
    <button
      type="button"
      className={active ? "manual-tool active" : "manual-tool"}
      onClick={onClick}
      title={`${label}${shortcut ? ` (${shortcut})` : ""}`}
    >
      <Icon weight={active ? "fill" : "regular"} />
      <span>{label}</span>
      {shortcut && <kbd>{shortcut}</kbd>}
    </button>
  );
}

export default function ManualImageEditor() {
  const canvasRef = useRef(null);
  const maskCanvasRef = useRef(null);
  const stageRef = useRef(null);
  const fileRef = useRef(null);
  const undoRef = useRef([]);
  const redoRef = useRef([]);
  const gestureRef = useRef(null);
  const aiAbortRef = useRef(null);
  const spacePressedRef = useRef(false);
  const zoomRafRef = useRef(null);
  const zoomRef = useRef(0.75);
  const offsetRef = useRef({ x: 0, y: 0 });
  const [tool, setTool] = useState("brush");
  const [color, setColor] = useState("#ff6f91");
  const [brushSize, setBrushSize] = useState(24);
  const [zoom, setZoom] = useState(0.75);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [hasContent, setHasContent] = useState(false);
  const [fileName, setFileName] = useState("未命名画布");
  const [canvasSize, setCanvasSize] = useState({ width: EMPTY_WIDTH, height: EMPTY_HEIGHT });
  const [historyState, setHistoryState] = useState({ undo: 0, redo: 0 });
  const [adjustments, setAdjustments] = useState({ ...DEFAULT_ADJUSTMENTS });
  const [exportFormat, setExportFormat] = useState("image/png");
  const [exportQuality, setExportQuality] = useState(92);
  const [notice, setNotice] = useState("");

  useEffect(() => { zoomRef.current = zoom; }, [zoom]);
  useEffect(() => { offsetRef.current = offset; }, [offset]);
  const [cutout, setCutout] = useState({ points: [], closed: false, selectedIndex: null, hover: null });
  const [hasMask, setHasMask] = useState(false);
  const [aiSettings, setAiSettings] = useState({ ...DEFAULT_AI_SETTINGS });
  const [aiWorkflows, setAiWorkflows] = useState([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState("");
  const [aiStatus, setAiStatus] = useState({ state: "loading", message: "正在连接本机 ComfyUI Agent…" });
  const [aiTask, setAiTask] = useState({ state: "idle", promptId: "" });
  const [bridgeToken, setBridgeToken] = useState("");
  const [spacePressed, setSpacePressed] = useState(false);

  const resetCutout = useCallback(() => {
    setCutout({ points: [], closed: false, selectedIndex: null, hover: null });
  }, []);

  const syncHistory = useCallback(() => {
    setHistoryState({ undo: undoRef.current.length, redo: redoRef.current.length });
  }, []);

  const capture = useCallback(() => {
    const canvas = canvasRef.current;
    const maskCanvas = maskCanvasRef.current;
    const context = canvas?.getContext("2d", { willReadFrequently: true });
    const maskContext = maskCanvas?.getContext("2d", { willReadFrequently: true });
    if (!canvas || !context || !maskCanvas || !maskContext) return null;
    return {
      width: canvas.width,
      height: canvas.height,
      pixels: context.getImageData(0, 0, canvas.width, canvas.height),
      maskPixels: maskContext.getImageData(0, 0, maskCanvas.width, maskCanvas.height),
      hasContent,
      hasMask,
    };
  }, [hasContent, hasMask]);

  const restore = useCallback((snapshot) => {
    const canvas = canvasRef.current;
    const maskCanvas = maskCanvasRef.current;
    if (!canvas || !maskCanvas || !snapshot) return;
    canvas.width = snapshot.width;
    canvas.height = snapshot.height;
    maskCanvas.width = snapshot.width;
    maskCanvas.height = snapshot.height;
    canvas.getContext("2d").putImageData(snapshot.pixels, 0, 0);
    if (snapshot.maskPixels) maskCanvas.getContext("2d").putImageData(snapshot.maskPixels, 0, 0);
    setCanvasSize({ width: snapshot.width, height: snapshot.height });
    setHasContent(snapshot.hasContent);
    setHasMask(Boolean(snapshot.hasMask));
  }, []);

  const checkpoint = useCallback(() => {
    const snapshot = capture();
    if (!snapshot) return;
    undoRef.current = trimHistory([...undoRef.current, snapshot]);
    redoRef.current = [];
    syncHistory();
  }, [capture, syncHistory]);

  const undo = useCallback(() => {
    if (!undoRef.current.length) return;
    const current = capture();
    const previous = undoRef.current.pop();
    if (current) redoRef.current = trimHistory([...redoRef.current, current]);
    restore(previous);
    syncHistory();
  }, [capture, restore, syncHistory]);

  const redo = useCallback(() => {
    if (!redoRef.current.length) return;
    const current = capture();
    const next = redoRef.current.pop();
    if (current) undoRef.current = trimHistory([...undoRef.current, current]);
    restore(next);
    syncHistory();
  }, [capture, restore, syncHistory]);

  const fitCanvas = useCallback(() => {
    const stage = stageRef.current;
    const canvas = canvasRef.current;
    if (!stage || !canvas) return;
    const nextZoom = Math.min(
      (stage.clientWidth - 96) / canvas.width,
      (stage.clientHeight - 96) / canvas.height,
      1,
    );
    setZoom(Math.max(0.1, nextZoom));
    setOffset({ x: 0, y: 0 });
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    const maskCanvas = maskCanvasRef.current;
    canvas.width = EMPTY_WIDTH;
    canvas.height = EMPTY_HEIGHT;
    maskCanvas.width = EMPTY_WIDTH;
    maskCanvas.height = EMPTY_HEIGHT;
    const frame = window.requestAnimationFrame(fitCanvas);
    const observer = new ResizeObserver(() => window.requestAnimationFrame(fitCanvas));
    if (stageRef.current) observer.observe(stageRef.current);
    return () => { window.cancelAnimationFrame(frame); observer.disconnect(); };
  }, [fitCanvas]);

  const loadAiWorkflows = useCallback(async () => {
    setAiStatus({ state: "loading", message: "正在连接本机 ComfyUI Agent…" });
    try {
      const configResponse = await fetch(`${COMFY_BRIDGE}/api/config`);
      if (!configResponse.ok) throw new Error("本机 ComfyUI Agent 未启动");
      const config = await configResponse.json();
      if (!config.token) throw new Error("本机 ComfyUI Agent 没有返回访问令牌");
      const headers = { "X-Local-Token": config.token };
      const statusResponse = await fetch(`${COMFY_BRIDGE}/api/comfy/status`, { headers });
      if (!statusResponse.ok) throw new Error(await responseMessage(statusResponse, "无法读取 ComfyUI 状态"));
      const comfyStatus = await statusResponse.json();
      if (comfyStatus.running === false || comfyStatus.connected === false) throw new Error(comfyStatus.message || "ComfyUI 尚未启动");
      const workflowsResponse = await fetch(`${COMFY_BRIDGE}/api/local-workflows`, { headers });
      if (!workflowsResponse.ok) throw new Error(await responseMessage(workflowsResponse, "无法读取本机工作流"));
      const payload = await workflowsResponse.json();
      const workflows = (payload.workflows || []).filter((workflow) => workflow.capabilities?.inpaint === true);
      setBridgeToken(config.token);
      setAiWorkflows(workflows);
      setSelectedWorkflowId((current) => workflows.some((workflow) => workflow.id === current) ? current : (workflows[0]?.id || ""));
      setAiStatus(workflows.length
        ? { state: "ready", message: `已发现 ${workflows.length} 个真实修补工作流` }
        : { state: "error", message: "没有发现包含蒙版修补节点的本机工作流" });
    } catch (error) {
      setBridgeToken("");
      setAiWorkflows([]);
      setSelectedWorkflowId("");
      setAiStatus({ state: "error", message: localErrorMessage(error, "无法连接本机 ComfyUI Agent") });
    }
  }, []);

  useEffect(() => {
    loadAiWorkflows();
    return () => aiAbortRef.current?.abort();
  }, [loadAiWorkflows]);

  useEffect(() => {
    const onKeyDown = (event) => {
      const key = event.key.toLowerCase();
      if ((event.ctrlKey || event.metaKey) && key === "z") {
        event.preventDefault();
        if (event.shiftKey) redo();
        else undo();
        return;
      }
      if ((event.ctrlKey || event.metaKey) && key === "y") {
        event.preventDefault();
        redo();
        return;
      }
      const isEditable = event.target instanceof HTMLInputElement
        || event.target instanceof HTMLTextAreaElement
        || event.target instanceof HTMLSelectElement
        || event.target?.isContentEditable;
      if (isEditable) return;
      if (event.code === "Space") {
        event.preventDefault();
        spacePressedRef.current = true;
        setSpacePressed(true);
        return;
      }
      if (key === "b") setTool("brush");
      if (key === "e") setTool("eraser");
      if (key === "h") setTool("pan");
      if (key === "p") setTool("polygon");
      if (key === "m") setTool(event.shiftKey ? "maskErase" : "mask");
      if (tool === "polygon" && key === "enter" && !cutout.closed && cutout.points.length >= 3) {
        event.preventDefault();
        if (!isSimplePolygon(cutout.points)) setNotice("选区边线不能交叉，请调整后再闭合");
        else setCutout((current) => ({ ...current, closed: true, selectedIndex: 0, hover: null }));
      }
      if (tool === "polygon" && key === "escape") {
        event.preventDefault();
        if (cutout.closed) resetCutout();
        else setCutout((current) => ({ ...current, points: current.points.slice(0, -1), hover: null }));
      }
      if (tool === "polygon" && cutout.closed && (key === "delete" || key === "backspace") && cutout.selectedIndex !== null) {
        event.preventDefault();
        if (cutout.points.length <= 3) setNotice("选区至少需要保留 3 个锚点");
        else setCutout((current) => ({ ...current, points: current.points.filter((_, index) => index !== current.selectedIndex), selectedIndex: null }));
      }
    };
    const onKeyUp = (event) => {
      if (event.code !== "Space") return;
      spacePressedRef.current = false;
      setSpacePressed(false);
    };
    const releaseSpace = () => {
      spacePressedRef.current = false;
      setSpacePressed(false);
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", releaseSpace);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", releaseSpace);
    };
  }, [cutout, redo, resetCutout, tool, undo]);

  const loadImageFile = useCallback((file) => {
    if (!file) return Promise.resolve(false);
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      setNotice("只支持 PNG、JPG 和 WebP 图片");
      return Promise.resolve(false);
    }
    return new Promise((resolve) => {
      const image = new Image();
      const objectUrl = URL.createObjectURL(file);
      image.onload = () => {
        checkpoint();
        const canvas = canvasRef.current;
        const maskCanvas = maskCanvasRef.current;
        canvas.width = image.naturalWidth;
        canvas.height = image.naturalHeight;
        maskCanvas.width = image.naturalWidth;
        maskCanvas.height = image.naturalHeight;
        canvas.getContext("2d").drawImage(image, 0, 0);
        URL.revokeObjectURL(objectUrl);
        setCanvasSize({ width: image.naturalWidth, height: image.naturalHeight });
        setHasContent(true);
        setHasMask(false);
        setFileName(file.name.replace(/\.[^.]+$/, "") || "未命名画布");
        setAdjustments({ ...DEFAULT_ADJUSTMENTS });
        setOffset({ x: 0, y: 0 });
        setNotice("");
        resetCutout();
        window.requestAnimationFrame(fitCanvas);
        resolve(true);
      };
      image.onerror = () => {
        URL.revokeObjectURL(objectUrl);
        setNotice("图片读取失败，请确认文件没有损坏");
        resolve(false);
      };
      image.src = objectUrl;
    });
  }, [checkpoint, fitCanvas, resetCutout]);

  const importImage = (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    loadImageFile(file);
  };

  useEffect(() => {
    const pasteImage = (event) => {
      const file = [...(event.clipboardData?.files || [])].find((item) => item.type.startsWith("image/"));
      if (!file) return;
      event.preventDefault();
      loadImageFile(file);
    };
    window.addEventListener("paste", pasteImage);
    return () => window.removeEventListener("paste", pasteImage);
  }, [loadImageFile]);

  const canvasPoint = (event) => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    return {
      x: ((event.clientX - rect.left) * canvas.width) / rect.width,
      y: ((event.clientY - rect.top) * canvas.height) / rect.height,
    };
  };

  const pointerDown = (event) => {
    if (tool === "pan" || event.button === 1 || event.altKey || spacePressedRef.current) {
      event.preventDefault();
      event.currentTarget.setPointerCapture(event.pointerId);
      gestureRef.current = { type: "pan", x: event.clientX, y: event.clientY, origin: offset };
      return;
    }
    checkpoint();
    event.currentTarget.setPointerCapture(event.pointerId);
    const point = canvasPoint(event);
    gestureRef.current = { type: "draw", point };
    const isMaskTool = tool === "mask" || tool === "maskErase";
    const context = (isMaskTool ? maskCanvasRef.current : canvasRef.current).getContext("2d");
    context.beginPath();
    context.moveTo(point.x, point.y);
    context.lineTo(point.x + 0.01, point.y + 0.01);
    context.lineWidth = brushSize;
    context.lineCap = "round";
    context.lineJoin = "round";
    context.strokeStyle = isMaskTool ? "#ff4f9a" : color;
    context.globalCompositeOperation = (tool === "eraser" || tool === "maskErase") ? "destination-out" : "source-over";
    context.stroke();
    if (isMaskTool) setHasMask(tool === "mask" ? true : hasMask);
    else setHasContent(true);
  };

  const closeCutout = () => {
    if (cutout.points.length < 3) return;
    if (!isSimplePolygon(cutout.points)) {
      setNotice("选区边线不能交叉，请调整后再闭合");
      return;
    }
    setNotice("");
    setCutout((current) => ({ ...current, closed: true, selectedIndex: 0, hover: null }));
  };

  const cutoutPointerDown = (event) => {
    if (!hasContent) return;
    if (event.button === 1 || event.altKey || spacePressedRef.current) return pointerDown(event);
    event.preventDefault();
    const point = canvasPoint(event);
    const tolerance = 11 / zoom;
    if (!cutout.closed) {
      if (cutout.points.length >= 3 && distance(point, cutout.points[0]) <= tolerance) {
        closeCutout();
        return;
      }
      if (event.detail > 1 && cutout.points.length >= 3) {
        closeCutout();
        return;
      }
      setCutout((current) => ({ ...current, points: [...current.points, point], hover: point }));
      return;
    }
    const anchor = nearestAnchor(cutout.points, point, tolerance);
    if (anchor) {
      event.currentTarget.setPointerCapture(event.pointerId);
      gestureRef.current = { type: "cutout-anchor", index: anchor.index };
      setCutout((current) => ({ ...current, selectedIndex: anchor.index }));
      return;
    }
    const segment = nearestSegment(cutout.points, point, 9 / zoom);
    if (segment) {
      const index = segment.index + 1;
      event.currentTarget.setPointerCapture(event.pointerId);
      gestureRef.current = { type: "cutout-anchor", index };
      setCutout((current) => ({ ...current, points: [...current.points.slice(0, index), segment.point, ...current.points.slice(index)], selectedIndex: index }));
      return;
    }
    setCutout((current) => ({ ...current, selectedIndex: null }));
  };

  const cutoutPointerMove = (event) => {
    if (gestureRef.current?.type === "pan") {
      const gesture = gestureRef.current;
      setOffset({
        x: gesture.origin.x + event.clientX - gesture.x,
        y: gesture.origin.y + event.clientY - gesture.y,
      });
      return;
    }
    const point = canvasPoint(event);
    if (gestureRef.current?.type === "cutout-anchor") {
      const { index } = gestureRef.current;
      const canvas = canvasRef.current;
      const bounded = { x: Math.max(0, Math.min(canvas.width, point.x)), y: Math.max(0, Math.min(canvas.height, point.y)) };
      setCutout((current) => ({ ...current, points: current.points.map((anchor, anchorIndex) => anchorIndex === index ? bounded : anchor) }));
      return;
    }
    if (!cutout.closed) setCutout((current) => ({ ...current, hover: point }));
  };

  const applyCutout = (mode) => {
    if (!cutout.closed || cutout.points.length < 3) return;
    if (!isSimplePolygon(cutout.points)) {
      setNotice("调整后的选区边线发生交叉，请先移动锚点消除交叉");
      return;
    }
    checkpoint();
    const canvas = canvasRef.current;
    const context = canvas.getContext("2d");
    const copy = document.createElement("canvas");
    copy.width = canvas.width;
    copy.height = canvas.height;
    copy.getContext("2d").drawImage(canvas, 0, 0);
    context.save();
    context.beginPath();
    context.moveTo(cutout.points[0].x, cutout.points[0].y);
    cutout.points.slice(1).forEach((point) => context.lineTo(point.x, point.y));
    context.closePath();
    if (mode === "keep") {
      context.globalCompositeOperation = "source-over";
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.clip();
      context.drawImage(copy, 0, 0);
    } else {
      context.globalCompositeOperation = "destination-out";
      context.fill();
    }
    context.restore();
    context.globalCompositeOperation = "source-over";
    setExportFormat("image/png");
    setNotice("抠图已应用，导出格式已切换为支持透明背景的 PNG");
    resetCutout();
  };

  const pointerMove = (event) => {
    const gesture = gestureRef.current;
    if (!gesture) return;
    if (gesture.type === "pan") {
      setOffset({
        x: gesture.origin.x + event.clientX - gesture.x,
        y: gesture.origin.y + event.clientY - gesture.y,
      });
      return;
    }
    const point = canvasPoint(event);
    const isMaskTool = tool === "mask" || tool === "maskErase";
    const context = (isMaskTool ? maskCanvasRef.current : canvasRef.current).getContext("2d");
    context.beginPath();
    context.moveTo(gesture.point.x, gesture.point.y);
    context.lineTo(point.x, point.y);
    context.lineWidth = brushSize;
    context.lineCap = "round";
    context.lineJoin = "round";
    context.strokeStyle = isMaskTool ? "#ff4f9a" : color;
    context.globalCompositeOperation = (tool === "eraser" || tool === "maskErase") ? "destination-out" : "source-over";
    context.stroke();
    gestureRef.current.point = point;
  };

  const pointerUp = (event) => {
    gestureRef.current = null;
    const context = canvasRef.current?.getContext("2d");
    const maskContext = maskCanvasRef.current?.getContext("2d");
    if (context) context.globalCompositeOperation = "source-over";
    if (maskContext) maskContext.globalCompositeOperation = "source-over";
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const rotate = (direction = 1) => {
    resetCutout();
    checkpoint();
    const canvas = canvasRef.current;
    [canvas, maskCanvasRef.current].forEach((target) => {
      const copy = document.createElement("canvas");
      copy.width = target.width;
      copy.height = target.height;
      copy.getContext("2d").drawImage(target, 0, 0);
      target.width = copy.height;
      target.height = copy.width;
      const context = target.getContext("2d");
      if (direction > 0) {
        context.translate(target.width, 0);
        context.rotate(Math.PI / 2);
      } else {
        context.translate(0, target.height);
        context.rotate(-Math.PI / 2);
      }
      context.drawImage(copy, 0, 0);
    });
    setCanvasSize({ width: canvas.width, height: canvas.height });
    window.requestAnimationFrame(fitCanvas);
  };

  const flip = (axis) => {
    resetCutout();
    checkpoint();
    [canvasRef.current, maskCanvasRef.current].forEach((canvas) => {
      const copy = document.createElement("canvas");
      copy.width = canvas.width;
      copy.height = canvas.height;
      copy.getContext("2d").drawImage(canvas, 0, 0);
      const context = canvas.getContext("2d");
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.save();
      if (axis === "x") {
        context.translate(canvas.width, 0);
        context.scale(-1, 1);
      } else {
        context.translate(0, canvas.height);
        context.scale(1, -1);
      }
      context.drawImage(copy, 0, 0);
      context.restore();
    });
  };

  const cropToRatio = (ratio) => {
    if (!hasContent) return;
    const canvas = canvasRef.current;
    let width = canvas.width;
    let height = canvas.height;
    if (width / height > ratio) width = Math.round(height * ratio);
    else height = Math.round(width / ratio);
    if (width === canvas.width && height === canvas.height) return;
    checkpoint();
    resetCutout();
    const sourceX = Math.round((canvas.width - width) / 2);
    const sourceY = Math.round((canvas.height - height) / 2);
    [canvas, maskCanvasRef.current].forEach((target) => {
      const copy = document.createElement("canvas");
      copy.width = target.width;
      copy.height = target.height;
      copy.getContext("2d").drawImage(target, 0, 0);
      target.width = width;
      target.height = height;
      target.getContext("2d").drawImage(copy, sourceX, sourceY, width, height, 0, 0, width, height);
    });
    setCanvasSize({ width, height });
    window.requestAnimationFrame(fitCanvas);
  };

  const clearCanvas = () => {
    checkpoint();
    const canvas = canvasRef.current;
    canvas.getContext("2d").clearRect(0, 0, canvas.width, canvas.height);
    maskCanvasRef.current.getContext("2d").clearRect(0, 0, canvas.width, canvas.height);
    setHasContent(false);
    setHasMask(false);
    resetCutout();
  };

  const clearMask = () => {
    const maskCanvas = maskCanvasRef.current;
    if (!maskCanvas || !hasMask) return;
    checkpoint();
    maskCanvas.getContext("2d").clearRect(0, 0, maskCanvas.width, maskCanvas.height);
    setHasMask(false);
  };

  const expandCanvas = (side) => {
    if (!hasContent) return;
    checkpoint();
    resetCutout();
    const canvas = canvasRef.current;
    const amount = Math.ceil(Math.max(64, (side === "left" || side === "right" ? canvas.width : canvas.height) * aiSettings.expandPercent / 100) / 8) * 8;
    const width = canvas.width + (side === "left" || side === "right" ? amount : 0);
    const height = canvas.height + (side === "top" || side === "bottom" ? amount : 0);
    const offsetX = side === "left" ? amount : 0;
    const offsetY = side === "top" ? amount : 0;
    [canvas, maskCanvasRef.current].forEach((target) => {
      const copy = document.createElement("canvas");
      copy.width = target.width;
      copy.height = target.height;
      copy.getContext("2d").drawImage(target, 0, 0);
      target.width = width;
      target.height = height;
      target.getContext("2d").drawImage(copy, offsetX, offsetY);
    });
    setCanvasSize({ width, height });
    setNotice(`已向${{ left: "左", right: "右", top: "上", bottom: "下" }[side]}扩展 ${amount}px；透明区域会作为扩图蒙版`);
    window.requestAnimationFrame(fitCanvas);
  };

  const createMaskedSource = async () => {
    const canvas = canvasRef.current;
    const maskCanvas = maskCanvasRef.current;
    const output = document.createElement("canvas");
    output.width = canvas.width;
    output.height = canvas.height;
    const context = output.getContext("2d", { willReadFrequently: true });
    context.filter = buildFilter(adjustments);
    context.drawImage(canvas, 0, 0);
    context.filter = "none";
    const pixels = context.getImageData(0, 0, output.width, output.height);
    const sourcePixels = canvas.getContext("2d", { willReadFrequently: true }).getImageData(0, 0, canvas.width, canvas.height);
    const maskPixels = maskCanvas.getContext("2d", { willReadFrequently: true }).getImageData(0, 0, maskCanvas.width, maskCanvas.height);
    let editablePixels = 0;
    for (let index = 0; index < pixels.data.length; index += 4) {
      if (maskPixels.data[index + 3] > 8 || sourcePixels.data[index + 3] < 250) {
        pixels.data[index + 3] = 0;
        editablePixels += 1;
      }
    }
    if (!editablePixels) throw new Error("请先用 AI 遮罩涂抹要修改的区域，或先扩展画布");
    context.putImageData(pixels, 0, 0);
    return new File([await canvasToBlob(output)], `${fileName || "image"}-inpaint.png`, { type: "image/png" });
  };

  const runAiEdit = async () => {
    const workflow = aiWorkflows.find((item) => item.id === selectedWorkflowId);
    if (!workflow) return setNotice("请选择一个真实的 ComfyUI 修补工作流");
    if (!bridgeToken) return setNotice("本机 ComfyUI Agent 尚未连接");
    const controller = new AbortController();
    aiAbortRef.current = controller;
    setNotice("");
    setAiTask({ state: "preparing", promptId: "" });
    try {
      const sourceImage = await createMaskedSource();
      const body = new FormData();
      Object.entries(workflow.defaults || {}).forEach(([key, value]) => body.append(key, Array.isArray(value) || (value && typeof value === "object") ? JSON.stringify(value) : String(value ?? "")));
      Object.entries(aiSettings).forEach(([key, value]) => body.set(key, String(value)));
      body.set("workflowId", workflow.id);
      body.set("workflowName", workflow.name || workflow.id);
      body.set("workflowDefinition", JSON.stringify(workflow.definition));
      body.set("workflowBinding", JSON.stringify(workflow.binding));
      body.set("width", String(canvasRef.current.width));
      body.set("height", String(canvasRef.current.height));
      body.set("folder", "aimaid/editor");
      body.set("clientId", crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`);
      body.set("sourceImage", sourceImage);
      const response = await fetch(`${COMFY_BRIDGE}/api/generate`, { method: "POST", headers: { "X-Local-Token": bridgeToken }, body, signal: controller.signal });
      if (!response.ok) throw new Error(await responseMessage(response, `ComfyUI 提交失败（HTTP ${response.status}）`));
      const submission = await response.json();
      if (!submission.promptId) throw new Error("ComfyUI 没有返回任务编号");
      setAiTask({ state: "running", promptId: submission.promptId });
      const startedAt = Date.now();
      let outputImage = null;
      while (!outputImage) {
        if (Date.now() - startedAt > AI_TIMEOUT_MS) throw new Error("AI 编辑等待超过 10 分钟，已停止轮询");
        await wait(AI_POLL_INTERVAL);
        const historyResponse = await fetch(`${COMFY_BRIDGE}/comfy/history/${encodeURIComponent(submission.promptId)}`, { headers: { "X-Local-Token": bridgeToken }, signal: controller.signal });
        if (!historyResponse.ok) throw new Error(await responseMessage(historyResponse, "无法读取 ComfyUI 任务状态"));
        const history = await historyResponse.json();
        const record = history?.[submission.promptId];
        if (!record) continue;
        if (record.status?.status_str === "error") throw new Error(record.status?.messages?.at?.(-1)?.[1]?.exception_message || "ComfyUI 执行失败");
        outputImage = record.outputs?.[submission.finalOutputNodeId]?.images?.[0]
          || Object.values(record.outputs || {}).find((output) => output?.images?.length)?.images?.[0]
          || null;
        if ((record.status?.status_str === "success" || record.status?.completed) && !outputImage) throw new Error("工作流已完成，但没有返回图片输出");
      }
      const query = new URLSearchParams({ filename: outputImage.filename, subfolder: outputImage.subfolder || "", type: outputImage.type || "output" });
      const imageResponse = await fetch(`${COMFY_BRIDGE}/comfy/view?${query}`, { headers: { "X-Local-Token": bridgeToken }, signal: controller.signal });
      if (!imageResponse.ok) throw new Error(await responseMessage(imageResponse, "无法读取 AI 编辑结果"));
      const resultBlob = await imageResponse.blob();
      await loadImageFile(new File([resultBlob], `${fileName || "image"}-ai.png`, { type: resultBlob.type || "image/png" }));
      setAiTask({ state: "idle", promptId: "" });
      setNotice("AI 编辑完成，结果已回填画布，可继续手动编辑或撤销");
    } catch (error) {
      setAiTask({ state: "idle", promptId: "" });
      if (error.name !== "AbortError") setNotice(localErrorMessage(error, "AI 编辑失败"));
    } finally {
      if (aiAbortRef.current === controller) aiAbortRef.current = null;
    }
  };

  const cancelAiEdit = async () => {
    aiAbortRef.current?.abort();
    if (aiTask.promptId && bridgeToken) {
      try {
        await fetch(`${COMFY_BRIDGE}/api/tasks/${encodeURIComponent(aiTask.promptId)}/cancel`, { method: "POST", headers: { "X-Local-Token": bridgeToken } });
      } catch {
        setNotice("取消请求未能送达本机 ComfyUI Agent");
      }
    }
    setAiTask({ state: "idle", promptId: "" });
  };

  const exportImage = () => {
    const canvas = canvasRef.current;
    const output = document.createElement("canvas");
    output.width = canvas.width;
    output.height = canvas.height;
    const context = output.getContext("2d");
    context.filter = buildFilter(adjustments);
    context.drawImage(canvas, 0, 0);
    output.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      const extension = exportFormat === "image/jpeg" ? "jpg" : exportFormat === "image/webp" ? "webp" : "png";
      link.download = `${fileName || "图片编辑"}-edited.${extension}`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
    }, exportFormat, exportQuality / 100);
  };

  const zoomAtPointer = (event) => {
    event.preventDefault();
    const stage = stageRef.current;
    if (!stage) return;
    const rect = stage.getBoundingClientRect();
    const pointer = {
      x: event.clientX - rect.left - rect.width / 2,
      y: event.clientY - rect.top - rect.height / 2,
    };
    const currentZoom = zoomRef.current;
    const nextZoom = Math.min(4, Math.max(0.1, currentZoom * (event.deltaY > 0 ? 0.9 : 1.1)));
    if (nextZoom === currentZoom) return;
    const ratio = nextZoom / currentZoom;
    const currentOffset = offsetRef.current;
    const nextOffset = {
      x: pointer.x - (pointer.x - currentOffset.x) * ratio,
      y: pointer.y - (pointer.y - currentOffset.y) * ratio,
    };
    zoomRef.current = nextZoom;
    offsetRef.current = nextOffset;
    if (zoomRafRef.current) return;
    zoomRafRef.current = requestAnimationFrame(() => {
      zoomRafRef.current = null;
      setZoom(zoomRef.current);
      setOffset(offsetRef.current);
    });
  };

  return (
    <section className="manual-editor-shell">
      <aside className="manual-editor-tools" aria-label="图片编辑工具">
        <div className="manual-tools-title"><span>TOOLS</span><strong>手动工具</strong></div>
        <ToolButton active={tool === "brush"} icon={PaintBrush} label="画笔" shortcut="B" onClick={() => setTool("brush")} />
        <ToolButton active={tool === "eraser"} icon={Eraser} label="橡皮擦" shortcut="E" onClick={() => setTool("eraser")} />
        <ToolButton active={tool === "mask"} icon={Selection} label="AI 遮罩" shortcut="M" onClick={() => setTool("mask")} />
        <ToolButton active={tool === "maskErase"} icon={Eraser} label="擦除遮罩" shortcut="⇧M" onClick={() => setTool("maskErase")} />
        <ToolButton active={tool === "polygon"} icon={Polygon} label="连线抠图" shortcut="P" onClick={() => setTool("polygon")} />
        <ToolButton active={tool === "pan"} icon={Hand} label="移动画布" shortcut="H" onClick={() => setTool("pan")} />
        <div className="manual-tool-separator" />
        <ToolButton icon={UploadSimple} label="导入图片" onClick={() => fileRef.current?.click()} />
        <ToolButton icon={ArrowCounterClockwise} label="逆时针旋转" onClick={() => rotate(-1)} />
        <ToolButton icon={ArrowClockwise} label="顺时针旋转" onClick={() => rotate(1)} />
        <ToolButton icon={FlipHorizontal} label="水平翻转" onClick={() => flip("x")} />
        <ToolButton icon={FlipVertical} label="垂直翻转" onClick={() => flip("y")} />
        <ToolButton icon={Trash} label="清空画布" onClick={clearCanvas} />
        <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" onChange={importImage} hidden />
      </aside>

      <div className="manual-editor-main">
        <header className="manual-editor-bar">
          <div className="manual-document-name">
            <ImageSquare weight="duotone" />
            <span><strong>{fileName}</strong><small>{canvasSize.width} × {canvasSize.height} px</small></span>
          </div>
          <div className="manual-history-actions">
            <button type="button" onClick={undo} disabled={!historyState.undo} title="撤销 (Ctrl+Z)"><ArrowCounterClockwise /></button>
            <button type="button" onClick={redo} disabled={!historyState.redo} title="重做 (Ctrl+Y)"><ArrowClockwise /></button>
          </div>
          <button type="button" className="manual-export" onClick={exportImage} disabled={!hasContent}><DownloadSimple />导出图片</button>
        </header>

        <div className="manual-editor-options">
          {tool === "polygon" ? <>
            <strong className="manual-cutout-status">{cutout.closed ? `已闭合 · ${cutout.points.length} 个锚点` : cutout.points.length ? `绘制中 · ${cutout.points.length} 个锚点` : "点击画面逐点连线"}</strong>
            <button type="button" className="manual-cutout-action" disabled={!cutout.closed} onClick={() => applyCutout("keep")}>保留选区</button>
            <button type="button" className="manual-cutout-action danger" disabled={!cutout.closed} onClick={() => applyCutout("remove")}>删除选区</button>
            <button type="button" className="manual-cutout-clear" disabled={!cutout.points.length} onClick={resetCutout}>重画</button>
            <span className="manual-hint">空格 / Alt / 中键拖动画布 · Enter 闭合 · Esc 回退</span>
          </> : (tool === "mask" || tool === "maskErase") ? <>
            <strong className="manual-cutout-status">{tool === "mask" ? "涂抹 AI 要重绘的区域" : "擦除 AI 遮罩"}</strong>
            <label className="manual-brush-size"><span>遮罩大小</span><input aria-label="笔刷大小" type="range" min="2" max="240" value={brushSize} onChange={(event) => setBrushSize(Number(event.target.value))} /><output>{brushSize}px</output></label>
            <button type="button" className="manual-cutout-clear" disabled={!hasMask} onClick={clearMask}>清空遮罩</button>
            <span className="manual-hint">粉色区域会交给 AI 重绘；透明扩展区会自动参与扩图</span>
          </> : <>
            <label className="manual-color"><span>颜色</span><input aria-label="画笔颜色" type="color" value={color} onChange={(event) => setColor(event.target.value)} /><code>{color.toUpperCase()}</code></label>
            <label className="manual-brush-size"><span>{tool === "eraser" ? "橡皮擦" : "画笔"}大小</span><input aria-label="笔刷大小" type="range" min="2" max="160" value={brushSize} onChange={(event) => setBrushSize(Number(event.target.value))} /><output>{brushSize}px</output></label>
            <span className="manual-hint">按住空格、Alt 或鼠标中键可临时移动画布</span>
          </>}
        </div>

        <div
          ref={stageRef}
          className={`manual-stage tool-${tool}${spacePressed ? " is-temporary-pan" : ""}`}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => {
            event.preventDefault();
            loadImageFile([...event.dataTransfer.files].find((item) => item.type.startsWith("image/")));
          }}
          onWheel={zoomAtPointer}
        >
          <div className={`manual-canvas-position${hasContent ? "" : " is-empty"}`} style={{ transform: `translate(calc(-50% + ${offset.x}px), calc(-50% + ${offset.y}px)) scale(${zoom})` }}>
            <canvas ref={canvasRef} aria-label="图片编辑画布" style={{ filter: buildFilter(adjustments) }} onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={pointerUp} onPointerCancel={pointerUp} />
            <canvas ref={maskCanvasRef} className="manual-mask-canvas" aria-label="AI 编辑遮罩" />
            {hasContent && tool === "polygon" && <svg className="manual-cutout-overlay" viewBox={`0 0 ${canvasSize.width} ${canvasSize.height}`} onPointerDown={cutoutPointerDown} onPointerMove={cutoutPointerMove} onPointerUp={pointerUp} onPointerCancel={pointerUp}>
              {cutout.points.length > 0 && <>
                <polyline points={cutout.points.map((point) => `${point.x},${point.y}`).join(" ")} className={cutout.closed ? "is-closed" : ""} />
                {!cutout.closed && cutout.hover && <line x1={cutout.points.at(-1).x} y1={cutout.points.at(-1).y} x2={cutout.hover.x} y2={cutout.hover.y} className="manual-cutout-pending" />}
                {cutout.points.map((point, index) => <circle key={index} cx={point.x} cy={point.y} r={7 / zoom} className={`${index === 0 ? "is-first" : ""}${index === cutout.selectedIndex ? " is-selected" : ""}`} />)}
              </>}
            </svg>}
          </div>
          {!hasContent && (
            <button type="button" className="manual-empty" onClick={() => fileRef.current?.click()}>
              <UploadSimple /><strong>导入一张图片开始编辑</strong><span>点击、拖入或粘贴 · 支持 PNG、JPG、WebP</span>
            </button>
          )}
          {notice && <div className="manual-editor-notice" role="alert">{notice}</div>}
          <div className="manual-zoom-controls">
            <button type="button" onClick={() => setZoom((value) => Math.max(0.1, value - 0.1))} title="缩小"><Minus /></button>
            <span>{Math.round(zoom * 100)}%</span>
            <button type="button" onClick={() => setZoom((value) => Math.min(4, value + 0.1))} title="放大"><Plus /></button>
            <button type="button" onClick={fitCanvas} title="适应窗口"><ArrowsOutSimple /></button>
          </div>
        </div>
      </div>

      <aside className="manual-editor-properties" aria-label="图片属性">
        <header><span>PROPERTIES</span><strong>图片属性</strong><small>非破坏式调整</small></header>
        <fieldset className="manual-ai-panel">
          <legend><MagicWand weight="duotone" /> AI 修补 / 扩图</legend>
          <div className={`manual-ai-status is-${aiStatus.state}`}><span />{aiStatus.message}<button type="button" onClick={loadAiWorkflows}>重新检测</button></div>
          <label className="manual-ai-field"><span>修补工作流</span><select aria-label="修补工作流" value={selectedWorkflowId} onChange={(event) => setSelectedWorkflowId(event.target.value)} disabled={aiStatus.state !== "ready" || aiTask.state !== "idle"}>{aiWorkflows.length ? aiWorkflows.map((workflow) => <option key={workflow.id} value={workflow.id}>{workflow.name}</option>) : <option value="">暂无可用工作流</option>}</select></label>
          <label className="manual-ai-field"><span>描述想要的结果</span><textarea aria-label="AI 正向提示词" rows="3" placeholder="例如：补全自然的草地和远处山脉" value={aiSettings.positivePrompt} onChange={(event) => setAiSettings((current) => ({ ...current, positivePrompt: event.target.value }))} /></label>
          <label className="manual-ai-field"><span>排除内容</span><textarea aria-label="AI 反向提示词" rows="2" value={aiSettings.negativePrompt} onChange={(event) => setAiSettings((current) => ({ ...current, negativePrompt: event.target.value }))} /></label>
          <div className="manual-ai-parameters">
            <label><span>重绘强度 <output>{aiSettings.denoise.toFixed(2)}</output></span><input aria-label="重绘强度" type="range" min="0.05" max="1" step="0.05" value={aiSettings.denoise} onChange={(event) => setAiSettings((current) => ({ ...current, denoise: Number(event.target.value) }))} /></label>
            <label><span>步数 <output>{aiSettings.steps}</output></span><input aria-label="生成步数" type="range" min="1" max="80" value={aiSettings.steps} onChange={(event) => setAiSettings((current) => ({ ...current, steps: Number(event.target.value) }))} /></label>
          </div>
          <div className="manual-expand-settings">
            <label><span>扩展</span><input aria-label="扩展比例" type="number" min="5" max="100" value={aiSettings.expandPercent} onChange={(event) => setAiSettings((current) => ({ ...current, expandPercent: Math.min(100, Math.max(5, Number(event.target.value) || 5)) }))} /><b>%</b></label>
            <div className="manual-expand-grid">
              <button type="button" aria-label="向上扩图" disabled={!hasContent} onClick={() => expandCanvas("top")}>上</button>
              <button type="button" aria-label="向左扩图" disabled={!hasContent} onClick={() => expandCanvas("left")}>左</button>
              <button type="button" aria-label="向右扩图" disabled={!hasContent} onClick={() => expandCanvas("right")}>右</button>
              <button type="button" aria-label="向下扩图" disabled={!hasContent} onClick={() => expandCanvas("bottom")}>下</button>
            </div>
          </div>
          {aiTask.state === "idle" ? <button type="button" className="manual-ai-run" disabled={!hasContent || aiStatus.state !== "ready" || !selectedWorkflowId} onClick={runAiEdit}><MagicWand />开始 AI 编辑</button> : <button type="button" className="manual-ai-run is-cancel" onClick={cancelAiEdit}><SpinnerGap className="is-spinning" /><span>{aiTask.state === "running" ? "ComfyUI 正在生成…" : "正在准备图片…"}</span><X />取消</button>}
          <p>使用本机 Agent 中真实的 ComfyUI 蒙版工作流。粉色遮罩和透明扩展区会作为重绘范围。</p>
        </fieldset>
        <fieldset disabled={!hasContent}>
          <legend>裁剪比例</legend>
          <div className="manual-crop-ratios">
            <button type="button" onClick={() => cropToRatio(1)}>1 : 1</button>
            <button type="button" onClick={() => cropToRatio(4 / 3)}>4 : 3</button>
            <button type="button" onClick={() => cropToRatio(16 / 9)}>16 : 9</button>
            <button type="button" onClick={() => cropToRatio(9 / 16)}>9 : 16</button>
          </div>
          <p>按当前画布中心裁剪，可通过撤销恢复。</p>
        </fieldset>
        <fieldset disabled={!hasContent}>
          <legend>画面调整</legend>
          {FILTER_FIELDS.map(([key, label, min, max, unit]) => (
            <label className="manual-property-range" key={key}>
              <span><b>{label}</b><output>{adjustments[key]}{unit}</output></span>
              <input aria-label={label} type="range" min={min} max={max} value={adjustments[key]} onChange={(event) => setAdjustments((current) => ({ ...current, [key]: Number(event.target.value) }))} />
            </label>
          ))}
          <button type="button" className="manual-reset-adjustments" onClick={() => setAdjustments({ ...DEFAULT_ADJUSTMENTS })}>重置画面调整</button>
        </fieldset>
        <fieldset disabled={!hasContent}>
          <legend>导出设置</legend>
          <label className="manual-property-select"><span>格式</span><select value={exportFormat} onChange={(event) => setExportFormat(event.target.value)}><option value="image/png">PNG</option><option value="image/jpeg">JPG</option><option value="image/webp">WebP</option></select></label>
          {exportFormat !== "image/png" && <label className="manual-property-range"><span><b>质量</b><output>{exportQuality}%</output></span><input aria-label="导出质量" type="range" min="30" max="100" value={exportQuality} onChange={(event) => setExportQuality(Number(event.target.value))} /></label>}
        </fieldset>
      </aside>
    </section>
  );
}

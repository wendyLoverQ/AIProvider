import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowClockwise, ArrowsLeftRight, ArrowCounterClockwise, CaretLeft, CaretRight, CheckCircle, Desktop, ImageSquare, MagnifyingGlassMinus, MagnifyingGlassPlus, Play, Star, Trash, UploadSimple, X } from "@phosphor-icons/react";
import { TransformComponent, TransformWrapper } from "react-zoom-pan-pinch";
import UiSearchField from "./UiSearchField";
import UiToast from "./UiToast";
import { readJsonResponse } from "./apiResponse";
import "./FavoriteMediaLibrary.css";

const VIEWER_ZOOM_STEP = 0.2;
const IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp", "image/gif"];
const VIDEO_TYPES = ["video/mp4", "video/webm", "video/quicktime"];
const ACCEPTED_TYPES = [...IMAGE_TYPES, ...VIDEO_TYPES];
const ACCEPT_ATTR = [...IMAGE_TYPES, ...VIDEO_TYPES].join(",");
const isVideoItem = (item) => item?.mediaType === "video" || String(item?.contentType || "").startsWith("video/");
const isVideoFile = (file) => file && (file.type ? String(file.type).startsWith("video/") : /\.(mp4|webm|mov)$/i.test(file.name));
const isAcceptedFile = (file) => file && (ACCEPTED_TYPES.includes(file.type) || /\.(png|jpe?g|webp|gif|mp4|webm|mov)$/i.test(file.name));
const uploadFavorite = (form, onProgress) => new Promise((resolve, reject) => {
  const request = new XMLHttpRequest();
  request.open("POST", "/api/favorites"); request.responseType = "json";
  request.upload.addEventListener("progress", (event) => {
    if (event.lengthComputable) onProgress(event.loaded, event.total);
  });
  request.addEventListener("load", () => {
    const result = request.response;
    if (!result) { reject(new Error("我的最爱上传接口响应异常")); return; }
    if (request.status < 200 || request.status >= 300 || result?.code !== 200) reject(new Error(result?.message || "上传失败"));
    else resolve(result.data);
  });
  request.addEventListener("error", () => reject(new Error("上传请求失败")));
  request.addEventListener("abort", () => reject(new Error("上传已取消")));
  request.send(form);
});

const BRIDGE = "http://127.0.0.1:32145";
const formatSize = (value) => {
  const bytes = Number(value || 0);
  if (bytes < 1024 * 1024) return `${Math.max(0, bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};
const formatDate = (value) => value ? new Date(value).toLocaleDateString("zh-CN", { month: "short", day: "numeric", year: "numeric" }) : "刚刚";
const imageFileName = (item) => item.originalFileName || `${item.title || "favorite"}.png`;
const drawCover = (context, image, width, height) => {
  const scale = Math.max(width / image.width, height / image.height);
  const drawnWidth = image.width * scale, drawnHeight = image.height * scale;
  context.drawImage(image, (width - drawnWidth) / 2, (height - drawnHeight) / 2, drawnWidth, drawnHeight);
};
const drawContain = (context, image, width, height) => {
  const scale = Math.min(width / image.width, height / image.height);
  const drawnWidth = image.width * scale, drawnHeight = image.height * scale;
  context.drawImage(image, (width - drawnWidth) / 2, (height - drawnHeight) / 2, drawnWidth, drawnHeight);
};
const renderWallpaper = async (blob, monitor, mode) => {
  const image = await createImageBitmap(blob);
  try {
    const width = Math.max(1, Number(monitor.width)), height = Math.max(1, Number(monitor.height));
    const canvas = document.createElement("canvas"); canvas.width = width; canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("浏览器无法创建壁纸画布");
    context.fillStyle = "#0b0d14"; context.fillRect(0, 0, width, height);
    const orientationMismatch = (image.width >= image.height) !== (width >= height);
    if (mode === "stretch") context.drawImage(image, 0, 0, width, height);
    else if (mode === "fit") drawContain(context, image, width, height);
    else if (mode === "fill" || !orientationMismatch) drawCover(context, image, width, height);
    else {
      context.save(); context.filter = `blur(${Math.max(28, Math.round(Math.min(width, height) * .035))}px) brightness(.55)`;
      drawCover(context, image, width, height); context.restore();
      context.fillStyle = "rgba(0,0,0,.08)"; context.fillRect(0, 0, width, height);
      drawContain(context, image, width, height);
    }
    return await new Promise((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error("壁纸渲染失败")), "image/png"));
  } finally { image.close(); }
};

export default function FavoriteMediaLibrary() {
  const inputRef = useRef(null);
  const dragDepth = useRef(0);
  const uploadingRef = useRef(false);
  const [items, setItems] = useState([]);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(() => new Set());
  const [selectionMode, setSelectionMode] = useState(false);
  const [uploading, setUploading] = useState({ active: false, done: 0, total: 0, uploadedBytes: 0, totalBytes: 0, currentName: "" });
  const [draggingFiles, setDraggingFiles] = useState(false);
  const [pendingDropFiles, setPendingDropFiles] = useState([]);
  const [previewIndex, setPreviewIndex] = useState(null);
  const [viewerOrientation, setViewerOrientation] = useState({ rotation: 0, mirrored: false });
  const viewerTransform = useRef(null);
  const [menu, setMenu] = useState(null);
  const [deleteIds, setDeleteIds] = useState([]);
  const [wallpaper, setWallpaper] = useState({ open: false, item: null, monitors: [], selectedId: "", fitMode: "smart", loading: false, token: "" });

  const load = async () => {
    setState("loading"); setError("");
    try {
      const response = await fetch("/api/favorites?page=1&pageSize=100");
      const result = await readJsonResponse(response, "我的最爱接口响应异常");
      if (!response.ok || result.code !== 200) throw new Error(result.message || "读取我的最爱失败");
      setItems(result.data?.items || []); setState("ready");
    } catch (exception) { setError(exception.message); setState("error"); }
  };
  useEffect(() => { load(); }, []);
  const openPreview = (index) => {
    setViewerOrientation({ rotation: 0, mirrored: false });
    setPreviewIndex(index);
  };
  const closePreview = () => {
    setPreviewIndex(null);
    viewerTransform.current?.resetTransform(0, "easeOut");
  };
  const navigatePreview = (delta) => {
    if (previewIndex === null || !filtered.length) return;
    const next = (previewIndex + delta + filtered.length) % filtered.length;
    setViewerOrientation({ rotation: 0, mirrored: false });
    viewerTransform.current?.resetTransform(140, "easeOut");
    setPreviewIndex(next);
  };
  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase();
    return keyword ? items.filter((item) => [item.title, item.originalFileName, item.prompt].some((value) => String(value || "").toLocaleLowerCase().includes(keyword))) : items;
  }, [items, query]);
  useEffect(() => {
    if (previewIndex === null) return;
    const onKey = (event) => {
      if (event.key === "ArrowLeft") { event.preventDefault(); navigatePreview(-1); }
      else if (event.key === "ArrowRight") { event.preventDefault(); navigatePreview(1); }
      else if (event.key === "Escape") { event.preventDefault(); closePreview(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [previewIndex, filtered.length]);
  const allSelected = filtered.length > 0 && filtered.every((item) => selected.has(item.id));
  const toggle = (id) => setSelected((current) => {
    const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next;
  });
  const toggleAll = () => { setSelected(allSelected ? new Set() : new Set(filtered.map((item) => item.id))); setSelectionMode(!allSelected); };

  const uploadFiles = useCallback(async (fileList) => {
    const files = [...(fileList || [])];
    if (!files.length || uploadingRef.current) return;
    const unsupported = files.filter((file) => !isAcceptedFile(file));
    if (unsupported.length) { setError(`不支持这些文件：${unsupported.map((file) => file.name).join("、")}`); return; }
    uploadingRef.current = true;
    const totalBytes = files.reduce((sum, file) => sum + Number(file.size || 0), 0);
    let completedBytes = 0;
    setUploading({ active: true, done: 0, total: files.length, uploadedBytes: 0, totalBytes, currentName: files[0].name }); setError(""); setNotice("");
    try {
      const uploaded = [];
      for (let index = 0; index < files.length; index++) {
        const file = files[index];
        setUploading((current) => ({ ...current, done: index, uploadedBytes: completedBytes, currentName: file.name }));
        const form = new FormData(); form.append("file", file, file.name);
        form.append("title", file.name.replace(/\.[^.]+$/, ""));
        if (!isVideoFile(file)) {
          const dimensions = await createImageBitmap(file);
          form.append("width", String(dimensions.width)); form.append("height", String(dimensions.height)); dimensions.close();
        }
        let uploadedItem;
        try {
          uploadedItem = await uploadFavorite(form, (loaded, requestTotal) => {
            const fileBytes = Number(file.size || 0);
            const currentBytes = requestTotal > 0 ? Math.round((loaded / requestTotal) * fileBytes) : 0;
            setUploading((current) => ({ ...current, uploadedBytes: completedBytes + currentBytes }));
          });
        } catch (exception) { throw new Error(`${file.name}：${exception.message}`); }
        uploaded.push(uploadedItem); completedBytes += Number(file.size || 0);
        setUploading((current) => ({ ...current, done: index + 1, uploadedBytes: completedBytes }));
      }
      setItems((current) => [...new Map([...uploaded, ...current].map((item) => [item.id, item])).values()]);
      setNotice(`已保存 ${uploaded.length} 个媒体到服务器`);
    } catch (exception) { setError(`上传失败：${exception.message}`); }
    finally { uploadingRef.current = false; setUploading({ active: false, done: 0, total: 0, uploadedBytes: 0, totalBytes: 0, currentName: "" }); if (inputRef.current) inputRef.current.value = ""; }
  }, []);
  useEffect(() => {
    const hasFiles = (event) => Boolean(event.dataTransfer?.files?.length) || Array.from(event.dataTransfer?.types || []).includes("Files");
    const onDragEnter = (event) => {
      if (!hasFiles(event)) return;
      event.preventDefault(); dragDepth.current += 1; setDraggingFiles(true);
    };
    const onDragOver = (event) => {
      if (!hasFiles(event)) return;
      event.preventDefault(); event.dataTransfer.dropEffect = "copy";
    };
    const onDragLeave = () => {
      if (!dragDepth.current) return;
      dragDepth.current = Math.max(0, dragDepth.current - 1);
      if (!dragDepth.current) setDraggingFiles(false);
    };
    const resetDrag = () => { dragDepth.current = 0; setDraggingFiles(false); };
    const onDrop = (event) => {
      if (!hasFiles(event)) return;
      event.preventDefault(); const files = [...(event.dataTransfer?.files || [])]; resetDrag();
      const unsupported = files.filter((file) => !isAcceptedFile(file));
      if (unsupported.length) setError(`不支持这些文件：${unsupported.map((file) => file.name).join("、")}`);
      else if (files.length && !uploadingRef.current) setPendingDropFiles(files);
    };
    window.addEventListener("dragenter", onDragEnter);
    window.addEventListener("dragover", onDragOver);
    window.addEventListener("dragleave", onDragLeave);
    window.addEventListener("drop", onDrop);
    window.addEventListener("dragend", resetDrag);
    return () => {
      window.removeEventListener("dragenter", onDragEnter);
      window.removeEventListener("dragover", onDragOver);
      window.removeEventListener("dragleave", onDragLeave);
      window.removeEventListener("drop", onDrop);
      window.removeEventListener("dragend", resetDrag);
    };
  }, []);
  const remove = async () => {
    if (!deleteIds.length) return;
    try {
      const response = await fetch("/api/favorites", { method: "DELETE", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ids: deleteIds }) });
      const result = await readJsonResponse(response, "我的最爱删除接口响应异常");
      if (!response.ok || result.code !== 200) throw new Error(result.message || "删除失败");
      const deleting = new Set(deleteIds); setItems((current) => current.filter((item) => !deleting.has(item.id)));
      setSelected(new Set()); setSelectionMode(false); setDeleteIds([]); setNotice(`已移除 ${result.data?.deleted || deleting.size} 项`);
    } catch (exception) { setError(`移除失败：${exception.message}`); }
  };
  const openWallpaper = async (item) => {
    setMenu(null); setError(""); setWallpaper({ open: true, item, monitors: [], selectedId: "", fitMode: "smart", loading: true, token: "" });
    try {
      const configResponse = await fetch(`${BRIDGE}/api/config`);
      const config = await configResponse.json();
      if (!configResponse.ok || !config.success || !config.token) throw new Error(config.message || "本机 Bridge 未就绪");
      const response = await fetch(`${BRIDGE}/api/wallpaper/monitors`, { headers: { "X-Local-Token": config.token } });
      const result = await response.json();
      if (!response.ok || !result.success) throw new Error(result.message || "读取显示器失败");
      const monitors = result.monitors || [];
      if (!monitors.length) throw new Error("没有检测到可用显示器");
      setWallpaper({ open: true, item, monitors, selectedId: monitors.find((monitor) => monitor.primary)?.id || monitors[0].id, fitMode: "smart", loading: false, token: config.token });
    } catch (exception) {
      setWallpaper((current) => ({ ...current, loading: false })); setError(`无法准备壁纸：${exception.message}`);
    }
  };
  const applyWallpaper = async () => {
    if (!wallpaper.item || !wallpaper.selectedId) return;
    setWallpaper((current) => ({ ...current, loading: true })); setError("");
    try {
      const imageResponse = await fetch(wallpaper.item.contentUrl);
      if (!imageResponse.ok) throw new Error("无法从服务器读取收藏原图");
      const monitor = wallpaper.monitors.find((entry) => entry.id === wallpaper.selectedId);
      if (!monitor) throw new Error("所选显示器已经不可用");
      const rendered = await renderWallpaper(await imageResponse.blob(), monitor, wallpaper.fitMode);
      const form = new FormData();
      form.append("file", rendered, `${imageFileName(wallpaper.item).replace(/\.[^.]+$/, "")}-${monitor.number}.png`);
      form.append("monitorId", wallpaper.selectedId);
      const response = await fetch(`${BRIDGE}/api/wallpaper/apply`, { method: "POST", headers: { "X-Local-Token": wallpaper.token }, body: form });
      const result = await response.json();
      if (!response.ok || !result.success) throw new Error(result.message || "壁纸应用失败");
      setWallpaper({ open: false, item: null, monitors: [], selectedId: "", fitMode: "smart", loading: false, token: "" });
      setNotice(`已应用到${monitor?.label || "所选显示器"}`);
    } catch (exception) { setError(`应用壁纸失败：${exception.message}`); setWallpaper((current) => ({ ...current, loading: false })); }
  };

  return <section className="favorite-library">
    {draggingFiles && <div className="favorite-drop-overlay" role="status" aria-label="拖放上传区域"><UploadSimple /><strong>松开即可上传</strong><span>支持图片与视频，可同时拖入多个文件</span></div>}
    <div className="favorite-hero">
      <div className="favorite-hero-text">
        <div className="favorite-hero-badge"><Star weight="fill" /></div>
        <span className="favorite-hero-eyebrow"><Star weight="fill" /> Private Media Library</span>
      </div>
      <div className="favorite-hero-stats">
        <div className="favorite-hero-stat"><strong>{items.length}</strong><span>张服务器原图</span><small>{items.reduce((sum, item) => sum + Number(item.fileSize || 0), 0) ? formatSize(items.reduce((sum, item) => sum + Number(item.fileSize || 0), 0)) : "等待第一张图片"}</small></div>
      </div>
    </div>

    <div className="favorite-toolbar">
      <UiSearchField aria-label="搜索我的最爱" placeholder="按标题、文件名或 Prompt 搜索" value={query} onChange={(event) => setQuery(event.target.value)} />
      <div className="favorite-toolbar-actions">
        {selectionMode ? <>
          <button type="button" onClick={toggleAll}><CheckCircle />{allSelected ? "取消全选" : "全选"}</button>
          <button type="button" className="danger" disabled={!selected.size} onClick={() => setDeleteIds([...selected])}><Trash />删除已选 {selected.size || ""}</button>
          <button type="button" onClick={() => { setSelectionMode(false); setSelected(new Set()); }}><X />完成</button>
        </> : <button type="button" className="danger" onClick={() => setSelectionMode(true)}><Trash />批量删除</button>}
        <button type="button" onClick={load} disabled={state === "loading"}><ArrowClockwise />刷新</button>
        <button type="button" className="primary" onClick={() => inputRef.current?.click()} disabled={uploading.active}><UploadSimple />{uploading.active ? `${uploading.done}/${uploading.total}` : "上传"}</button>
        <input ref={inputRef} className="favorite-file-input" type="file" accept={ACCEPT_ATTR} multiple onChange={(event) => uploadFiles(event.target.files)} />
      </div>
    </div>

    <UiToast message={error || notice} tone={error ? "error" : "success"} onDismiss={() => { setError(""); setNotice(""); }} />
    {uploading.active && <div className="favorite-upload-progress" role="status" aria-label="上传进度">
      <header><strong>正在上传 {uploading.currentName}</strong><span>{uploading.done}/{uploading.total}</span></header>
      <progress max={Math.max(1, uploading.totalBytes)} value={uploading.uploadedBytes} />
      <small>{Math.round((uploading.uploadedBytes / Math.max(1, uploading.totalBytes)) * 100)}%</small>
    </div>}
    {state === "loading" && !items.length ? <div className="favorite-empty"><Star /><strong>正在打开你的私人画廊…</strong></div>
      : !filtered.length ? <div className="favorite-empty"><ImageSquare /><strong>{query ? "没有找到匹配的媒体" : "这里还没有喜欢的画面"}</strong><span>{query ? "换一个关键词试试" : "可以直接上传，或从图像工坊的“我的资产”批量转入"}</span>{!query && <button type="button" className="primary" onClick={() => inputRef.current?.click()}><UploadSimple />上传第一个媒体</button>}</div>
        : <div className="favorite-grid">{filtered.map((item, index) => <article className="favorite-card" key={item.id} data-selected={selected.has(item.id)}>
          <button type="button" className="favorite-image" aria-label={selectionMode ? `${selected.has(item.id) ? "取消选择" : "选择"} ${item.title}` : `预览 ${item.title}`} onClick={() => selectionMode ? toggle(item.id) : openPreview(index)} onContextMenu={(event) => { event.preventDefault(); setMenu({ item, x: Math.min(event.clientX, window.innerWidth - 210), y: Math.min(event.clientY, window.innerHeight - 150) }); }}>{isVideoItem(item) ? <><img src={item.thumbnailUrl || item.contentUrl} alt="" loading="lazy" /><span className="favorite-video-badge"><Play weight="fill" /></span></> : <img src={item.thumbnailUrl || item.contentUrl} alt="" loading="lazy" />}</button>
          <div className="favorite-card-meta"><strong>{item.title}</strong><span>{formatDate(item.createdAt)} · {formatSize(item.fileSize)}</span></div>
        </article>)}</div>}

    {menu && <div className="favorite-context-menu" style={{ left: menu.x, top: menu.y }}>
      <button type="button" onClick={() => openWallpaper(menu.item)}><Desktop />应用为壁纸</button>
      <button type="button" onClick={() => { const targetIndex = filtered.findIndex((entry) => entry.id === menu.item.id); setMenu(null); if (targetIndex >= 0) openPreview(targetIndex); }}><ImageSquare />查看原图</button>
      <button type="button" className="danger" onClick={() => { setMenu(null); setDeleteIds([menu.item.id]); }}><Trash />从我的最爱移除</button>
    </div>}
    {previewIndex !== null && filtered[previewIndex] && <div className="favorite-preview" role="dialog" aria-modal="true" aria-label={`预览 ${filtered[previewIndex].title}`} onMouseDown={(event) => event.target === event.currentTarget && closePreview()}>
      <div className="favorite-preview-panel">
        <header className="favorite-preview-header">
          <div className="favorite-preview-tools">
            {isVideoItem(filtered[previewIndex]) ? <small>← / → 切换 · Esc 关闭</small> : <>
              <button type="button" aria-label="缩小图片" title="缩小 (每次 20%)" onClick={() => viewerTransform.current?.zoomOut(VIEWER_ZOOM_STEP, 120, "easeOut")}><MagnifyingGlassMinus /></button>
              <button type="button" aria-label="恢复原始大小和位置" title="恢复原位" onClick={() => viewerTransform.current?.resetTransform(140, "easeOut")}><ArrowCounterClockwise /></button>
              <button type="button" aria-label="放大图片" title="放大 (每次 20%)" onClick={() => viewerTransform.current?.zoomIn(VIEWER_ZOOM_STEP, 120, "easeOut")}><MagnifyingGlassPlus /></button>
              <button type="button" aria-label="顺时针旋转图片 90 度" title="顺时针旋转 90°" onClick={() => setViewerOrientation((current) => ({ ...current, rotation: (current.rotation + 90) % 360 }))}><ArrowClockwise /></button>
              <button type="button" aria-pressed={viewerOrientation.mirrored} aria-label="水平镜像图片" title="水平镜像" onClick={() => setViewerOrientation((current) => ({ ...current, mirrored: !current.mirrored }))}><ArrowsLeftRight /></button>
              <small>滚轮缩放 · 双击切换 · ← / → 切换 · Esc 关闭</small>
            </>}
          </div>
          <div className="favorite-preview-title" title={filtered[previewIndex].originalFileName || filtered[previewIndex].title}>
            <strong>{filtered[previewIndex].title}</strong>
            <span>{previewIndex + 1} / {filtered.length}</span>
            <small>{filtered[previewIndex].originalFileName} · {formatSize(filtered[previewIndex].fileSize)} · {formatDate(filtered[previewIndex].createdAt)}</small>
          </div>
          <div className="favorite-preview-actions">
            <button type="button" aria-label="应用为壁纸" title="应用为壁纸" onClick={() => openWallpaper(filtered[previewIndex])}><Desktop /></button>
            <button type="button" aria-label="关闭预览" title="关闭预览 (Esc)" onClick={closePreview}><X /></button>
          </div>
        </header>
        <div className="favorite-preview-stage">
          {isVideoItem(filtered[previewIndex]) ? (
            <video
              className="favorite-preview-video"
              src={filtered[previewIndex].contentUrl}
              poster={filtered[previewIndex].thumbnailUrl || undefined}
              controls
              autoPlay
              loop
              preload="metadata"
            />
          ) : (
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
              panning={{ velocityDisabled: false }}
            >
              {() => (
                <TransformComponent wrapperClass="favorite-preview-zoom" contentClass="favorite-preview-zoom-content">
                  <img
                    className={`favorite-preview-image ${viewerOrientation.rotation % 180 ? "is-quarter-turn" : ""}`}
                    style={{ transform: `rotate(${viewerOrientation.rotation}deg) scaleX(${viewerOrientation.mirrored ? -1 : 1})` }}
                    src={filtered[previewIndex].contentUrl}
                    alt={filtered[previewIndex].title}
                    draggable={false}
                  />
                </TransformComponent>
              )}
            </TransformWrapper>
          )}
          {filtered.length > 1 && <>
            <button type="button" className="favorite-preview-nav prev" aria-label="上一张" onClick={() => navigatePreview(-1)}><CaretLeft /></button>
            <button type="button" className="favorite-preview-nav next" aria-label="下一张" onClick={() => navigatePreview(1)}><CaretRight /></button>
          </>}
        </div>
        <footer className="favorite-preview-footer">
          <span><strong>{filtered[previewIndex].width || "?"} × {filtered[previewIndex].height || "?"}</strong><small>原始分辨率</small></span>
          <span><strong>{filtered[previewIndex].sourcePlatform || "本地上传"}</strong><small>来源</small></span>
          <span><strong>{filtered[previewIndex].prompt ? "已记录" : "无"}</strong><small>Prompt</small></span>
          <div className="favorite-preview-footer-actions">
            <button type="button" onClick={() => openWallpaper(filtered[previewIndex])}><Desktop />选择显示器并应用为壁纸</button>
          </div>
        </footer>
      </div>
    </div>}
    {!!pendingDropFiles.length && <div className="favorite-confirm" role="dialog" aria-modal="true" aria-label="确认拖放上传"><div><UploadSimple /><h3>上传这些媒体？</h3><p>即将把 {pendingDropFiles.length} 个图片或视频复制到服务器“我的最爱”。</p><span className="favorite-drop-files">{pendingDropFiles.slice(0, 4).map((file) => file.name).join("、")}{pendingDropFiles.length > 4 ? ` 等 ${pendingDropFiles.length} 个文件` : ""}</span><footer><button type="button" onClick={() => setPendingDropFiles([])}>取消</button><button type="button" className="primary" onClick={() => { const files = pendingDropFiles; setPendingDropFiles([]); uploadFiles(files); }}>确认上传</button></footer></div></div>}
    {!!deleteIds.length && <div className="favorite-confirm" role="dialog" aria-modal="true" aria-label="确认删除"><div><Trash /><h3>删除选中的媒体？</h3><p>将按 ID 删除服务器上的 {deleteIds.length} 个原图及其记录，此操作不可恢复。</p><footer><button type="button" onClick={() => setDeleteIds([])}>取消</button><button type="button" className="danger" onClick={remove}>确认删除</button></footer></div></div>}
    {wallpaper.open && <div className="favorite-confirm wallpaper-dialog" role="dialog" aria-modal="true" aria-label="选择壁纸显示器"><div>
      <Desktop /><h3>应用到哪一台显示器？</h3><p>默认会判断图片与屏幕方向，只替换所选屏幕。</p>
      {wallpaper.loading && !wallpaper.monitors.length ? <span className="wallpaper-loading">正在读取本机显示器…</span> : <>
        <div className="monitor-options">{wallpaper.monitors.map((monitor) => <label key={monitor.id} data-selected={wallpaper.selectedId === monitor.id}><input type="radio" name="wallpaper-monitor" value={monitor.id} checked={wallpaper.selectedId === monitor.id} onChange={() => setWallpaper((current) => ({ ...current, selectedId: monitor.id }))} /><i>{monitor.number}</i><span><strong>{monitor.label}{monitor.primary ? " · 主屏" : ""}</strong><small>{monitor.width} × {monitor.height}</small></span></label>)}</div>
        <div className="wallpaper-fit"><span>画面适配</span><div>{[["smart","智能适配"],["fill","裁剪铺满"],["fit","完整显示"],["stretch","拉伸"]].map(([value,label]) => <button type="button" key={value} aria-pressed={wallpaper.fitMode === value} onClick={() => setWallpaper((current) => ({ ...current, fitMode: value }))}>{label}</button>)}</div><small>{wallpaper.fitMode === "smart" ? "方向不一致时使用模糊扩展背景，保留完整主体" : wallpaper.fitMode === "fill" ? "保持比例并裁掉超出屏幕的边缘" : wallpaper.fitMode === "fit" ? "保持整张图片，空余区域使用深色背景" : "强制铺满屏幕，可能改变图片比例"}</small></div>
        {wallpaper.item && wallpaper.selectedId && (() => { const monitor = wallpaper.monitors.find((entry) => entry.id === wallpaper.selectedId); const portrait = monitor && monitor.height > monitor.width; const mismatch = monitor && ((wallpaper.item.width >= wallpaper.item.height) !== (monitor.width >= monitor.height)); return monitor ? <div className={`wallpaper-preview mode-${wallpaper.fitMode}${mismatch ? " mismatch" : ""}`} style={{ aspectRatio: `${monitor.width}/${monitor.height}`, width: portrait ? "auto" : "min(100%,350px)", height: portrait ? "190px" : "auto" }}><img className="wallpaper-preview-bg" src={wallpaper.item.contentUrl} alt="" /><img className="wallpaper-preview-main" src={wallpaper.item.contentUrl} alt="适配预览" /></div> : null; })()}
      </>}
      <footer><button type="button" disabled={wallpaper.loading} onClick={() => setWallpaper({ open: false, item: null, monitors: [], selectedId: "", fitMode: "smart", loading: false, token: "" })}>取消</button><button type="button" className="primary" disabled={wallpaper.loading || !wallpaper.selectedId} onClick={applyWallpaper}>{wallpaper.loading ? "正在生成并应用…" : "应用壁纸"}</button></footer>
    </div></div>}
  </section>;
}

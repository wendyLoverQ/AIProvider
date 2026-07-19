import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowClockwise, CheckCircle, Desktop, DotsThree, ImageSquare, Star, Trash, UploadSimple, X } from "@phosphor-icons/react";
import UiSearchField from "./UiSearchField";
import UiToast from "./UiToast";
import { readJsonResponse } from "./apiResponse";
import "./FavoriteMediaLibrary.css";

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
  const [items, setItems] = useState([]);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(() => new Set());
  const [selectionMode, setSelectionMode] = useState(false);
  const [uploading, setUploading] = useState({ active: false, done: 0, total: 0 });
  const [preview, setPreview] = useState(null);
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
  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase();
    return keyword ? items.filter((item) => [item.title, item.originalFileName, item.prompt].some((value) => String(value || "").toLocaleLowerCase().includes(keyword))) : items;
  }, [items, query]);
  const allSelected = filtered.length > 0 && filtered.every((item) => selected.has(item.id));
  const toggle = (id) => setSelected((current) => {
    const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next;
  });
  const toggleAll = () => { setSelected(allSelected ? new Set() : new Set(filtered.map((item) => item.id))); setSelectionMode(!allSelected); };

  const uploadFiles = async (fileList) => {
    const files = [...(fileList || [])];
    if (!files.length) return;
    setUploading({ active: true, done: 0, total: files.length }); setError(""); setNotice("");
    try {
      const uploaded = [];
      for (let index = 0; index < files.length; index++) {
        const form = new FormData(); form.append("file", files[index], files[index].name);
        form.append("title", files[index].name.replace(/\.[^.]+$/, ""));
        const dimensions = await createImageBitmap(files[index]);
        form.append("width", String(dimensions.width)); form.append("height", String(dimensions.height)); dimensions.close();
        const response = await fetch("/api/favorites", { method: "POST", body: form });
        const result = await readJsonResponse(response, "我的最爱上传接口响应异常");
        if (!response.ok || result.code !== 200) throw new Error(`${files[index].name}：${result.message || "上传失败"}`);
        uploaded.push(result.data); setUploading({ active: true, done: index + 1, total: files.length });
      }
      setItems((current) => [...new Map([...uploaded, ...current].map((item) => [item.id, item])).values()]);
      setNotice(`已保存 ${uploaded.length} 张图片到服务器`);
    } catch (exception) { setError(`上传失败：${exception.message}`); }
    finally { setUploading({ active: false, done: 0, total: 0 }); if (inputRef.current) inputRef.current.value = ""; }
  };
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
    <div className="favorite-hero">
      <div><span><Star weight="fill" /> PRIVATE MEDIA LIBRARY</span><h2>把真正喜欢的画面，留在自己的空间里。</h2><p>原图保存在服务器。当前开放图片，视频媒体能力已经预留。</p></div>
      <div className="favorite-hero-stat"><strong>{items.length}</strong><span>张服务器原图</span><small>{items.reduce((sum, item) => sum + Number(item.fileSize || 0), 0) ? formatSize(items.reduce((sum, item) => sum + Number(item.fileSize || 0), 0)) : "等待第一张图片"}</small></div>
    </div>

    <div className="favorite-toolbar">
      <UiSearchField aria-label="搜索我的最爱" placeholder="按标题、文件名或 Prompt 搜索" value={query} onChange={(event) => setQuery(event.target.value)} />
      <div className="favorite-toolbar-actions">
        {selectionMode ? <>
          <button type="button" onClick={toggleAll}><CheckCircle />{allSelected ? "取消全选" : "全选"}</button>
          <button type="button" className="danger" disabled={!selected.size} onClick={() => setDeleteIds([...selected])}><Trash />移除 {selected.size || ""}</button>
          <button type="button" onClick={() => { setSelectionMode(false); setSelected(new Set()); }}><X />完成</button>
        </> : <button type="button" onClick={() => setSelectionMode(true)}><CheckCircle />选择</button>}
        <button type="button" onClick={load} disabled={state === "loading"}><ArrowClockwise />刷新</button>
        <button type="button" className="primary" onClick={() => inputRef.current?.click()} disabled={uploading.active}><UploadSimple />{uploading.active ? `${uploading.done}/${uploading.total}` : "上传图片"}</button>
        <input ref={inputRef} className="favorite-file-input" type="file" accept="image/png,image/jpeg,image/webp,image/gif" multiple onChange={(event) => uploadFiles(event.target.files)} />
      </div>
    </div>

    <UiToast message={error || notice} tone={error ? "error" : "success"} onDismiss={() => { setError(""); setNotice(""); }} />
    {state === "loading" && !items.length ? <div className="favorite-empty"><Star /><strong>正在打开你的私人画廊…</strong></div>
      : !filtered.length ? <div className="favorite-empty"><ImageSquare /><strong>{query ? "没有找到匹配的图片" : "这里还没有喜欢的画面"}</strong><span>{query ? "换一个关键词试试" : "可以直接上传，或从图像工坊的“我的资产”批量转入"}</span>{!query && <button type="button" className="primary" onClick={() => inputRef.current?.click()}><UploadSimple />上传第一张图片</button>}</div>
        : <div className="favorite-grid">{filtered.map((item) => <article className="favorite-card" key={item.id} data-selected={selected.has(item.id)}>
          <button type="button" className="favorite-image" aria-label={`预览 ${item.title}`} onClick={() => selectionMode ? toggle(item.id) : setPreview(item)} onContextMenu={(event) => { event.preventDefault(); setMenu({ item, x: Math.min(event.clientX, window.innerWidth - 210), y: Math.min(event.clientY, window.innerHeight - 150) }); }}><img src={item.contentUrl} alt="" loading="lazy" /></button>
          <button type="button" className="favorite-select" aria-label={`${selected.has(item.id) ? "取消选择" : "选择"} ${item.title}`} onClick={() => { setSelectionMode(true); toggle(item.id); }}><i>{selected.has(item.id) ? "✓" : ""}</i></button>
          <button type="button" className="favorite-more" aria-label={`${item.title} 更多操作`} onClick={(event) => { event.stopPropagation(); const box = event.currentTarget.getBoundingClientRect(); setMenu({ item, x: Math.min(box.right - 190, window.innerWidth - 210), y: Math.min(box.bottom + 6, window.innerHeight - 150) }); }}><DotsThree weight="bold" /></button>
          <div className="favorite-card-meta"><strong>{item.title}</strong><span>{formatDate(item.createdAt)} · {formatSize(item.fileSize)}</span></div>
        </article>)}</div>}

    {menu && <div className="favorite-context-menu" style={{ left: menu.x, top: menu.y }}>
      <button type="button" onClick={() => openWallpaper(menu.item)}><Desktop />应用为壁纸</button>
      <button type="button" onClick={() => { setMenu(null); setPreview(menu.item); }}><ImageSquare />查看原图</button>
      <button type="button" className="danger" onClick={() => { setMenu(null); setDeleteIds([menu.item.id]); }}><Trash />从我的最爱移除</button>
    </div>}
    {preview && <div className="favorite-preview" role="dialog" aria-modal="true" aria-label={`预览 ${preview.title}`}><div><header><span><strong>{preview.title}</strong><small>{preview.originalFileName} · {formatSize(preview.fileSize)}</small></span><button type="button" aria-label="关闭预览" onClick={() => setPreview(null)}><X /></button></header><img src={preview.contentUrl} alt={preview.title} /><footer><button type="button" onClick={() => openWallpaper(preview)}><Desktop />选择显示器并应用为壁纸</button></footer></div></div>}
    {!!deleteIds.length && <div className="favorite-confirm" role="dialog" aria-modal="true" aria-label="确认移除"><div><Star weight="fill" /><h3>从我的最爱移除？</h3><p>将同时删除服务器上的 {deleteIds.length} 个原图文件，此操作不可恢复。</p><footer><button type="button" onClick={() => setDeleteIds([])}>取消</button><button type="button" className="danger" onClick={remove}>确认移除</button></footer></div></div>}
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

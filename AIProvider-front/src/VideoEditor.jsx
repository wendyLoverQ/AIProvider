import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowClockwise, ArrowCounterClockwise, Copy, DownloadSimple, Eye, EyeSlash, FilmStrip, FloppyDisk,
  FolderOpen, ImageSquare, MagnifyingGlassMinus, MagnifyingGlassPlus, MusicNotes,
  Pause, Play, Plus, Scissors, SpeakerHigh, SpeakerSlash, TextT, Trash, UploadSimple,
} from "@phosphor-icons/react";
import {
  addText, addTrack, createProject, duplicateElement, findElement, formatTimelineTime, insertAsset, mediaKind,
  moveElement, projectDuration, removeElement, removeTrack, resizeElement, snapTime, splitElement, updateElement,
} from "./videoEditorModel";
import { exportProjectBundle, importProjectBundle, loadLatestProject, saveAssetBlob, saveProject } from "./videoProjectStore";
import { useProjectHistory } from "./useProjectHistory";
import "./VideoEditor.css";

const TRACK_WIDTH = 112;
const MIN_TIMELINE = 20;

function readMediaMetadata(file, kind) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    if (kind === "image") {
      const image = new Image();
      image.onload = () => { URL.revokeObjectURL(url); resolve({ duration: 5, width: image.naturalWidth, height: image.naturalHeight }); };
      image.onerror = () => { URL.revokeObjectURL(url); reject(new Error(`图片解析失败：${file.name}`)); };
      image.src = url;
      return;
    }
    const media = document.createElement(kind === "video" ? "video" : "audio");
    media.preload = "metadata";
    media.onloadedmetadata = () => {
      const metadata = { duration: media.duration, width: media.videoWidth || 0, height: media.videoHeight || 0 };
      URL.revokeObjectURL(url);
      if (!Number.isFinite(metadata.duration)) reject(new Error(`无法读取素材时长：${file.name}`)); else resolve(metadata);
    };
    media.onerror = () => { URL.revokeObjectURL(url); reject(new Error(`媒体解析失败：${file.name}`)); };
    media.src = url;
  });
}

function download(blob, name) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url; link.download = name; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function fitRect(sourceWidth, sourceHeight, targetWidth, targetHeight) {
  const scale = Math.min(targetWidth / sourceWidth, targetHeight / sourceHeight);
  return { width: sourceWidth * scale, height: sourceHeight * scale };
}

function drawProjectFrame(canvas, project, mediaMap, time) {
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  const { width, height, background } = project.settings;
  ctx.fillStyle = background; ctx.fillRect(0, 0, width, height);
  for (const track of [...project.tracks].reverse()) {
    if (track.hidden) continue;
    for (const element of track.elements) {
      if (time < element.start || time >= element.start + element.duration || element.type === "audio") continue;
      const edge = Math.min(
        element.transitionIn ? (time - element.start) / element.transitionIn : 1,
        element.transitionOut ? (element.start + element.duration - time) / element.transitionOut : 1,
        1,
      );
      ctx.save();
      ctx.globalAlpha = Math.max(0, edge) * (element.opacity ?? 1);
      ctx.translate(width / 2 + (element.x || 0), height / 2 + (element.y || 0));
      ctx.rotate((element.rotation || 0) * Math.PI / 180);
      ctx.scale(element.scale || 1, element.scale || 1);
      if (element.type === "text") {
        ctx.fillStyle = element.color || "#fff";
        ctx.font = `${element.fontWeight || 700} ${element.fontSize || 64}px ${element.fontFamily || "sans-serif"}`;
        ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillText(element.text || "", 0, 0, width * 0.82);
      } else {
        const asset = project.assets.find((item) => item.id === element.assetId);
        const media = mediaMap.get(element.assetId);
        const naturalWidth = asset?.width || media?.videoWidth || media?.naturalWidth;
        const naturalHeight = asset?.height || media?.videoHeight || media?.naturalHeight;
        if (media && naturalWidth && naturalHeight && (element.type !== "video" || media.readyState >= 2)) {
          const rect = fitRect(naturalWidth, naturalHeight, width, height);
          ctx.drawImage(media, -rect.width / 2, -rect.height / 2, rect.width, rect.height);
        }
      }
      ctx.restore();
    }
  }
}

function waitForMedia(media, eventName, errorMessage) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { cleanup(); reject(new Error(errorMessage)); }, 8000);
    const done = () => { cleanup(); resolve(); };
    const failed = () => { cleanup(); reject(new Error(errorMessage)); };
    const cleanup = () => {
      clearTimeout(timeout); media.removeEventListener(eventName, done); media.removeEventListener("error", failed);
    };
    media.addEventListener(eventName, done, { once: true });
    media.addEventListener("error", failed, { once: true });
  });
}

async function seekVideo(media, time, assetName) {
  if (media.readyState < 2) await waitForMedia(media, "loadeddata", `导出素材未能载入：${assetName}`);
  if (Math.abs(media.currentTime - time) < 0.001) return;
  const ready = waitForMedia(media, "seeked", `导出素材定位超时：${assetName}`);
  media.currentTime = time;
  await ready;
}

async function decodeAssetAudio(asset, mediabunny) {
  const { ALL_FORMATS, AudioBufferSink, BlobSource, Input } = mediabunny;
  const blob = await (await fetch(asset.url)).blob();
  const input = new Input({ source: new BlobSource(blob), formats: ALL_FORMATS });
  const track = await input.getPrimaryAudioTrack();
  if (!track) return null;
  if (!(await track.canDecode())) throw new Error(`浏览器不能解码素材音轨：${asset.name}`);
  const duration = await input.computeDuration();
  const sink = new AudioBufferSink(track);
  const buffer = new AudioBuffer({
    length: Math.max(1, Math.ceil(duration * track.sampleRate)),
    numberOfChannels: Math.max(1, track.numberOfChannels),
    sampleRate: track.sampleRate,
  });
  for await (const chunk of sink.buffers()) {
    const offset = Math.max(0, Math.round(chunk.timestamp * track.sampleRate));
    for (let channel = 0; channel < Math.min(buffer.numberOfChannels, chunk.buffer.numberOfChannels); channel++) {
      buffer.copyToChannel(chunk.buffer.getChannelData(channel), channel, offset);
    }
  }
  return buffer;
}

async function mixProjectAudio(project, totalDuration, mediabunny) {
  const candidates = project.tracks.flatMap((track) => track.muted ? [] : track.elements
    .filter((element) => ["video", "audio"].includes(element.type))
    .map((element) => ({ element, asset: project.assets.find((asset) => asset.id === element.assetId) })));
  if (!candidates.length) return null;
  const cache = new Map();
  for (const { asset } of candidates) {
    if (!asset) throw new Error("时间线引用了缺失素材");
    if (!cache.has(asset.id)) cache.set(asset.id, await decodeAssetAudio(asset, mediabunny));
  }
  if (![...cache.values()].some(Boolean)) return null;
  const sampleRate = 48_000;
  const offline = new OfflineAudioContext(2, Math.max(1, Math.ceil(totalDuration * sampleRate)), sampleRate);
  for (const { element, asset } of candidates) {
    const buffer = cache.get(asset.id);
    if (!buffer) continue;
    const source = offline.createBufferSource();
    const gain = offline.createGain();
    source.buffer = buffer; source.playbackRate.value = element.speed || 1; gain.gain.value = element.volume ?? 1;
    source.connect(gain).connect(offline.destination);
    source.start(element.start, element.trimStart || 0, element.duration * (element.speed || 1));
  }
  return offline.startRendering();
}

export default function VideoEditor() {
  const {
    project, projectRef, replaceProject: setProject, commitProject, checkpoint,
    undo, redo, resetHistory, canUndo, canRedo,
  } = useProjectHistory(createProject());
  const [selectedId, setSelectedId] = useState(null);
  const [playhead, setPlayhead] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [zoom, setZoom] = useState(42);
  const [status, setStatus] = useState("正在载入本地工程…");
  const [exportState, setExportState] = useState({ active: false, progress: 0 });
  const [mediaReady, setMediaReady] = useState(0);
  const canvasRef = useRef(null);
  const fileInputRef = useRef(null);
  const projectInputRef = useRef(null);
  const rafRef = useRef(0);
  const playStartRef = useRef({ time: 0, clock: 0 });
  const mediaRef = useRef(new Map());
  const gestureRef = useRef(null);
  const duration = Math.max(MIN_TIMELINE, projectDuration(project) + 2);
  const selected = selectedId ? findElement(project, selectedId)?.element : null;

  useEffect(() => {
    let live = true;
    loadLatestProject().then((saved) => {
      if (!live) return;
      if (saved) { resetHistory(saved); setStatus(`已恢复工程 · ${new Date(saved.updatedAt).toLocaleString("zh-CN")}`); }
      else setStatus("新工程 · 素材和工程保存在本机浏览器");
    }).catch((error) => setStatus(error.message));
    return () => { live = false; };
  }, [resetHistory]);

  useEffect(() => {
    const map = mediaRef.current;
    const valid = new Set(project.assets.map((asset) => asset.id));
    for (const [id, media] of map) if (!valid.has(id)) { media.pause?.(); map.delete(id); }
    for (const asset of project.assets) {
      if (map.has(asset.id) || !asset.url) continue;
      if (asset.kind === "image") {
        const image = new Image(); image.onload = () => setMediaReady((value) => value + 1); image.src = asset.url; map.set(asset.id, image);
      } else {
        const media = document.createElement(asset.kind);
        media.preload = "auto"; media.playsInline = true; media.src = asset.url;
        media.onloadeddata = () => setMediaReady((value) => value + 1);
        map.set(asset.id, media);
      }
    }
  }, [project.assets]);

  const renderFrame = useCallback((time) => {
    if (!playing) for (const track of project.tracks) for (const element of track.elements) {
      if (element.type !== "video" || time < element.start || time >= element.start + element.duration) continue;
      const asset = project.assets.find((item) => item.id === element.assetId);
      const media = mediaRef.current.get(element.assetId);
      const local = (time - element.start) * (element.speed || 1) + element.trimStart;
      if (media && asset && Math.abs((media.currentTime || 0) - local) > 0.04) media.currentTime = Math.min(local, Math.max(0, asset.duration - 0.02));
    }
    drawProjectFrame(canvasRef.current, project, mediaRef.current, time);
  }, [playing, project]);

  const syncMedia = useCallback((time, shouldPlay) => {
    for (const track of project.tracks) {
      for (const element of track.elements) {
        if (!["video", "audio"].includes(element.type)) continue;
        const media = mediaRef.current.get(element.assetId);
        if (!media) continue;
        const active = !track.muted && time >= element.start && time < element.start + element.duration;
        if (!active) { media.pause(); continue; }
        const local = (time - element.start) * (element.speed || 1) + element.trimStart;
        if (Math.abs(media.currentTime - local) > 0.18) media.currentTime = local;
        media.playbackRate = element.speed || 1;
        media.volume = Math.max(0, Math.min(1, element.volume ?? 1));
        if (shouldPlay) media.play().catch(() => {}); else media.pause();
      }
    }
  }, [project]);

  useEffect(() => { renderFrame(playhead); syncMedia(playhead, false); }, [playhead, mediaReady, renderFrame, syncMedia]);

  // Playback intentionally snapshots the current timeline when Play is pressed;
  // edits take effect on the next playback session instead of restarting the clock.
  // oxlint-disable react-hooks/exhaustive-deps
  useEffect(() => {
    if (!playing) { cancelAnimationFrame(rafRef.current); syncMedia(playhead, false); return; }
    playStartRef.current = { time: playhead, clock: performance.now() };
    syncMedia(playhead, true);
    const tick = (clock) => {
      const next = playStartRef.current.time + (clock - playStartRef.current.clock) / 1000;
      if (next >= projectDuration(project)) { setPlayhead(0); setPlaying(false); return; }
      setPlayhead(next); renderFrame(next); syncMedia(next, true); rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [playing]);
  // oxlint-enable react-hooks/exhaustive-deps

  const importFiles = async (files) => {
    try {
      let next = project;
      for (const file of files) {
        const kind = mediaKind(file);
        const metadata = await readMediaMetadata(file, kind);
        const asset = { id: crypto.randomUUID(), name: file.name, kind, type: file.type, size: file.size, ...metadata, url: URL.createObjectURL(file) };
        await saveAssetBlob(asset, file);
        next = { ...next, assets: [...next.assets, asset] };
        next = insertAsset(next, asset, projectDuration(next));
      }
      commitProject(next); setStatus(`已导入 ${files.length} 个素材`);
    } catch (error) { setStatus(error.message); }
  };

  const persist = useCallback(async () => {
    try { const saved = await saveProject(project); setProject((value) => ({ ...value, updatedAt: saved.updatedAt })); setStatus("工程已完整保存到本机"); }
    catch (error) { setStatus(error.message); }
  }, [project, setProject]);

  const doSplit = () => {
    try { const result = splitElement(project, selectedId, playhead); commitProject(result.project); setSelectedId(result.selectedId); setStatus("片段已在播放头处分割"); }
    catch (error) { setStatus(error.message); }
  };

  const createText = () => {
    const result = addText(project, "新标题", playhead); commitProject(result.project); setSelectedId(result.selectedId);
  };

  const toggleTrack = (trackId, key) => commitProject((value) => ({ ...value, tracks: value.tracks.map((track) => track.id === trackId ? { ...track, [key]: !track[key] } : track) }));

  const doDuplicate = () => {
    try { const result = duplicateElement(project, selectedId); commitProject(result.project); setSelectedId(result.selectedId); setStatus("片段已复制到原片段之后"); }
    catch (error) { setStatus(error.message); }
  };

  const doRemoveTrack = (trackId) => {
    try { commitProject(removeTrack(project, trackId)); setSelectedId(null); setStatus("轨道已删除"); }
    catch (error) { setStatus(error.message); }
  };

  const exportVideo = async () => {
    const total = projectDuration(project);
    if (!total) return setStatus("时间线为空，不能导出");
    const canvas = canvasRef.current;
    if (!canvas || typeof VideoEncoder === "undefined") return setStatus("当前浏览器不支持 WebCodecs，无法执行帧级导出");
    try {
      setPlaying(false); setPlayhead(0); setExportState({ active: true, progress: 0 });
      const mediabunny = await import("mediabunny");
      const { AudioBufferSource, BufferTarget, CanvasSource, Output, QUALITY_HIGH, WebMOutputFormat } = mediabunny;
      const exportMedia = new Map();
      for (const asset of project.assets.filter((item) => ["video", "image"].includes(item.kind))) {
        if (asset.kind === "image") {
          const image = new Image(); const ready = waitForMedia(image, "load", `导出图片未能载入：${asset.name}`);
          image.src = asset.url; await ready; exportMedia.set(asset.id, image);
        } else {
          const video = document.createElement("video"); video.preload = "auto"; video.playsInline = true;
          const ready = waitForMedia(video, "loadeddata", `导出视频未能载入：${asset.name}`);
          video.src = asset.url; await ready; exportMedia.set(asset.id, video);
        }
      }
      setStatus("正在离线解码并混合音轨…");
      const audioBuffer = await mixProjectAudio(project, total, mediabunny);
      const target = new BufferTarget();
      const output = new Output({ format: new WebMOutputFormat(), target });
      const videoSource = new CanvasSource(canvas, { codec: "vp9", bitrate: QUALITY_HIGH });
      output.addVideoTrack(videoSource, { frameRate: project.settings.fps });
      let audioSource = null;
      if (audioBuffer) {
        audioSource = new AudioBufferSource({ codec: "opus", bitrate: 192_000 });
        output.addAudioTrack(audioSource);
      }
      await output.start();
      if (audioSource) { await audioSource.add(audioBuffer); audioSource.close(); }
      const frameCount = Math.max(1, Math.ceil(total * project.settings.fps));
      for (let frame = 0; frame < frameCount; frame++) {
        const time = frame / project.settings.fps;
        const desired = new Map();
        for (const track of project.tracks) for (const element of track.elements) {
          if (track.hidden || element.type !== "video" || time < element.start || time >= element.start + element.duration) continue;
          const local = (time - element.start) * (element.speed || 1) + element.trimStart;
          if (desired.has(element.assetId) && Math.abs(desired.get(element.assetId) - local) > 0.001) throw new Error(`同一视频素材不能在同一时刻以不同入点叠加：${element.name}`);
          desired.set(element.assetId, local);
        }
        for (const [assetId, local] of desired) {
          const asset = project.assets.find((item) => item.id === assetId);
          await seekVideo(exportMedia.get(assetId), Math.min(local, Math.max(0, asset.duration - 0.001)), asset.name);
        }
        drawProjectFrame(canvas, project, exportMedia, time);
        await videoSource.add(time, 1 / project.settings.fps);
        if (frame % 3 === 0) { setPlayhead(time); setExportState({ active: true, progress: (frame + 1) / frameCount }); }
      }
      videoSource.close(); await output.finalize();
      if (!target.buffer?.byteLength) throw new Error("编码器没有产生视频数据");
      const result = new Blob([target.buffer], { type: "video/webm" });
      download(result, `${project.name || "video"}.webm`);
      setStatus(`WebM 视频已导出 · ${(result.size / 1024 / 1024).toFixed(2)} MB · ${frameCount} 帧 · ${audioBuffer ? "含混合音轨" : "无音轨"}`);
    } catch (error) { setStatus(`导出失败：${error.message}`); }
    finally { setExportState({ active: false, progress: 0 }); setPlayhead(0); }
  };

  const onTimelinePointerMove = (event) => {
    const gesture = gestureRef.current;
    if (!gesture) return;
    const delta = (event.clientX - gesture.clientX) / zoom;
    try {
      if (gesture.kind === "resize") {
        setProject(resizeElement(gesture.project, gesture.elementId, gesture.side, delta));
      } else {
        const row = document.elementFromPoint(event.clientX, event.clientY)?.closest(".ve-track-row");
        const trackId = row?.dataset.trackId || gesture.trackId;
        const start = snapTime(gesture.project, gesture.start + delta, gesture.elementId);
        setProject(moveElement(gesture.project, gesture.elementId, trackId, start));
      }
    } catch (error) { setStatus(error.message); }
  };

  useEffect(() => {
    const up = () => {
      if (gestureRef.current) checkpoint(gestureRef.current.project);
      gestureRef.current = null;
    };
    window.addEventListener("pointerup", up); return () => window.removeEventListener("pointerup", up);
  }, [checkpoint]);

  const exportProject = async () => {
    try { setStatus("正在打包工程与原始素材…"); download(await exportProjectBundle(project), `${project.name}.aivideo`); setStatus("可移植工程包已导出"); }
    catch (error) { setStatus(`工程打包失败：${error.message}`); }
  };

  const importProject = async (file) => {
    if (!file) return;
    try {
      setPlaying(false); setStatus("正在校验并导入工程包…");
      const imported = await importProjectBundle(file);
      resetHistory(imported); setSelectedId(null); setPlayhead(0); setStatus(`工程已导入 · ${imported.assets.length} 个素材`);
    } catch (error) { setStatus(`工程导入失败：${error.message}`); }
  };

  useEffect(() => {
    const onKeyDown = (event) => {
      const tag = event.target?.tagName?.toLowerCase();
      if (["input", "textarea", "select"].includes(tag) || event.target?.isContentEditable) return;
      const command = event.ctrlKey || event.metaKey;
      if (command && event.key.toLowerCase() === "z") { event.preventDefault(); if (event.shiftKey) redo(); else undo(); return; }
      if (command && event.key.toLowerCase() === "y") { event.preventDefault(); redo(); return; }
      if (command && event.key.toLowerCase() === "s") { event.preventDefault(); persist(); return; }
      if (command && event.key.toLowerCase() === "d" && selectedId) {
        event.preventDefault();
        try { const result = duplicateElement(projectRef.current, selectedId); commitProject(result.project); setSelectedId(result.selectedId); } catch (error) { setStatus(error.message); }
        return;
      }
      if ((event.key === "Delete" || event.key === "Backspace") && selectedId) { event.preventDefault(); commitProject(removeElement(projectRef.current, selectedId)); setSelectedId(null); return; }
      if (event.key === " ") { event.preventDefault(); setPlaying((value) => !value); }
    };
    window.addEventListener("keydown", onKeyDown); return () => window.removeEventListener("keydown", onKeyDown);
  }, [commitProject, persist, projectRef, redo, selectedId, undo]);

  return (
    <section className="video-editor-shell" onPointerMove={onTimelinePointerMove}>
      <input ref={fileInputRef} hidden type="file" accept="video/*,audio/*,image/*" multiple onChange={(e) => importFiles([...e.target.files])} />
      <input ref={projectInputRef} hidden type="file" accept=".aivideo,application/x-aiprovider-video-project" onChange={(e) => importProject(e.target.files?.[0])} />
      <header className="ve-commandbar">
        <div className="ve-project-name"><FilmStrip weight="duotone" /><input value={project.name} onChange={(e) => setProject({ ...project, name: e.target.value })} /></div>
        <div className="ve-actions">
          <button onClick={() => fileInputRef.current.click()}><UploadSimple /> 导入媒体</button>
          <button onClick={createText}><TextT /> 添加文字</button>
          <button onClick={persist}><FloppyDisk /> 保存工程</button>
          <button onClick={() => projectInputRef.current.click()}><FolderOpen /> 导入工程</button>
          <button onClick={exportProject}><DownloadSimple /> 打包工程</button>
          <button className="primary" disabled={exportState.active} onClick={exportVideo}><DownloadSimple /> {exportState.active ? `导出 ${Math.round(exportState.progress * 100)}%` : "导出 WebM"}</button>
        </div>
      </header>

      <div className="ve-main-grid">
        <aside className="ve-media-panel">
          <div className="ve-panel-title"><span>媒体库</span><small>{project.assets.length} 项</small></div>
          <button className="ve-import-card" onClick={() => fileInputRef.current.click()}><UploadSimple size={24} /><strong>导入素材</strong><small>视频 / 音频 / 图片</small></button>
          <div className="ve-assets">
            {project.assets.map((asset) => <button key={asset.id} className="ve-asset" onDoubleClick={() => commitProject(insertAsset(project, asset, playhead))} title="双击插入播放头位置">
              <span>{asset.kind === "audio" ? <MusicNotes /> : asset.kind === "image" ? <ImageSquare /> : <FilmStrip />}</span>
              <div><strong>{asset.name}</strong><small>{asset.kind.toUpperCase()} · {formatTimelineTime(asset.duration)}</small></div>
            </button>)}
            {!project.assets.length && <p className="ve-empty">导入后素材会立即进入时间线；双击媒体库条目可重复插入。</p>}
          </div>
        </aside>

        <div className="ve-stage-column">
          <div className="ve-preview-wrap"><canvas ref={canvasRef} width={project.settings.width} height={project.settings.height} /></div>
          <div className="ve-transport">
            <span>{formatTimelineTime(playhead)}</span>
            <button onClick={() => setPlaying(!playing)}>{playing ? <Pause weight="fill" /> : <Play weight="fill" />}</button>
            <span>{formatTimelineTime(projectDuration(project))}</span>
          </div>
        </div>

        <Inspector element={selected} project={project} onChange={(patch) => commitProject(updateElement(project, selectedId, patch))} onSettings={(settings) => commitProject({ ...project, settings: { ...project.settings, ...settings } })} />
      </div>

      <div className="ve-timeline">
        <div className="ve-timeline-tools">
          <button disabled={!canUndo} title="撤销 Ctrl+Z" onClick={undo}><ArrowCounterClockwise /> 撤销</button>
          <button disabled={!canRedo} title="重做 Ctrl+Y" onClick={redo}><ArrowClockwise /> 重做</button>
          <button disabled={!selected} onClick={doSplit}><Scissors /> 分割</button>
          <button disabled={!selected} title="复制 Ctrl+D" onClick={doDuplicate}><Copy /> 复制</button>
          <button disabled={!selected} onClick={() => { commitProject(removeElement(project, selectedId)); setSelectedId(null); }}><Trash /> 删除</button>
          <button onClick={() => commitProject(addTrack(project, "overlay"))}><Plus /> 叠加轨</button>
          <button onClick={() => commitProject(addTrack(project, "audio"))}><Plus /> 音频轨</button>
          <div className="ve-zoom"><MagnifyingGlassMinus /><input aria-label="时间线缩放" type="range" min="20" max="100" value={zoom} onChange={(e) => setZoom(Number(e.target.value))} /><MagnifyingGlassPlus /></div>
          <span className="ve-status">{status}</span>
        </div>
        <div className="ve-timeline-scroll">
          <div className="ve-ruler-label" style={{ width: TRACK_WIDTH }}>轨道</div>
          <div className="ve-ruler" style={{ width: duration * zoom }} onPointerDown={(e) => { const rect = e.currentTarget.getBoundingClientRect(); setPlayhead(Math.max(0, (e.clientX - rect.left) / zoom)); }}>
            {Array.from({ length: Math.ceil(duration) + 1 }, (_, i) => <i key={i} style={{ left: i * zoom }}><span>{i % 5 === 0 ? formatTimelineTime(i).slice(0, 5) : ""}</span></i>)}
          </div>
          {project.tracks.map((track) => <TimelineTrack key={track.id} track={track} zoom={zoom} duration={duration} selectedId={selectedId} onSelect={setSelectedId} onToggle={toggleTrack} onRemoveTrack={doRemoveTrack} onDragStart={(event, element) => { gestureRef.current = { kind: "move", project: structuredClone(project), clientX: event.clientX, start: element.start, elementId: element.id, trackId: track.id }; }} onResizeStart={(event, element, side) => { gestureRef.current = { kind: "resize", project: structuredClone(project), clientX: event.clientX, elementId: element.id, side }; }} />)}
          <div className="ve-playhead" style={{ left: TRACK_WIDTH + playhead * zoom }}><i /></div>
        </div>
      </div>
    </section>
  );
}

function TimelineTrack({ track, zoom, duration, selectedId, onSelect, onToggle, onRemoveTrack, onDragStart, onResizeStart }) {
  return <div className="ve-track-row" data-track-id={track.id}>
    <div className="ve-track-head" style={{ width: TRACK_WIDTH }}><span>{track.type === "audio" ? <MusicNotes /> : track.type === "main" ? <FilmStrip /> : <ImageSquare />}{track.name}</span><div>
      {track.type !== "audio" && <button title={track.hidden ? "显示轨道" : "隐藏轨道"} onClick={() => onToggle(track.id, "hidden")}>{track.hidden ? <EyeSlash /> : <Eye />}</button>}
      <button title={track.muted ? "取消静音轨道" : "静音轨道"} onClick={() => onToggle(track.id, "muted")}>{track.muted ? <SpeakerSlash /> : <SpeakerHigh />}</button>
      {track.type !== "main" && <button title="删除轨道" onClick={() => onRemoveTrack(track.id)}><Trash /></button>}
    </div></div>
    <div className="ve-track-lane" style={{ width: duration * zoom }}>
      {track.elements.map((element) => <button key={element.id} className={`ve-clip clip-${element.type} ${selectedId === element.id ? "selected" : ""}`} style={{ left: element.start * zoom, width: Math.max(18, element.duration * zoom) }} onPointerDown={(event) => { event.currentTarget.setPointerCapture?.(event.pointerId); onSelect(element.id); onDragStart(event, element); }}>
        <i className="ve-trim-handle left" title="拖动裁剪片段入点" onPointerDown={(event) => { event.stopPropagation(); event.currentTarget.setPointerCapture?.(event.pointerId); onSelect(element.id); onResizeStart(event, element, "left"); }} />
        <span>{element.type === "text" ? <TextT /> : element.type === "audio" ? <MusicNotes /> : <FilmStrip />}{element.name}</span><small>{formatTimelineTime(element.duration)}</small>
        <i className="ve-trim-handle right" title="拖动裁剪片段出点" onPointerDown={(event) => { event.stopPropagation(); event.currentTarget.setPointerCapture?.(event.pointerId); onSelect(element.id); onResizeStart(event, element, "right"); }} />
      </button>)}
    </div>
  </div>;
}

function Inspector({ element, project, onChange, onSettings }) {
  if (!element) return <aside className="ve-inspector"><div className="ve-panel-title"><span>画布</span></div><Field label="宽度" value={project.settings.width} onChange={(width) => onSettings({ width })} /><Field label="高度" value={project.settings.height} onChange={(height) => onSettings({ height })} /><Field label="帧率" value={project.settings.fps} min={24} max={60} onChange={(fps) => onSettings({ fps })} /><label className="ve-field"><span>背景</span><input type="color" value={project.settings.background} onChange={(e) => onSettings({ background: e.target.value })} /></label><p className="ve-empty">选择时间线片段后，可调整裁剪、速度、音量和画面变换。</p></aside>;
  return <aside className="ve-inspector"><div className="ve-panel-title"><span>片段属性</span><small>{element.type}</small></div>
    {element.type === "text" && <><label className="ve-field"><span>文字</span><textarea value={element.text} onChange={(e) => onChange({ text: e.target.value })} /></label><Field label="字号" value={element.fontSize} min={12} max={240} onChange={(fontSize) => onChange({ fontSize })} /><label className="ve-field"><span>颜色</span><input type="color" value={element.color} onChange={(e) => onChange({ color: e.target.value })} /></label></>}
    <Field label="开始时间" value={element.start} step={0.1} onChange={(start) => onChange({ start })} />
    <Field label="片段时长" value={element.duration} min={0.05} step={0.1} onChange={(duration) => onChange({ duration })} />
    {["video", "audio"].includes(element.type) && <><Field label="素材入点" value={element.trimStart} min={0} step={0.1} onChange={(trimStart) => onChange({ trimStart })} /><Field label="播放速度" value={element.speed} min={0.25} max={4} step={0.05} onChange={(speed) => onChange({ speed })} /><Field label="音量" value={element.volume} min={0} max={1} step={0.05} onChange={(volume) => onChange({ volume })} /></>}
    {element.type !== "audio" && <><Field label="横向位置" value={element.x} step={1} onChange={(x) => onChange({ x })} /><Field label="纵向位置" value={element.y} step={1} onChange={(y) => onChange({ y })} /><Field label="缩放" value={element.scale} min={0.05} max={5} step={0.05} onChange={(scale) => onChange({ scale })} /><Field label="旋转" value={element.rotation} step={1} onChange={(rotation) => onChange({ rotation })} /><Field label="透明度" value={element.opacity} min={0} max={1} step={0.05} onChange={(opacity) => onChange({ opacity })} /><Field label="淡入" value={element.transitionIn} min={0} max={5} step={0.1} onChange={(transitionIn) => onChange({ transitionIn })} /><Field label="淡出" value={element.transitionOut} min={0} max={5} step={0.1} onChange={(transitionOut) => onChange({ transitionOut })} /></>}
  </aside>;
}

function Field({ label, value, onChange, ...props }) {
  return <label className="ve-field"><span>{label}</span><input type="number" value={Number(value ?? 0).toFixed(props.step && props.step < 1 ? 2 : 0)} onChange={(e) => onChange(Number(e.target.value))} {...props} /></label>;
}

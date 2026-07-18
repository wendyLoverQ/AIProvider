import { v4 as uuid } from "uuid";

export const DEFAULT_PROJECT = Object.freeze({
  version: 1,
  name: "未命名视频",
  settings: { width: 1280, height: 720, fps: 30, background: "#070b16" },
  tracks: [
    { id: "overlay-1", name: "叠加轨", type: "overlay", hidden: false, muted: false, elements: [] },
    { id: "main-1", name: "主视频", type: "main", hidden: false, muted: false, elements: [] },
    { id: "audio-1", name: "音频轨", type: "audio", hidden: false, muted: false, elements: [] },
  ],
  assets: [],
});

export function createProject(name = "未命名视频") {
  return { ...structuredClone(DEFAULT_PROJECT), id: uuid(), name, createdAt: Date.now(), updatedAt: Date.now() };
}

export function projectDuration(project) {
  return Math.max(0, ...project.tracks.flatMap((track) => track.elements.map((item) => item.start + item.duration)));
}

export function mediaKind(file) {
  if (file.type.startsWith("video/")) return "video";
  if (file.type.startsWith("audio/")) return "audio";
  if (file.type.startsWith("image/")) return "image";
  throw new Error(`不支持的素材格式：${file.type || file.name}`);
}

export function targetTrack(project, kind) {
  const type = kind === "audio" ? "audio" : kind === "video" ? "main" : "overlay";
  const track = project.tracks.find((item) => item.type === type);
  if (!track) throw new Error(`缺少 ${type} 轨道`);
  return track;
}

export function createElement(asset, start = 0) {
  const duration = asset.kind === "image" ? 5 : asset.duration;
  if (!Number.isFinite(duration) || duration <= 0) throw new Error(`无法读取素材时长：${asset.name}`);
  return {
    id: uuid(), assetId: asset.id, name: asset.name, type: asset.kind,
    start: Math.max(0, start), duration, trimStart: 0, trimEnd: 0,
    speed: 1, volume: 1, opacity: 1, x: 0, y: 0, scale: 1, rotation: 0,
    transitionIn: 0, transitionOut: 0,
  };
}

export function insertAsset(project, asset, start) {
  const clone = structuredClone(project);
  if (!clone.assets.some((item) => item.id === asset.id)) clone.assets.push(structuredClone(asset));
  const track = targetTrack(clone, asset.kind);
  track.elements.push(createElement(asset, start ?? projectDuration(clone)));
  track.elements.sort((a, b) => a.start - b.start);
  clone.updatedAt = Date.now();
  return clone;
}

export function findElement(project, elementId) {
  for (const track of project.tracks) {
    const index = track.elements.findIndex((item) => item.id === elementId);
    if (index >= 0) return { track, element: track.elements[index], index };
  }
  return null;
}

export function updateElement(project, elementId, patch) {
  const clone = structuredClone(project);
  const found = findElement(clone, elementId);
  if (!found) return project;
  Object.assign(found.element, patch);
  found.element.start = Math.max(0, Number(found.element.start) || 0);
  found.element.duration = Math.max(0.05, Number(found.element.duration) || 0.05);
  clone.updatedAt = Date.now();
  return clone;
}

export function splitElement(project, elementId, splitTime) {
  const clone = structuredClone(project);
  const found = findElement(clone, elementId);
  if (!found) throw new Error("请先选择一个片段");
  const { element, track, index } = found;
  const relative = splitTime - element.start;
  if (relative <= 0.03 || relative >= element.duration - 0.03) throw new Error("播放头必须位于片段内部");
  const sourceSpan = relative * (element.speed || 1);
  const right = {
    ...element,
    id: uuid(),
    name: `${element.name}（右）`,
    start: splitTime,
    duration: element.duration - relative,
    trimStart: element.trimStart + sourceSpan,
  };
  const left = {
    ...element,
    name: `${element.name}（左）`,
    duration: relative,
    trimEnd: element.trimEnd + right.duration * (element.speed || 1),
  };
  track.elements.splice(index, 1, left, right);
  clone.updatedAt = Date.now();
  return { project: clone, selectedId: right.id };
}

export function removeElement(project, elementId) {
  const clone = structuredClone(project);
  for (const track of clone.tracks) track.elements = track.elements.filter((item) => item.id !== elementId);
  clone.updatedAt = Date.now();
  return clone;
}

export function duplicateElement(project, elementId) {
  const clone = structuredClone(project);
  const found = findElement(clone, elementId);
  if (!found) throw new Error("请先选择一个片段");
  const copy = {
    ...found.element,
    id: uuid(),
    name: `${found.element.name}（副本）`,
    start: found.element.start + found.element.duration,
  };
  found.track.elements.push(copy);
  found.track.elements.sort((a, b) => a.start - b.start);
  clone.updatedAt = Date.now();
  return { project: clone, selectedId: copy.id };
}

export function resizeElement(project, elementId, side, deltaTime) {
  const clone = structuredClone(project);
  const found = findElement(clone, elementId);
  if (!found) throw new Error("片段不存在");
  const element = found.element;
  const speed = element.speed || 1;
  const minimum = 0.05;
  if (side === "left") {
    const maximumDelta = element.duration - minimum;
    const sourceMinimum = ["video", "audio"].includes(element.type) ? -(element.trimStart || 0) / speed : -Infinity;
    const minimumDelta = Math.max(-element.start, sourceMinimum);
    const delta = Math.max(minimumDelta, Math.min(maximumDelta, deltaTime));
    element.start += delta;
    element.duration -= delta;
    if (["video", "audio"].includes(element.type)) element.trimStart += delta * speed;
  } else if (side === "right") {
    let duration = Math.max(minimum, element.duration + deltaTime);
    if (["video", "audio"].includes(element.type)) {
      const asset = clone.assets.find((item) => item.id === element.assetId);
      if (!asset) throw new Error(`缺少片段素材：${element.name}`);
      const maximum = Math.max(minimum, (asset.duration - element.trimStart) / speed);
      duration = Math.min(duration, maximum);
      element.trimEnd = Math.max(0, asset.duration - element.trimStart - duration * speed);
    }
    element.duration = duration;
  } else {
    throw new Error("裁剪方向无效");
  }
  clone.updatedAt = Date.now();
  return clone;
}

export function addTrack(project, type) {
  if (!["overlay", "audio"].includes(type)) throw new Error("只能新增叠加轨或音频轨");
  const clone = structuredClone(project);
  const count = clone.tracks.filter((track) => track.type === type).length + 1;
  const track = {
    id: `${type}-${uuid()}`,
    name: type === "audio" ? `音频轨 ${count}` : `叠加轨 ${count}`,
    type,
    hidden: false,
    muted: false,
    elements: [],
  };
  const mainIndex = clone.tracks.findIndex((item) => item.type === "main");
  if (type === "overlay") clone.tracks.splice(mainIndex, 0, track); else clone.tracks.push(track);
  clone.updatedAt = Date.now();
  return clone;
}

export function removeTrack(project, trackId) {
  const track = project.tracks.find((item) => item.id === trackId);
  if (!track) return project;
  if (track.type === "main") throw new Error("主视频轨不能删除");
  if (project.tracks.filter((item) => item.type === track.type).length <= 1) throw new Error(`至少保留一条${track.type === "audio" ? "音频" : "叠加"}轨`);
  const clone = structuredClone(project);
  clone.tracks = clone.tracks.filter((item) => item.id !== trackId);
  clone.updatedAt = Date.now();
  return clone;
}

export function moveElement(project, elementId, trackId, start) {
  const clone = structuredClone(project);
  const found = findElement(clone, elementId);
  const destination = clone.tracks.find((track) => track.id === trackId);
  if (!found || !destination) return project;
  const visual = ["video", "image", "text"].includes(found.element.type);
  if ((destination.type === "audio") === visual) throw new Error("音频与画面片段不能放在同类轨道");
  found.track.elements.splice(found.index, 1);
  found.element.start = Math.max(0, start);
  destination.elements.push(found.element);
  destination.elements.sort((a, b) => a.start - b.start);
  clone.updatedAt = Date.now();
  return clone;
}

export function createTextElement(text, start) {
  return {
    id: uuid(), name: text || "文字", type: "text", start: Math.max(0, start), duration: 5,
    trimStart: 0, trimEnd: 0, text: text || "双击编辑文字", fontSize: 64, color: "#ffffff",
    fontFamily: "Noto Sans SC", fontWeight: 700, opacity: 1, x: 0, y: 0, scale: 1,
    rotation: 0, transitionIn: 0.2, transitionOut: 0.2,
  };
}

export function addText(project, text, start) {
  const clone = structuredClone(project);
  const track = clone.tracks.find((item) => item.type === "overlay");
  track.elements.push(createTextElement(text, start));
  clone.updatedAt = Date.now();
  return { project: clone, selectedId: track.elements.at(-1).id };
}

export function snapTime(project, time, excludeId, threshold = 0.12) {
  const points = [0, ...project.tracks.flatMap((track) => track.elements
    .filter((item) => item.id !== excludeId)
    .flatMap((item) => [item.start, item.start + item.duration]))];
  let result = Math.max(0, time);
  for (const point of points) if (Math.abs(point - result) <= threshold) result = point;
  return result;
}

export function formatTimelineTime(value) {
  const total = Math.max(0, Number(value) || 0);
  const minutes = Math.floor(total / 60);
  const seconds = Math.floor(total % 60);
  const frames = Math.floor((total % 1) * 30);
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}:${String(frames).padStart(2, "0")}`;
}

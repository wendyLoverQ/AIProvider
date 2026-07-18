const DB_NAME = "aiprovider-video-editor";
const DB_VERSION = 1;

function openDatabase() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) return reject(new Error("当前浏览器不支持 IndexedDB，无法保存视频工程"));
    const request = window.indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains("projects")) db.createObjectStore("projects", { keyPath: "id" });
      if (!db.objectStoreNames.contains("assets")) db.createObjectStore("assets", { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("工程数据库打开失败"));
  });
}

async function transaction(storeName, mode, action) {
  const db = await openDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(storeName, mode);
      const store = tx.objectStore(storeName);
      const request = action(store);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("工程数据库操作失败"));
      tx.onabort = () => reject(tx.error || new Error("工程数据库事务失败"));
    });
  } finally {
    db.close();
  }
}

export async function saveAssetBlob(asset, blob) {
  if (!(blob instanceof Blob)) throw new Error("素材文件无效，不能保存工程");
  await transaction("assets", "readwrite", (store) => store.put({ id: asset.id, blob }));
}

export async function loadAssetBlob(id) {
  const result = await transaction("assets", "readonly", (store) => store.get(id));
  if (!result?.blob) throw new Error(`工程素材已丢失：${id}`);
  return result.blob;
}

export async function saveProject(project) {
  const clean = structuredClone(project);
  clean.assets = clean.assets.map(({ url: _url, ...asset }) => asset);
  clean.updatedAt = Date.now();
  await transaction("projects", "readwrite", (store) => store.put(clean));
  return clean;
}

export async function loadLatestProject() {
  const projects = await transaction("projects", "readonly", (store) => store.getAll());
  if (!projects?.length) return null;
  projects.sort((a, b) => b.updatedAt - a.updatedAt);
  const project = projects[0];
  project.assets = await Promise.all(project.assets.map(async (asset) => {
    const blob = await loadAssetBlob(asset.id);
    return { ...asset, url: URL.createObjectURL(blob) };
  }));
  return project;
}

export function exportProjectJson(project) {
  const clean = structuredClone(project);
  clean.assets = clean.assets.map(({ url: _url, ...asset }) => asset);
  return JSON.stringify(clean, null, 2);
}

export function parseProjectJson(source) {
  const project = JSON.parse(source);
  if (project?.version !== 1 || !Array.isArray(project.tracks) || !Array.isArray(project.assets)) {
    throw new Error("不是有效的视频工程文件");
  }
  return project;
}

export async function exportProjectBundle(project) {
  const clean = structuredClone(project);
  clean.assets = clean.assets.map(({ url: _url, ...asset }) => asset);
  const files = { "project.json": strToU8(JSON.stringify(clean)) };
  for (const asset of clean.assets) {
    const blob = await loadAssetBlob(asset.id);
    files[`assets/${asset.id}`] = new Uint8Array(await blob.arrayBuffer());
  }
  return new Blob([zipSync(files, { level: 6 })], { type: "application/x-aiprovider-video-project" });
}

export async function importProjectBundle(file) {
  let archive;
  try { archive = unzipSync(new Uint8Array(await file.arrayBuffer())); }
  catch { throw new Error("工程包损坏或不是有效的 .aivideo 文件"); }
  const manifest = archive["project.json"];
  if (!manifest) throw new Error("工程包缺少 project.json");
  const project = parseProjectJson(strFromU8(manifest));
  const idMap = new Map();
  const assets = [];
  for (const asset of project.assets) {
    const bytes = archive[`assets/${asset.id}`];
    if (!bytes) throw new Error(`工程包缺少素材：${asset.name}`);
    const id = crypto.randomUUID();
    const blob = new Blob([bytes], { type: asset.type || "application/octet-stream" });
    const imported = { ...asset, id, url: URL.createObjectURL(blob) };
    await saveAssetBlob(imported, blob);
    idMap.set(asset.id, id);
    assets.push(imported);
  }
  for (const track of project.tracks) for (const element of track.elements) {
    if (element.assetId) {
      const mapped = idMap.get(element.assetId);
      if (!mapped) throw new Error(`片段素材映射失败：${element.name}`);
      element.assetId = mapped;
    }
  }
  project.id = crypto.randomUUID();
  project.name = `${project.name}（导入）`;
  project.assets = assets;
  project.createdAt = Date.now();
  project.updatedAt = Date.now();
  await saveProject(project);
  return project;
}
import { strFromU8, strToU8, unzipSync, zipSync } from "fflate";

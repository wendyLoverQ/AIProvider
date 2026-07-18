import "fake-indexeddb/auto";
import { beforeAll, describe, expect, it } from "vitest";
import { exportProjectBundle, importProjectBundle, loadAssetBlob, saveAssetBlob } from "./videoProjectStore";

beforeAll(() => {
  globalThis.window = { indexedDB: globalThis.indexedDB };
});

describe("portable video project bundle", () => {
  it("packages original blobs and restores them with remapped ids", async () => {
    const asset = { id: "asset-original", name: "sample.mp4", kind: "video", type: "video/mp4", duration: 2, width: 640, height: 360 };
    const source = new Blob([new Uint8Array([1, 2, 3, 4, 5])], { type: asset.type });
    await saveAssetBlob(asset, source);
    const project = {
      id: "project-original", version: 1, name: "可移植工程", createdAt: 1, updatedAt: 1,
      settings: { width: 1280, height: 720, fps: 30, background: "#000000" },
      assets: [asset],
      tracks: [{ id: "main-1", name: "主视频", type: "main", elements: [{ id: "clip-1", assetId: asset.id, type: "video", name: asset.name, start: 0, duration: 2, trimStart: 0, trimEnd: 0 }] }],
    };

    const bundle = await exportProjectBundle(project);
    expect(bundle.size).toBeGreaterThan(source.size);
    const restored = await importProjectBundle(bundle);
    expect(restored.id).not.toBe(project.id);
    expect(restored.assets[0].id).not.toBe(asset.id);
    expect(restored.tracks[0].elements[0].assetId).toBe(restored.assets[0].id);
    const restoredBlob = await loadAssetBlob(restored.assets[0].id);
    expect([...new Uint8Array(await restoredBlob.arrayBuffer())]).toEqual([1, 2, 3, 4, 5]);
  });
});

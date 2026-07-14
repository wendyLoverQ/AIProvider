import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const settingsPath = fileURLToPath(new URL(
  "../../ComfyUIAgent/bin/Release/net8.0/win-x64/publish/appsettings.json",
  import.meta.url,
));
const bridgeDescribe = existsSync(settingsPath) ? describe : describe.skip;
const bridgeBaseUrl = "http://127.0.0.1:32145";

const imagePaths = (payload) => (payload.items || []).flatMap((item) =>
  (item.images || []).map((image) => image.path).filter(Boolean));

bridgeDescribe("ComfyUIAgent bridge contract", () => {
  const settings = JSON.parse(readFileSync(settingsPath, "utf8"));
  const headers = { "X-Local-Token": settings.LocalToken };

  const bridgeFetch = async (path, options = {}) => {
    const response = await fetch(`${bridgeBaseUrl}${path}`, {
      ...options,
      headers: { ...headers, ...(options.headers || {}) },
      signal: AbortSignal.timeout(15_000),
    });
    return response;
  };

  const bridgeJson = async (path) => {
    const response = await bridgeFetch(path);
    expect(response.ok, `${path} returned HTTP ${response.status}`).toBe(true);
    return response.json();
  };

  it("authenticates and reports the configured ComfyUI runtime", async () => {
    const status = await bridgeJson("/api/comfy/status");

    expect(status).toMatchObject({ success: true, configured: true, running: true });
    expect(status.platform).toMatch(/Windows|Linux|macOS/);
  });

  it("serves 100-image pages without duplicate addresses", async () => {
    const first = await bridgeJson("/api/gallery?page=1&pageSize=100");
    const second = first.pages > 1
      ? await bridgeJson("/api/gallery?page=2&pageSize=100")
      : { page: 2, items: [] };
    const firstPaths = imagePaths(first);
    const secondPaths = imagePaths(second);
    const allPaths = [...firstPaths, ...secondPaths];

    expect(first.success).toBe(true);
    expect(first.page).toBe(1);
    expect(first.pages).toBe(first.total ? Math.ceil(first.total / 100) : 0);
    expect(firstPaths).toHaveLength(Math.min(100, first.total));
    expect(secondPaths.length).toBeLessThanOrEqual(100);
    expect(new Set(allPaths).size).toBe(allPaths.length);
  });

  it("proxies the latest 20 ComfyUI history records", async () => {
    const history = await bridgeJson("/comfy/history?max_items=20");
    const entries = Object.entries(history);

    expect(Array.isArray(history)).toBe(false);
    expect(entries.length).toBeGreaterThan(0);
    expect(entries.length).toBeLessThanOrEqual(20);
    expect(entries.every(([promptId, item]) => promptId && item && typeof item === "object")).toBe(true);
  });

  it("returns image bytes for an address supplied by the gallery", async () => {
    const gallery = await bridgeJson("/api/gallery?page=1&pageSize=1");
    const [path] = imagePaths(gallery);
    expect(path).toBeTruthy();

    const query = new URLSearchParams({ path });
    const response = await bridgeFetch(`/api/gallery/file?${query}`, {
      headers: { Range: "bytes=0-31" },
    });
    const bytes = await response.arrayBuffer();

    expect([200, 206]).toContain(response.status);
    expect(response.headers.get("content-type")).toMatch(/^image\//);
    expect(bytes.byteLength).toBeGreaterThan(0);
  });
}, 30_000);

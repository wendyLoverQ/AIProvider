import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const settingsPath = fileURLToPath(new URL(
  "../../ComfyUIAgent/bin/Release/net8.0/win-x64/publish/appsettings.json",
  import.meta.url,
));
const bridgeDescribe = process.env.RUN_COMFY_BRIDGE_CONTRACT === "1" && existsSync(settingsPath)
  ? describe
  : describe.skip;
const bridgeBaseUrl = "http://127.0.0.1:32145";
const bridgeSettings = existsSync(settingsPath)
  ? JSON.parse(readFileSync(settingsPath, "utf8"))
  : null;

const localImagePaths = () => {
  const root = bridgeSettings?.OutputDirectory;
  if (!root || !existsSync(root)) return [];
  const walk = (directory) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? walk(path) : /\.(png|jpe?g|webp)$/i.test(entry.name) ? [relative(root, path).replace(/\\/g, "/")] : [];
  });
  return walk(root);
};

bridgeDescribe("ComfyUIAgent bridge contract", () => {
  const headers = bridgeSettings
    ? { "X-Local-Token": bridgeSettings.LocalToken }
    : {};

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

  it("does not expose a Bridge-owned gallery pagination queue", async () => {
    const response = await bridgeFetch("/api/gallery?page=1&pageSize=100");
    expect(response.status).toBe(404);
  });

  it("proxies the latest 20 ComfyUI history records", async () => {
    const history = await bridgeJson("/comfy/history?max_items=20");
    const entries = Object.entries(history);

    expect(Array.isArray(history)).toBe(false);
    expect(entries.length).toBeLessThanOrEqual(20);
    expect(entries.every(([promptId, item]) => promptId && item && typeof item === "object")).toBe(true);
  });

  it("returns image bytes for a backend-record-compatible relative path", async () => {
    const [path] = localImagePaths();
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

  it("proxies a generated image whose filename contains non-ASCII characters", async () => {
    const normalizedPath = localImagePaths().find((path) => /[^\x00-\x7F]/.test(path));
    expect(normalizedPath, "local gallery should contain a non-ASCII filename fixture").toBeTruthy();
    const slash = normalizedPath.lastIndexOf("/");

    const query = new URLSearchParams({
      filename: normalizedPath.slice(slash + 1),
      subfolder: slash < 0 ? "" : normalizedPath.slice(0, slash),
      type: "output",
    });
    const response = await bridgeFetch(`/comfy/view?${query}`);
    const bytes = await response.arrayBuffer();

    expect(response.ok, `/comfy/view returned HTTP ${response.status}`).toBe(true);
    expect(response.headers.get("content-type")).toMatch(/^image\//);
    expect(bytes.byteLength).toBeGreaterThan(0);
  });
}, 30_000);

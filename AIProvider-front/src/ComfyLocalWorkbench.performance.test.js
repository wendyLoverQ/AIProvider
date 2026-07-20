import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const sourcePath = fileURLToPath(new URL("./ComfyLocalWorkbench.jsx", import.meta.url));

describe("ComfyLocalWorkbench active-task rendering", () => {
  it("keeps the one-second elapsed clock out of the full workbench component", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain("function TaskElapsed({ task })");
    expect(source).not.toContain("setTaskClock");
    expect(source).toContain("startTransition(() => setTasks");
    expect(source).toContain("sameTaskRuntime(existing, updated) ? existing : updated");
    expect(source).toContain("const GalleryImageWall = memo(");
    expect(source).toContain("const history = useMemo(");
    expect(source).toContain("const galleryImages = useMemo(");
    expect(source).toContain('call("/api/tasks/states"');
    expect(source).not.toMatch(/nextTasks\.forEach\(\(task\) => poll\(/);
    expect(source).not.toContain('call("/comfy/history?max_items=20"');
  });

  it("loads each gallery queue once and keeps database-id item identity", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('if (source.status === "idle") await loadGalleryPage');
    expect(source).toContain("galleryStableKey(mode, item, image)");
    expect(source).toContain('`asset:${item.assetId}`');
    expect(source).toContain('`local:${recordId}`');
  });

  it("uses one request for duplicate checks, Bridge submission and task persistence", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('fetch("/api/comfy-tasks/duplicates"');
    expect(source).toContain('call("/api/generate/batch"');
    expect(source).toContain('call("/api/generate/batch-configs"');
    expect(source).toContain('fetch("/api/comfy-tasks/batch"');
    expect(source).toContain('fetch("/api/favorites/batch"');
    expect(source).not.toContain("for (const input of prepared)");
    expect(source).not.toContain("for (const entry of entries)");
    expect(source).not.toContain("while (submitted < total");
  });

  it("uses database ids for every local-image queue mutation", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('fetch("/api/local-generated-images/trash", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ platform, ids: localIds }) })');
    expect(source).toContain('fetch("/api/local-generated-images/restore", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ platform, ids: localIds }) })');
    expect(source).toContain('fetch("/api/local-generated-images/delete", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ platform, ids: localIds }) })');
    expect(source).not.toMatch(/local-generated-images\/(?:trash|restore|delete)[^\n]+paths/);
    expect(source).toContain('call("/api/gallery/delete", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ paths: localPaths }) }');
  });
});

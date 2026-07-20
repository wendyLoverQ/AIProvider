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
});

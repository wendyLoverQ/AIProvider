import { describe, expect, it } from "vitest";
import { applySchemeToWorkflow, calculateComfyProgress, createComfyProgressPlan, createWorkflowForm, describeComfyProgress, FALLBACK_FORM, findFinalOutput, getWorkflowFieldKeys, getWorkflowRevision, normalizeFolder, refreshWorkflowForm } from "./workbench";

describe("workbench workflow state", () => {
  it("rebuilds state from the selected workflow instead of retaining the previous workflow", () => {
    expect(createWorkflowForm({ id: "futa01", fields: ["width"], defaults: { width: 1080 } }))
      .toEqual({ workflowId: "futa01", width: 1080, randomSeed: true, generateTransparent: false });
    expect(createWorkflowForm(null).workflowId).toBe("");
    expect(createWorkflowForm({ id: "unknown", fields: ["customField"] }))
      .toEqual({ workflowId: "unknown", customField: "", randomSeed: true, generateTransparent: false });
    expect(createWorkflowForm({ id: "flags", fields: [], defaults: { randomSeed: false, generateTransparent: true } }))
      .toEqual({ workflowId: "flags", randomSeed: false, generateTransparent: true });
    expect(createWorkflowForm({ id: "loras", fields: ["loras"], defaults: { loras: ["detail.safetensors"] } }))
      .toEqual({ workflowId: "loras", loras: ["detail.safetensors"], randomSeed: true, generateTransparent: false });
    expect(createWorkflowForm({ id: "invalid-loras", fields: ["loras"], defaults: { loras: "detail.safetensors" } }))
      .toEqual({ workflowId: "invalid-loras", loras: [], randomSeed: true, generateTransparent: false });
    expect(FALLBACK_FORM.workflowId).toBe("futa01");
  });

  it("discovers workflow parameters across agent response versions", () => {
    expect(getWorkflowFieldKeys(null)).toEqual([]);
    expect(getWorkflowFieldKeys({})).toEqual([]);
    expect(getWorkflowFieldKeys({ fields: ["width", "height"] })).toEqual(["width", "height"]);
    expect(getWorkflowFieldKeys({ fields: ["ignored"], binding: { fields: { seed: {}, steps: {} } } })).toEqual(["seed", "steps"]);
    expect(getWorkflowFieldKeys({ defaults: { workflowId: "local-1", width: 1024, cfg: 7, randomSeed: true } }))
      .toEqual(["width", "cfg"]);
  });

  it("only applies Prompt fields that belong to the current workflow", () => {
    const workflow = { id: "futa01", binding: { fields: { positivePrompt: {}, width: {} } } };
    expect(applySchemeToWorkflow({ workflowId: "futa01", positivePrompt: "old", width: 1 }, { positivePrompt: "new", negativePrompt: "negative" }, workflow))
      .toEqual({ workflowId: "futa01", positivePrompt: "new", width: 1 });
    expect(applySchemeToWorkflow({ workflowId: "futa01", width: 1 }, null, workflow)).toEqual({ workflowId: "futa01", width: 1 });
    expect(applySchemeToWorkflow({ workflowId: "futa01" }, { positivePrompt: "new" }, null)).toEqual({ workflowId: "futa01" });
  });

  it("loads the saved final Prompt exactly, including an intentionally empty value", () => {
    const workflow = { id: "futa01", binding: { fields: { positivePrompt: {} } } };
    expect(applySchemeToWorkflow(
      { workflowId: "futa01", positivePrompt: "old" },
      { positivePrompt: "" },
      workflow,
    )).toEqual({ workflowId: "futa01", positivePrompt: "" });
  });

  it("keeps edits when the JSON is unchanged and reloads defaults when modifiedAt changes", () => {
    const workflow = { id: "local-1", modifiedAt: "v1", fields: ["steps"], defaults: { steps: 20 } };
    const edited = { workflowId: "local-1", steps: 33 };
    const revision = getWorkflowRevision(workflow);
    expect(revision).toContain('"modifiedAt":"v1"');
    expect(refreshWorkflowForm(edited, workflow, revision)).toBe(edited);
    expect(refreshWorkflowForm(edited, { ...workflow, modifiedAt: "v2", defaults: { steps: 44 } }, revision))
      .toEqual({ workflowId: "local-1", steps: 44, randomSeed: true, generateTransparent: false });
    expect(getWorkflowRevision({ id: "fallback", fields: [] })).toContain('"fields":[]');
  });

  it("normalizes empty output folders", () => {
    expect(normalizeFolder(" output ")).toBe("output");
    expect(normalizeFolder(" ")).toBe("aimaid");
    expect(normalizeFolder(null)).toBe("aimaid");
  });
});

describe("ComfyUI progress", () => {
  it("calculates total progress against every reachable workflow node", () => {
    const definition = {
      a: { class_type: "LoadImage", inputs: {}, _meta: { title: "加载图片" } },
      b: { class_type: "Encode", inputs: { image: ["a", 0] } },
      c: { class_type: "Sampler", inputs: { latent: ["b", 0] }, _meta: { title: "KSampler" } },
      d: { class_type: "SaveImage", inputs: { images: ["c", 0] } },
      unused: { class_type: "PreviewImage", inputs: {} },
    };
    const plan = createComfyProgressPlan(definition, ["d"]);
    expect(plan.nodeIds).toEqual(["d", "c", "b", "a"]);
    expect(plan.labels.c).toBe("KSampler");
    const detail = describeComfyProgress({ promptId: "p1", nodes: {
      a: { state: "finished", value: 1, max: 1 },
      b: { state: "finished", value: 1, max: 1 },
      c: { state: "running", value: 5, max: 10 },
    } }, "p1", plan);
    expect(detail).toEqual({
      totalPercent: 63,
      completedNodes: 2,
      totalNodes: 4,
      currentNode: { id: "c", name: "KSampler", value: 5, max: 10 },
    });
    expect(calculateComfyProgress({ promptId: "p1", nodes: {
      a: { state: "finished" }, b: { state: "finished" }, c: { state: "running", value: 5, max: 10 },
    } }, "p1", plan)).toBe(63);
    expect(calculateComfyProgress({ promptId: "p1", nodes: { a: { state: "finished" } } }, "p1")).toBe(99);
  });

  it("falls back to all graph nodes when no output node is known", () => {
    expect(createComfyProgressPlan({ a: { inputs: {} }, b: {} }).nodeIds).toEqual(["a", "b"]);
    expect(createComfyProgressPlan(null, "missing")).toEqual({ nodeIds: [], labels: {} });
    expect(createComfyProgressPlan([], [])).toEqual({ nodeIds: [], labels: {} });
    expect(createComfyProgressPlan({
      a: { inputs: { cycle: ["b", 0], literal: "text", short: ["b"] } },
      b: { inputs: { cycle: ["a", 0], missing: ["missing", 0] } },
    }, "a").nodeIds).toEqual(["a", "b"]);
  });

  it("does not invent progress when live data is absent or belongs to another task", () => {
    expect(calculateComfyProgress(null, "p1")).toBeNull();
    expect(calculateComfyProgress({}, "p1")).toBeNull();
    expect(calculateComfyProgress({ promptId: "p2", nodes: {} }, "p1")).toBeNull();
    expect(calculateComfyProgress({ promptId: "p1", nodes: {} }, "p1")).toBeNull();
    expect(calculateComfyProgress({ promptId: "p1" }, "p1")).toBeNull();
  });

  it("ignores invalid step data and clamps valid fractions", () => {
    expect(calculateComfyProgress({ promptId: "p1", nodes: {
      a: { state: "running", value: "bad", max: 10 },
      b: { state: "running", value: 20, max: 10 },
      c: { state: "running", value: -5, max: 10 },
    } }, "p1")).toBe(33);
  });
});

describe("completed image discovery", () => {
  const imageOutput = { images: [{ filename: "done.png" }] };

  it("prefers the output node returned by the bridge", () => {
    expect(findFinalOutput({ outputs: { "7": imageOutput } }, "7")).toBe(imageOutput);
  });

  it("recognizes both current and legacy final node titles", () => {
    for (const title of ["最终输出", "保存最终成图"]) {
      const item = { prompt: [0, 0, { "7": { class_type: "SaveImage", _meta: { title } } }], outputs: { "7": imageOutput } };
      expect(findFinalOutput(item)).toBe(imageOutput);
    }
  });

  it("falls back to any real image output", () => {
    expect(findFinalOutput({ prompt: [0, 0, {}], outputs: { preview: {}, result: imageOutput } })).toBe(imageOutput);
  });

  it("returns null for incomplete or image-free history", () => {
    expect(findFinalOutput(null)).toBeNull();
    expect(findFinalOutput({ outputs: {}, prompt: {} })).toBeNull();
    expect(findFinalOutput({ outputs: {}, prompt: [] }, "missing")).toBeNull();
    expect(findFinalOutput({ outputs: {}, prompt: [0, 0, null] })).toBeNull();
  });
});

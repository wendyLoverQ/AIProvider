export const FALLBACK_FORM = {
  workflowId: "futa01",
  positivePrompt: "",
  negativePrompt: "",
  loras: "",
  seed: 1,
  randomSeed: true,
  controlMode: "none",
  styleStrength: 0.45,
  styleWeightType: "style transfer (SDXL)",
  styleEndAt: 0.65,
  combineEmbeds: "average",
  openPoseStrength: 0.8,
  depthStrength: 0.4,
  width: 1080,
  height: 1920,
  batchSize: 1,
  steps: 30,
  cfg: 5,
  denoise: 1,
  secondPassSteps: 22,
  secondPassDenoise: 0.28,
  faceDetailerSteps: 20,
  faceDetailerDenoise: 0.3,
  sampler: "uni_pc",
  scheduler: "normal",
  checkpoint: "",
  generateTransparent: true,
};

export function getWorkflowFieldKeys(workflow) {
  if (!workflow) return [];
  const bindingFields = workflow.binding?.fields;
  const keys = bindingFields && typeof bindingFields === "object" && Object.keys(bindingFields).length
    ? Object.keys(bindingFields)
    : Array.isArray(workflow.fields)
      ? workflow.fields
      : Object.keys(workflow.defaults || {});
  return [...new Set(keys)].filter(
    (key) => !["workflowId", "randomSeed", "generateTransparent"].includes(key),
  );
}

export function createWorkflowForm(workflow) {
  if (!workflow) return { ...FALLBACK_FORM, workflowId: "" };
  const fieldKeys = getWorkflowFieldKeys(workflow);
  const defaults = workflow.defaults || {};
  const values = Object.fromEntries(fieldKeys.map((key) => [key, defaults[key] ?? FALLBACK_FORM[key] ?? ""]));
  return {
    ...values,
    workflowId: workflow.id,
    randomSeed: defaults.randomSeed ?? true,
    generateTransparent: defaults.generateTransparent ?? false,
  };
}

export function getWorkflowRevision(workflow) {
  return JSON.stringify({ modifiedAt: workflow?.modifiedAt, fields: workflow?.fields, defaults: workflow?.defaults, binding: workflow?.binding });
}

export function refreshWorkflowForm(form, workflow, previousRevision) {
  if (form.workflowId === workflow.id && previousRevision === getWorkflowRevision(workflow)) return form;
  return createWorkflowForm(workflow);
}

export function applySchemeToWorkflow(form, scheme, workflow) {
  if (!scheme || !workflow) return form;
  const workflowFields = new Set(getWorkflowFieldKeys(workflow));
  const allowed = new Set(["positivePrompt", "negativePrompt"].filter((key) => workflowFields.has(key)));
  const parameters = Object.fromEntries(Object.entries(scheme.parameters || {}).filter(([key, value]) =>
    allowed.has(key) && !(value === "" && form[key] !== undefined && form[key] !== ""),
  ));
  return { ...form, ...parameters, workflowId: workflow.id };
}

export function findFinalOutput(item, preferredNodeId) {
  if (!item?.outputs) return null;
  if (preferredNodeId && item.outputs[preferredNodeId]?.images?.length) return item.outputs[preferredNodeId];
  const prompt = Array.isArray(item.prompt) ? item.prompt[2] || {} : {};
  const titled = Object.entries(prompt).find(([, node]) =>
    node?.class_type === "SaveImage" && ["最终输出", "保存最终成图"].includes(node?._meta?.title),
  );
  if (titled && item.outputs[titled[0]]?.images?.length) return item.outputs[titled[0]];
  return Object.values(item.outputs).find((output) => output?.images?.length) || null;
}

export function normalizeFolder(folder) {
  const value = String(folder || "").trim();
  return value || "aimaid";
}

export function createComfyProgressPlan(definition, outputNodeIds = []) {
  const graph = definition && typeof definition === "object" && !Array.isArray(definition) ? definition : {};
  const requestedOutputs = (Array.isArray(outputNodeIds) ? outputNodeIds : [outputNodeIds])
    .map(String)
    .filter((nodeId) => graph[nodeId]);
  const roots = requestedOutputs.length ? requestedOutputs : Object.keys(graph);
  const planned = new Set();
  const visit = (nodeId) => {
    const id = String(nodeId);
    const node = graph[id];
    if (!node || planned.has(id)) return;
    planned.add(id);
    Object.values(node.inputs || {}).forEach((input) => {
      if (Array.isArray(input) && input.length >= 2 && graph[String(input[0])]) visit(input[0]);
    });
  };
  roots.forEach(visit);
  return {
    nodeIds: [...planned],
    labels: Object.fromEntries([...planned].map((nodeId) => [
      nodeId,
      graph[nodeId]?._meta?.title || graph[nodeId]?.class_type || `节点 ${nodeId}`,
    ])),
  };
}

export function describeComfyProgress(payload, promptId, progressPlan) {
  if (!payload || String(payload.promptId || "") !== String(promptId)) return null;
  const nodes = payload.nodes || {};
  const liveNodeIds = Object.keys(nodes);
  if (!liveNodeIds.length) return null;
  const plannedNodeIds = progressPlan?.nodeIds?.length
    ? progressPlan.nodeIds.map(String)
    : liveNodeIds;
  let completed = 0;
  let runningFraction = 0;
  for (const nodeId of plannedNodeIds) {
    const node = nodes[nodeId];
    if (node?.state === "finished") completed += 1;
    else if (node?.state === "running") {
      const value = Number(node.value);
      const max = Number(node.max);
      if (Number.isFinite(value) && Number.isFinite(max) && max > 0) {
        runningFraction += Math.max(0, Math.min(1, value / max));
      }
    }
  }
  const runningEntry = Object.entries(nodes).find(([, node]) => node?.state === "running");
  const currentNode = runningEntry ? (() => {
    const [nodeId, node] = runningEntry;
    const value = Number(node.value);
    const max = Number(node.max);
    const hasSteps = Number.isFinite(value) && Number.isFinite(max) && max > 0;
    return {
      id: nodeId,
      name: progressPlan?.labels?.[nodeId] || `节点 ${nodeId}`,
      value: hasSteps ? value : null,
      max: hasSteps ? max : null,
    };
  })() : null;
  const totalNodes = plannedNodeIds.length;
  return {
    totalPercent: Math.max(0, Math.min(99, Math.round(((completed + runningFraction) / totalNodes) * 100))),
    completedNodes: completed,
    totalNodes,
    currentNode,
  };
}

export function calculateComfyProgress(payload, promptId, progressPlan) {
  return describeComfyProgress(payload, promptId, progressPlan)?.totalPercent ?? null;
}

import { Sparkle, UploadSimple } from "@phosphor-icons/react";
import { useState } from "react";
import "./WorkflowPanel.css";
import MaskPointEditor from "./MaskPointEditor";

const LABELS = {
  positivePrompt: "正向提示词",
  negativePrompt: "反向提示词",
  width: "宽度",
  height: "高度",
  batchSize: "生成数量",
  seed: "Seed",
  steps: "Steps",
  cfg: "CFG",
  sampler: "Sampler",
  scheduler: "Scheduler",
  loras: "LoRA",
  checkpoint: "主模型",
};

const SELECT_OPTIONS = {
  sampler: [["uni_pc", "UniPC"], ["euler_ancestral", "祖先欧拉"], ["euler", "欧拉"], ["dpmpp_2m", "DPM++ 2M"], ["dpmpp_2m_sde", "DPM++ 2M SDE"]],
  scheduler: [["normal", "标准"], ["karras", "Karras"], ["exponential", "指数"], ["sgm_uniform", "SGM 均匀"]],
};

const BASIC_FIELDS = new Set(["sourceImage", "positivePrompt", "negativePrompt", "width", "height", "batchSize", "seed"]);
const SIZE_OPTIONS = [
  ["1920x1080", "横屏 · 1K（1920 × 1080）"], ["3840x2160", "横屏 · 2K（3840 × 2160）"], ["7680x4320", "横屏 · 4K（7680 × 4320）"],
  ["1080x1920", "竖屏 · 1K（1080 × 1920）"], ["2160x3840", "竖屏 · 2K（2160 × 3840）"], ["4320x7680", "竖屏 · 4K（4320 × 7680）"],
];

function displayLabel(fieldKey, fieldSpec) {
  return LABELS[fieldKey] || fieldSpec?.label || fieldKey;
}

function NumberField({ fieldKey, fieldSpec, value, onChange }) {
  const decimal = ["cfg", "denoise", "secondPassDenoise"].includes(fieldKey);
  const max = ["denoise", "secondPassDenoise"].includes(fieldKey) ? 1 : undefined;
  const dimension = ["width", "height"].includes(fieldKey);
  const label = displayLabel(fieldKey, fieldSpec);
  return <label>{label}<input aria-label={label} type="number" min={dimension ? "4" : "0"} max={max} step={dimension ? "4" : decimal ? "0.01" : "1"} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value === "" ? "" : Number(event.target.value))} /></label>;
}

function SelectField({ fieldKey, value, onChange }) {
  const options = SELECT_OPTIONS[fieldKey];
  const known = options.some(([key]) => key === value);
  return <label>{LABELS[fieldKey]}<select aria-label={LABELS[fieldKey]} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value)}>{!known && value && <option value={value}>{value}</option>}{options.map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>;
}

function SizeField({ width, height, onChange }) {
  const [customMode, setCustomMode] = useState(false);
  const current = `${width}x${height}`;
  const preset = !customMode && SIZE_OPTIONS.some(([value]) => value === current) ? current : "custom";
  const choose = (value) => {
    if (value === "custom") { setCustomMode(true); return; }
    setCustomMode(false);
    const [nextWidth, nextHeight] = value.split("x").map(Number);
    onChange("width", nextWidth); onChange("height", nextHeight);
  };
  return <div className="workflow-panel__size">
    <label>最终输出尺寸<select aria-label="最终输出尺寸" value={preset} onChange={(event) => choose(event.target.value)}>
      {SIZE_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      <option value="custom">自定义</option>
    </select></label>
    {preset === "custom" && <div><NumberField fieldKey="width" value={width} onChange={onChange} /><NumberField fieldKey="height" value={height} onChange={onChange} /></div>}
  </div>;
}

function SeedField({ value, random, onChange }) {
  return <div className="workflow-panel__seed">
    <span>种子模式</span>
    <div className="workflow-panel__segments">
      <button type="button" aria-label="随机种子" className={random ? "active" : ""} onClick={() => onChange("randomSeed", true)}>随机</button>
      <button type="button" aria-label="固定种子" className={!random ? "active" : ""} onClick={() => onChange("randomSeed", false)}>固定</button>
    </div>
    {!random && <NumberField fieldKey="seed" value={value} onChange={onChange} />}
  </div>;
}

function WorkflowField({ fieldKey, fieldSpec, value, workflow, referenceFile, onReference, onChange }) {
  const label = displayLabel(fieldKey, fieldSpec);
  if (fieldKey === "sourceImage") return <label className="workflow-panel__file">待处理原图<input aria-label="待处理原图" type="file" accept="image/*" onChange={(event) => onReference("sourceImage", event)} /><span><UploadSimple />{referenceFile?.name || "选择图片"}</span></label>;
  if (["positivePrompt", "negativePrompt", "loras"].includes(fieldKey)) return <label className={fieldKey === "positivePrompt" || fieldKey === "negativePrompt" ? "workflow-panel__prompt-field" : undefined}>{LABELS[fieldKey]}<textarea aria-label={LABELS[fieldKey]} rows={fieldKey === "positivePrompt" ? 9 : fieldKey === "negativePrompt" ? 7 : 3} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value)} /></label>;
  if (fieldKey === "checkpoint" && workflow.models?.length) return <label>{LABELS.checkpoint}<select aria-label={LABELS.checkpoint} value={value || workflow.models[0]} onChange={(event) => onChange(fieldKey, event.target.value)}>{workflow.models.map((model) => <option key={model} value={model}>{model}</option>)}</select></label>;
  if (SELECT_OPTIONS[fieldKey]) return <SelectField fieldKey={fieldKey} value={value} onChange={onChange} />;
  if (typeof value === "number" || ["width", "height", "batchSize", "seed", "steps", "cfg", "denoise", "secondPassSteps", "secondPassDenoise", "secondPassSeed"].includes(fieldKey)) return <NumberField fieldKey={fieldKey} fieldSpec={fieldSpec} value={value} onChange={onChange} />;
  if (typeof value === "boolean") return <label className="workflow-panel__check"><input type="checkbox" checked={value} onChange={(event) => onChange(fieldKey, event.target.checked)} /><span>{label}</span></label>;
  return <label>{label}<input aria-label={label} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value)} /></label>;
}

export default function WorkflowPanel({ workflows, loading, workflow, fieldKeys, fieldSpecs, values, onWorkflowChange, onFieldChange, referenceFiles, onReference, presets, presetQuery, onPresetChange, appliedPresetTitle, onGenerate, disabled }) {
  const supportsPrompt = fieldKeys.includes("positivePrompt") || fieldKeys.includes("negativePrompt");
  const editorKey = fieldKeys.find((fieldKey) => fieldSpecs[fieldKey]?.nodeType === "MaskEditMEC" && fieldSpecs[fieldKey]?.input === "editor_data");
  const editorNodeId = editorKey ? fieldSpecs[editorKey]?.nodeId : null;
  const radiusKey = editorNodeId ? fieldKeys.find((fieldKey) => fieldSpecs[fieldKey]?.nodeId === editorNodeId && fieldSpecs[fieldKey]?.input === "default_radius") : null;
  const basicFieldKeys = fieldKeys.filter((fieldKey) => BASIC_FIELDS.has(fieldKey));
  const advancedFieldKeys = fieldKeys.filter((fieldKey) => !BASIC_FIELDS.has(fieldKey) && fieldKey !== editorKey);
  const hasCombinedSize = fieldKeys.includes("width") && fieldKeys.includes("height");
  const renderField = (fieldKey) => {
    if (hasCombinedSize && fieldKey === "width") return <SizeField key="size" width={values.width} height={values.height} onChange={onFieldChange} />;
    if (hasCombinedSize && fieldKey === "height") return null;
    if (fieldKey === "seed") return <SeedField key="seed" value={values.seed} random={values.randomSeed !== false} onChange={onFieldChange} />;
    return <WorkflowField key={fieldKey} fieldKey={fieldKey} fieldSpec={fieldSpecs[fieldKey]} value={values[fieldKey]} workflow={workflow} referenceFile={referenceFiles[fieldKey]} onReference={onReference} onChange={onFieldChange} />;
  };
  return <div className="workflow-panel">
    <section className="workflow-panel__chooser" aria-label="选择工作流">
      <div className="workflow-panel__chooser-main">
        <label>工作流<select aria-label="当前生成工作流" value={workflow?.id || ""} onChange={(event) => onWorkflowChange(event.target.value)}>
          {!workflows.length && <option value="">{loading ? "正在读取工作流…" : "没有识别到可运行工作流"}</option>}
          {workflows.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>
        <button className="comfy-submit workflow-panel__generate" type="button" onClick={onGenerate} disabled={disabled.blocked}><Sparkle />{disabled.busy ? "正在提交…" : "开始生成"}</button>
      </div>
      <small>工作流决定下方参数；切换后立即载入它的必要参数和默认值。</small>
    </section>

    {workflow ? <section className="workflow-panel__parameters" aria-label="工作流参数" key={workflow.id}>
      <header><div><strong>工作流参数</strong><small>{fieldKeys.length} 项</small></div><span>{workflow.name}</span></header>
      {basicFieldKeys.length ? <div className="workflow-panel__fields workflow-panel__fields--basic">{basicFieldKeys.map(renderField)}</div> : !advancedFieldKeys.length && <p role="alert">该工作流没有返回可编辑参数，请刷新工作流扫描结果。</p>}
      {editorKey && <MaskPointEditor file={referenceFiles.sourceImage} value={values[editorKey]} radius={Number(values[radiusKey] || 12)} onChange={(next) => onFieldChange(editorKey, next)} onRadiusChange={(next) => radiusKey && onFieldChange(radiusKey, next)} />}
      {workflow.capabilities?.generationAndCutout && <label className="workflow-panel__check workflow-panel__transparent"><input type="checkbox" checked={Boolean(values.generateTransparent)} onChange={(event) => onFieldChange("generateTransparent", event.target.checked)} /><span>透明背景</span></label>}
    </section> : <section className="workflow-panel__empty">请先启动 ComfyUI 并读取工作流。</section>}

    {supportsPrompt && <section className="workflow-panel__presets">
      <header><strong>Prompt 方案</strong><small>方案全局可用，只回填正向和反向 Prompt</small></header>
      <select aria-label="Prompt 方案" value={presetQuery} onChange={(event) => onPresetChange(event.target.value)}>
        <option value="">请选择 Prompt 方案</option>
        {presets.map((preset) => <option key={preset.id} value={String(preset.id)}>{preset.title}</option>)}
      </select>
      {!presets.length && <small>还没有 Prompt 方案。</small>}
      {appliedPresetTitle && <div>已应用：{appliedPresetTitle}</div>}
    </section>}

    {workflow && advancedFieldKeys.length > 0 && <details className="workflow-panel__advanced workflow-panel__advanced--bottom"><summary><span>高级选项</span><small>{advancedFieldKeys.length} 项</small></summary><div className="workflow-panel__fields">{advancedFieldKeys.map(renderField)}</div></details>}
  </div>;
}

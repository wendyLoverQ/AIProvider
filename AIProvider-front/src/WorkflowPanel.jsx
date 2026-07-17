import { ArrowClockwise, ArrowDown, ArrowUp, CaretUp, DiceFive, FloppyDisk, PencilSimple, Plus, Sparkle, Stack, UploadSimple, X } from "@phosphor-icons/react";
import { useEffect, useMemo, useRef, useState } from "react";
import "./WorkflowPanel.css";
import MaskPointEditor from "./MaskPointEditor";
import UiSearchField from "./UiSearchField";
import { normalizePrompt, PROMPT_CATEGORIES } from "./promptComposer";
import { getPromptTranslationService } from "./promptTranslationService";

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
  cutoutTarget: "抠图目标",
};

const SELECT_OPTIONS = {
  sampler: [["uni_pc", "UniPC"], ["euler_ancestral", "祖先欧拉"], ["euler", "欧拉"], ["dpmpp_2m", "DPM++ 2M"], ["dpmpp_2m_sde", "DPM++ 2M SDE"]],
  scheduler: [["normal", "标准"], ["karras", "Karras"], ["exponential", "指数"], ["sgm_uniform", "SGM 均匀"]],
};

const BASIC_FIELDS = new Set(["sourceImage", "cutoutTarget", "positivePrompt", "negativePrompt", "loras", "width", "height", "batchSize", "seed"]);
const BASIC_FIELD_ORDER = ["sourceImage", "cutoutTarget", "positivePrompt", "negativePrompt", "loras", "width", "height", "batchSize", "seed"];
const SIZE_OPTIONS = [
  ["1920x1080", "横屏 · 1K（1920 × 1080）"], ["3840x2160", "横屏 · 2K（3840 × 2160）"], ["7680x4320", "横屏 · 4K（7680 × 4320）"],
  ["1080x1920", "竖屏 · 1K（1080 × 1920）"], ["2160x3840", "竖屏 · 2K（2160 × 3840）"], ["4320x7680", "竖屏 · 4K（4320 × 7680）"],
];

function displayLabel(fieldKey, fieldSpec) {
  return LABELS[fieldKey] || fieldSpec?.label || fieldKey;
}

function NumberField({ fieldKey, fieldSpec, value, onChange }) {
  const decimal = ["cfg", "denoise", "secondPassDenoise"].includes(fieldKey);
  const max = ["denoise", "secondPassDenoise"].includes(fieldKey) ? 1 : fieldKey === "batchSize" ? 10000 : undefined;
  const dimension = ["width", "height"].includes(fieldKey);
  const label = displayLabel(fieldKey, fieldSpec);
  return <label className={fieldKey === "batchSize" ? "workflow-panel__inline-field" : undefined}>{label}<input aria-label={label} type="number" min={dimension ? "4" : fieldKey === "batchSize" ? "1" : "0"} max={max} step={dimension ? "4" : decimal ? "0.01" : "1"} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value === "" ? "" : Number(event.target.value))} /></label>;
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
    <label className="workflow-panel__size-select"><select aria-label="最终输出尺寸" value={preset} onChange={(event) => choose(event.target.value)}>
      {SIZE_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      <option value="custom">自定义</option>
    </select></label>
    {preset === "custom" && <div><NumberField fieldKey="width" value={width} onChange={onChange} /><NumberField fieldKey="height" value={height} onChange={onChange} /></div>}
  </div>;
}

function SeedField({ value, random, onChange }) {
  return <div className={`workflow-panel__seed ${random ? "" : "is-fixed"}`}>
    <span>种子</span>
    <div className="workflow-panel__segments">
      <button type="button" aria-label="随机种子" className={random ? "active" : ""} onClick={() => onChange("randomSeed", true)}>随机</button>
      <button type="button" aria-label="固定种子" className={!random ? "active" : ""} onClick={() => onChange("randomSeed", false)}>固定</button>
    </div>
    {!random && <NumberField fieldKey="seed" value={value} onChange={onChange} />}
  </div>;
}

function CutoutTargetField({ value, onChange }) {
  return <fieldset className="workflow-panel__cutout-target">
    <legend>抠图目标</legend>
    <div className="workflow-panel__segments">
      <button type="button" className={value === "person" ? "active" : ""} aria-pressed={value === "person"} onClick={() => onChange("cutoutTarget", "person")}>保留人物</button>
      <button type="button" className={value === "background" ? "active" : ""} aria-pressed={value === "background"} onClick={() => onChange("cutoutTarget", "background")}>保留背景</button>
    </div>
    <small>{value === "background" ? "移除人物，输出透明人物区域" : "移除背景，输出透明背景"}</small>
  </fieldset>;
}

function LoraField({ value, models, loading, onChange }) {
  const selected = Array.isArray(value) ? value : [];
  const selectedNames = new Set(selected.map((item) => item.name));
  const available = models.filter((model) => {
    const name = String(model.name || "").trim();
    const displayName = String(model.displayName || "").trim();
    return name && !/^(none|no[ _-]?one|null|undefined)$/i.test(name) &&
      !/^(none|no[ _-]?one|null|undefined)$/i.test(displayName) && !selectedNames.has(model.name);
  });
  const add = (name) => {
    const model = models.find((item) => item.name === name);
    if (!model) return;
    onChange("loras", [...selected, { name: model.name, displayName: model.displayName, modelStrength: 1, clipStrength: 1, enabled: true }]);
  };
  const update = (index, patch) => onChange("loras", selected.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item));
  const move = (index, offset) => {
    const target = index + offset;
    if (target < 0 || target >= selected.length) return;
    const next = [...selected]; [next[index], next[target]] = [next[target], next[index]]; onChange("loras", next);
  };
  return <section className="workflow-panel__loras">
    <header><div><strong>LoRA 模型</strong><small>按顺序叠加 · 使用原文件名加载</small></div><span>{selected.length} 个</span></header>
    <select className="lora-select" aria-label="选择 LoRA 模型" value="" disabled={loading || !available.length} onChange={(event) => add(event.target.value)}>
      <option value="" disabled hidden>{loading ? "正在读取 LoRA 模型…" : available.length ? "选择 LoRA 模型…" : "没有更多可选 LoRA"}</option>
      {available.map((model) => <option key={model.name} value={model.name}>{model.displayName}</option>)}
    </select>
    {selected.length > 0 && <div className="lora-selected-list">{selected.map((item, index) => <article key={`${item.name}-${index}`} className={item.enabled === false ? "disabled" : ""}>
      <label className="lora-enabled"><input type="checkbox" checked={item.enabled !== false} onChange={(event) => update(index, { enabled: event.target.checked })} /><span title={item.name}>{item.displayName || models.find((model) => model.name === item.name)?.displayName || item.name}</span></label>
      <label>模型强度<input type="number" min="-2" max="2" step="0.05" value={item.modelStrength ?? 1} onChange={(event) => update(index, { modelStrength: Number(event.target.value) })} /></label>
      <label>CLIP 强度<input type="number" min="-2" max="2" step="0.05" value={item.clipStrength ?? 1} onChange={(event) => update(index, { clipStrength: Number(event.target.value) })} /></label>
      <div className="lora-row-actions"><button type="button" onClick={() => move(index, -1)} disabled={index === 0} title="上移"><ArrowUp /></button><button type="button" onClick={() => move(index, 1)} disabled={index === selected.length - 1} title="下移"><ArrowDown /></button><button type="button" onClick={() => onChange("loras", selected.filter((_, itemIndex) => itemIndex !== index))} title="移除"><X /></button></div>
    </article>)}</div>}
  </section>;
}

function WorkflowField({ fieldKey, fieldSpec, value, workflow, referenceFile, onReference, onReferenceDrop, onChange }) {
  const [draggingFile, setDraggingFile] = useState(false);
  const [referenceUrl, setReferenceUrl] = useState("");
  useEffect(() => {
    if (!referenceFile) { setReferenceUrl(""); return undefined; }
    const url = URL.createObjectURL(referenceFile);
    setReferenceUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [referenceFile]);
  const label = displayLabel(fieldKey, fieldSpec);
  if (fieldKey === "sourceImage") return <label
    className={`workflow-panel__file ${draggingFile ? "is-dragging" : ""}`}
    onDragEnter={(event) => { event.preventDefault(); setDraggingFile(true); }}
    onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = "copy"; setDraggingFile(true); }}
    onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDraggingFile(false); }}
    onDrop={(event) => { setDraggingFile(false); onReferenceDrop?.("sourceImage", event); }}
  >待处理原图<input aria-label="待处理原图" type="file" accept="image/*" onChange={(event) => onReference("sourceImage", event)} /><span className={referenceUrl ? "has-image" : ""}>{referenceUrl ? <img src={referenceUrl} alt={referenceFile?.name || "待处理原图"} /> : <UploadSimple />}<em>{referenceFile?.name || "拖入本机图片，或点击选择"}</em></span></label>;
  if (fieldKey === "cutoutTarget" && fieldSpec?.nodeType === "CutoutTarget") return <CutoutTargetField value={value} onChange={onChange} />;
  if (["positivePrompt", "negativePrompt", "loras"].includes(fieldKey)) return <label className={fieldKey === "positivePrompt" || fieldKey === "negativePrompt" ? "workflow-panel__prompt-field" : undefined}>{fieldKey === "loras" && LABELS[fieldKey]}<textarea aria-label={LABELS[fieldKey]} rows={fieldKey === "positivePrompt" ? 9 : fieldKey === "negativePrompt" ? 7 : 3} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value)} /></label>;
  if (fieldKey === "checkpoint" && workflow.models?.length) return <label>{LABELS.checkpoint}<select aria-label={LABELS.checkpoint} value={value || workflow.models[0]} onChange={(event) => onChange(fieldKey, event.target.value)}>{workflow.models.map((model) => <option key={model} value={model}>{model}</option>)}</select></label>;
  if (SELECT_OPTIONS[fieldKey]) return <SelectField fieldKey={fieldKey} value={value} onChange={onChange} />;
  if (typeof value === "number" || ["width", "height", "batchSize", "seed", "steps", "cfg", "denoise", "secondPassSteps", "secondPassDenoise", "secondPassSeed"].includes(fieldKey)) return <NumberField fieldKey={fieldKey} fieldSpec={fieldSpec} value={value} onChange={onChange} />;
  if (typeof value === "boolean") return <label className="workflow-panel__check"><input type="checkbox" checked={value} onChange={(event) => onChange(fieldKey, event.target.checked)} /><span>{label}</span></label>;
  return <label>{label}<input aria-label={label} value={value ?? ""} onChange={(event) => onChange(fieldKey, event.target.value)} /></label>;
}

export default function WorkflowPanel({ workflows, loading, workflow, fieldKeys, fieldSpecs, values, onWorkflowChange, onFieldChange, referenceFiles, onReference, onReferenceDrop, loraModels, loraModelsLoading, promptOptions = [], onPromptOptionsReload, presets, presetQuery, onPresetChange, presetSaveName, onPresetSaveNameChange, onSavePreset, onReloadPreset, onEditPreset, presetSaving, presetReloading, onLuckyGenerate, luckyLoading, onBatchGenerate, onGenerate, disabled }) {
  const [promptEditing, setPromptEditing] = useState({ positivePrompt: false, negativePrompt: false });
  const [promptTermQuery, setPromptTermQuery] = useState("");
  const [negativeTermQuery, setNegativeTermQuery] = useState("");
  const [promptTermCategory, setPromptTermCategory] = useState("");
  const [negativeTermCategory, setNegativeTermCategory] = useState("");
  const [promptTermOpen, setPromptTermOpen] = useState({ positivePrompt: false, negativePrompt: false });
  const [promptLanguage, setPromptLanguage] = useState("en");
  const [quickDrafts, setQuickDrafts] = useState({ positivePrompt: { category: "Clothing", name: "", prompt: "" }, negativePrompt: { category: "Quality", name: "", prompt: "" } });
  const [quickSaving, setQuickSaving] = useState("");
  const [quickError, setQuickError] = useState("");
  const [disabledPromptTerms, setDisabledPromptTerms] = useState({ positivePrompt: [], negativePrompt: [] });
  const promptTermOrderRef = useRef({ positivePrompt: new Map(), negativePrompt: new Map() });
  const [draggedPromptTerm, setDraggedPromptTerm] = useState(null);
  const [chipContextMenu, setChipContextMenu] = useState(null);
  const [chipEditDraft, setChipEditDraft] = useState(null);
  const [chipEditSaving, setChipEditSaving] = useState(false);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [batchDialog, setBatchDialog] = useState({ open: false, category: "", selectedIds: [] });
  const promptTranslationService = useMemo(() => getPromptTranslationService(promptOptions), [promptOptions]);
  const promptTermResults = useMemo(() => {
    const keyword = promptTermQuery.trim().toLocaleLowerCase("zh-CN");
    if (!keyword && !promptTermCategory) return [];
    return promptOptions.filter((option) => option.type !== "negative" && (!promptTermCategory || option.category === promptTermCategory) && (!keyword || [option.name, option.prompt, option.positivePrompt, option.id].filter(Boolean).join(" ").toLocaleLowerCase("zh-CN").includes(keyword))).slice(0, 50);
  }, [promptOptions, promptTermCategory, promptTermQuery]);
  const negativeTermResults = useMemo(() => {
    const keyword = negativeTermQuery.trim().toLocaleLowerCase("zh-CN");
    if (!keyword && !negativeTermCategory) return [];
    return promptOptions.filter((option) => option.type === "negative" && (!negativeTermCategory || option.category === negativeTermCategory) && (!keyword || [option.name, option.prompt, option.negativePrompt, option.id].filter(Boolean).join(" ").toLocaleLowerCase("zh-CN").includes(keyword))).slice(0, 50);
  }, [promptOptions, negativeTermCategory, negativeTermQuery]);
  const batchCategories = useMemo(() => {
    const available = new Set(promptOptions.filter((option) => option?.category && (option.prompt || option.positivePrompt || option.negativePrompt)).map((option) => option.category));
    const ordered = PROMPT_CATEGORIES.filter((item) => available.has(item.category));
    const known = new Set(ordered.map((item) => item.category));
    return [...ordered, ...[...available].filter((category) => !known.has(category)).sort().map((category) => ({ category, label: category }))];
  }, [promptOptions]);
  const batchCategoryOptions = useMemo(() => promptOptions
    .filter((option) => option.category === batchDialog.category && (option.prompt || option.positivePrompt || option.negativePrompt))
    .sort((left, right) => Number(left.sortOrder ?? 0) - Number(right.sortOrder ?? 0) || String(left.name || left.id).localeCompare(String(right.name || right.id), "zh-CN")), [batchDialog.category, promptOptions]);
  const supportsPrompt = fieldKeys.includes("positivePrompt") || fieldKeys.includes("negativePrompt");
  useEffect(() => { setPromptEditing({ positivePrompt: false, negativePrompt: false }); }, [presetQuery, workflow?.id]);
  useEffect(() => { setDisabledPromptTerms({ positivePrompt: [], negativePrompt: [] }); promptTermOrderRef.current = { positivePrompt: new Map(), negativePrompt: new Map() }; }, [presetQuery, workflow?.id, presetReloading]);
  const editorKey = fieldKeys.find((fieldKey) => fieldSpecs[fieldKey]?.nodeType === "MaskEditMEC" && fieldSpecs[fieldKey]?.input === "editor_data");
  const editorNodeId = editorKey ? fieldSpecs[editorKey]?.nodeId : null;
  const radiusKey = editorNodeId ? fieldKeys.find((fieldKey) => fieldSpecs[fieldKey]?.nodeId === editorNodeId && fieldSpecs[fieldKey]?.input === "default_radius") : null;
  const promptFieldKeys = fieldKeys.filter((fieldKey) => fieldKey === "positivePrompt" || fieldKey === "negativePrompt");
  const basicFieldKeys = fieldKeys.filter((fieldKey) => BASIC_FIELDS.has(fieldKey) && !promptFieldKeys.includes(fieldKey)).sort((left, right) => BASIC_FIELD_ORDER.indexOf(left) - BASIC_FIELD_ORDER.indexOf(right));
  const advancedFieldKeys = fieldKeys.filter((fieldKey) => !BASIC_FIELDS.has(fieldKey) && fieldKey !== editorKey);
  const hasCombinedSize = fieldKeys.includes("width") && fieldKeys.includes("height");
  const renderField = (fieldKey) => {
    if (hasCombinedSize && fieldKey === "width") return <SizeField key="size" width={values.width} height={values.height} onChange={onFieldChange} />;
    if (hasCombinedSize && fieldKey === "height") return null;
    if (fieldKey === "seed") return <SeedField key="seed" value={values.seed} random={values.randomSeed !== false} onChange={onFieldChange} />;
    if (fieldKey === "loras") return <LoraField key="loras" value={values.loras} models={loraModels || []} loading={loraModelsLoading} onChange={onFieldChange} />;
    return <WorkflowField key={fieldKey} fieldKey={fieldKey} fieldSpec={fieldSpecs[fieldKey]} value={values[fieldKey]} workflow={workflow} referenceFile={referenceFiles[fieldKey]} onReference={onReference} onReferenceDrop={onReferenceDrop} onChange={onFieldChange} />;
  };
  const renderPromptField = (fieldKey) => {
    const source = String(values[fieldKey] || "");
    const tokens = source.split(",").map((token, index) => ({ token: token.trim().replace(/\s+/g, " "), index })).filter((item) => item.token);
    const orderMap = promptTermOrderRef.current[fieldKey];
    tokens.forEach((item) => { const key = item.token.toLocaleLowerCase("en-US"); if (!orderMap.has(key)) orderMap.set(key, orderMap.size); });
    const targetType = fieldKey === "positivePrompt" ? "positive" : "negative";
    const mapped = tokens.map((item) => {
      const option = promptOptions.find((candidate) => {
        if (targetType === "positive" && candidate.type === "negative") return false;
        if (targetType === "negative" && candidate.type === "positive" && !candidate.negativePrompt) return false;
        const prompt = candidate.prompt || (targetType === "positive" ? candidate.positivePrompt : candidate.negativePrompt);
        return String(prompt || "").split(",").some((term) => term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US") === item.token.toLocaleLowerCase("en-US"));
      });
      return { ...item, option };
    });
    const disabled = disabledPromptTerms[fieldKey] || [];
    const isDisabled = (token) => disabled.some((item) => item.token.toLocaleLowerCase("en-US") === token.toLocaleLowerCase("en-US"));
    const structured = mapped.filter((item) => item.option && !isDisabled(item.token));
    const disabledStructured = disabled.filter((item) => item.option);
    const chipItems = [...structured.map((item) => ({ ...item, disabled: false })), ...disabledStructured.map((item) => ({ ...item, disabled: true }))]
      .sort((left, right) => (orderMap.get(left.token.toLocaleLowerCase("en-US")) ?? Number.MAX_SAFE_INTEGER) - (orderMap.get(right.token.toLocaleLowerCase("en-US")) ?? Number.MAX_SAFE_INTEGER));
    const manual = mapped.filter((item) => !item.option).map((item) => item.token);
    const manualDisplay = promptLanguage === "zh" ? promptTranslationService.toChinese(fieldKey, manual.join(", ")) : manual.join(", ");
    const toggleStructured = (item) => {
      if (isDisabled(item.token)) {
        setDisabledPromptTerms((current) => ({ ...current, [fieldKey]: (current[fieldKey] || []).filter((term) => term.token !== item.token) }));
        onFieldChange(fieldKey, normalizePrompt(source, item.token));
      } else {
        setDisabledPromptTerms((current) => ({ ...current, [fieldKey]: [...(current[fieldKey] || []), { token: item.token, option: item.option }] }));
        onFieldChange(fieldKey, normalizePrompt(mapped.filter((term) => term.token !== item.token).map((term) => term.token).join(", ")));
      }
    };
    const chipLabel = (item) => promptLanguage === "zh" ? item.option.name : item.token;
    const reorderStructured = (targetToken) => {
      if (!draggedPromptTerm || draggedPromptTerm === targetToken) return;
      const from = chipItems.findIndex((item) => item.token === draggedPromptTerm);
      const to = chipItems.findIndex((item) => item.token === targetToken);
      if (from < 0 || to < 0) return;
      const reordered = [...chipItems];
      const [moved] = reordered.splice(from, 1); reordered.splice(to, 0, moved);
      reordered.forEach((item, index) => orderMap.set(item.token.toLocaleLowerCase("en-US"), index));
      onFieldChange(fieldKey, normalizePrompt(reordered.filter((item) => !item.disabled).map((item) => item.token).join(", ")));
      setDraggedPromptTerm(null);
    };
    const updateManual = (value) => {
      const restored = promptLanguage === "zh" ? promptTranslationService.toOriginal(fieldKey, value) : value;
      onFieldChange(fieldKey, normalizePrompt(structured.map((item) => item.token).join(", "), restored));
    };
    return <div key={fieldKey} className="workflow-panel__prompt-field-group">
      <div className="workflow-panel__prompt-chip-list" aria-label={`${fieldKey === "positivePrompt" ? "正向" : "反向"}已选结构化词条`}>{chipItems.map((item) => <span className={`workflow-panel__prompt-chip ${item.disabled ? "is-disabled" : ""}`} key={item.token} draggable onContextMenu={(event) => { event.preventDefault(); setChipContextMenu({ fieldKey, item, x: event.clientX, y: event.clientY }); }} onDragStart={() => setDraggedPromptTerm(item.token)} onDragOver={(event) => event.preventDefault()} onDrop={() => reorderStructured(item.token)} onDragEnd={() => setDraggedPromptTerm(null)}><span className="workflow-panel__prompt-chip-label">{chipLabel(item)}</span><button type="button" className="workflow-panel__prompt-chip-toggle" aria-pressed={!item.disabled} aria-label={`${item.disabled ? "启用" : "停用"}词条 ${item.option.name}`} onClick={() => toggleStructured(item)}><i /></button></span>)}</div>
      <label className="workflow-panel__prompt-field workflow-panel__prompt-manual-field">{fieldKey === "positivePrompt" ? "正向提示词" : "反向提示词"}<textarea aria-label={LABELS[fieldKey]} rows={manual.length ? 3 : 2} value={manualDisplay} onChange={(event) => updateManual(event.target.value)} /></label>
    </div>;
  };
  const addPromptTerm = (option, target = "positivePrompt") => {
    const term = option.prompt || (target === "positivePrompt" ? option.positivePrompt : option.negativePrompt);
    if (!term || !promptFieldKeys.includes(target)) return;
    onFieldChange(target, normalizePrompt(values[target], term));
    setPromptTermQuery("");
    setNegativeTermQuery("");
    setPromptTermOpen((current) => ({ ...current, [target]: false }));
  };
  const renderPromptTermSearch = (target) => {
    const positive = target === "positivePrompt";
    const category = positive ? promptTermCategory : negativeTermCategory;
    const setCategory = positive ? setPromptTermCategory : setNegativeTermCategory;
    const query = positive ? promptTermQuery : negativeTermQuery;
    const setQuery = positive ? setPromptTermQuery : setNegativeTermQuery;
    const results = positive ? promptTermResults : negativeTermResults;
    const open = promptTermOpen[target] && Boolean(category || query.trim());
    const setOpen = (value) => setPromptTermOpen((current) => ({ ...current, [target]: value }));
    return <div className="workflow-prompt-term-picker" onFocus={() => setOpen(true)} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false); }} onKeyDown={(event) => { if (event.key === "Escape") { event.preventDefault(); setOpen(false); } }}>
      <select aria-label={`筛选${positive ? "正向" : "反向"} Prompt 词条分类`} value={category} onChange={(event) => { setCategory(event.target.value); setOpen(true); }}>
        <option value="">全部分类</option>
        {PROMPT_CATEGORIES.map((item) => <option key={item.category} value={item.category}>{item.label}</option>)}
      </select>
      <UiSearchField className="workflow-prompt-term-search" aria-label={`搜索并添加${positive ? "正向" : "反向"} Prompt 词条`} value={query} onChange={(event) => { setQuery(event.target.value); setOpen(true); }} placeholder={`搜索${positive ? "正向" : "反向"}词条并添加…`}>{open && <div className="workflow-prompt-term-results">{results.map((option) => <button type="button" key={option.id} onClick={() => addPromptTerm(option, target)}><strong>{option.name}</strong><small>{option.prompt || (positive ? option.positivePrompt : option.negativePrompt)}</small><Plus /></button>)}{!results.length && <span>没有匹配词条</span>}</div>}</UiSearchField>
    </div>;
  };
  const saveQuickPromptOption = async (event, target) => {
    event.preventDefault();
    const draft = quickDrafts[target];
    const name = draft.name.trim();
    const prompt = draft.prompt.trim();
    if (!name || !prompt || quickSaving) return;
    const type = target === "positivePrompt" ? "positive" : "negative";
    const slug = prompt.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 30) || "term";
    const positiveDefinition = promptOptions.find((option) => option.type !== "negative" && option.category === draft.category);
    const payload = { id: `custom_${type[0]}_${Date.now().toString(36)}_${slug}`.slice(0, 64), category: draft.category, name, prompt, type, reverseId: null, sortOrder: 10000, enabled: true, allowMultiple: type === "positive" && Boolean(positiveDefinition?.allowMultiple) };
    setQuickSaving(target); setQuickError("");
    try {
      const response = await fetch("/api/prompt-options", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      const result = await response.json();
      if (!response.ok || result.code !== 200) throw new Error(result.message || "快捷新建词条失败");
      onFieldChange(target, normalizePrompt(values[target], prompt));
      setQuickDrafts((current) => ({ ...current, [target]: { ...current[target], name: "", prompt: "" } }));
      await onPromptOptionsReload?.();
    } catch (exception) { setQuickError(exception.message); }
    finally { setQuickSaving(""); }
  };
  const openChipEdit = () => {
    if (!chipContextMenu?.item) return;
    const option = chipContextMenu.item.option;
    setChipEditDraft({ fieldKey: chipContextMenu.fieldKey, token: chipContextMenu.item.token, disabled: chipContextMenu.item.disabled, option, id: option.id, category: option.category, name: option.name, prompt: option.prompt || option.positivePrompt || option.negativePrompt || "", type: option.type, reverseId: option.reverseId || "", sortOrder: option.sortOrder ?? 10000, enabled: option.enabled !== false, allowMultiple: option.allowMultiple !== false });
    setChipContextMenu(null);
  };
  const saveChipEdit = async (event) => {
    event.preventDefault();
    if (!chipEditDraft || chipEditSaving) return;
    const draft = chipEditDraft;
    const positiveDefinition = promptOptions.find((option) => option.type !== "negative" && option.category === draft.category);
    const payload = { id: draft.id, category: draft.category, name: draft.name.trim(), prompt: draft.prompt.trim(), type: draft.type, reverseId: draft.type === "positive" ? (draft.reverseId || null) : null, sortOrder: Number(draft.sortOrder), enabled: draft.enabled, allowMultiple: draft.type === "positive" && Boolean(positiveDefinition?.allowMultiple ?? draft.allowMultiple) };
    if (!payload.name || !payload.prompt) return;
    setChipEditSaving(true); setQuickError("");
    try {
      const response = await fetch(`/api/prompt-options/${encodeURIComponent(draft.id)}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      const result = await response.json();
      if (!response.ok || result.code !== 200) throw new Error(result.message || "编辑词条失败");
      const current = String(values[draft.fieldKey] || "");
      const oldPrompt = String(draft.option.prompt || draft.option.positivePrompt || draft.option.negativePrompt || draft.token);
      const oldTerms = oldPrompt.split(",").map((term) => term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US"));
      const next = current.split(",").map((term) => oldTerms.includes(term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US")) ? payload.prompt : term).join(", ");
      const nextPrompt = normalizePrompt(next);
      const nextTerms = payload.prompt.split(",").map((term) => term.trim().replace(/\s+/g, " ")).filter(Boolean);
      const nextKeys = nextTerms.map((term) => term.toLocaleLowerCase("en-US"));
      const orderMap = promptTermOrderRef.current[draft.fieldKey];
      const orderedKeys = [...orderMap.entries()].sort((left, right) => left[1] - right[1]).map(([key]) => key);
      const firstOldIndex = orderedKeys.findIndex((key) => oldTerms.includes(key));
      const retainedKeys = orderedKeys.filter((key) => !oldTerms.includes(key));
      retainedKeys.splice(firstOldIndex < 0 ? retainedKeys.length : firstOldIndex, 0, ...nextKeys);
      promptTermOrderRef.current[draft.fieldKey] = new Map([...new Set(retainedKeys)].map((key, index) => [key, index]));
      if (draft.disabled) {
        const nextOption = { ...draft.option, ...payload, positivePrompt: draft.type === "positive" ? payload.prompt : undefined, negativePrompt: draft.type === "negative" ? payload.prompt : undefined };
        const replacementToken = nextTerms[Math.max(0, oldTerms.indexOf(draft.token.toLocaleLowerCase("en-US")))] || nextTerms[0] || draft.token;
        setDisabledPromptTerms((currentTerms) => ({ ...currentTerms, [draft.fieldKey]: (currentTerms[draft.fieldKey] || []).map((item) => item.option?.id === draft.id ? { token: replacementToken, option: nextOption } : item) }));
      } else {
        onFieldChange(draft.fieldKey, nextPrompt);
      }
      setChipEditDraft(null);
      await onPromptOptionsReload?.();
    } catch (exception) { setQuickError(exception.message); }
    finally { setChipEditSaving(false); }
  };
  const updateQuickDraft = (target, key, value) => setQuickDrafts((current) => ({ ...current, [target]: { ...current[target], [key]: value } }));
  const renderQuickPromptOption = (target) => {
    const positive = target === "positivePrompt";
    const draft = quickDrafts[target];
    return <form className={`workflow-panel__quick-option ${positive ? "is-positive" : "is-negative"}`} onSubmit={(event) => saveQuickPromptOption(event, target)}>
      <select aria-label={`${positive ? "正向" : "反向"}词条分类`} value={draft.category} onChange={(event) => updateQuickDraft(target, "category", event.target.value)}>{PROMPT_CATEGORIES.map((item) => <option key={item.category} value={item.category}>{item.label}</option>)}</select>
      <input aria-label={`${positive ? "正向" : "反向"}词条中文名称`} value={draft.name} maxLength="100" onChange={(event) => updateQuickDraft(target, "name", event.target.value)} placeholder="中文名称" />
      <input aria-label={`${positive ? "正向" : "反向"}词条英文 Prompt`} value={draft.prompt} maxLength="500" onChange={(event) => updateQuickDraft(target, "prompt", event.target.value)} placeholder="英文 Prompt" />
      <button type="submit" aria-label={`添加${positive ? "正向" : "反向"}词条`} disabled={quickSaving === target || !draft.name.trim() || !draft.prompt.trim()}><Plus />{quickSaving === target ? "保存中" : "添加"}</button>
    </form>;
  };
  const openBatchDialog = () => setBatchDialog((current) => {
    const category = batchCategories.some((item) => item.category === current.category) ? current.category : (batchCategories[0]?.category || "");
    const availableIds = new Set(promptOptions.filter((option) => option.category === category).map((option) => option.id));
    return { ...current, open: true, category, selectedIds: current.selectedIds.filter((id) => availableIds.has(id)) };
  });
  const submitBatchDialog = (event) => {
    event.preventDefault();
    const selectedIds = new Set(batchDialog.selectedIds);
    const selected = batchCategoryOptions.filter((option) => selectedIds.has(option.id));
    if (!selected.length) return;
    setBatchDialog((current) => ({ ...current, open: false }));
    onBatchGenerate?.(selected);
  };
  return <div className="workflow-panel" onMouseDown={() => chipContextMenu && setChipContextMenu(null)}>
    {chipContextMenu && <div className="workflow-panel__chip-context-menu" style={{ left: chipContextMenu.x, top: chipContextMenu.y }} onMouseDown={(event) => event.stopPropagation()}><button type="button" onClick={openChipEdit}>编辑</button></div>}
    {chipEditDraft && <div className="workflow-panel__chip-edit-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !chipEditSaving && setChipEditDraft(null)}><form className="workflow-panel__chip-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="chip-edit-title" onSubmit={saveChipEdit}><header><div><span>EDIT TERM</span><h3 id="chip-edit-title">编辑结构化词条</h3></div><button type="button" aria-label="关闭词条编辑" disabled={chipEditSaving} onClick={() => setChipEditDraft(null)}><X /></button></header><label>分类<select aria-label="编辑词条分类" value={chipEditDraft.category} onChange={(event) => setChipEditDraft((current) => ({ ...current, category: event.target.value }))}>{PROMPT_CATEGORIES.map((item) => <option key={item.category} value={item.category}>{item.label}</option>)}</select></label><label>中文名称<input aria-label="编辑词条中文名称" value={chipEditDraft.name} onChange={(event) => setChipEditDraft((current) => ({ ...current, name: event.target.value }))} /></label><label>英文 Prompt<input aria-label="编辑词条英文 Prompt" value={chipEditDraft.prompt} onChange={(event) => setChipEditDraft((current) => ({ ...current, prompt: event.target.value }))} /></label><footer><button type="button" disabled={chipEditSaving} onClick={() => setChipEditDraft(null)}>取消</button><button className="primary" type="submit" disabled={chipEditSaving || !chipEditDraft.name.trim() || !chipEditDraft.prompt.trim()}><FloppyDisk />{chipEditSaving ? "保存中" : "保存"}</button></footer></form></div>}
    <section className="workflow-panel__chooser" aria-label="选择工作流">
      <div className="workflow-panel__chooser-main">
        <select aria-label="当前生成工作流" value={workflow?.id || ""} onChange={(event) => onWorkflowChange(event.target.value)}>
          {!workflows.length && <option value="">{loading ? "正在读取工作流…" : "没有识别到可运行工作流"}</option>}
          {workflows.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </div>
      <div className="workflow-panel__chooser-actions">
        {supportsPrompt && <button className="workflow-panel__lucky" type="button" onClick={onLuckyGenerate} disabled={disabled.blocked || luckyLoading}><DiceFive />{luckyLoading ? "正在抽取" : "手气不错"}</button>}
        {supportsPrompt && <button className="workflow-panel__batch-generate" type="button" onClick={openBatchDialog} disabled={disabled.blocked || !batchCategories.length}><Stack />批量生成</button>}
        <button className="comfy-submit workflow-panel__generate" type="button" onClick={onGenerate} disabled={disabled.blocked}><Sparkle />{disabled.busy ? "正在提交…" : "开始生成"}</button>
      </div>
      {supportsPrompt && <div className="workflow-panel__presets workflow-panel__presets--chooser">
        <select aria-label="Prompt 方案" value={presetQuery} onChange={(event) => onPresetChange(event.target.value)}>
          <option value="">请选择 Prompt 方案</option>
          {presets.map((preset) => <option key={preset.id} value={String(preset.id)}>{preset.name}</option>)}
        </select>
        <div className="workflow-panel__preset-save workflow-panel__preset-save--compact">
          <button type="button" disabled={presetSaving} onClick={() => setSaveDialogOpen(true)}><Plus />另存为方案</button>
          <button type="button" disabled={presetSaving || !presetQuery} onClick={() => onSavePreset("overwrite")}><FloppyDisk />覆盖方案</button>
          <button type="button" disabled={presetReloading || !presetQuery} onClick={onReloadPreset}><ArrowClockwise className={presetReloading ? "spin" : ""} />{presetReloading ? "正在重载" : "重新加载当前方案"}</button>
          <button type="button" disabled={!presetQuery} onClick={onEditPreset}>编辑当前方案</button>
        </div>
      </div>}
    </section>

    {batchDialog.open && <div className="workflow-batch-dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setBatchDialog((current) => ({ ...current, open: false }))}>
      <form className="workflow-batch-dialog" role="dialog" aria-modal="true" aria-labelledby="workflow-batch-dialog-title" onSubmit={submitBatchDialog}>
        <header><div><span>BATCH GENERATION</span><h3 id="workflow-batch-dialog-title">批量生成 Prompt 分类</h3></div><button type="button" aria-label="关闭批量生成" onClick={() => setBatchDialog((current) => ({ ...current, open: false }))}><X /></button></header>
        <label>选择类型<select aria-label="批量生成 Prompt 类型" value={batchDialog.category} onChange={(event) => setBatchDialog((current) => ({ ...current, category: event.target.value, selectedIds: [] }))}>{batchCategories.map((item) => <option key={item.category} value={item.category}>{item.label}</option>)}</select></label>
        <div className="workflow-batch-dialog__selection-head"><span>选择词条</span><label><input aria-label="批量生成全选" type="checkbox" checked={batchCategoryOptions.length > 0 && batchDialog.selectedIds.length === batchCategoryOptions.length} onChange={(event) => setBatchDialog((current) => ({ ...current, selectedIds: event.target.checked ? batchCategoryOptions.map((option) => option.id) : [] }))} /><span>全选</span></label></div>
        <div className="workflow-batch-dialog__options" role="group" aria-label="批量生成词条列表">{batchCategoryOptions.map((option) => <label key={option.id}><input type="checkbox" aria-label={`选择批量词条 ${option.name}`} checked={batchDialog.selectedIds.includes(option.id)} onChange={(event) => setBatchDialog((current) => ({ ...current, selectedIds: event.target.checked ? [...current.selectedIds, option.id] : current.selectedIds.filter((id) => id !== option.id) }))} /><span><strong>{option.name}</strong><small>{option.prompt || option.positivePrompt || option.negativePrompt}</small></span></label>)}{!batchCategoryOptions.length && <p>该分类暂无可生成词条</p>}</div>
        <footer><span>已选 {batchDialog.selectedIds.length} 项，将生成 {batchDialog.selectedIds.length} 张</span><button type="button" onClick={() => setBatchDialog((current) => ({ ...current, open: false }))}>取消</button><button className="primary" type="submit" disabled={!batchDialog.selectedIds.length}><Stack />开始批量生成</button></footer>
      </form>
    </div>}

    {saveDialogOpen && <div className="workflow-preset-dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !presetSaving && setSaveDialogOpen(false)}>
      <form className="workflow-preset-dialog" role="dialog" aria-modal="true" aria-labelledby="workflow-preset-dialog-title" onSubmit={async (event) => { event.preventDefault(); const saved = await onSavePreset("new"); if (saved !== false) setSaveDialogOpen(false); }}>
        <header><div><span>SAVE PROMPT SCHEME</span><h3 id="workflow-preset-dialog-title">另存为 Prompt 方案</h3></div><button type="button" aria-label="关闭另存为方案" disabled={presetSaving} onClick={() => setSaveDialogOpen(false)}><X /></button></header>
        <label>方案名称<input autoFocus aria-label="新 Prompt 方案名称" value={presetSaveName} maxLength="100" onChange={(event) => onPresetSaveNameChange(event.target.value)} placeholder="输入方案名称" /></label>
        <footer><button type="button" disabled={presetSaving} onClick={() => setSaveDialogOpen(false)}>取消</button><button className="primary" type="submit" disabled={presetSaving || !presetSaveName.trim()}><FloppyDisk />{presetSaving ? "正在保存" : "确定保存"}</button></footer>
      </form>
    </div>}

    {workflow ? <section className="workflow-panel__parameters" aria-label="工作流参数" key={workflow.id}>
      <header><div><strong>工作流参数</strong><small>{fieldKeys.length} 项</small></div>{supportsPrompt && <button type="button" className="workflow-panel__language" aria-label={promptLanguage === "en" ? "切换为中文 Prompt" : "切换为英文 Prompt"} onClick={() => setPromptLanguage((current) => current === "zh" ? "en" : "zh")}>{promptLanguage === "en" ? "中文" : "EN"}</button>}</header>
      {supportsPrompt && promptFieldKeys.includes("positivePrompt") && <section className={`workflow-panel__prompt-editor workflow-panel__prompt-editor--positive ${promptEditing.positivePrompt ? "is-open" : ""}`}>
        <header><div><strong>正向提示词</strong><small>{presetQuery ? "已由当前结构化方案填充" : "选择方案填充，或手动编辑"}</small></div><button type="button" aria-expanded={promptEditing.positivePrompt} onClick={() => setPromptEditing((current) => ({ ...current, positivePrompt: !current.positivePrompt }))}>{promptEditing.positivePrompt ? <><CaretUp />收起</> : <><PencilSimple />手动编辑</>}</button></header>
        {promptEditing.positivePrompt && <div className="workflow-panel__fields workflow-panel__prompt-fields">
          {renderPromptField("positivePrompt")}
          {renderQuickPromptOption("positivePrompt")}
          {renderPromptTermSearch("positivePrompt")}
          {quickError && <small className="workflow-panel__quick-option-error">{quickError}</small>}
        </div>}
      </section>}
      {supportsPrompt && promptFieldKeys.includes("negativePrompt") && <section className={`workflow-panel__prompt-editor workflow-panel__prompt-editor--negative ${promptEditing.negativePrompt ? "is-open" : ""}`}>
        <header><div><strong>反向提示词</strong><small>{presetQuery ? "已由当前结构化方案填充" : "选择方案填充，或手动编辑"}</small></div><button type="button" aria-expanded={promptEditing.negativePrompt} onClick={() => setPromptEditing((current) => ({ ...current, negativePrompt: !current.negativePrompt }))}>{promptEditing.negativePrompt ? <><CaretUp />收起</> : <><PencilSimple />手动编辑</>}</button></header>
        {promptEditing.negativePrompt && <div className="workflow-panel__fields workflow-panel__prompt-fields">
          {renderPromptField("negativePrompt")}
          {renderQuickPromptOption("negativePrompt")}
          {renderPromptTermSearch("negativePrompt")}
          {quickError && <small className="workflow-panel__quick-option-error">{quickError}</small>}
        </div>}
      </section>}
      {basicFieldKeys.length ? <div className="workflow-panel__fields workflow-panel__fields--basic">{basicFieldKeys.map(renderField)}</div> : !advancedFieldKeys.length && <p role="alert">该工作流没有返回可编辑参数，请刷新工作流扫描结果。</p>}
      {editorKey && <MaskPointEditor file={referenceFiles.sourceImage} value={values[editorKey]} radius={Number(values[radiusKey] || 12)} onChange={(next) => onFieldChange(editorKey, next)} onRadiusChange={(next) => radiusKey && onFieldChange(radiusKey, next)} />}
      {workflow.capabilities?.generationAndCutout && <label className="workflow-panel__check workflow-panel__transparent"><input type="checkbox" checked={Boolean(values.generateTransparent)} onChange={(event) => onFieldChange("generateTransparent", event.target.checked)} /><span>透明背景</span></label>}
    </section> : <section className="workflow-panel__empty">请先启动 ComfyUI 并读取工作流。</section>}

    {workflow && advancedFieldKeys.length > 0 && <details className="workflow-panel__advanced workflow-panel__advanced--bottom"><summary><span>高级选项</span><small>{advancedFieldKeys.length} 项</small></summary><div className="workflow-panel__fields">{advancedFieldKeys.map(renderField)}</div></details>}
  </div>;
}

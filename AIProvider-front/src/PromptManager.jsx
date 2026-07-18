import { useEffect, useMemo, useRef, useState } from "react";
import { Copy, FloppyDisk, Plus, Star, Trash, Warning, X } from "@phosphor-icons/react";
import { buildPromptCategories, composePrompts, emptySelectedOptions, extractNegativeExtra, extractPositiveExtra, normalizePrompt, relatedNegativePromptsForPositive, normalizeSelectedOptions } from "./promptComposer";
import UiSearchField from "./UiSearchField";
import "./PromptManager.css";

const emptyDraft = (definitions) => ({ id: null, name: "", selectedOptions: emptySelectedOptions(definitions), positiveExtra: "", negativeExtra: "", positivePrompt: "", negativePrompt: "", remark: "", isDefault: false });

async function request(path, options) {
  const response = await fetch(path, options);
  const payload = await response.json();
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · ${response.status}`);
  return payload.data;
}

function normalizePreset(item, definitions) {
  return { ...emptyDraft(definitions), ...item, selectedOptions: normalizeSelectedOptions(item?.selectedOptions, definitions) };
}

function MultiOptionPicker({ definition, options, value, onChange, onEditOptions }) {
  const rootRef = useRef(null);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const close = (event) => { if (!rootRef.current?.contains(event.target)) setOpen(false); };
    document.addEventListener("pointerdown", close); return () => document.removeEventListener("pointerdown", close);
  }, []);
  const selected = options.filter((option) => value.includes(option.id));
  const keyword = query.trim().toLocaleLowerCase("zh-CN");
  const available = options.filter((option) => !value.includes(option.id) &&
    [option.name, option.positivePrompt, option.negativePrompt, option.id].filter(Boolean).join(" ").toLocaleLowerCase("zh-CN").includes(keyword));
  return <div ref={rootRef} className="prompt-option-field prompt-option-multi">
    <div className="prompt-option-heading">
      <span>{definition.label}<small>可多选</small></span>
      {selected.length > 0 && <div className="prompt-option-selected">
        {selected.map((option) => <button type="button" key={option.id} title={option.positivePrompt} onClick={() => onChange(value.filter((id) => id !== option.id))}>{option.name}<X /></button>)}
      </div>}
    </div>
    <UiSearchField className={`prompt-option-search ${open ? "is-open" : ""}`} aria-label={`搜索${definition.label}`} value={query} onFocus={() => setOpen(true)} onChange={(event) => { setQuery(event.target.value); setOpen(true); }} onKeyDown={(event) => event.key === "Escape" && setOpen(false)} placeholder={`搜索中文或英文 ${definition.label}`} />
    {open && <div className="prompt-option-results">
      <header><span>{keyword ? `找到 ${available.length} 项` : "常用词条"}</span><button type="button" onClick={() => setOpen(false)}>收起</button></header>
      {available.map((option) => <button type="button" key={option.id} onClick={() => { onChange([...value, option.id]); setQuery(""); }}><span><strong>{option.name}</strong><small> · {option.positivePrompt}</small></span><Plus /></button>)}
      {!available.length && <span>没有匹配词条，请换中文或英文关键词</span>}
    </div>}
    <button type="button" className="prompt-option-manage" onClick={() => onEditOptions(definition.category)}><Plus />编辑词条</button>
  </div>;
}

function SingleOptionPicker({ definition, options, value, onChange, onEditOptions }) {
  return <div className="prompt-option-field"><span>{definition.label}<small>单选</small></span><select aria-label={definition.label} value={value[0] || ""} onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}>
    <option value="">不选择</option>
    {options.map((option) => <option key={option.id} value={option.id}>{option.name} · {option.positivePrompt}</option>)}
  </select><button type="button" className="prompt-option-manage" onClick={() => onEditOptions(definition.category)}><Plus />编辑词条</button></div>;
}

export default function PromptManager({ onEditOptions }) {
  const [items, setItems] = useState([]);
  const [catalog, setCatalog] = useState({ options: [], generalNegativePrompt: "" });
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState(emptyDraft);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [globalOptionQuery, setGlobalOptionQuery] = useState("");
  const definitions = useMemo(() => buildPromptCategories(catalog.options), [catalog.options]);
  const filtered = useMemo(() => items.filter((item) => item.name.toLowerCase().includes(query.trim().toLowerCase())), [items, query]);
  const optionsByCategory = useMemo(() => Object.fromEntries(definitions.map(({ category }) => [category, catalog.options.filter((option) => option.category === category)])), [catalog.options, definitions]);
  const globalOptionResults = useMemo(() => {
    const keyword = globalOptionQuery.trim().toLocaleLowerCase("zh-CN");
    if (!keyword) return [];
    return catalog.options.filter((option) => [option.name, option.positivePrompt, option.negativePrompt, option.id].filter(Boolean).join(" ").toLocaleLowerCase("zh-CN").includes(keyword)).slice(0, 50);
  }, [catalog.options, globalOptionQuery]);

  const select = (item) => setDraft(normalizePreset(item, definitions));
  const load = async (preferredId) => {
    const next = await request("/api/comfy-presets");
    setItems(next || []);
    const queryId = new URLSearchParams(window.location.search).get("edit");
    const selected = next?.find((item) => String(item.id) === String(preferredId ?? queryId)) || next?.find((item) => item.isDefault) || next?.[0];
    if (selected) select(selected); else setDraft(emptyDraft(definitions));
  };
  useEffect(() => {
    Promise.all([request("/api/prompt-catalog"), request("/api/comfy-presets")]).then(([nextCatalog, nextItems]) => {
      const nextDefinitions = buildPromptCategories(nextCatalog?.options || []);
      const next = nextItems || [];
      const queryId = new URLSearchParams(window.location.search).get("edit");
      const selected = next.find((item) => String(item.id) === String(queryId)) || next.find((item) => item.isDefault) || next[0];
      setCatalog(nextCatalog); setItems(next);
      setDraft(selected ? normalizePreset(selected, nextDefinitions) : emptyDraft(nextDefinitions));
    }).catch((exception) => setError(exception.message));
  }, []);

  const setRoot = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const setFinalPositivePrompt = (value) => setDraft((current) => ({ ...current, positivePrompt: value, positiveExtra: extractPositiveExtra(value, current.selectedOptions, catalog.options), negativePrompt: normalizePrompt(current.negativePrompt, ...relatedNegativePromptsForPositive(value, catalog.options)) }));
  const setFinalNegativePrompt = (value) => setDraft((current) => ({ ...current, negativePrompt: value, negativeExtra: extractNegativeExtra(value, current.selectedOptions, catalog.options, catalog.generalNegativePrompt) }));
  const regenerate = (current, patch) => {
    const next = { ...current, ...patch };
    return { ...next, ...composePrompts(next.selectedOptions, catalog.options, catalog.generalNegativePrompt, next.positiveExtra, next.negativeExtra, definitions) };
  };
  const setSelection = (key, value) => setDraft((current) => regenerate(current, { selectedOptions: { ...current.selectedOptions, [key]: value } }));
  const setExtra = (key, value) => setDraft((current) => regenerate(current, { [key]: value }));
  const payload = () => ({
    name: draft.name.trim(), selectedOptions: draft.selectedOptions,
    positiveExtra: draft.positiveExtra, negativeExtra: draft.negativeExtra,
    positivePrompt: draft.positivePrompt, negativePrompt: draft.negativePrompt,
    remark: "", isDefault: draft.isDefault,
  });
  const save = async () => {
    if (!draft.name.trim()) return setError("请填写方案名称");
    setBusy(true); setError("");
    try {
      let preferredId = draft.id;
      if (draft.id) await request(`/api/comfy-presets/${draft.id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload()) });
      else {
        const created = await request("/api/comfy-presets", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload()) });
        preferredId = created.id;
      }
      await load(preferredId);
    } catch (exception) { setError(exception.message); } finally { setBusy(false); }
  };
  const remove = async () => {
    if (!draft.id || !window.confirm(`删除 Prompt 方案“${draft.name}”？`)) return;
    setBusy(true);
    try { await request(`/api/comfy-presets/${draft.id}`, { method: "DELETE" }); await load(); }
    catch (exception) { setError(exception.message); } finally { setBusy(false); }
  };
  const toggleDefault = async (item) => {
    setBusy(true); setError("");
    try { await request(`/api/comfy-presets/${item.id}/default`, { method: item.isDefault ? "DELETE" : "POST" }); await load(item.id); }
    catch (exception) { setError(exception.message); } finally { setBusy(false); }
  };
  const createNew = () => setDraft(emptyDraft(definitions));
  const duplicate = () => setDraft({ ...draft, id: null, name: `${draft.name || "未命名方案"} - 副本`, isDefault: false, selectedOptions: normalizeSelectedOptions(draft.selectedOptions, definitions) });

  return <div className="prompt-manager">
    {error && <div className="prompt-manager-error"><Warning />{error}<button onClick={() => setError("")}>×</button></div>}
    <aside className="prompt-scheme-list">
      <header><div><span>PROMPT LIBRARY</span><h2>Prompt 方案</h2></div><div className="prompt-list-actions"><button onClick={onEditOptions}>编辑词条</button><button onClick={createNew} title="新建方案"><Plus /></button></div></header>
      <UiSearchField className="prompt-list-search" aria-label="搜索 Prompt 方案" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索方案名称…" />
      <div className="prompt-list-scroll">
        {filtered.map((item) => <div key={item.id} role="button" tabIndex="0" className={`prompt-scheme-row ${String(item.id) === String(draft.id) ? "active" : ""}`} onClick={() => select(item)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); select(item); } }}>
          <span>{item.name}</span><button type="button" className={`prompt-default-star ${item.isDefault ? "is-default" : ""}`} aria-label={item.isDefault ? `取消默认方案 ${item.name}` : `设为默认方案 ${item.name}`} title={item.isDefault ? "取消默认方案" : "设为默认方案"} onClick={(event) => { event.stopPropagation(); toggleDefault(item); }}><Star weight={item.isDefault ? "fill" : "regular"} /></button><small>{item.selectedOptions ? `${Object.values(item.selectedOptions).flat().length} 个结构化词条` : "未选择词条"}</small>
        </div>)}
        {!filtered.length && <p>没有匹配的 Prompt 方案</p>}
      </div>
    </aside>
    <section className="prompt-editor">
      <header><div><span>{draft.id ? `方案 #${draft.id}` : "新方案"}</span><h2>{draft.name || "未命名 Prompt 方案"}</h2></div><div className="prompt-editor-actions">
        <button onClick={createNew}><Plus />新建</button><button onClick={duplicate}><Copy />复制</button><button onClick={save} disabled={busy}><FloppyDisk />保存</button><button onClick={remove} disabled={busy || !draft.id} className="danger"><Trash />删除</button>
      </div></header>
      <div className="prompt-editor-form">
        <section className="prompt-basic-info">
          <h3>基础信息</h3>
          <label className="prompt-scheme-name-field">方案名称<input aria-label="方案名称" value={draft.name} maxLength="100" onChange={(event) => setRoot("name", event.target.value)} /></label>
          <label className="check"><input aria-label="是否默认方案" type="checkbox" checked={draft.isDefault} onChange={(event) => setRoot("isDefault", event.target.checked)} /><span>设为默认方案</span></label>
        </section>
        <section className="prompt-composer">
          <header><div><h3>Prompt 组合器</h3><small>词条来自数据库配置；修改选择后自动重建最终 Prompt</small></div><span>{Object.values(draft.selectedOptions).flat().length} 项</span></header>
          <UiSearchField className="prompt-option-search prompt-global-search" aria-label="全局搜索 Prompt 词条" value={globalOptionQuery} onChange={(event) => setGlobalOptionQuery(event.target.value)} placeholder="搜索中文、英文或词条 ID"><div className="prompt-global-search-results">{globalOptionQuery.trim() && globalOptionResults.map((option) => { const definition = definitions.find((item) => item.category === option.category); const selected = draft.selectedOptions?.[option.category]?.includes(option.id); return <button type="button" key={option.id} className={selected ? "is-selected" : ""} onClick={() => { const current = draft.selectedOptions?.[option.category] || []; const next = definition?.multiple ? (current.includes(option.id) ? current : [...current, option.id]) : [option.id]; setSelection(option.category, next); setGlobalOptionQuery(""); window.setTimeout(() => document.querySelector(`[data-prompt-category="${CSS.escape(option.category)}"]`)?.scrollIntoView({ behavior: "smooth", block: "center" }), 0); }}><span><strong>{option.name}</strong><small>{definition?.label || option.category} · {option.positivePrompt}</small></span>{selected && <span>已选</span>}</button>})}{globalOptionQuery.trim() && !globalOptionResults.length && <p>没有匹配词条</p>}</div></UiSearchField>
          <div className="prompt-category-grid">{definitions.map((definition) => {
            const props = { definition, options: optionsByCategory[definition.category] || [], value: draft.selectedOptions[definition.key], onChange: (value) => setSelection(definition.key, value), onEditOptions };
            return definition.multiple ? <div key={definition.key} data-prompt-category={definition.category}><MultiOptionPicker {...props} /></div> : <div key={definition.key} data-prompt-category={definition.category}><SingleOptionPicker {...props} /></div>;
          })}</div>
        </section>
        <section className="prompt-output-grid">
          <label>正向手动补充<textarea aria-label="正向手动补充" rows="4" value={draft.positiveExtra} onChange={(event) => setExtra("positiveExtra", event.target.value)} /></label>
          <label>反向手动补充<textarea aria-label="反向手动补充" rows="4" value={draft.negativeExtra} onChange={(event) => setExtra("negativeExtra", event.target.value)} /></label>
          <label>最终正向 Prompt <small>可直接编辑；匹配到词条关联反向词时自动补充</small><textarea aria-label="最终正向 Prompt" rows="8" value={draft.positivePrompt} onChange={(event) => setFinalPositivePrompt(event.target.value)} /></label>
          <label>最终反向 Prompt <small>可直接编辑；新增内容自动同步到反向补充</small><textarea aria-label="最终反向 Prompt" rows="8" value={draft.negativePrompt} onChange={(event) => setFinalNegativePrompt(event.target.value)} /></label>
        </section>
      </div>
    </section>
  </div>;
}

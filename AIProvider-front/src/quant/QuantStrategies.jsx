import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowsClockwise, Flask, Warning } from "@phosphor-icons/react";
import UiSearchField from "../UiSearchField";
import QuantPageScaffold from "./QuantPageScaffold";
import { fetchQuantStrategies } from "./quantStrategiesApi";
import "./QuantStrategies.css";

function LoadingState() { return <div className="quant-loading" role="status">正在读取策略目录…</div>; }

function ErrorState({ error, retry }) {
  return <div className="strategy-error" role="alert"><span><Warning weight="fill" /> 策略目录加载失败：{error}</span><button type="button" className="quant-refresh" onClick={retry}><ArrowsClockwise />重试</button></div>;
}

function navigateToBacktest(strategyCode) {
  const params = new URLSearchParams({ openCreate: "1", strategyCode });
  const target = `/quant/backtests?${params.toString()}`;
  window.history.pushState({}, "", target);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

export default function QuantStrategies() {
  const [strategies, setStrategies] = useState([]);
  const [selectedCode, setSelectedCode] = useState("");
  const [query, setQuery] = useState("");
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const controllerRef = useRef(null);
  const sequenceRef = useRef(0);

  const load = useCallback(async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const sequence = ++sequenceRef.current;
    setState("loading");
    setError("");
    try {
      const next = await fetchQuantStrategies(controller.signal);
      if (sequence !== sequenceRef.current) return;
      setStrategies(next);
      setSelectedCode((current) => next.some((item) => item.code === current) ? current : (next[0]?.code || ""));
      setState("ready");
    } catch (exception) {
      if (exception.name === "AbortError" || sequence !== sequenceRef.current) return;
      setError(exception.message || "策略服务请求失败");
      setState("error");
    }
  }, []);

  useEffect(() => { load(); return () => controllerRef.current?.abort(); }, [load]);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    if (!normalized) return strategies;
    return strategies.filter((item) => [item.name, item.code, item.description].some((value) => value.toLocaleLowerCase().includes(normalized)));
  }, [query, strategies]);
  const selected = strategies.find((item) => item.code === selectedCode) || null;

  return <QuantPageScaffold pageClass="quant-strategies-page" title="策略研究"><div className="quant-workspace-head"><div><span className="eyebrow">QUANT · STRATEGY LAB</span><h3>策略研究</h3><small>查看已注册的策略定义、版本与参数，并进入回测实验验证</small></div><div className="strategy-lab-actions"><button type="button" className="quant-refresh" onClick={load} disabled={state === "loading"}><ArrowsClockwise className={state === "loading" ? "spin" : ""} />刷新</button></div></div>{state === "loading" && <LoadingState />}{state === "error" && <ErrorState error={error} retry={load} />}{state === "ready" && <><div className="quant-status-grid"><div className="quant-status-card"><div className="quant-status-text"><span>已注册策略数</span><strong>{strategies.length}</strong></div></div><div className="quant-status-card"><div className="quant-status-text"><span>当前选中策略</span><strong>{selected?.name || "—"}</strong></div></div><div className="quant-status-card"><div className="quant-status-text"><span>参数数量</span><strong>{selected?.parameters.length ?? "—"}</strong></div></div><div className="quant-status-card"><div className="quant-status-text"><span>最低 K 线数</span><strong>{selected?.minimumRequiredBars ?? "—"}</strong></div></div></div><div className="strategy-lab-layout"><section className="strategy-lab-card" aria-label="策略目录"><header className="quant-section-head"><h4>策略目录</h4><small>{filtered.length} / {strategies.length}</small></header><UiSearchField className="strategy-search" aria-label="搜索策略" placeholder="搜索名称、code 或描述" value={query} onChange={(event) => setQuery(event.target.value)} />{!filtered.length ? <div className="strategy-empty">没有匹配的策略</div> : <div className="strategy-list">{filtered.map((item) => <button type="button" className={`strategy-list-item ${item.code === selectedCode ? "selected" : ""}`} key={item.code} onClick={() => setSelectedCode(item.code)} aria-pressed={item.code === selectedCode}><strong>{item.name}</strong><small>{item.code} · v{item.version}</small><small>{item.minimumRequiredBars} 根 · {item.parameters.length} 个参数</small></button>)}</div>}</section><section className="strategy-lab-card" aria-label="策略详情">{!strategies.length ? <div className="strategy-empty">当前没有已注册策略</div> : !selected ? <div className="strategy-empty">请选择策略</div> : <><div className="strategy-detail-head"><div><span className="eyebrow">REGISTERED STRATEGY</span><h4>{selected.name}</h4><span className="strategy-code">{selected.code}</span></div><span className="strategy-version">版本 {selected.version}</span></div><p className="strategy-description">{selected.description}</p><dl className="strategy-detail-meta"><div><dt>最低 K 线数</dt><dd>{selected.minimumRequiredBars}</dd></div><div><dt>可配置参数</dt><dd>{selected.parameters.length}</dd></div></dl><h4>参数定义</h4>{selected.parameters.length ? <div className="strategy-parameter-wrap"><table className="strategy-parameter-table"><thead><tr><th>参数名</th><th>默认值</th><th>最小值</th><th>最大值</th></tr></thead><tbody>{selected.parameters.map((parameter) => <tr key={parameter.name}><td>{parameter.name}</td><td>{parameter.defaultValue}</td><td>{parameter.minValue}</td><td>{parameter.maxValue}</td></tr>)}</tbody></table></div> : <div className="strategy-empty">该策略没有可配置参数</div>}<p className="strategy-research-note">策略定义只描述历史回测规则，不代表未来收益，也不等于实盘建议。</p><button type="button" className="quant-primary-action strategy-create-action" onClick={() => navigateToBacktest(selected.code)}><Flask />用此策略创建回测</button></>}</section></div></>}</QuantPageScaffold>;
}

export { navigateToBacktest };

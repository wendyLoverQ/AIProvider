import { useEffect, useMemo, useRef, useState } from "react";
import { X } from "@phosphor-icons/react";
import { compareDecimalStrings, intervalCode, intervalDurationMs, normalizeDecimalString, toUtcIso, utcInstantToLocalInput, isPositiveDecimal } from "./quantBacktestsFormat";
import { createWalkForwardStudy } from "./quantWalkForwardApi";
import { calculateCandidateCount, parseIntegerCsv, splitDataset7030 } from "./quantExperimentsFormat";
import { calculateWalkForwardWindow, WALK_FORWARD_SELECTION_METRIC_LABELS } from "./quantExperimentsFormat";

export default function QuantWalkForwardCreatePanel({ strategies, datasets, onClose, onCreated, onSavingChange = () => {} }) {
  const [datasetId, setDatasetId] = useState("");
  const [strategyCode, setStrategyCode] = useState("");
  const [inputs, setInputs] = useState({});
  const [studyStart, setStudyStart] = useState("");
  const [studyEnd, setStudyEnd] = useState("");
  const [trainingBars, setTrainingBars] = useState("");
  const [validationBars, setValidationBars] = useState("");
  const [selectionMetric, setSelectionMetric] = useState("TRAIN_TOTAL_RETURN_RATIO");
  const [minimumTrainTrades, setMinimumTrainTrades] = useState("10");
  const [orderAmount, setOrderAmount] = useState("1");
  const [feeRate, setFeeRate] = useState("0.001");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const requestRef = useRef(null);
  const mountedRef = useRef(true);
  const dataset = datasets.find((item) => String(item.id) === String(datasetId));
  const strategy = strategies.find((item) => item.code === strategyCode);

  useEffect(() => () => { mountedRef.current = false; requestRef.current?.abort(); }, []);
  useEffect(() => {
    const onKey = (event) => { if (event.key === "Escape" && !savingRef.current) onClose(); };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const parsedGrid = useMemo(() => {
    if (!strategy) return { grid: null, error: "请选择策略" };
    const grid = {};
    for (const parameter of strategy.parameters || []) {
      const parsed = parseIntegerCsv(inputs[parameter.name], parameter);
      if (parsed.error) return { grid: null, error: `${parameter.name}：${parsed.error}` };
      grid[parameter.name] = parsed.values;
    }
    return { grid };
  }, [inputs, strategy]);
  const candidateCount = calculateCandidateCount(parsedGrid.grid);
  const derived = useMemo(() => calculateWalkForwardWindow({
    studyStart: toUtcIso(studyStart),
    studyEnd: toUtcIso(studyEnd),
    dataset,
    trainingBars: Number(trainingBars),
    validationBars: Number(validationBars),
    candidateCount,
  }), [candidateCount, dataset, studyEnd, studyStart, trainingBars, validationBars]);

  const chooseStrategy = (code) => {
    const selected = strategies.find((item) => item.code === code);
    setStrategyCode(code);
    setInputs(Object.fromEntries((selected?.parameters || []).map((parameter) => [parameter.name, String(parameter.defaultValue)])));
    setError("");
  };
  const chooseDataset = (value) => {
    const selected = datasets.find((item) => String(item.id) === String(value));
    setDatasetId(value);
    if (selected) {
      setStudyStart(utcInstantToLocalInput(selected.earliestOpenTime));
      const duration = intervalDurationMs(selected.intervalCode || selected.interval);
      if (duration > 0) setStudyEnd(utcInstantToLocalInput(new Date(new Date(selected.latestOpenTime).getTime() + duration).toISOString()));
    }
  };
  const fillBars = () => {
    if (!dataset) return setError("请先选择数据集");
    const split = splitDataset7030(dataset, strategy?.minimumRequiredBars || 1);
    if (split.error) return setError(split.error);
    setTrainingBars(String(split.trainingBars));
    setValidationBars(String(split.validationBars));
  };
  const submit = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    setError("");
    if (!dataset || !strategy || !parsedGrid.grid || parsedGrid.error) return setError(parsedGrid.error || "请完整选择数据集和策略");
    if (candidateCount == null || candidateCount < 1 || candidateCount > 64) return setError("候选组合必须为 1～64 个");
    const window = calculateWalkForwardWindow({ studyStart: toUtcIso(studyStart), studyEnd: toUtcIso(studyEnd), dataset, trainingBars: Number(trainingBars), validationBars: Number(validationBars), candidateCount });
    if (window.error) return setError(window.error);
    const trades = Number(minimumTrainTrades);
    if (!Number.isSafeInteger(trades) || trades < 0) return setError("minimumTrainTrades 必须为非负安全整数");
    const normalizedOrder = normalizeDecimalString(orderAmount, { maxIntegerDigits: 20, maxFractionDigits: 18 });
    const normalizedFee = normalizeDecimalString(feeRate, { maxIntegerDigits: 1, maxFractionDigits: 18 });
    if (!normalizedOrder || !isPositiveDecimal(normalizedOrder)) return setError("orderAmount 必须是大于 0 的十进制字符串");
    if (!normalizedFee || compareDecimalStrings(normalizedFee, "0") < 0 || compareDecimalStrings(normalizedFee, "0.01") > 0) return setError("feeRate 必须为 0～0.01，最多 18 位小数");
    const body = {
      datasetId: Number(dataset.id), strategyCode: strategy.code, strategyVersion: strategy.version,
      parameterGrid: parsedGrid.grid,
      studyStartOpenTimeInclusive: toUtcIso(studyStart), studyEndOpenTimeExclusive: toUtcIso(studyEnd),
      trainingBars: Number(trainingBars), validationBars: Number(validationBars), selectionMetric,
      minimumTrainTrades: trades, orderAmount: normalizedOrder, feeRate: normalizedFee, forceCloseAtEnd: true,
    };
    savingRef.current = true;
    const controller = new AbortController();
    requestRef.current = controller;
    setSaving(true); onSavingChange(true);
    try {
      const created = await createWalkForwardStudy(body, controller.signal);
      if (!mountedRef.current || controller.signal.aborted) return;
      await onCreated(created);
      if (mountedRef.current && !controller.signal.aborted) onClose();
    } catch (exception) {
      if (mountedRef.current && exception.name !== "AbortError" && !controller.signal.aborted) setError(exception.message || "创建滚动验证失败");
    } finally {
      savingRef.current = false;
      if (requestRef.current === controller) requestRef.current = null;
      if (mountedRef.current) { setSaving(false); onSavingChange(false); }
    }
  };

  return <aside className="backtest-create-panel quant-walk-forward-create" role="dialog" aria-modal="true" aria-label="新建滚动验证">
    <div className="backtest-panel-head"><div><span className="eyebrow">WALK-FORWARD STUDY</span><h3>新建滚动验证</h3></div><button type="button" aria-label="关闭新建滚动验证" disabled={saving} onClick={onClose}><X /></button></div>
    <form onSubmit={submit}>
      <label>连续历史数据集<select aria-label="连续历史数据集" value={datasetId} onChange={(event) => chooseDataset(event.target.value)}><option value="">请选择已校验数据集</option>{datasets.map((item) => <option key={item.id} value={item.id}>{item.symbol} · {intervalCode(item.interval)} · {Number(item.candleCount).toLocaleString()} 根</option>)}</select></label>
      <label>策略<select aria-label="策略" value={strategyCode} onChange={(event) => chooseStrategy(event.target.value)}><option value="">请选择策略</option>{strategies.map((item) => <option key={item.code} value={item.code}>{item.name} · {item.version}</option>)}</select></label>
      {(strategy?.parameters || []).map((parameter) => <label key={parameter.name}>{parameter.name}<input aria-label={`${parameter.name} 候选值`} value={inputs[parameter.name] || ""} onChange={(event) => setInputs((current) => ({ ...current, [parameter.name]: event.target.value }))} placeholder="逗号分隔整数" /><small>默认 {parameter.defaultValue} · 范围 {parameter.minValue}～{parameter.maxValue}</small></label>)}
      {parsedGrid.error && <p className="backtest-inline-error" role="alert">{parsedGrid.error}</p>}
      <div className="backtest-form-grid"><label>研究开始<input aria-label="研究开始" type="datetime-local" value={studyStart} onChange={(event) => setStudyStart(event.target.value)} /></label><label>研究结束（不包含）<input aria-label="研究结束（不包含）" type="datetime-local" value={studyEnd} onChange={(event) => setStudyEnd(event.target.value)} /></label></div>
      <div className="backtest-form-grid"><label>trainingBars<input aria-label="trainingBars" inputMode="numeric" value={trainingBars} onChange={(event) => setTrainingBars(event.target.value)} /></label><label>validationBars<input aria-label="validationBars" inputMode="numeric" value={validationBars} onChange={(event) => setValidationBars(event.target.value)} /></label></div>
      <button type="button" className="quant-secondary-action" onClick={fillBars}>按 70% / 30% 填充窗口</button>
      <label>选择指标<select aria-label="选择指标" value={selectionMetric} onChange={(event) => setSelectionMetric(event.target.value)}>{Object.entries(WALK_FORWARD_SELECTION_METRIC_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label>minimumTrainTrades<input aria-label="minimumTrainTrades" inputMode="numeric" value={minimumTrainTrades} onChange={(event) => setMinimumTrainTrades(event.target.value)} /></label>
      <div className="backtest-form-grid"><label>orderAmount<input aria-label="orderAmount" value={orderAmount} onChange={(event) => setOrderAmount(event.target.value)} /></label><label>feeRate<input aria-label="feeRate" value={feeRate} onChange={(event) => setFeeRate(event.target.value)} /></label></div>
      <div className="quant-walk-forward-window-summary"><strong>滚动模式：ROLLING</strong><span>步长：{validationBars || "—"} · Fold 数量：{derived.error ? "—" : derived.foldCount || "—"}</span><span>每 Fold 候选：{candidateCount || "—"} · 总回测任务：{derived.error ? "—" : derived.totalChildRuns || "—"}</span>{derived.error ? <small>{derived.error}</small> : <small>首个 VALIDATION 结束：{derived.firstValidationEnd || "—"} · 最后结束：{derived.lastValidationEnd || "—"}</small>}</div>
      {strategy && <p className="backtest-help">默认参数最低需要 {strategy.minimumRequiredBars} 根 K 线；网格中更大周期参数的最终要求由后端逐组合校验。</p>}
      <p className="backtest-help">候选只按 TRAIN 指标选择；VALIDATION 结果不参与选择。样本外结果不代表未来收益。</p>
      {error && <p className="backtest-inline-error" role="alert">{error}</p>}
      <button type="submit" className="quant-primary-action" disabled={saving || Boolean(derived.error) || candidateCount == null || candidateCount > 64}>{saving ? "正在创建…" : "创建滚动验证"}</button>
    </form>
  </aside>;
}

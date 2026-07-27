import { useEffect, useMemo, useRef, useState } from "react";
import { X } from "@phosphor-icons/react";
import {
  compareDecimalStrings,
  formatInstant,
  isPositiveDecimal,
  normalizeDecimalString,
  toUtcIso,
} from "./quantBacktestsFormat";
import {
  calculateCandidateCount,
  parseIntegerCsv,
  splitDataset7030,
  validateExperimentRanges,
} from "./quantExperimentsFormat";
import { createExperiment } from "./quantExperimentsApi";
import QuantExecutionContextFields from "./QuantExecutionContextFields";
import {
  executionContextPayload,
  validateExecutionSelection,
} from "./quantExecutionContext";

function ParameterInput({ parameter, value, onChange }) {
  const parsed = parseIntegerCsv(value, parameter);
  return (
    <label className="quant-experiment-parameter">
      <span>{parameter.name}</span>
      <input
        aria-label={`${parameter.name} 候选值`}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="逗号分隔整数"
      />
      <small>
        默认 {parameter.defaultValue} · 范围 {parameter.minValue}～
        {parameter.maxValue}
      </small>
      {!parsed.error && (
        <span
          className="quant-experiment-chips"
          aria-label={`${parameter.name} 已解析候选值`}
        >
          {parsed.values.map((item) => (
            <i key={item}>{item}</i>
          ))}
        </span>
      )}
      {parsed.error && <small className="field-error">{parsed.error}</small>}
    </label>
  );
}

export default function QuantExperimentCreatePanel({
  strategies,
  datasets,
  executionProfiles = [],
  onClose,
  onCreated,
  onSavingChange = () => {},
}) {
  const [executionContext, setExecutionContext] = useState({
    marketType: "",
    datasetId: "",
    strategyCode: "",
    executionProfileCode: "",
  });
  const [contextErrors, setContextErrors] = useState({});
  const [parameterInputs, setParameterInputs] = useState({});
  const [ranges, setRanges] = useState({
    trainingStart: "",
    trainingEnd: "",
    validationStart: "",
    validationEnd: "",
  });
  const [orderAmount, setOrderAmount] = useState("1");
  const [feeRate, setFeeRate] = useState("0.001");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const requestRef = useRef(null);
  const mountedRef = useRef(true);

  const dataset = datasets.find(
    (item) => String(item.id) === String(executionContext.datasetId),
  );
  const strategy = strategies.find(
    (item) => item.code === executionContext.strategyCode,
  );
  const parsedGrid = useMemo(() => {
    if (!strategy) return { grid: null, error: "请选择策略" };
    const grid = {};
    for (const parameter of strategy.parameters || []) {
      const parsed = parseIntegerCsv(
        parameterInputs[parameter.name],
        parameter,
      );
      if (parsed.error)
        return { grid: null, error: `${parameter.name}：${parsed.error}` };
      grid[parameter.name] = parsed.values;
    }
    return { grid };
  }, [parameterInputs, strategy]);
  const candidateCount = calculateCandidateCount(parsedGrid.grid);
  const totalLegs = candidateCount == null ? null : candidateCount * 2;
  const overLimit =
    candidateCount != null && (candidateCount > 64 || totalLegs > 128);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === "Escape" && !saving) onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose, saving]);

  useEffect(
    () => () => {
      mountedRef.current = false;
      requestRef.current?.abort();
    },
    [],
  );

  useEffect(() => {
    setParameterInputs(
      Object.fromEntries(
        (strategy?.parameters || []).map((parameter) => [
          parameter.name,
          String(parameter.defaultValue),
        ]),
      ),
    );
    setError("");
  }, [strategy]);

  const fillSplit = () => {
    setError("");
    if (!dataset || !strategy) {
      setError("请先选择数据集和策略");
      return;
    }
    const result = splitDataset7030(dataset, strategy.minimumRequiredBars || 1);
    if (result.error) {
      setError(result.error);
      return;
    }
    setRanges({
      trainingStart: result.trainingStart,
      trainingEnd: result.trainingEnd,
      validationStart: result.validationStart,
      validationEnd: result.validationEnd,
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    setError("");
    const contextResult = validateExecutionSelection({
      datasets,
      strategies,
      profiles: executionProfiles,
      value: executionContext,
    });
    setContextErrors(contextResult.errors);
    if (!contextResult.valid) {
      setError("请完整选择兼容的市场、数据集、策略和执行模型");
      return;
    }
    if (parsedGrid.error || !parsedGrid.grid) {
      setError(parsedGrid.error || "参数候选值无效");
      return;
    }
    const nextCount = calculateCandidateCount(parsedGrid.grid);
    if (
      nextCount == null ||
      nextCount < 1 ||
      nextCount > 64 ||
      nextCount * 2 > 128
    ) {
      setError("候选组合必须为 1～64 个，回测任务最多 128 个");
      return;
    }
    const rangeResult = validateExperimentRanges(
      {
        trainingStart: toUtcIso(ranges.trainingStart),
        trainingEnd: toUtcIso(ranges.trainingEnd),
        validationStart: toUtcIso(ranges.validationStart),
        validationEnd: toUtcIso(ranges.validationEnd),
      },
      dataset,
    );
    if (rangeResult.error) {
      setError(rangeResult.error);
      return;
    }
    const normalizedOrderAmount = normalizeDecimalString(orderAmount, {
      maxIntegerDigits: 20,
      maxFractionDigits: 18,
    });
    const normalizedFeeRate = normalizeDecimalString(feeRate, {
      maxIntegerDigits: 1,
      maxFractionDigits: 18,
    });
    if (!normalizedOrderAmount || !isPositiveDecimal(normalizedOrderAmount)) {
      setError("基础资产数量必须大于 0，整数最多 20 位、小数最多 18 位");
      return;
    }
    if (
      !normalizedFeeRate ||
      compareDecimalStrings(normalizedFeeRate, "0") < 0 ||
      compareDecimalStrings(normalizedFeeRate, "0.01") > 0
    ) {
      setError("手续费比例必须为 0～0.01，最多 18 位小数");
      return;
    }
    const body = {
      datasetId: Number(dataset.id),
      strategyCode: strategy.code,
      strategyVersion: strategy.version,
      ...executionContextPayload(contextResult.profile),
      parameterGrid: parsedGrid.grid,
      trainingStartOpenTimeInclusive: toUtcIso(ranges.trainingStart),
      trainingEndOpenTimeExclusive: toUtcIso(ranges.trainingEnd),
      validationStartOpenTimeInclusive: toUtcIso(ranges.validationStart),
      validationEndOpenTimeExclusive: toUtcIso(ranges.validationEnd),
      orderAmount: normalizedOrderAmount,
      feeRate: normalizedFeeRate,
      forceCloseAtEnd: true,
    };
    savingRef.current = true;
    const controller = new AbortController();
    requestRef.current = controller;
    setSaving(true);
    onSavingChange(true);
    try {
      const created = await createExperiment(body, controller.signal);
      if (!mountedRef.current || controller.signal.aborted) return;
      await onCreated(created);
      if (!mountedRef.current || controller.signal.aborted) return;
      onClose();
    } catch (exception) {
      if (
        mountedRef.current &&
        exception.name !== "AbortError" &&
        !controller.signal.aborted
      )
        setError(exception.message || "创建参数实验失败");
    } finally {
      savingRef.current = false;
      if (requestRef.current === controller) requestRef.current = null;
      if (mountedRef.current) {
        setSaving(false);
        onSavingChange(false);
      }
    }
  };

  return (
    <aside
      className="backtest-create-panel quant-experiment-create"
      role="dialog"
      aria-modal="true"
      aria-label="新建参数实验"
    >
      <div className="backtest-panel-head">
        <div>
          <span className="eyebrow">PARAMETER EXPERIMENT</span>
          <h3>新建参数实验</h3>
        </div>
        <button
          type="button"
          aria-label="关闭新建参数实验"
          disabled={saving}
          onClick={onClose}
        >
          <X />
        </button>
      </div>
      <form onSubmit={submit}>
        <QuantExecutionContextFields
          autoFocus
          datasets={datasets}
          strategies={strategies}
          executionProfiles={executionProfiles}
          value={executionContext}
          onChange={(next) => {
            setExecutionContext(next);
            setContextErrors({});
          }}
          disabled={saving}
          errors={contextErrors}
        />
        {dataset && (
          <p className="backtest-help">
            {formatInstant(dataset.earliestOpenTime)} ～{" "}
            {formatInstant(dataset.latestOpenTime)} · 选择数据集不会自动切分时间
          </p>
        )}
        {(strategy?.parameters || []).map((parameter) => (
          <ParameterInput
            key={parameter.name}
            parameter={parameter}
            value={parameterInputs[parameter.name] || ""}
            onChange={(value) =>
              setParameterInputs((current) => ({
                ...current,
                [parameter.name]: value,
              }))
            }
          />
        ))}
        {strategy && (
          <p className="backtest-help">
            默认参数最低需要 {strategy.minimumRequiredBars} 根 K
            线；参数网格的最终最低 K 线要求由后端逐组合校验。
          </p>
        )}
        <div className={`quant-experiment-count ${overLimit ? "invalid" : ""}`}>
          <strong>候选组合：{candidateCount ?? "—"}</strong>
          <span>
            回测任务：{totalLegs ?? "—"}
            {candidateCount != null
              ? `（TRAIN ${candidateCount} + VALIDATION ${candidateCount}）`
              : ""}
          </span>
          {overLimit && <small>最多 64 个候选、128 个回测任务</small>}
        </div>
        <button
          type="button"
          className="quant-secondary-action"
          onClick={fillSplit}
        >
          按 70% / 30% 填充
        </button>
        <div className="backtest-form-grid">
          <label>
            TRAIN 开始
            <input
              aria-label="TRAIN 开始"
              type="datetime-local"
              value={ranges.trainingStart}
              onChange={(event) =>
                setRanges((current) => ({
                  ...current,
                  trainingStart: event.target.value,
                }))
              }
            />
          </label>
          <label>
            TRAIN 结束（不包含）
            <input
              aria-label="TRAIN 结束（不包含）"
              type="datetime-local"
              value={ranges.trainingEnd}
              onChange={(event) =>
                setRanges((current) => ({
                  ...current,
                  trainingEnd: event.target.value,
                }))
              }
            />
          </label>
          <label>
            VALIDATION 开始
            <input
              aria-label="VALIDATION 开始"
              type="datetime-local"
              value={ranges.validationStart}
              onChange={(event) =>
                setRanges((current) => ({
                  ...current,
                  validationStart: event.target.value,
                }))
              }
            />
          </label>
          <label>
            VALIDATION 结束（不包含）
            <input
              aria-label="VALIDATION 结束（不包含）"
              type="datetime-local"
              value={ranges.validationEnd}
              onChange={(event) =>
                setRanges((current) => ({
                  ...current,
                  validationEnd: event.target.value,
                }))
              }
            />
          </label>
        </div>
        <div className="backtest-form-grid">
          <label>
            基础资产数量
            <input
              aria-label="基础资产数量"
              inputMode="decimal"
              value={orderAmount}
              onChange={(event) => setOrderAmount(event.target.value)}
            />
            <small>
              按交易对的基础资产数量解释，不是 USDT 金额。例如 BTCUSDT 填
              0.01，表示 0.01 BTC 名义数量。
            </small>
          </label>
          <label>
            手续费比例
            <input
              aria-label="手续费比例"
              inputMode="decimal"
              value={feeRate}
              onChange={(event) => setFeeRate(event.target.value)}
            />
          </label>
        </div>
        <p className="backtest-help">
          结束时固定强制平仓；TRAIN 用于参数研究，VALIDATION
          为样本外区间。70/30 切分仅按默认参数最低 K
          线数检查，更大周期候选可能被后端拒绝，最终由后端逐组合校验。
        </p>
        {error && (
          <p className="backtest-inline-error" role="alert">
            {error}
          </p>
        )}
        <button
          type="submit"
          className="quant-primary-action"
          disabled={saving || overLimit || !executionProfiles.length}
        >
          {saving ? "正在创建…" : "创建异步实验"}
        </button>
      </form>
    </aside>
  );
}

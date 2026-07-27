import { useEffect, useRef, useState } from "react";
import { X } from "@phosphor-icons/react";
import {
  calculateExpectedBars,
  compareDecimalStrings,
  formatInstant,
  intervalDurationMs,
  isPositiveDecimal,
  normalizeDecimalString,
  toUtcIso,
  utcInstantToLocalInput,
} from "./quantBacktestsFormat";
import { createBacktestRun } from "./quantBacktestsApi";
import QuantExecutionContextFields from "./QuantExecutionContextFields";
import {
  compatibleStrategies,
  executionContextPayload,
  validateExecutionSelection,
} from "./quantExecutionContext";

export default function QuantSingleBacktestCreatePanel({
  strategies,
  datasets,
  executionProfiles = [],
  initialStrategyCode = "",
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
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [params, setParams] = useState(() =>
    Object.fromEntries(
      [],
    ),
  );
  const [orderAmount, setOrderAmount] = useState("1");
  const [feeRate, setFeeRate] = useState("0");
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

  useEffect(() => {
    const close = (event) => {
      if (event.key === "Escape" && !savingRef.current) onClose();
    };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [onClose]);

  useEffect(() => {
    if (!strategy) {
      setParams({});
      return;
    }
    setParams(
      Object.fromEntries(
        (strategy.parameters || []).map((parameter) => [
          parameter.name,
          String(parameter.defaultValue),
        ]),
      ),
    );
  }, [strategy]);

  useEffect(() => {
    if (!dataset) return;
    const duration = intervalDurationMs(dataset.interval);
    setStart(utcInstantToLocalInput(dataset.earliestOpenTime));
    setEnd(
      duration
        ? utcInstantToLocalInput(
            new Date(
              new Date(dataset.latestOpenTime).getTime() + duration,
            ).toISOString(),
          )
        : "",
    );
  }, [dataset]);

  useEffect(() => {
    if (
      !initialStrategyCode ||
      !dataset ||
      executionContext.strategyCode ||
      !compatibleStrategies(strategies, dataset).some(
        (item) => item.code === initialStrategyCode,
      )
    )
      return;
    setExecutionContext((current) => ({
      ...current,
      strategyCode: initialStrategyCode,
    }));
  }, [
    dataset,
    executionContext.strategyCode,
    initialStrategyCode,
    strategies,
  ]);

  useEffect(
    () => () => {
      mountedRef.current = false;
      requestRef.current?.abort();
    },
    [],
  );

  const expectedBars = dataset
    ? calculateExpectedBars(toUtcIso(start), toUtcIso(end), dataset.interval)
    : null;

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
    const normalizedOrderAmount = normalizeDecimalString(orderAmount, {
      maxIntegerDigits: 20,
      maxFractionDigits: 18,
    });
    const normalizedFeeRate = normalizeDecimalString(feeRate, {
      maxIntegerDigits: 1,
      maxFractionDigits: 18,
    });
    const startIso = toUtcIso(start);
    const endIso = toUtcIso(end);
    const invalidParameter = (strategy?.parameters || []).find(
      (parameter) =>
        !/^[0-9]+$/.test(params[parameter.name] || "") ||
        Number(params[parameter.name]) < parameter.minValue ||
        Number(params[parameter.name]) > parameter.maxValue,
    );
    if (!contextResult.valid || !startIso || !endIso)
      return setError("请完整选择兼容的市场、数据集、策略、执行模型和时间范围");
    const duration = intervalDurationMs(dataset.interval);
    if (!duration) return setError("当前周期暂不支持前端时间计算");
    if (
      new Date(startIso).getTime() % duration !== 0 ||
      new Date(endIso).getTime() % duration !== 0
    )
      return setError("开始和结束时间必须对齐数据集周期");
    if (
      new Date(startIso) >= new Date(endIso) ||
      new Date(startIso) < new Date(dataset.earliestOpenTime) ||
      new Date(endIso).getTime() >
        new Date(dataset.latestOpenTime).getTime() + duration
    )
      return setError("时间范围必须有效且处于数据集范围内");
    if (!expectedBars || expectedBars < (strategy.minimumRequiredBars || 1))
      return setError(
        `预计 K 线数不足，至少需要 ${strategy.minimumRequiredBars} 根`,
      );
    if (invalidParameter)
      return setError(
        `${invalidParameter.name} 必须为 ${invalidParameter.minValue}～${invalidParameter.maxValue} 的整数`,
      );
    if (!normalizedOrderAmount || !isPositiveDecimal(normalizedOrderAmount))
      return setError(
        "数量必须是大于 0、20 位整数且最多 18 位小数的十进制字符串",
      );
    if (
      !normalizedFeeRate ||
      compareDecimalStrings(normalizedFeeRate, "0") < 0 ||
      compareDecimalStrings(normalizedFeeRate, "0.01") > 0
    )
      return setError("手续费必须是 0～0.01、最多 18 位小数的十进制字符串");

    savingRef.current = true;
    const controller = new AbortController();
    requestRef.current = controller;
    setSaving(true);
    onSavingChange(true);
    try {
      const run = await createBacktestRun({
        datasetId: Number(dataset.id),
        startOpenTimeInclusive: startIso,
        endOpenTimeExclusive: endIso,
        strategyCode: strategy.code,
        strategyVersion: strategy.version,
        ...executionContextPayload(contextResult.profile),
        orderAmount: normalizedOrderAmount,
        feeRate: normalizedFeeRate,
        strategyParameters: Object.fromEntries(
          Object.entries(params).map(([key, value]) => [key, Number(value)]),
        ),
        forceCloseAtEnd: true,
      }, controller.signal);
      if (!mountedRef.current || controller.signal.aborted) return;
      await onCreated(run);
      if (!mountedRef.current || controller.signal.aborted) return;
      onClose();
    } catch (exception) {
      if (exception.name !== "AbortError" && !controller.signal.aborted)
        setError(exception.message || "创建回测失败");
    } finally {
      savingRef.current = false;
      if (requestRef.current === controller) requestRef.current = null;
      if (mountedRef.current) {
        setSaving(false);
        onSavingChange(false);
      }
    }
  };

  const requestedStrategyUnavailable =
    initialStrategyCode &&
    strategies.length > 0 &&
    (!strategies.some((item) => item.code === initialStrategyCode) ||
      (dataset && !strategy));

  return (
    <aside
      className="backtest-create-panel"
      role="dialog"
      aria-modal="true"
      aria-label="新建回测"
    >
      <div className="backtest-panel-head">
        <div>
          <span className="eyebrow">NEW EXPERIMENT</span>
          <h3>新建回测</h3>
        </div>
        <button
          type="button"
          aria-label="关闭新建回测"
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
            {formatInstant(dataset.latestOpenTime)} · 已校验 · 结束时间不包含
          </p>
        )}
        {requestedStrategyUnavailable && (
          <p className="strategy-unavailable" role="alert">
            指定策略当前不可用
          </p>
        )}
        {strategy?.parameters?.map((parameter) => (
          <label key={parameter.name}>
            {parameter.name}
            <input
              aria-label={parameter.name}
              type="number"
              step="1"
              value={params[parameter.name] || ""}
              min={parameter.minValue}
              max={parameter.maxValue}
              onChange={(event) =>
                setParams((current) => ({
                  ...current,
                  [parameter.name]: event.target.value,
                }))
              }
            />
            <small>
              默认 {parameter.defaultValue} · 范围 {parameter.minValue}～
              {parameter.maxValue}
            </small>
          </label>
        ))}
        <div className="backtest-form-grid">
          <label>
            开始时间
            <input
              aria-label="开始时间"
              type="datetime-local"
              value={start}
              onChange={(event) => setStart(event.target.value)}
            />
          </label>
          <label>
            结束时间（不包含）
            <input
              aria-label="结束时间（不包含）"
              type="datetime-local"
              value={end}
              onChange={(event) => setEnd(event.target.value)}
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
            <small>例如 0.001 表示 0.1%</small>
          </label>
        </div>
        <p className="backtest-help">
          提交时按 UTC 绝对时间保存。预计 K 线：{expectedBars ?? "—"} 根
        </p>
        <p className="backtest-help">
          固定强制平仓：结束仍持仓时按最后一根 K 线收盘价平仓。
        </p>
        {error && (
          <div className="backtest-inline-error" role="alert">
            {error}
          </div>
        )}
        <button
          className="quant-primary-action"
          type="submit"
          disabled={
            saving ||
            !strategies.length ||
            !datasets.length ||
            !executionProfiles.length
          }
        >
          {saving ? "正在创建" : "创建异步回测"}
        </button>
      </form>
    </aside>
  );
}

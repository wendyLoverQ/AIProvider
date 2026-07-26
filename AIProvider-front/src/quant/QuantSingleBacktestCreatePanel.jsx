import { useEffect, useRef, useState } from "react";
import { X } from "@phosphor-icons/react";
import {
  calculateExpectedBars,
  compareDecimalStrings,
  formatInstant,
  intervalCode,
  intervalDurationMs,
  isPositiveDecimal,
  normalizeDecimalString,
  toUtcIso,
  utcInstantToLocalInput,
} from "./quantBacktestsFormat";
import { createBacktestRun } from "./quantBacktestsApi";

export default function QuantSingleBacktestCreatePanel({
  strategies,
  datasets,
  initialStrategyCode = "",
  onClose,
  onCreated,
}) {
  const initialStrategy = strategies.find(
    (item) => item.code === initialStrategyCode,
  );
  const [datasetId, setDatasetId] = useState("");
  const [strategyCode, setStrategyCode] = useState(initialStrategy?.code || "");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [params, setParams] = useState(() =>
    Object.fromEntries(
      (initialStrategy?.parameters || []).map((parameter) => [
        parameter.name,
        String(parameter.defaultValue),
      ]),
    ),
  );
  const [orderAmount, setOrderAmount] = useState("1");
  const [feeRate, setFeeRate] = useState("0");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const dataset = datasets.find(
    (item) => String(item.id) === String(datasetId),
  );
  const strategy = strategies.find((item) => item.code === strategyCode);

  useEffect(() => {
    const close = (event) => {
      if (event.key === "Escape" && !savingRef.current) onClose();
    };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [onClose]);

  useEffect(() => {
    if (!initialStrategyCode) return;
    const selected = strategies.find(
      (item) => item.code === initialStrategyCode,
    );
    setStrategyCode(selected?.code || "");
    setParams(
      Object.fromEntries(
        (selected?.parameters || []).map((parameter) => [
          parameter.name,
          String(parameter.defaultValue),
        ]),
      ),
    );
  }, [initialStrategyCode, strategies]);

  const selectDataset = (value) => {
    const selected = datasets.find((item) => String(item.id) === String(value));
    setDatasetId(value);
    if (!selected) return;
    const duration = intervalDurationMs(selected.interval);
    setStart(utcInstantToLocalInput(selected.earliestOpenTime));
    setEnd(
      duration
        ? utcInstantToLocalInput(
            new Date(
              new Date(selected.latestOpenTime).getTime() + duration,
            ).toISOString(),
          )
        : "",
    );
  };

  const selectStrategy = (value) => {
    const selected = strategies.find((item) => item.code === value);
    setStrategyCode(value);
    setParams(
      Object.fromEntries(
        (selected?.parameters || []).map((parameter) => [
          parameter.name,
          String(parameter.defaultValue),
        ]),
      ),
    );
  };

  const expectedBars = dataset
    ? calculateExpectedBars(toUtcIso(start), toUtcIso(end), dataset.interval)
    : null;

  const submit = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    setError("");
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
    if (!dataset || !strategy || !startIso || !endIso)
      return setError("请完整选择数据集、策略和时间范围");
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
    setSaving(true);
    try {
      const run = await createBacktestRun({
        datasetId: Number(dataset.id),
        startOpenTimeInclusive: startIso,
        endOpenTimeExclusive: endIso,
        strategyCode: strategy.code,
        strategyVersion: strategy.version,
        orderAmount: normalizedOrderAmount,
        feeRate: normalizedFeeRate,
        strategyParameters: Object.fromEntries(
          Object.entries(params).map(([key, value]) => [key, Number(value)]),
        ),
        forceCloseAtEnd: true,
      });
      await onCreated(run);
      onClose();
    } catch (exception) {
      setError(exception.message || "创建回测失败");
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const requestedStrategyUnavailable =
    initialStrategyCode && strategies.length > 0 && !strategy;

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
        <label>
          连续历史数据集
          <select
            autoFocus
            aria-label="连续历史数据集"
            value={datasetId}
            onChange={(event) => selectDataset(event.target.value)}
          >
            <option value="">请选择已校验数据集</option>
            {datasets.map((item) => (
              <option key={item.id} value={item.id}>
                {item.symbol} · {intervalCode(item.interval)} ·{" "}
                {Number(item.candleCount).toLocaleString()} 根
              </option>
            ))}
          </select>
        </label>
        {dataset && (
          <p className="backtest-help">
            {formatInstant(dataset.earliestOpenTime)} ～{" "}
            {formatInstant(dataset.latestOpenTime)} · 已校验 · 结束时间不包含
          </p>
        )}
        <label>
          策略
          <select
            aria-label="策略"
            value={strategyCode}
            onChange={(event) => selectStrategy(event.target.value)}
          >
            <option value="">请选择策略</option>
            {strategies.map((item) => (
              <option key={item.code} value={item.code}>
                {item.name} · {item.version}
              </option>
            ))}
          </select>
        </label>
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
            下单数量
            <input
              aria-label="下单数量"
              inputMode="decimal"
              value={orderAmount}
              onChange={(event) => setOrderAmount(event.target.value)}
            />
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
          disabled={saving || !strategies.length || !datasets.length}
        >
          {saving ? "正在创建" : "创建异步回测"}
        </button>
      </form>
    </aside>
  );
}

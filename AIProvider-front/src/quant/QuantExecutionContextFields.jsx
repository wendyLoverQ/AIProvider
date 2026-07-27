import { useEffect, useMemo } from "react";
import {
  compatibleDatasets,
  compatibleProfiles,
  compatibleStrategies,
  executionSelectionEqual,
  formatDirectionMode,
  formatMarketType,
  formatOrderSide,
  formatOrderSizingMode,
  formatPositionSide,
  marketTypesFromDatasets,
  resolveExecutionSelection,
} from "./quantExecutionContext";
import { formatInstant, intervalCode } from "./quantBacktestsFormat";

const errorId = (field) => `quant-execution-${field}-error`;

function FieldError({ field, message }) {
  return message ? (
    <small id={errorId(field)} className="field-error">
      {message}
    </small>
  ) : null;
}

export default function QuantExecutionContextFields({
  datasets = [],
  strategies = [],
  executionProfiles = [],
  value,
  onChange,
  disabled = false,
  errors = {},
  autoFocus = false,
}) {
  const resolved = useMemo(
    () =>
      resolveExecutionSelection({
        datasets,
        strategies,
        profiles: executionProfiles,
        value,
      }),
    [datasets, executionProfiles, strategies, value],
  );
  useEffect(() => {
    if (!executionSelectionEqual(resolved, value)) onChange(resolved);
  }, [onChange, resolved, value]);

  const marketTypes = marketTypesFromDatasets(datasets);
  const availableDatasets = compatibleDatasets(datasets, resolved.marketType);
  const dataset = availableDatasets.find(
    (item) => String(item.id) === String(resolved.datasetId),
  );
  const availableStrategies = compatibleStrategies(strategies, dataset);
  const strategy = availableStrategies.find(
    (item) => item.code === resolved.strategyCode,
  );
  const availableProfiles = compatibleProfiles(
    executionProfiles,
    dataset,
    strategy,
  );
  const profile = availableProfiles.find(
    (item) => item.code === resolved.executionProfileCode,
  );
  const change = (patch) =>
    onChange(
      resolveExecutionSelection({
        datasets,
        strategies,
        profiles: executionProfiles,
        value: { ...resolved, ...patch },
      }),
    );

  return (
    <fieldset className="quant-execution-context" disabled={disabled}>
      <legend>市场与执行上下文</legend>
      <label>
        市场类型
        <select
          disabled={disabled}
          autoFocus={autoFocus}
          aria-label="市场类型"
          aria-describedby={
            errors.marketType
              ? errorId("marketType")
              : "quant-execution-market-help"
          }
          value={resolved.marketType}
          onChange={(event) =>
            change({
              marketType: event.target.value,
              datasetId: "",
              strategyCode: "",
              executionProfileCode: "",
            })
          }
        >
          <option value="">请选择市场类型</option>
          {marketTypes.map((item) => (
            <option key={item} value={item}>
              {formatMarketType(item)}
            </option>
          ))}
        </select>
        <small id="quant-execution-market-help">
          市场类型来自数据集，不属于策略本身。
        </small>
        <FieldError field="marketType" message={errors.marketType} />
      </label>
      <label>
        数据集 / 交易对
        <select
          disabled={disabled}
          aria-label="数据集 / 交易对"
          aria-describedby={
            errors.datasetId ? errorId("datasetId") : undefined
          }
          value={resolved.datasetId}
          onChange={(event) =>
            change({
              datasetId: event.target.value,
              strategyCode: "",
              executionProfileCode: "",
            })
          }
        >
          <option value="">请选择已校验数据集</option>
          {availableDatasets.map((item) => (
            <option key={item.id} value={item.id}>
              {item.provider} · {item.symbol} · {formatMarketType(item.marketType)}
              {" · "}
              {intervalCode(item.interval || item.intervalCode)} ·{" "}
              {formatInstant(item.earliestOpenTime)}～{formatInstant(item.latestOpenTime)}
              {" · "}
              {Number(item.candleCount).toLocaleString()} 根
            </option>
          ))}
        </select>
        <FieldError field="datasetId" message={errors.datasetId} />
      </label>
      <label>
        策略
        <select
          disabled={disabled}
          aria-label="策略"
          aria-describedby={
            errors.strategyCode ? errorId("strategyCode") : undefined
          }
          value={resolved.strategyCode}
          onChange={(event) =>
            change({
              strategyCode: event.target.value,
              executionProfileCode: "",
            })
          }
        >
          <option value="">请选择兼容策略</option>
          {availableStrategies.map((item) => (
            <option key={item.code} value={item.code}>
              {item.name} · {item.version} ·{" "}
              {item.supportedDirectionModes.map(formatDirectionMode).join("/")} ·
              最低 {item.minimumRequiredBars} 根 K 线
            </option>
          ))}
        </select>
        <FieldError field="strategyCode" message={errors.strategyCode} />
      </label>
      <label>
        执行模型
        <select
          disabled={disabled}
          aria-label="执行模型"
          aria-describedby={
            errors.executionProfileCode
              ? errorId("executionProfileCode")
              : undefined
          }
          value={resolved.executionProfileCode}
          onChange={(event) =>
            change({ executionProfileCode: event.target.value })
          }
        >
          <option value="">请选择执行模型</option>
          {availableProfiles.map((item) => (
            <option key={item.code} value={item.code}>
              {item.name}
            </option>
          ))}
        </select>
        <FieldError
          field="executionProfileCode"
          message={errors.executionProfileCode}
        />
      </label>
      {profile && (
        <section
          className="quant-execution-summary"
          aria-label="执行语义摘要"
        >
          <strong>{profile.name}</strong>
          <dl>
            <div><dt>方向</dt><dd>{formatDirectionMode(profile.directionMode)}</dd></div>
            <div><dt>持仓</dt><dd>{formatPositionSide(profile.positionSide)}</dd></div>
            <div><dt>开仓</dt><dd>{formatOrderSide(profile.entryOrderSide)}</dd></div>
            <div><dt>平仓</dt><dd>{formatOrderSide(profile.exitOrderSide)}</dd></div>
            <div><dt>杠杆</dt><dd>{profile.leverage}×</dd></div>
            <div><dt>成交</dt><dd>下一根 K 线开盘</dd></div>
            <div><dt>规模</dt><dd>{formatOrderSizingMode(profile.orderSizingMode)}</dd></div>
            <div><dt>手续费</dt><dd>线性费率</dd></div>
            <div><dt>持仓成本</dt><dd>0</dd></div>
          </dl>
          <ul className="quant-execution-limitations">
            {profile.limitations.map((limitation) => (
              <li key={limitation}>{limitation}</li>
            ))}
          </ul>
        </section>
      )}
    </fieldset>
  );
}

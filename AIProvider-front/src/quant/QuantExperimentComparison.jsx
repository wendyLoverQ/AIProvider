import { useEffect, useRef, useState } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { fetchEquity } from "./quantBacktestsApi";
import {
  decimalSubtract,
  formatDecimalString,
  formatInstant,
  formatRatioString,
  validateEquityResponse,
} from "./quantBacktestsFormat";
import {
  formatDispatchStatus,
  metricDifference,
  orderedParameterEntries,
} from "./quantExperimentsFormat";

const METRICS = [
  ["总收益率", "totalReturnRatio", formatRatioString],
  ["最大回撤", "maximumDrawdownRatio", formatRatioString],
  ["Profit Factor", "profitFactor", formatDecimalString],
  ["净利润", "netProfit", formatDecimalString],
  ["胜率", "winRate", formatRatioString],
  ["交易数", "tradeCount", (value) => formatDecimalString(value, 0)],
  ["总手续费", "totalFees", formatDecimalString],
  ["买入持有收益率", "buyAndHoldReturnRatio", formatRatioString],
  ["平均交易收益率", "averageTradeReturnRatio", formatRatioString],
];

function updateBacktestsQuery(mode, runId) {
  const url = new URL(window.location.href);
  url.searchParams.set("mode", mode);
  url.searchParams.set("runId", runId);
  url.searchParams.delete("experimentId");
  url.searchParams.delete("candidatePage");
  url.searchParams.delete("candidateSort");
  url.searchParams.delete("candidateOrder");
  window.history.pushState(
    {},
    "",
    `${url.pathname}?${url.searchParams.toString()}`,
  );
  window.dispatchEvent(new PopStateEvent("popstate"));
}

function EquityChart({ title, segment, state }) {
  if (segment.status !== "COMPLETED")
    return (
      <section className="quant-equity-panel">
        <h5>{title} 权益曲线</h5>
        <div className="backtest-empty">当前状态：{segment.status}</div>
      </section>
    );
  if (state.loading)
    return (
      <section className="quant-equity-panel">
        <h5>{title} 权益曲线</h5>
        <div className="backtest-empty">正在读取权益曲线…</div>
      </section>
    );
  if (state.error)
    return (
      <section className="quant-equity-panel">
        <h5>{title} 权益曲线</h5>
        <div className="backtest-inline-error">{state.error}</div>
      </section>
    );
  const points = state.data?.points || [];
  const chartPoints = points.map((point) => ({
    ...point,
    equityReturnRatio: decimalSubtract(point.equityRatio, "1"),
  }));
  return (
    <section className="quant-equity-panel">
      <header>
        <h5>{title} 权益曲线</h5>
        <small>
          {state.data?.sampled
            ? `展示 ${points.length} / ${state.data.totalPoints} 个抽样点`
            : ""}
        </small>
      </header>
      {!chartPoints.length ? (
        <div className="backtest-empty">权益曲线不可用</div>
      ) : (
        <div
          className="quant-experiment-chart"
          aria-label={`${title} 权益和回撤曲线`}
        >
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartPoints}>
              <CartesianGrid stroke="var(--border-subtle)" />
              <XAxis
                dataKey="openTime"
                tickFormatter={(value) =>
                  new Date(value).toLocaleDateString("zh-CN")
                }
              />
              <YAxis
                tickFormatter={(value) =>
                  `${(Number(value) * 100).toFixed(0)}%`
                }
              />
              <Tooltip
                formatter={(value, name) => [
                  formatRatioString(String(value)),
                  name === "equityReturnRatio" ? "权益变化" : "回撤",
                ]}
                labelFormatter={formatInstant}
              />
              <Area
                type="monotone"
                dataKey="equityReturnRatio"
                stroke="var(--accent-primary)"
                fill="var(--accent-primary)"
                fillOpacity={0.12}
              />
              <Area
                type="monotone"
                dataKey="drawdownRatio"
                stroke="var(--accent-red)"
                fill="var(--accent-red)"
                fillOpacity={0.08}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  );
}

export default function QuantExperimentComparison({ candidate, strategy }) {
  const [curves, setCurves] = useState({
    TRAIN: { loading: false, data: null, error: "" },
    VALIDATION: { loading: false, data: null, error: "" },
  });
  const sequence = useRef(0);
  const candidateId = candidate?.candidateId;
  const trainingRunId = candidate?.training.runId;
  const trainingStatus = candidate?.training.status;
  const validationRunId = candidate?.validation.runId;
  const validationStatus = candidate?.validation.status;

  useEffect(() => {
    const current = ++sequence.current;
    const controllers = [];
    setCurves({
      TRAIN: {
        loading: trainingStatus === "COMPLETED",
        data: null,
        error: "",
      },
      VALIDATION: {
        loading: validationStatus === "COMPLETED",
        data: null,
        error: "",
      },
    });
    if (!candidateId) return undefined;
    [
      ["TRAIN", trainingRunId, trainingStatus],
      ["VALIDATION", validationRunId, validationStatus],
    ].forEach(([key, runId, status]) => {
      if (status !== "COMPLETED") return;
      const controller = new AbortController();
      controllers.push(controller);
      fetchEquity(runId, 1200, controller.signal)
        .then((data) => {
          if (!validateEquityResponse(data))
            throw new Error("权益曲线数据格式异常");
          if (current === sequence.current)
            setCurves((value) => ({
              ...value,
              [key]: { loading: false, data, error: "" },
            }));
        })
        .catch((exception) => {
          if (exception.name !== "AbortError" && current === sequence.current) {
            setCurves((value) => ({
              ...value,
              [key]: { loading: false, data: null, error: exception.message },
            }));
          }
        });
    });
    return () => controllers.forEach((controller) => controller.abort());
  }, [
    candidateId,
    trainingRunId,
    trainingStatus,
    validationRunId,
    validationStatus,
  ]);

  if (!candidate) return null;
  const train = candidate.training.metrics || {};
  const validation = candidate.validation.metrics || {};
  const returnDelta = metricDifference(
    validation.totalReturnRatio,
    train.totalReturnRatio,
  );
  const drawdownDelta = metricDifference(
    validation.maximumDrawdownRatio,
    train.maximumDrawdownRatio,
  );
  return (
    <section className="backtest-card quant-comparison">
      <header className="quant-section-head">
        <div>
          <h4>TRAIN / VALIDATION 对照</h4>
          <small className="copyable-id">{candidate.candidateId}</small>
        </div>
        <span>
          {orderedParameterEntries(candidate.parameters, strategy)
            .map(([name, value]) => `${name}=${value}`)
            .join(" · ")}
        </span>
      </header>
      <div className="backtest-table-wrap">
        <table className="quant-comparison-table">
          <thead>
            <tr>
              <th>指标</th>
              <th>TRAIN</th>
              <th>VALIDATION</th>
            </tr>
          </thead>
          <tbody>
            {METRICS.map(([label, key, formatter]) => (
              <tr key={key}>
                <th>{label}</th>
                <td>{formatter(train[key])}</td>
                <td>{formatter(validation[key])}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="quant-objective-deltas">
        <span>验证收益率 - 训练收益率：{formatRatioString(returnDelta)}</span>
        <span>
          验证最大回撤 - 训练最大回撤：{formatRatioString(drawdownDelta)}
        </span>
      </div>
      <p className="quant-dispatch-state">
        候选派发状态：{formatDispatchStatus(candidate.dispatchStatus)}
      </p>
      <div className="quant-segment-debug">
        {[
          ["TRAIN", candidate.training],
          ["VALIDATION", candidate.validation],
        ].map(([label, segment]) => (
          <section key={label}>
            <strong>{label}</strong>
            <span>状态：{segment.status}</span>
            <span className="copyable-id">runId：{segment.runId}</span>
            {segment.errorCode && (
              <span>
                {segment.errorCode} · {segment.errorMessage || "任务失败"}
              </span>
            )}
            <button
              type="button"
              onClick={() => updateBacktestsQuery("single", segment.runId)}
            >
              查看 {label} 原始任务
            </button>
          </section>
        ))}
      </div>
      <div className="quant-dual-curves">
        <EquityChart
          title="TRAIN"
          segment={candidate.training}
          state={curves.TRAIN}
        />
        <EquityChart
          title="VALIDATION"
          segment={candidate.validation}
          state={curves.VALIDATION}
        />
      </div>
    </section>
  );
}

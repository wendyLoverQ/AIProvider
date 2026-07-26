import { ArrowsClockwise, ChartLineUp, Warning } from "@phosphor-icons/react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  compareDecimalStrings,
  decimalSubtract,
  formatDecimalString,
  formatInstant,
  formatRatioString,
  formatRunStatus,
  intervalCode,
  validateEquityResponse,
} from "./quantBacktestsFormat";

function LoadState({ label }) {
  return (
    <div className="quant-loading" role="status">
      {label}
    </div>
  );
}

function ErrorState({ label, error, retry }) {
  return (
    <div className="quant-error" role="alert">
      <Warning weight="fill" />
      <div>
        <strong>{label}</strong>
        <span>{error}</span>
      </div>
      <button type="button" className="quant-error-retry" onClick={retry}>
        <ArrowsClockwise />
        重试
      </button>
    </div>
  );
}

function Metric({ label, value, tone }) {
  return (
    <div className={`backtest-metric ${tone || ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export default function QuantSingleBacktestRunDetail({
  run,
  loading,
  error,
  retry,
  equity,
  equityError,
  retryEquity,
}) {
  if (loading) return <LoadState label="正在读取任务详情…" />;
  if (error)
    return <ErrorState label="任务详情加载失败" error={error} retry={retry} />;
  if (!run) return <div className="backtest-empty">选择一个任务查看详情</div>;
  const metrics = run.metrics || {};
  const points = equity?.points || [];
  const valid = validateEquityResponse(equity);
  const chartPoints = valid
    ? points.map((point) => ({
        ...point,
        equityReturnRatio: decimalSubtract(point.equityRatio, "1"),
        drawdownValue: point.drawdownRatio,
      }))
    : [];
  const warnings = Array.isArray(run.warnings) ? run.warnings : [];

  return (
    <div className="backtest-detail">
      <section className="backtest-card">
        <header className="quant-section-head">
          <h4>任务详情</h4>
          <span
            className={`backtest-status status-${run.status?.toLowerCase()}`}
          >
            {formatRunStatus(run.status)}
          </span>
        </header>
        <dl className="backtest-detail-grid">
          {[
            ["runId", run.runId],
            ["datasetId", run.datasetId],
            [
              "交易对 / 周期",
              `${run.symbol || "—"} · ${intervalCode(run.intervalCode)}`,
            ],
            [
              "策略",
              `${run.strategyCode || "—"} · ${run.strategyVersion || "—"}`,
            ],
            ["请求参数", JSON.stringify(run.requestedParameters || {})],
            ["解析参数", JSON.stringify(run.resolvedParameters || {})],
            [
              "回测区间",
              `${formatInstant(run.startOpenTimeInclusive)} ～ ${formatInstant(run.endOpenTimeExclusive)}`,
            ],
            [
              "数量 / 手续费",
              `${run.orderAmount ?? "—"} / ${run.feeRate ?? "—"}`,
            ],
            [
              "K 线数 / 交易数",
              `${run.barCount ?? "—"} / ${run.tradeCount ?? "—"}`,
            ],
            ["成交模型", run.executionModel || "—"],
            ["阶段", run.status],
            [
              "开始 / 完成",
              `${formatInstant(run.startedAt)} / ${formatInstant(run.finishedAt)}`,
            ],
          ].map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value || "—"}</dd>
            </div>
          ))}
        </dl>
        {warnings.length > 0 && (
          <div className="backtest-warnings">
            <strong>警告</strong>
            {warnings.map((warning, index) => (
              <span key={`${warning}-${index}`}>{warning}</span>
            ))}
          </div>
        )}
        {run.status === "FAILED" && (
          <div className="backtest-failure">
            <strong>{run.errorCode || "FAILED"}</strong>
            <span>{run.errorMessage || "回测失败"}</span>
            <span>失败前进度：{run.progressPercent ?? "—"}</span>
          </div>
        )}
      </section>
      {run.status === "COMPLETED" && (
        <>
          <section className="backtest-metrics">
            {[
              [
                "总收益率",
                formatRatioString(metrics.totalReturnRatio),
                compareDecimalStrings(metrics.totalReturnRatio, "0") >= 0
                  ? "positive"
                  : "negative",
              ],
              ["最大回撤", formatRatioString(metrics.maximumDrawdownRatio)],
              ["胜率", formatRatioString(metrics.winRate)],
              [
                "交易数",
                formatDecimalString(run.tradeCount ?? metrics.tradeCount, 0),
              ],
              ["净利润", formatDecimalString(metrics.netProfit)],
              ["Profit Factor", formatDecimalString(metrics.profitFactor)],
              ["买入持有", formatRatioString(metrics.buyAndHoldReturnRatio)],
              ["总手续费", formatDecimalString(metrics.totalFees)],
            ].map(([label, value, tone]) => (
              <Metric key={label} label={label} value={value} tone={tone} />
            ))}
          </section>
          <section className="backtest-card">
            <header className="quant-section-head">
              <h4>
                <ChartLineUp />
                权益曲线
              </h4>
              <small>
                {equity?.sampled
                  ? `图表展示 ${chartPoints.length} / ${equity.totalPoints} 个抽样点，完整结果保存在服务器`
                  : ""}
              </small>
            </header>
            {equityError ? (
              <ErrorState
                label="权益曲线加载失败"
                error={equityError}
                retry={retryEquity}
              />
            ) : !valid ? (
              <div className="backtest-empty">权益曲线数据格式异常</div>
            ) : chartPoints.length ? (
              <div className="backtest-chart" aria-label="权益和回撤曲线">
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
                      dataKey="drawdownValue"
                      stroke="var(--accent-red)"
                      fill="var(--accent-red)"
                      fillOpacity={0.08}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div className="backtest-empty">权益曲线不可用</div>
            )}
          </section>
        </>
      )}
    </div>
  );
}

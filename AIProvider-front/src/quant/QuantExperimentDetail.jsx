import { formatInstant, intervalCode } from "./quantBacktestsFormat";
import {
  formatExperimentStatus,
  orderedParameterEntries,
} from "./quantExperimentsFormat";
import {
  formatDirectionMode,
  formatOrderSizingMode,
} from "./quantExecutionContext";

export default function QuantExperimentDetail({
  experiment,
  strategy,
  executionProfile,
  loading,
  error,
  onRetry,
}) {
  if (loading)
    return (
      <section className="backtest-card backtest-empty">
        正在读取实验详情…
      </section>
    );
  if (error)
    return (
      <section className="backtest-card">
        <div className="backtest-inline-error" role="alert">
          {error}
        </div>
        <button
          type="button"
          className="quant-secondary-action"
          onClick={onRetry}
        >
          重试
        </button>
      </section>
    );
  if (!experiment)
    return (
      <section className="backtest-card backtest-empty">
        请选择一个参数实验
      </section>
    );
  const gridEntries = orderedParameterEntries(
    experiment.parameterGrid,
    strategy,
  );
  return (
    <section className="backtest-card quant-experiment-detail">
      <header className="quant-section-head">
        <div>
          <h4>实验详情</h4>
          <small className="copyable-id">{experiment.experimentId}</small>
        </div>
        <span
          className={`backtest-status status-${experiment.status.toLowerCase()}`}
        >
          {formatExperimentStatus(experiment.status)}
        </span>
      </header>
      <dl className="backtest-detail-grid">
        <div>
          <dt>数据集</dt>
          <dd>
            {experiment.datasetId ?? "—"} · {experiment.symbol || "—"} ·{" "}
            {intervalCode(experiment.intervalCode)}
          </dd>
        </div>
        <div>
          <dt>策略</dt>
          <dd>
            {experiment.strategyCode || "—"} ·{" "}
            {experiment.strategyVersion || "—"}
          </dd>
        </div>
        <div>
          <dt>执行模型</dt>
          <dd>{executionProfile?.name || experiment.executionProfileCode}</dd>
        </div>
        <div>
          <dt>方向</dt>
          <dd>{formatDirectionMode(experiment.directionMode)}</dd>
        </div>
        <div>
          <dt>规模模式</dt>
          <dd>{formatOrderSizingMode(experiment.orderSizingMode)}</dd>
        </div>
        <div>
          <dt>候选 / 总任务</dt>
          <dd>
            {experiment.candidateCount} /{" "}
            {experiment.totalLegs ?? experiment.candidateCount * 2}
          </dd>
        </div>
        <div>
          <dt>基础资产数量 / 手续费</dt>
          <dd>
            {experiment.orderAmount ?? "—"} / {experiment.feeRate ?? "—"}
          </dd>
        </div>
        <div>
          <dt>结束强制平仓</dt>
          <dd>{experiment.forceCloseAtEnd ? "是" : "否"}</dd>
        </div>
        <div>
          <dt>TRAIN 区间</dt>
          <dd>
            {formatInstant(experiment.trainingStartOpenTimeInclusive)} ～{" "}
            {formatInstant(experiment.trainingEndOpenTimeExclusive)}
          </dd>
        </div>
        <div>
          <dt>VALIDATION 区间</dt>
          <dd>
            {formatInstant(experiment.validationStartOpenTimeInclusive)} ～{" "}
            {formatInstant(experiment.validationEndOpenTimeExclusive)}
          </dd>
        </div>
        <div>
          <dt>候选状态</dt>
          <dd>
            待处理 {experiment.pendingCandidates} · 活跃{" "}
            {experiment.activeCandidates} · 完成{" "}
            {experiment.completedCandidates} · 失败{" "}
            {experiment.failedCandidates}
          </dd>
        </div>
        <div>
          <dt>任务状态</dt>
          <dd>
            完成 {experiment.completedLegs} · 失败 {experiment.failedLegs}
          </dd>
        </div>
        <div>
          <dt>状态 / 进度</dt>
          <dd>
            {formatExperimentStatus(experiment.status)} ·{" "}
            {experiment.progressPercent}%
          </dd>
        </div>
        <div>
          <dt>创建 / 开始 / 完成</dt>
          <dd>
            {formatInstant(experiment.createdAt)} /{" "}
            {formatInstant(experiment.startedAt)} /{" "}
            {formatInstant(experiment.finishedAt)}
          </dd>
        </div>
      </dl>
      <div className="quant-parameter-grid">
        <strong>参数网格</strong>
        {gridEntries.map(([name, values]) => (
          <span key={name}>
            <b>{name}</b>
            {values.join(", ")}
          </span>
        ))}
      </div>
      {(experiment.errorCode || experiment.errorMessage) && (
        <div className="backtest-failure">
          <strong>{experiment.errorCode || "FAILED"}</strong>
          <span>{experiment.errorMessage || "实验失败"}</span>
        </div>
      )}
      <p className="quant-research-warning">
        训练集表现用于参数研究；验证集是未参与该组合筛选的样本外区间。两者都不代表未来收益。
      </p>
    </section>
  );
}

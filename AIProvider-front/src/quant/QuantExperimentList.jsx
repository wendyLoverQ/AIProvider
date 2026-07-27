import { CaretLeft, CaretRight } from "@phosphor-icons/react";
import UiSearchField from "../UiSearchField";
import { formatInstant, intervalCode } from "./quantBacktestsFormat";
import {
  EXPERIMENT_STATUS_LABELS,
  formatExperimentStatus,
} from "./quantExperimentsFormat";
import {
  formatDirectionMode,
  formatOrderSizingMode,
} from "./quantExecutionContext";

export default function QuantExperimentList({
  page,
  data,
  filters,
  selectedId,
  loading,
  executionProfiles = [],
  onFilters,
  onPage,
  onSelect,
}) {
  const totalPages = Math.max(1, Math.ceil((data.total || 0) / 20));
  return (
    <section className="backtest-card quant-experiment-list">
      <header className="quant-section-head">
        <h4>参数实验</h4>
        <small>{data.total || 0} 条</small>
      </header>
      <div className="quant-experiment-filters">
        <select
          aria-label="实验状态筛选"
          value={filters.status}
          onChange={(event) =>
            onFilters({ ...filters, status: event.target.value })
          }
        >
          <option value="">全部状态</option>
          {Object.entries(EXPERIMENT_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <UiSearchField
          aria-label="交易对筛选"
          value={filters.symbol}
          onChange={(event) =>
            onFilters({ ...filters, symbol: event.target.value })
          }
          placeholder="交易对"
        />
        <UiSearchField
          aria-label="策略代码筛选"
          value={filters.strategyCode}
          onChange={(event) =>
            onFilters({ ...filters, strategyCode: event.target.value })
          }
          placeholder="策略代码"
        />
      </div>
      {loading && <div className="backtest-empty">正在读取参数实验…</div>}
      {!loading && !data.records.length && (
        <div className="backtest-empty">当前没有参数实验</div>
      )}
      {!loading &&
        data.records.map((experiment) => (
          <button
            type="button"
            className={`quant-experiment-row ${experiment.experimentId === selectedId ? "selected" : ""}`}
            key={experiment.experimentId}
            onClick={() => onSelect(experiment.experimentId)}
          >
            <span
              className={`backtest-status status-${experiment.status.toLowerCase()}`}
            >
              {formatExperimentStatus(experiment.status)}
            </span>
            <strong>
              {experiment.symbol || "—"} ·{" "}
              {intervalCode(experiment.intervalCode)}
            </strong>
            <span>
              {experiment.strategyCode || "—"} ·{" "}
              {experiment.strategyVersion || "—"}
            </span>
            <span>
              {executionProfiles.find(
                (profile) =>
                  profile.code === experiment.executionProfileCode,
              )?.name ||
                experiment.executionProfileCode ||
                "—"}{" "}
              ·{" "}
              {formatDirectionMode(experiment.directionMode)} ·{" "}
              {formatOrderSizingMode(experiment.orderSizingMode)}
            </span>
            <span>
              候选 {experiment.candidateCount} · 完成{" "}
              {experiment.completedCandidates} · 失败{" "}
              {experiment.failedCandidates}
            </span>
            <span>
              TRAIN {formatInstant(experiment.trainingStartOpenTimeInclusive)}{" "}
              ～ {formatInstant(experiment.trainingEndOpenTimeExclusive)}
            </span>
            <span>
              VALIDATION{" "}
              {formatInstant(experiment.validationStartOpenTimeInclusive)} ～{" "}
              {formatInstant(experiment.validationEndOpenTimeExclusive)}
            </span>
            <span>
              进度 {experiment.progressPercent}% · 创建{" "}
              {formatInstant(experiment.createdAt)}
            </span>
          </button>
        ))}
      <div className="backtest-pagination">
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => onPage(page - 1)}
        >
          <CaretLeft />
          上一页
        </button>
        <span>
          第 {page} / {totalPages} 页
        </span>
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          下一页
          <CaretRight />
        </button>
      </div>
    </section>
  );
}

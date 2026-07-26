import { CaretLeft, CaretRight } from "@phosphor-icons/react";
import { formatDecimalString, formatRatioString } from "./quantBacktestsFormat";
import {
  CANDIDATE_SORTS,
  formatDispatchStatus,
  orderedParameterEntries,
} from "./quantExperimentsFormat";

function segmentMetric(segment, key, formatter) {
  return segment.status === "COMPLETED"
    ? formatter(segment.metrics?.[key])
    : "—";
}

export default function QuantExperimentCandidates({
  data,
  page,
  sortBy,
  order,
  strategy,
  loading,
  error,
  selectedId,
  onPage,
  onSort,
  onSelect,
  onRetry,
}) {
  const totalPages = Math.max(1, Math.ceil((data.total || 0) / 50));
  const toggleSort = (value) =>
    onSort(value, value === sortBy && order === "ASC" ? "DESC" : "ASC");
  return (
    <section className="backtest-card quant-candidate-section">
      <header className="quant-section-head">
        <div>
          <h4>候选结果</h4>
          <small>{data.total || 0} 个参数组合</small>
        </div>
        <label className="candidate-sort-control">
          排序
          <select
            aria-label="候选排序字段"
            value={sortBy}
            onChange={(event) => onSort(event.target.value, order)}
          >
            {CANDIDATE_SORTS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => onSort(sortBy, order === "ASC" ? "DESC" : "ASC")}
          >
            {order === "ASC" ? "升序" : "降序"}
          </button>
        </label>
      </header>
      {error && (
        <div className="backtest-inline-error" role="alert">
          {error}
          <button type="button" onClick={onRetry}>
            重试
          </button>
        </div>
      )}
      {loading && <div className="backtest-empty">正在读取候选结果…</div>}
      {!loading && !error && (
        <div className="backtest-table-wrap">
          <table className="quant-candidate-table">
            <thead>
              <tr>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("CANDIDATE_INDEX")}
                  >
                    序号
                  </button>
                </th>
                <th>参数组合</th>
                <th>TRAIN 状态</th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("TRAIN_TOTAL_RETURN_RATIO")}
                  >
                    TRAIN 总收益率
                  </button>
                </th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("TRAIN_MAXIMUM_DRAWDOWN_RATIO")}
                  >
                    TRAIN 最大回撤
                  </button>
                </th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("TRAIN_PROFIT_FACTOR")}
                  >
                    TRAIN Profit Factor
                  </button>
                </th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("TRAIN_TRADE_COUNT")}
                  >
                    TRAIN 交易数
                  </button>
                </th>
                <th>VALIDATION 状态</th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("VALIDATION_TOTAL_RETURN_RATIO")}
                  >
                    VALIDATION 总收益率
                  </button>
                </th>
                <th>
                  <button
                    type="button"
                    onClick={() =>
                      toggleSort("VALIDATION_MAXIMUM_DRAWDOWN_RATIO")
                    }
                  >
                    VALIDATION 最大回撤
                  </button>
                </th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("VALIDATION_PROFIT_FACTOR")}
                  >
                    VALIDATION Profit Factor
                  </button>
                </th>
                <th>
                  <button
                    type="button"
                    onClick={() => toggleSort("VALIDATION_TRADE_COUNT")}
                  >
                    VALIDATION 交易数
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.records.map((candidate) => (
                <tr
                  key={candidate.candidateId}
                  className={
                    candidate.candidateId === selectedId ? "selected" : ""
                  }
                >
                  <td>
                    <button
                      type="button"
                      className="candidate-select-button"
                      aria-label={`查看候选 ${candidate.candidateIndex}`}
                      onClick={() => onSelect(candidate)}
                    >
                      {candidate.candidateIndex}
                    </button>
                  </td>
                  <td className="candidate-parameters">
                    {orderedParameterEntries(candidate.parameters, strategy)
                      .map(([name, value]) => `${name}=${value}`)
                      .join(" · ")}
                  </td>
                  <td>
                    {candidate.training.status} ·{" "}
                    {formatDispatchStatus(candidate.dispatchStatus)}
                  </td>
                  <td>
                    {segmentMetric(
                      candidate.training,
                      "totalReturnRatio",
                      formatRatioString,
                    )}
                  </td>
                  <td>
                    {segmentMetric(
                      candidate.training,
                      "maximumDrawdownRatio",
                      formatRatioString,
                    )}
                  </td>
                  <td>
                    {segmentMetric(
                      candidate.training,
                      "profitFactor",
                      formatDecimalString,
                    )}
                  </td>
                  <td>
                    {segmentMetric(candidate.training, "tradeCount", (value) =>
                      formatDecimalString(value, 0),
                    )}
                  </td>
                  <td>{candidate.validation.status}</td>
                  <td>
                    {segmentMetric(
                      candidate.validation,
                      "totalReturnRatio",
                      formatRatioString,
                    )}
                  </td>
                  <td>
                    {segmentMetric(
                      candidate.validation,
                      "maximumDrawdownRatio",
                      formatRatioString,
                    )}
                  </td>
                  <td>
                    {segmentMetric(
                      candidate.validation,
                      "profitFactor",
                      formatDecimalString,
                    )}
                  </td>
                  <td>
                    {segmentMetric(
                      candidate.validation,
                      "tradeCount",
                      (value) => formatDecimalString(value, 0),
                    )}
                  </td>
                </tr>
              ))}
              {!data.records.length && (
                <tr>
                  <td colSpan="12" className="backtest-empty">
                    当前页没有候选结果
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
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

import { CaretLeft, CaretRight } from "@phosphor-icons/react";
import UiSearchField from "../UiSearchField";
import { formatInstant, intervalCode } from "./quantBacktestsFormat";
import { WALK_FORWARD_STUDY_STATUS_LABELS } from "./quantWalkForwardFormat";

export default function QuantWalkForwardStudyList({ page, data, filters, loading, selectedId, onFilters, onPage, onSelect }) {
  const totalPages = Math.max(1, Math.ceil((data.total || 0) / 20));
  return <section className="backtest-card quant-walk-forward-list"><header className="quant-section-head"><h4>滚动验证 Study</h4><small>{data.total || 0} 条</small></header>
    <div className="quant-experiment-filters"><select aria-label="滚动验证状态筛选" value={filters.status} onChange={(event) => onFilters({ ...filters, status: event.target.value })}><option value="">全部状态</option>{Object.entries(WALK_FORWARD_STUDY_STATUS_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select><UiSearchField aria-label="滚动验证交易对筛选" placeholder="交易对" value={filters.symbol} onChange={(event) => onFilters({ ...filters, symbol: event.target.value })} /><UiSearchField aria-label="滚动验证策略筛选" placeholder="策略代码" value={filters.strategyCode} onChange={(event) => onFilters({ ...filters, strategyCode: event.target.value })} /></div>
    {loading && <div className="backtest-empty">正在读取滚动验证…</div>}{!loading && !data.records.length && <div className="backtest-empty">当前没有滚动验证 Study</div>}
    {!loading && data.records.map((study) => <button type="button" className={`quant-walk-forward-study-row ${study.studyId === selectedId ? "selected" : ""}`} key={study.studyId} onClick={() => onSelect(study.studyId)}><span className={`backtest-status status-${study.status.toLowerCase()}`}>{WALK_FORWARD_STUDY_STATUS_LABELS[study.status]}</span><strong>{study.symbol} · {intervalCode(study.intervalCode)}</strong><span>{study.strategyCode} · {study.strategyVersion}</span><span>ROLLING · {study.trainingBars}/{study.validationBars} 根 · {study.foldCount} Fold</span><span>候选 {study.candidateCountPerFold} · 任务 {study.totalChildRuns}</span><span>{study.selectionMetric} · {study.completedFolds} 完成 / {study.failedFolds} 失败</span><span>进度 {study.progressPercent}% · {formatInstant(study.createdAt)}</span></button>)}
    <div className="backtest-pagination"><button type="button" disabled={page <= 1} onClick={() => onPage(page - 1)}><CaretLeft />上一页</button><span>第 {page} / {totalPages} 页</span><button type="button" disabled={page >= totalPages} onClick={() => onPage(page + 1)}>下一页<CaretRight /></button></div>
  </section>;
}

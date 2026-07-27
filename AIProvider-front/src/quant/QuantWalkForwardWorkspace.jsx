import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowsClockwise, Flask, Warning } from "@phosphor-icons/react";
import QuantPageScaffold from "./QuantPageScaffold";
import QuantWalkForwardCreatePanel from "./QuantWalkForwardCreatePanel";
import QuantWalkForwardStudyList from "./QuantWalkForwardStudyList";
import QuantWalkForwardStudyDetail from "./QuantWalkForwardStudyDetail";
import QuantWalkForwardFolds from "./QuantWalkForwardFolds";
import QuantWalkForwardParameterFrequency from "./QuantWalkForwardParameterFrequency";
import QuantWalkForwardOosChart from "./QuantWalkForwardOosChart";
import { fetchDatasets, fetchExecutionProfiles, fetchStrategies } from "./quantBacktestsApi";
import { fetchWalkForwardFolds, fetchWalkForwardOosEquity, fetchWalkForwardStudies, fetchWalkForwardStudy } from "./quantWalkForwardApi";
import "./QuantWalkForward.css";

const NON_TERMINAL = new Set(["QUEUED", "RUNNING"]);
const TERMINAL = new Set(["COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED"]);

export function readRoute() {
  const params = new URLSearchParams(window.location.search);
  const page = Number(params.get("foldPage"));
  return {
    studyId: params.get("studyId") || "",
    foldPage: Number.isSafeInteger(page) && page > 0 ? page : 1,
    foldId: params.get("foldId") || "",
  };
}

function routeUrl(values) {
  const url = new URL(window.location.href);
  url.searchParams.set("mode", "walk-forward");
  ["runId", "openCreate", "strategyCode", "experimentId", "candidatePage", "candidateSort", "candidateOrder"].forEach((key) => url.searchParams.delete(key));
  if (values.studyId) url.searchParams.set("studyId", values.studyId); else url.searchParams.delete("studyId");
  if (values.foldPage && values.foldPage > 1) url.searchParams.set("foldPage", String(values.foldPage)); else url.searchParams.delete("foldPage");
  if (values.foldId) url.searchParams.set("foldId", values.foldId); else url.searchParams.delete("foldId");
  return `${url.pathname}${url.searchParams.toString() ? `?${url.searchParams.toString()}` : ""}`;
}

export function writeRoute(values, replace = false) {
  window.history[replace ? "replaceState" : "pushState"]({}, "", routeUrl(values));
  window.dispatchEvent(new PopStateEvent("popstate"));
}

function jump(mode, key, value) {
  const url = new URL(window.location.href);
  url.searchParams.set("mode", mode);
  ["studyId", "foldPage", "foldId", "experimentId", "candidatePage", "candidateSort", "candidateOrder", "runId", "openCreate", "strategyCode"].forEach((item) => url.searchParams.delete(item));
  url.searchParams.set(key, value);
  window.history.pushState({}, "", `${url.pathname}?${url.searchParams.toString()}`);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

const validDataset = (item) => item && Number.isSafeInteger(Number(item.id)) && item.status === "CONTIGUOUS" && item.gapCount === 0 && item.gapSegmentCount === 0 && item.earliestOpenTime && item.latestOpenTime && item.lastValidatedAt && Number(item.candleCount) > 0;

export default function QuantWalkForwardWorkspace() {
  const initial = useRef(readRoute()).current;
  const [strategies, setStrategies] = useState([]);
  const [datasets, setDatasets] = useState([]);
  const [executionProfiles, setExecutionProfiles] = useState([]);
  const [filters, setFilters] = useState({ status: "", symbol: "", strategyCode: "" });
  const [listPage, setListPage] = useState(1);
  const [listData, setListData] = useState({ records: [], total: 0, page: 1, pageSize: 20 });
  const [studyId, setStudyId] = useState(initial.studyId);
  const [foldPage, setFoldPage] = useState(initial.foldPage);
  const [selectedFoldId, setSelectedFoldId] = useState(initial.foldId);
  const [detail, setDetail] = useState(null);
  const [foldData, setFoldData] = useState({ records: [], total: 0, page: 1, pageSize: 50 });
  const [oos, setOos] = useState(null);
  const [loading, setLoading] = useState({ shared: true, list: true, detail: false, folds: false, oos: false });
  const [errors, setErrors] = useState({});
  const [refreshing, setRefreshing] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [visible, setVisible] = useState(() => document.visibilityState === "visible");
  const openCreateButtonRef = useRef(null);
  const aborts = useRef({});
  const sequence = useRef({ shared: 0, list: 0, detail: 0, folds: 0, oos: 0 });
  const selectedStudyRef = useRef(initial.studyId);
  const polling = useRef(false);
  const oosLoadingKeyRef = useRef("");
  const oosLoadedKeyRef = useRef("");
  const pollingGeneration = useRef(0);

  const abort = useCallback((type) => aborts.current[type]?.abort(), []);
  const closeCreateByUser = useCallback(() => {
    if (creating) return;
    setShowCreate(false);
    requestAnimationFrame(() => openCreateButtonRef.current?.focus());
  }, [creating]);

  const loadShared = useCallback(async () => {
    abort("shared");
    const controller = new AbortController();
    aborts.current.shared = controller;
    const current = ++sequence.current.shared;
    setLoading((value) => ({ ...value, shared: true }));
    setErrors((value) => ({ ...value, strategies: "", datasets: "", profiles: "" }));
    const [strategyResult, datasetResult, profileResult] = await Promise.allSettled([fetchStrategies(controller.signal), fetchDatasets(controller.signal), fetchExecutionProfiles(controller.signal)]);
    if (current !== sequence.current.shared) return;
    if (strategyResult.status === "fulfilled") setStrategies(strategyResult.value); else if (strategyResult.reason?.name !== "AbortError") setErrors((value) => ({ ...value, strategies: strategyResult.reason.message }));
    if (datasetResult.status === "fulfilled") setDatasets(datasetResult.value.filter(validDataset)); else if (datasetResult.reason?.name !== "AbortError") setErrors((value) => ({ ...value, datasets: datasetResult.reason.message }));
    if (profileResult.status === "fulfilled") setExecutionProfiles(profileResult.value); else if (profileResult.reason?.name !== "AbortError") setErrors((value) => ({ ...value, profiles: profileResult.reason.message }));
    setLoading((value) => ({ ...value, shared: false }));
  }, [abort]);

  const loadList = useCallback(async (page = 1, options = {}) => {
    abort("list");
    const controller = new AbortController();
    aborts.current.list = controller;
    const current = ++sequence.current.list;
    if (!options.background) setLoading((value) => ({ ...value, list: true }));
    setErrors((value) => ({ ...value, list: "" }));
    try {
      const result = await fetchWalkForwardStudies({ ...filters, page, pageSize: 20 }, controller.signal);
      if (current !== sequence.current.list) return null;
      setListData(result); setListPage(result.page); return result;
    } catch (exception) {
      if (exception.name !== "AbortError" && current === sequence.current.list) setErrors((value) => ({ ...value, list: exception.message }));
      return null;
    } finally {
      if (current === sequence.current.list) setLoading((value) => ({ ...value, list: false }));
    }
  }, [abort, filters]);

  const loadDetail = useCallback(async (id, options = {}) => {
    if (!id) return null;
    abort("detail");
    const controller = new AbortController(); aborts.current.detail = controller;
    const current = ++sequence.current.detail;
    if (!options.background) setLoading((value) => ({ ...value, detail: true }));
    setErrors((value) => ({ ...value, detail: "" }));
    try {
      const result = await fetchWalkForwardStudy(id, controller.signal);
      if (current !== sequence.current.detail || id !== selectedStudyRef.current) return null;
      setDetail(result); return result;
    } catch (exception) {
      if (exception.name !== "AbortError" && current === sequence.current.detail && id === selectedStudyRef.current) { setDetail(null); setErrors((value) => ({ ...value, detail: exception.message })); }
      return null;
    } finally {
      if (current === sequence.current.detail) setLoading((value) => ({ ...value, detail: false }));
    }
  }, [abort]);

  const loadFolds = useCallback(async (id, page, options = {}) => {
    if (!id) return null;
    abort("folds");
    const controller = new AbortController(); aborts.current.folds = controller;
    const current = ++sequence.current.folds;
    if (!options.background) setLoading((value) => ({ ...value, folds: true }));
    setErrors((value) => ({ ...value, folds: "" }));
    try {
      const result = await fetchWalkForwardFolds(id, { page, pageSize: 50 }, controller.signal);
      if (current !== sequence.current.folds || id !== selectedStudyRef.current) return null;
      setFoldData(result);
      const routeFoldId = readRoute().foldId;
      if (routeFoldId && result.records.some((item) => item.foldId === routeFoldId)) setSelectedFoldId(routeFoldId);
      else if (routeFoldId) { setSelectedFoldId(""); writeRoute({ studyId: id, foldPage: page }, true); }
      else setSelectedFoldId("");
      return result;
    } catch (exception) {
      if (exception.name !== "AbortError" && current === sequence.current.folds && id === selectedStudyRef.current) setErrors((value) => ({ ...value, folds: exception.message }));
      return null;
    } finally {
      if (current === sequence.current.folds) setLoading((value) => ({ ...value, folds: false }));
    }
  }, [abort]);

  const loadOos = useCallback(async (id, key) => {
    if (!id) return null;
    abort("oos");
    const controller = new AbortController(); aborts.current.oos = controller;
    const current = ++sequence.current.oos;
    oosLoadingKeyRef.current = key;
    setLoading((value) => ({ ...value, oos: true })); setErrors((value) => ({ ...value, oos: "" }));
    try {
      const result = await fetchWalkForwardOosEquity(id, 1200, controller.signal);
      if (current !== sequence.current.oos || id !== selectedStudyRef.current || controller.signal.aborted) return null;
      setOos(result);
      oosLoadedKeyRef.current = key;
      oosLoadingKeyRef.current = "";
      return result;
    } catch (exception) {
      if (exception.name !== "AbortError" && current === sequence.current.oos && id === selectedStudyRef.current) setErrors((value) => ({ ...value, oos: exception.message }));
      return null;
    } finally {
      if (current === sequence.current.oos) {
        oosLoadingKeyRef.current = oosLoadingKeyRef.current === key ? "" : oosLoadingKeyRef.current;
        setLoading((value) => ({ ...value, oos: false }));
      }
    }
  }, [abort]);

  const ensureOos = useCallback(async (id, status, options = {}) => {
    if (!id || !TERMINAL.has(status)) return null;
    const key = `${id}:${status}`;
    if (oosLoadedKeyRef.current === key && !options.force) return null;
    if (oosLoadingKeyRef.current === key) return null;
    return loadOos(id, key);
  }, [loadOos]);

  useEffect(() => { loadShared(); return () => Object.values(aborts.current).forEach((controller) => controller?.abort()); }, [loadShared]);
  useEffect(() => { loadList(1); }, [filters, loadList]);
  useEffect(() => {
    selectedStudyRef.current = studyId;
    abort("oos");
    oosLoadingKeyRef.current = "";
    oosLoadedKeyRef.current = "";
    setOos(null); setSelectedFoldId("");
    setErrors((value) => ({ ...value, oos: "" }));
    if (!studyId) { setDetail(null); setFoldData({ records: [], total: 0, page: 1, pageSize: 50 }); return undefined; }
    void loadDetail(studyId).then((result) => ensureOos(studyId, result?.summary?.status));
    return undefined;
  }, [ensureOos, studyId, loadDetail, loadFolds]);
  useEffect(() => { if (studyId) loadFolds(studyId, foldPage); }, [foldPage, studyId, loadFolds]);

  const detailStatus = detail?.summary?.status;

  useEffect(() => {
    const onPop = () => { const route = readRoute(); selectedStudyRef.current = route.studyId; setStudyId(route.studyId); setFoldPage(route.foldPage); setSelectedFoldId(route.foldId); };
    window.addEventListener("popstate", onPop); return () => window.removeEventListener("popstate", onPop);
  }, []);

  useEffect(() => {
    polling.current = false;
    if (!studyId || !NON_TERMINAL.has(detailStatus) || !visible) return undefined;
    let active = true;
    const generation = ++pollingGeneration.current;
    const poll = async () => {
      if (!active || generation !== pollingGeneration.current || polling.current || document.visibilityState !== "visible") return;
      polling.current = true;
      try {
        const result = await loadDetail(studyId, { background: true });
        if (!result || !active || generation !== pollingGeneration.current || studyId !== selectedStudyRef.current) return;
        await Promise.all([loadFolds(studyId, foldPage, { background: true }), loadList(listPage, { background: true })]);
        await ensureOos(studyId, result.summary?.status);
      } finally {
        if (generation === pollingGeneration.current) polling.current = false;
      }
    };
    poll(); const timer = window.setInterval(poll, 3000);
    return () => { active = false; ++pollingGeneration.current; polling.current = false; window.clearInterval(timer); abort("detail"); abort("folds"); abort("list"); };
  }, [abort, detailStatus, ensureOos, foldPage, listPage, loadDetail, loadFolds, loadList, studyId, visible]);

  useEffect(() => {
    const onVisibility = () => {
      const next = document.visibilityState === "visible";
      setVisible(next);
      if (!next) {
        Object.values(aborts.current).forEach((controller) => controller?.abort());
        oosLoadingKeyRef.current = "";
        polling.current = false;
      } else if (selectedStudyRef.current) {
        const id = selectedStudyRef.current;
        void (async () => {
          const [, latestDetail] = await Promise.all([
            loadList(listPage, { background: true }),
            loadDetail(id, { background: true }),
            loadFolds(id, foldPage, { background: true }),
          ]);
          if (id === selectedStudyRef.current) await ensureOos(id, latestDetail?.summary?.status);
        })();
      }
    };
    document.addEventListener("visibilitychange", onVisibility); return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [ensureOos, foldPage, listPage, loadDetail, loadFolds, loadList]);

  const selectStudy = (id) => { selectedStudyRef.current = id; writeRoute({ studyId: id, foldPage: 1 }); };
  const selectFold = (fold) => writeRoute({ studyId, foldPage, foldId: fold.foldId });
  const onFoldPage = (page) => writeRoute({ studyId, foldPage: page });

  const retryDetail = async () => {
    const result = await loadDetail(studyId);
    await ensureOos(studyId, result?.summary?.status);
  };

  const refresh = async () => {
    setRefreshing(true);
    try {
      const [, , latestDetail] = await Promise.all([loadShared(), loadList(listPage), studyId ? loadDetail(studyId) : null, studyId ? loadFolds(studyId, foldPage) : null]);
      if (studyId) await ensureOos(studyId, latestDetail?.summary?.status, { force: true });
    } finally { setRefreshing(false); }
  };

  const completeCreate = async (created) => {
    selectedStudyRef.current = created.studyId;
    setCreating(false); setShowCreate(false);
    writeRoute({ studyId: created.studyId, foldPage: 1 });
    requestAnimationFrame(() => openCreateButtonRef.current?.focus());
    await Promise.all([loadList(1), loadDetail(created.studyId), loadFolds(created.studyId, 1)]);
  };

  const selectedFold = useMemo(() => foldData.records.find((fold) => fold.foldId === selectedFoldId) || null, [foldData.records, selectedFoldId]);
  const detailData = detail?.summary;
  return <QuantPageScaffold pageClass="quant-backtests-page quant-walk-forward-page"><div className="quant-workspace-head"><div><span className="eyebrow">QUANT · WALK-FORWARD LAB</span><h3>滚动验证</h3><small>按 TRAIN 指标选择参数，再观察连续样本外 VALIDATION 表现</small></div><div className="backtest-head-actions"><button type="button" className="quant-refresh" onClick={refresh} disabled={refreshing}><ArrowsClockwise className={refreshing ? "spin" : ""} />刷新</button><button ref={openCreateButtonRef} type="button" className="quant-primary-action quant-walk-forward-open" onClick={() => setShowCreate(true)}><Flask />新建滚动验证</button></div></div>{errors.strategies && <div className="backtest-notice" role="alert"><Warning />策略不可用：{errors.strategies}</div>}{errors.datasets && <div className="backtest-notice" role="alert"><Warning />数据集不可用：{errors.datasets}</div>}{errors.profiles && <div className="backtest-notice" role="alert"><Warning />执行模型不可用：{errors.profiles}</div>}{errors.list && <div className="backtest-notice" role="alert">Study 列表加载失败：{errors.list}</div>}<div className="quant-walk-forward-main"><QuantWalkForwardStudyList page={listPage} data={listData} filters={filters} loading={loading.list} selectedId={studyId} onFilters={(next) => { setFilters(next); setListPage(1); }} onPage={loadList} onSelect={selectStudy} /><QuantWalkForwardStudyDetail detail={detail} executionProfile={executionProfiles.find((item) => item.code === detailData?.executionProfileCode)} loading={loading.detail} error={errors.detail} onRetry={retryDetail} /></div>{detailData && <><QuantWalkForwardFolds data={foldData} page={foldPage} selectedFold={selectedFold} loading={loading.folds} error={errors.folds} onPage={onFoldPage} onSelect={selectFold} onOpenExperiment={(id) => jump("experiment", "experimentId", id)} onOpenRun={(id) => jump("single", "runId", id)} /><div className="quant-walk-forward-bottom"><QuantWalkForwardParameterFrequency frequencies={detail.parameterFrequencies} selectedParameterChanges={detailData.selectedParameterChanges} /><QuantWalkForwardOosChart equity={oos} loading={loading.oos} error={errors.oos} /></div></>}{showCreate && <div className="backtest-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeCreateByUser(); }}><QuantWalkForwardCreatePanel strategies={strategies} datasets={datasets} executionProfiles={executionProfiles} onClose={closeCreateByUser} onCreated={completeCreate} onSavingChange={setCreating} /></div>}</QuantPageScaffold>;
}

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowsClockwise, Flask, Warning } from "@phosphor-icons/react";
import QuantPageScaffold from "./QuantPageScaffold";
import QuantExperimentCandidates from "./QuantExperimentCandidates";
import QuantExperimentComparison from "./QuantExperimentComparison";
import QuantExperimentCreatePanel from "./QuantExperimentCreatePanel";
import QuantExperimentDetail from "./QuantExperimentDetail";
import QuantExperimentList from "./QuantExperimentList";
import {
  fetchDatasets,
  fetchExecutionProfiles,
  fetchStrategies,
} from "./quantBacktestsApi";
import {
  fetchExperiment,
  fetchExperimentCandidates,
  fetchExperiments,
} from "./quantExperimentsApi";
import { CANDIDATE_SORTS } from "./quantExperimentsFormat";

const NON_TERMINAL = new Set(["QUEUED", "RUNNING"]);
const VALID_SORTS = new Set(CANDIDATE_SORTS.map(([value]) => value));
const validDataset = (item) =>
  item &&
  Number.isSafeInteger(Number(item.id)) &&
  item.status === "CONTIGUOUS" &&
  item.gapCount === 0 &&
  item.gapSegmentCount === 0 &&
  item.earliestOpenTime &&
  item.latestOpenTime &&
  item.lastValidatedAt &&
  Number(item.candleCount) > 0;

function readCandidateRoute() {
  const params = new URLSearchParams(window.location.search);
  const page = Number(params.get("candidatePage"));
  const sort = params.get("candidateSort");
  const order = params.get("candidateOrder");
  return {
    experimentId: params.get("experimentId") || "",
    candidatePage: Number.isSafeInteger(page) && page > 0 ? page : 1,
    sortBy: VALID_SORTS.has(sort) ? sort : "CANDIDATE_INDEX",
    order: order === "DESC" ? "DESC" : "ASC",
  };
}

function writeExperimentRoute(values, replace = false) {
  const url = new URL(window.location.href);
  url.searchParams.set("mode", "experiment");
  url.searchParams.delete("runId");
  if (values.experimentId)
    url.searchParams.set("experimentId", values.experimentId);
  else url.searchParams.delete("experimentId");
  url.searchParams.set("candidatePage", String(values.candidatePage));
  url.searchParams.set("candidateSort", values.sortBy);
  url.searchParams.set("candidateOrder", values.order);
  window.history[replace ? "replaceState" : "pushState"](
    {},
    "",
    `${url.pathname}?${url.searchParams.toString()}`,
  );
}

export default function QuantExperimentWorkspace() {
  const initialRoute = useRef(readCandidateRoute()).current;
  const [strategies, setStrategies] = useState([]);
  const [datasets, setDatasets] = useState([]);
  const [executionProfiles, setExecutionProfiles] = useState([]);
  const [listPage, setListPage] = useState(1);
  const [listData, setListData] = useState({
    records: [],
    total: 0,
    page: 1,
    pageSize: 20,
  });
  const [filters, setFilters] = useState({
    status: "",
    symbol: "",
    strategyCode: "",
  });
  const [selectedId, setSelectedId] = useState(initialRoute.experimentId);
  const [detail, setDetail] = useState(null);
  const [candidatePage, setCandidatePage] = useState(
    initialRoute.candidatePage,
  );
  const [sortBy, setSortBy] = useState(initialRoute.sortBy);
  const [order, setOrder] = useState(initialRoute.order);
  const [candidateData, setCandidateData] = useState({
    records: [],
    total: 0,
    page: 1,
    pageSize: 50,
  });
  const [selectedCandidate, setSelectedCandidate] = useState(null);
  const [loading, setLoading] = useState({
    list: true,
    detail: false,
    candidates: false,
    shared: true,
  });
  const [errors, setErrors] = useState({});
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [visible, setVisible] = useState(
    () => document.visibilityState === "visible",
  );
  const createButtonRef = useRef(null);
  const aborts = useRef({});
  const sequence = useRef({ shared: 0, list: 0, detail: 0, candidates: 0 });
  const polling = useRef(false);
  const selectedIdRef = useRef(initialRoute.experimentId);
  const pollRouteRef = useRef({
    candidatePage: initialRoute.candidatePage,
    sortBy: initialRoute.sortBy,
    order: initialRoute.order,
    listPage: 1,
  });
  const loadListRef = useRef(null);
  pollRouteRef.current = { candidatePage, sortBy, order, listPage };

  const strategy = useMemo(
    () => strategies.find((item) => item.code === detail?.strategyCode),
    [detail?.strategyCode, strategies],
  );

  const loadShared = useCallback(async () => {
    aborts.current.shared?.abort();
    const controller = new AbortController();
    aborts.current.shared = controller;
    const current = ++sequence.current.shared;
    setLoading((value) => ({ ...value, shared: true }));
    setErrors((value) => ({
      ...value,
      strategies: "",
      datasets: "",
      profiles: "",
    }));
    const [strategyResult, datasetResult, profileResult] = await Promise.allSettled([
      fetchStrategies(controller.signal),
      fetchDatasets(controller.signal),
      fetchExecutionProfiles(controller.signal),
    ]);
    if (current !== sequence.current.shared || controller.signal.aborted) return;
    if (strategyResult.status === "fulfilled") {
      setStrategies(strategyResult.value);
      setErrors((value) => ({ ...value, strategies: "" }));
    }
    else if (strategyResult.reason?.name !== "AbortError")
      setErrors((value) => ({
        ...value,
        strategies: strategyResult.reason.message,
      }));
    if (datasetResult.status === "fulfilled") {
      setDatasets(datasetResult.value.filter(validDataset));
      setErrors((value) => ({ ...value, datasets: "" }));
    }
    else if (datasetResult.reason?.name !== "AbortError")
      setErrors((value) => ({
        ...value,
        datasets: datasetResult.reason.message,
      }));
    if (profileResult.status === "fulfilled") {
      setExecutionProfiles(profileResult.value);
      setErrors((value) => ({ ...value, profiles: "" }));
    } else if (profileResult.reason?.name !== "AbortError")
      setErrors((value) => ({
        ...value,
        profiles: profileResult.reason.message,
      }));
    if (current === sequence.current.shared)
      setLoading((value) => ({ ...value, shared: false }));
  }, []);

  const loadList = useCallback(
    async (page, options = {}) => {
      aborts.current.list?.abort();
      const controller = new AbortController();
      aborts.current.list = controller;
      const current = ++sequence.current.list;
      if (!options.background)
        setLoading((value) => ({ ...value, list: true }));
      setErrors((value) => ({ ...value, list: "" }));
      try {
        const result = await fetchExperiments(
          { ...filters, page, pageSize: 20 },
          controller.signal,
        );
        if (current !== sequence.current.list) return;
        setListData(result);
        setListPage(result.page);
      } catch (exception) {
        if (
          exception.name !== "AbortError" &&
          current === sequence.current.list
        )
          setErrors((value) => ({ ...value, list: exception.message }));
      } finally {
        if (current === sequence.current.list)
          setLoading((value) => ({ ...value, list: false }));
      }
    },
    [filters],
  );
  loadListRef.current = loadList;

  const loadDetail = useCallback(async (experimentId, options = {}) => {
    if (!experimentId) return null;
    aborts.current.detail?.abort();
    const controller = new AbortController();
    aborts.current.detail = controller;
    const current = ++sequence.current.detail;
    if (!options.background)
      setLoading((value) => ({ ...value, detail: true }));
    setErrors((value) => ({ ...value, detail: "" }));
    try {
      const result = await fetchExperiment(experimentId, controller.signal);
      if (
        current !== sequence.current.detail ||
        experimentId !== selectedIdRef.current
      )
        return null;
      setDetail(result);
      return result;
    } catch (exception) {
      if (
        exception.name !== "AbortError" &&
        current === sequence.current.detail &&
        experimentId === selectedIdRef.current
      ) {
        setDetail(null);
        setErrors((value) => ({ ...value, detail: exception.message }));
      }
      return null;
    } finally {
      if (current === sequence.current.detail)
        setLoading((value) => ({ ...value, detail: false }));
    }
  }, []);

  const loadCandidates = useCallback(
    async (
      experimentId,
      page,
      nextSort,
      nextOrder,
      options = {},
    ) => {
      if (!experimentId) return null;
      aborts.current.candidates?.abort();
      const controller = new AbortController();
      aborts.current.candidates = controller;
      const current = ++sequence.current.candidates;
      if (!options.background)
        setLoading((value) => ({ ...value, candidates: true }));
      setErrors((value) => ({ ...value, candidates: "" }));
      try {
        const result = await fetchExperimentCandidates(
          experimentId,
          {
            page,
            pageSize: 50,
            sortBy: nextSort,
            order: nextOrder,
          },
          controller.signal,
        );
        if (
          current !== sequence.current.candidates ||
          experimentId !== selectedIdRef.current
        )
          return null;
        setCandidateData(result);
        setCandidatePage(result.page);
        setSelectedCandidate((currentCandidate) =>
          currentCandidate
            ? result.records.find(
                (item) => item.candidateId === currentCandidate.candidateId,
              ) || null
            : null,
        );
        return result;
      } catch (exception) {
        if (
          exception.name !== "AbortError" &&
          current === sequence.current.candidates &&
          experimentId === selectedIdRef.current
        ) {
          setErrors((value) => ({ ...value, candidates: exception.message }));
        }
        return null;
      } finally {
        if (current === sequence.current.candidates)
          setLoading((value) => ({ ...value, candidates: false }));
      }
    },
    [],
  );

  const refreshSelected = useCallback(
    async (background = false) => {
      if (!selectedId) return;
      await Promise.all([
        loadDetail(selectedId, { background }),
        loadCandidates(selectedId, candidatePage, sortBy, order, {
          background,
        }),
        loadList(listPage, { background }),
      ]);
    },
    [
      candidatePage,
      listPage,
      loadCandidates,
      loadDetail,
      loadList,
      order,
      selectedId,
      sortBy,
    ],
  );

  useEffect(() => {
    const currentAborts = aborts.current;
    loadShared();
    return () => {
      Object.values(currentAborts).forEach((controller) =>
        controller?.abort(),
      );
    };
  }, [loadShared]);

  useEffect(() => {
    const timer = window.setTimeout(() => loadList(1), 250);
    return () => window.clearTimeout(timer);
  }, [filters, loadList]);

  useEffect(() => {
    selectedIdRef.current = selectedId;
    if (!selectedId) {
      setDetail(null);
      setCandidateData({ records: [], total: 0, page: 1, pageSize: 50 });
      setSelectedCandidate(null);
      return;
    }
    loadDetail(selectedId);
  }, [loadDetail, selectedId]);

  useEffect(() => {
    if (!selectedId) return;
    loadCandidates(selectedId, candidatePage, sortBy, order);
  }, [candidatePage, loadCandidates, order, selectedId, sortBy]);

  useEffect(() => {
    const onPopState = () => {
      const route = readCandidateRoute();
      selectedIdRef.current = route.experimentId;
      setSelectedId(route.experimentId);
      setCandidatePage(route.candidatePage);
      setSortBy(route.sortBy);
      setOrder(route.order);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  useEffect(() => {
    polling.current = false;
    if (!selectedId || !NON_TERMINAL.has(detail?.status) || !visible)
      return undefined;
    let active = true;
    const poll = async () => {
      if (!active || polling.current || !visible) return;
      polling.current = true;
      const result = await loadDetail(selectedId, { background: true });
      if (!result || selectedId !== selectedIdRef.current) {
        polling.current = false;
        return;
      }
      const route = pollRouteRef.current;
      await Promise.all([
        loadCandidates(
          selectedId,
          route.candidatePage,
          route.sortBy,
          route.order,
          {
          background: true,
          },
        ),
        loadListRef.current(route.listPage, { background: true }),
      ]);
      polling.current = false;
      if (result && !NON_TERMINAL.has(result.status))
        window.clearInterval(timer);
    };
    poll();
    const timer = window.setInterval(poll, 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [
    candidatePage,
    detail?.status,
    listPage,
    loadCandidates,
    loadDetail,
    loadList,
    order,
    selectedId,
    sortBy,
    visible,
  ]);

  useEffect(() => {
    const onVisibility = () => {
      const nextVisible = document.visibilityState === "visible";
      setVisible(nextVisible);
      if (!nextVisible) {
        aborts.current.detail?.abort();
        aborts.current.candidates?.abort();
        aborts.current.list?.abort();
      }
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, []);

  const chooseExperiment = (experimentId) => {
    const next = {
      experimentId,
      candidatePage: 1,
      sortBy: "CANDIDATE_INDEX",
      order: "ASC",
    };
    selectedIdRef.current = experimentId;
    setSelectedId(experimentId);
    setCandidatePage(1);
    setSortBy(next.sortBy);
    setOrder(next.order);
    setSelectedCandidate(null);
    writeExperimentRoute(next);
  };

  const changeCandidatePage = (page) => {
    setCandidatePage(page);
    setSelectedCandidate(null);
    writeExperimentRoute({
      experimentId: selectedId,
      candidatePage: page,
      sortBy,
      order,
    });
  };

  const changeSort = (nextSort, nextOrder) => {
    setSortBy(nextSort);
    setOrder(nextOrder);
    setCandidatePage(1);
    setSelectedCandidate(null);
    writeExperimentRoute({
      experimentId: selectedId,
      candidatePage: 1,
      sortBy: nextSort,
      order: nextOrder,
    });
  };

  const onCreated = async (created) => {
    const next = {
      experimentId: created.experimentId,
      candidatePage: 1,
      sortBy: "CANDIDATE_INDEX",
      order: "ASC",
    };
    selectedIdRef.current = created.experimentId;
    setSelectedId(created.experimentId);
    setCandidatePage(1);
    setSortBy(next.sortBy);
    setOrder(next.order);
    writeExperimentRoute(next);
    await loadList(1);
    await Promise.all([
      loadDetail(created.experimentId),
      loadCandidates(created.experimentId, 1, next.sortBy, next.order),
    ]);
  };

  const refresh = async () => {
    setRefreshing(true);
    await Promise.all([loadShared(), loadList(listPage), refreshSelected()]);
    setRefreshing(false);
  };

  const closeCreatePanel = () => {
    if (creating) return;
    setShowCreate(false);
    requestAnimationFrame(() => createButtonRef.current?.focus());
  };

  return (
    <QuantPageScaffold pageClass="quant-backtests-page quant-experiments-page">
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">QUANT · PARAMETER LAB</span>
          <h3>参数实验</h3>
          <small>比较 TRAIN 与样本外 VALIDATION 的真实回测结果</small>
        </div>
        <div className="backtest-head-actions">
          <button
            type="button"
            className="quant-refresh"
            onClick={refresh}
            disabled={refreshing}
          >
            <ArrowsClockwise className={refreshing ? "spin" : ""} />
            刷新
          </button>
          <button
            ref={createButtonRef}
            type="button"
            className="quant-primary-action"
            onClick={() => setShowCreate(true)}
          >
            <Flask />
            新建参数实验
          </button>
        </div>
      </div>
      {errors.strategies && (
        <div className="backtest-notice" role="alert">
          <Warning />
          策略不可用：{errors.strategies}
        </div>
      )}
      {errors.datasets && (
        <div className="backtest-notice" role="alert">
          <Warning />
          数据集不可用：{errors.datasets}
        </div>
      )}
      {errors.profiles && (
        <div className="backtest-notice" role="alert">
          <Warning />
          执行模型不可用：{errors.profiles}
        </div>
      )}
      {errors.list && (
        <div className="backtest-notice" role="alert">
          实验列表加载失败：{errors.list}
          <button type="button" onClick={() => loadList(listPage)}>
            重试
          </button>
        </div>
      )}
      <div className="quant-experiment-main">
        <QuantExperimentList
          page={listPage}
          data={listData}
          filters={filters}
          selectedId={selectedId}
          loading={loading.list}
          onFilters={(next) => {
            setFilters(next);
            setListPage(1);
          }}
          onPage={loadList}
          onSelect={chooseExperiment}
        />
        <QuantExperimentDetail
          experiment={detail}
          strategy={strategy}
          executionProfile={executionProfiles.find(
            (item) => item.code === detail?.executionProfileCode,
          )}
          loading={loading.detail}
          error={errors.detail}
          onRetry={() => loadDetail(selectedId)}
        />
      </div>
      {selectedId && (
        <>
          <QuantExperimentCandidates
            data={candidateData}
            page={candidatePage}
            sortBy={sortBy}
            order={order}
            strategy={strategy}
            loading={loading.candidates}
            error={errors.candidates}
            selectedId={selectedCandidate?.candidateId}
            onPage={changeCandidatePage}
            onSort={changeSort}
            onSelect={setSelectedCandidate}
            onRetry={() =>
              loadCandidates(selectedId, candidatePage, sortBy, order)
            }
          />
          <QuantExperimentComparison
            candidate={selectedCandidate}
            strategy={strategy}
          />
        </>
      )}
      {showCreate && (
        <div
          className="backtest-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeCreatePanel();
          }}
        >
          <QuantExperimentCreatePanel
            strategies={strategies}
            datasets={datasets}
            executionProfiles={executionProfiles}
            onClose={closeCreatePanel}
            onCreated={onCreated}
            onSavingChange={setCreating}
          />
        </div>
      )}
    </QuantPageScaffold>
  );
}

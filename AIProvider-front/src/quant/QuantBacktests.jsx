import { useCallback, useEffect, useState } from "react";
import QuantExperimentWorkspace from "./QuantExperimentWorkspace";
import QuantSingleBacktests from "./QuantSingleBacktests";
import QuantWalkForwardWorkspace from "./QuantWalkForwardWorkspace";
import "./QuantExperiments.css";

function readRoute() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("openCreate") === "1") return "single";
  if (params.get("mode") === "walk-forward") return "walk-forward";
  return params.get("mode") === "experiment" ? "experiment" : "single";
}

function writeMode(mode) {
  const url = new URL(window.location.href);
  url.searchParams.set("mode", mode);
  if (mode === "single") ["experimentId", "candidatePage", "candidateSort", "candidateOrder", "studyId", "foldPage", "foldId"].forEach((key) => url.searchParams.delete(key));
  if (mode === "experiment") ["runId", "openCreate", "strategyCode", "studyId", "foldPage", "foldId"].forEach((key) => url.searchParams.delete(key));
  if (mode === "walk-forward") ["runId", "openCreate", "strategyCode", "experimentId", "candidatePage", "candidateSort", "candidateOrder"].forEach((key) => url.searchParams.delete(key));
  window.history.pushState(
    {},
    "",
    `${url.pathname}?${url.searchParams.toString()}`,
  );
  window.dispatchEvent(new PopStateEvent("popstate"));
}

export default function QuantBacktests() {
  const [mode, setMode] = useState(readRoute);

  useEffect(() => {
    const sync = () => setMode(readRoute());
    window.addEventListener("popstate", sync);
    return () => window.removeEventListener("popstate", sync);
  }, []);

  const chooseMode = useCallback(
    (nextMode) => {
      if (nextMode === mode) return;
      writeMode(nextMode);
    },
    [mode],
  );

  return (
    <div className="quant-backtests-shell">
      <nav className="quant-backtests-tabs" aria-label="回测实验视图">
        <button
          type="button"
          className={mode === "single" ? "active" : ""}
          aria-current={mode === "single" ? "page" : undefined}
          onClick={() => chooseMode("single")}
        >
          单次回测
        </button>
        <button
          type="button"
          className={mode === "experiment" ? "active" : ""}
          aria-current={mode === "experiment" ? "page" : undefined}
          onClick={() => chooseMode("experiment")}
        >
          参数实验
        </button>
        <button
          type="button"
          className={mode === "walk-forward" ? "active" : ""}
          aria-current={mode === "walk-forward" ? "page" : undefined}
          onClick={() => chooseMode("walk-forward")}
        >
          滚动验证
        </button>
      </nav>
      {mode === "experiment" ? (
        <QuantExperimentWorkspace />
      ) : mode === "walk-forward" ? (
        <QuantWalkForwardWorkspace />
      ) : (
        <QuantSingleBacktests />
      )}
    </div>
  );
}

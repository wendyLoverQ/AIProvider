import QuantPageScaffold, { QuantSkeletonBody } from "./QuantPageScaffold";

const CONFIG = {
  title: "回测实验",
  intro: "历史数据、回测任务、撮合假设、手续费与滑点、参数实验、结果报告",
  items: ["历史数据", "回测任务", "撮合假设", "手续费与滑点", "参数实验", "结果报告"],
  note: "尚未接入具体业务 · 不展示收益、曲线或胜率",
};

export default function QuantBacktests() {
  return (
    <QuantPageScaffold pageClass="quant-backtests-page" title={CONFIG.title}>
      <QuantSkeletonBody {...CONFIG} />
    </QuantPageScaffold>
  );
}

import QuantPageScaffold, { QuantSkeletonBody } from "./QuantPageScaffold";

const CONFIG = {
  title: "策略管理",
  intro: "策略定义、参数版本、启停状态与信号记录",
  items: ["策略定义", "策略版本", "参数管理", "启停状态", "信号记录"],
  note: "尚未接入具体业务",
};

export default function QuantStrategies() {
  return (
    <QuantPageScaffold pageClass="quant-strategies-page" title={CONFIG.title}>
      <QuantSkeletonBody {...CONFIG} />
    </QuantPageScaffold>
  );
}

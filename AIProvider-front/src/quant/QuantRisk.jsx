import QuantPageScaffold, { QuantSkeletonBody } from "./QuantPageScaffold";

const CONFIG = {
  title: "风控中心",
  intro: "单笔风险、仓位限制、杠杆限制、日亏熔断、连续亏损熔断、紧急停止",
  items: ["单笔风险", "仓位限制", "杠杆限制", "日亏熔断", "连续亏损熔断", "紧急停止"],
  note: "尚未接入具体业务 · 不提供开关或误操作按钮",
};

export default function QuantRisk() {
  return (
    <QuantPageScaffold pageClass="quant-risk-page" title={CONFIG.title}>
      <QuantSkeletonBody {...CONFIG} />
    </QuantPageScaffold>
  );
}

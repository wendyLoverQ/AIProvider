import QuantPageScaffold, { QuantSkeletonBody } from "./QuantPageScaffold";

const CONFIG = {
  title: "账户仓位",
  intro: "账户余额、可用保证金、当前仓位、已实现盈亏、未实现盈亏、资金费率",
  items: ["账户余额", "可用保证金", "当前仓位", "已实现盈亏", "未实现盈亏", "资金费率"],
  note: "尚未接入 · 不展示余额或盈亏数字",
};

export default function QuantPortfolio() {
  return (
    <QuantPageScaffold pageClass="quant-portfolio-page" title={CONFIG.title}>
      <QuantSkeletonBody {...CONFIG} />
    </QuantPageScaffold>
  );
}

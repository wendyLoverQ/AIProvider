import QuantPageScaffold, { QuantSkeletonBody } from "./QuantPageScaffold";

const CONFIG = {
  title: "订单成交",
  intro: "活跃订单、历史订单、成交记录、保护单、执行异常",
  items: ["活跃订单", "历史订单", "成交记录", "保护单", "执行异常"],
  note: "尚未接入具体业务 · 不展示订单",
};

export default function QuantOrders() {
  return (
    <QuantPageScaffold pageClass="quant-orders-page" title={CONFIG.title}>
      <QuantSkeletonBody {...CONFIG} />
    </QuantPageScaffold>
  );
}

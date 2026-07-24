import QuantPageScaffold, { QuantSkeletonBody } from "./QuantPageScaffold";

const CONFIG = {
  title: "运行记录",
  intro: "策略运行记录、风控决策、对账记录、系统异常、发布版本",
  items: ["策略运行记录", "风控决策", "对账记录", "系统异常", "发布版本"],
  note: "尚未接入具体业务 · 不生成日志",
};

export default function QuantLogs() {
  return (
    <QuantPageScaffold pageClass="quant-logs-page" title={CONFIG.title}>
      <QuantSkeletonBody {...CONFIG} />
    </QuantPageScaffold>
  );
}

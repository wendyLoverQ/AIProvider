// QuantPageScaffold — 仅复用一致的页面容器、空状态和能力项视觉。
// 不负责页面路由，也不把多个页面重新塞进同一个组件。
import "./QuantPages.css";

export default function QuantPageScaffold({ pageClass, title, children }) {
  return (
    <section className={`quant-page ${pageClass}`} aria-label={title}>
      {children}
    </section>
  );
}

export function QuantCapabilityGrid({ items, label }) {
  return (
    <section className="quant-cap-grid" aria-label={label || "能力结构"}>
      {items.map((item) => (
        <article key={item} className="quant-cap-row">
          <span className="quant-cap-name">{item}</span>
          <span className="quant-cap-status">未接入</span>
        </article>
      ))}
    </section>
  );
}

// 骨架页面共享的“尚未接入”结构：标题 + 简介 + 能力项 + 注脚。
export function QuantSkeletonBody({ title, intro, items, note }) {
  return (
    <>
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">QUANT · 尚未接入</span>
          <h3>{title}</h3>
          <small>{intro}</small>
        </div>
      </div>
      <QuantCapabilityGrid items={items} label={`${title}能力结构`} />
      <p className="quant-skeleton-note">{note}</p>
    </>
  );
}

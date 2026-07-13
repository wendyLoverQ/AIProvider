import { useEffect, useState } from "react";
import "./DynamicShowcase.css";

const modules = import.meta.glob("./assets/show/*.{png,jpg,jpeg,webp,gif,avif}", {
  eager: true,
  import: "default",
  query: "?url",
});

export const showImages = Object.entries(modules)
  .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }))
  .map(([path, src]) => ({ path, src, name: path.split("/").pop() }));

export default function DynamicShowcase({ variant = "home" }) {
  const [active, setActive] = useState(0);
  useEffect(() => {
    if (showImages.length < 2) return undefined;
    const timer = window.setInterval(() => setActive((current) => (current + 1) % showImages.length), 4500);
    return () => window.clearInterval(timer);
  }, []);
  if (!showImages.length) return null;
  const current = showImages[active];
  const previous = showImages[(active - 1 + showImages.length) % showImages.length];
  const next = showImages[(active + 1) % showImages.length];

  if (variant === "workshop") {
    return <div className="workshop-dynamic-showcase" aria-hidden="true">
      {showImages.length > 1 && <img key={`previous-${previous.path}`} src={previous.src} alt="" className="previous" loading="lazy" />}
      <img key={`active-${current.path}`} src={current.src} alt="" className="active" />
      {showImages.length > 2 && <img key={`next-${next.path}`} src={next.src} alt="" className="next" loading="lazy" />}
    </div>;
  }

  return <>
    <div className="home-showcase home-dynamic-sides" aria-hidden="true">
      {showImages.length > 1 && <img key={`previous-${previous.path}`} src={previous.src} alt="" className="home-showcase-card previous" loading="lazy" />}
      {showImages.length > 2 && <img key={`next-${next.path}`} src={next.src} alt="" className="home-showcase-card next" loading="lazy" />}
    </div>
    <div className="home-launcher-portrait home-dynamic-portrait">
      <i className="showcase-orbit orbit-a" aria-hidden="true" />
      <i className="showcase-orbit orbit-b" aria-hidden="true" />
      <img key={current.path} src={current.src} alt={current.name} className="dynamic-show-image active" />
      <div className="showcase-dots" aria-hidden="true">{showImages.map((image, index) => <i key={image.path} className={index === active ? "active" : ""} />)}</div>
    </div>
  </>;
}

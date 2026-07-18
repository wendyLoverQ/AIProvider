import { useEffect, useRef, useState } from "react";
import "./DynamicShowcase.css";

const PRESS_SCALE_PER_SECOND = 0.075;

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
  const [cycleToken, setCycleToken] = useState(0);
  const [bouncingSide, setBouncingSide] = useState("");
  const [mainPressState, setMainPressState] = useState("");
  const sideTimer = useRef(0);
  const mainPressStartedAt = useRef(null);
  const mainReleaseTimer = useRef(0);
  const mainScaleFrame = useRef(0);
  const mainButton = useRef(null);
  useEffect(() => {
    if (showImages.length < 2) return undefined;
    const timer = window.setInterval(() => {
      if (mainPressStartedAt.current !== null) return;
      setActive((current) => (current + 1) % showImages.length);
    }, 4500);
    return () => window.clearInterval(timer);
  }, [cycleToken]);
  useEffect(() => () => { window.clearTimeout(sideTimer.current); window.clearTimeout(mainReleaseTimer.current); window.cancelAnimationFrame(mainScaleFrame.current); }, []);
  if (!showImages.length) return null;
  const current = showImages[active];
  const previous = showImages[(active - 1 + showImages.length) % showImages.length];
  const next = showImages[(active + 1) % showImages.length];
  const selectImage = (index) => {
    setActive((index + showImages.length) % showImages.length);
    setCycleToken((token) => token + 1);
  };
  const selectSide = (direction) => {
    if (bouncingSide) return;
    const side = direction < 0 ? "previous" : "next";
    setBouncingSide(side);
    window.clearTimeout(sideTimer.current);
    sideTimer.current = window.setTimeout(() => {
      selectImage(active + direction);
      setBouncingSide("");
    }, 360);
  };
  const beginMainPress = (event) => {
    if (event.button !== 0) return;
    mainPressStartedAt.current = performance.now();
    window.clearTimeout(mainReleaseTimer.current);
    window.cancelAnimationFrame(mainScaleFrame.current);
    const enlarge = (now) => {
      if (mainPressStartedAt.current === null || !mainButton.current) return;
      const scale = 1 + ((now - mainPressStartedAt.current) / 1000) * PRESS_SCALE_PER_SECOND;
      mainButton.current.style.setProperty("--showcase-scale", String(scale));
      mainScaleFrame.current = window.requestAnimationFrame(enlarge);
    };
    setMainPressState("holding");
    mainScaleFrame.current = window.requestAnimationFrame(enlarge);
  };
  const endMainPress = () => {
    if (mainPressStartedAt.current === null) return;
    mainPressStartedAt.current = null;
    window.cancelAnimationFrame(mainScaleFrame.current);
    setMainPressState("releasing");
    setCycleToken((token) => token + 1);
    mainReleaseTimer.current = window.setTimeout(() => {
      mainButton.current?.style.removeProperty("--showcase-scale");
      setMainPressState("");
    }, 760);
  };

  if (variant === "workshop") {
    return <div className="workshop-dynamic-showcase" aria-hidden="true">
      {showImages.length > 1 && <img key={`previous-${previous.path}`} src={previous.src} alt="" className="previous" loading="lazy" />}
      <img key={`active-${current.path}`} src={current.src} alt="" className="active" />
      {showImages.length > 2 && <img key={`next-${next.path}`} src={next.src} alt="" className="next" loading="lazy" />}
    </div>;
  }

  return <>
    <div className="home-showcase home-dynamic-sides">
      {showImages.length > 1 && <button type="button" className={`showcase-side-button previous ${bouncingSide === "previous" ? "is-bouncing" : ""}`} onClick={() => selectSide(-1)} aria-label="查看上一张轮播图片">
        <img key={`previous-${previous.path}`} src={previous.src} alt="" className="home-showcase-card" loading="lazy" />
      </button>}
      {showImages.length > 2 && <button type="button" className={`showcase-side-button next ${bouncingSide === "next" ? "is-bouncing" : ""}`} onClick={() => selectSide(1)} aria-label="查看下一张轮播图片">
        <img key={`next-${next.path}`} src={next.src} alt="" className="home-showcase-card" loading="lazy" />
      </button>}
    </div>
    <div className="home-launcher-portrait home-dynamic-portrait">
      <i className="showcase-orbit orbit-a" aria-hidden="true" />
      <i className="showcase-orbit orbit-b" aria-hidden="true" />
      <button ref={mainButton} type="button" className={`showcase-main-button ${mainPressState ? `is-${mainPressState}` : ""}`} onPointerDown={beginMainPress} onPointerUp={endMainPress} onPointerCancel={endMainPress} onPointerLeave={() => mainPressStartedAt.current !== null && endMainPress()} aria-label="按住持续放大轮播图片">
        <img key={current.path} src={current.src} alt={current.name} className="dynamic-show-image active" />
      </button>
    </div>
  </>;
}

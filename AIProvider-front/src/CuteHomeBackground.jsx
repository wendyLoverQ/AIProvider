import { useEffect, useRef } from "react";
import { Cat, FlowerLotus, Heart, PawPrint, Sparkle, Star } from "@phosphor-icons/react";
import "./CuteHomeBackground.css";

const FLOATERS = [
  { Icon: Heart, x: "8%", y: "15%", size: 31, delay: "-1s", duration: "7s", depth: 1.2, tone: "pink" },
  { Icon: Sparkle, x: "20%", y: "72%", size: 24, delay: "-4s", duration: "6s", depth: .8, tone: "violet" },
  { Icon: Star, x: "78%", y: "12%", size: 27, delay: "-2s", duration: "8s", depth: 1.1, tone: "yellow" },
  { Icon: FlowerLotus, x: "89%", y: "67%", size: 34, delay: "-5s", duration: "9s", depth: .7, tone: "mint" },
  { Icon: PawPrint, x: "66%", y: "82%", size: 25, delay: "-3s", duration: "7.5s", depth: 1.35, tone: "pink" },
  { Icon: Sparkle, x: "36%", y: "10%", size: 18, delay: "-6s", duration: "5.5s", depth: .6, tone: "violet" },
  { Icon: Heart, x: "94%", y: "29%", size: 18, delay: "-2.5s", duration: "6.5s", depth: 1.5, tone: "pink" },
  { Icon: Star, x: "4%", y: "52%", size: 17, delay: "-4.5s", duration: "8.5s", depth: .9, tone: "yellow" },
];
const MAGIC_DUST = Array.from({ length: 22 }, (_, index) => ({
  x: `${(index * 37 + 9) % 97}%`,
  y: `${(index * 53 + 7) % 91}%`,
  delay: `${-(index % 9) * .73}s`,
  duration: `${4.8 + (index % 6) * .7}s`,
  size: `${2 + (index % 3)}px`,
}));

export default function CuteHomeBackground() {
  const root = useRef(null);
  useEffect(() => {
    let frame = 0;
    const move = (event) => {
      if (frame) cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const x = (event.clientX / window.innerWidth - .5).toFixed(3);
        const y = (event.clientY / window.innerHeight - .5).toFixed(3);
        root.current?.style.setProperty("--cute-x", x);
        root.current?.style.setProperty("--cute-y", y);
      });
    };
    window.addEventListener("pointermove", move, { passive: true });
    return () => { window.removeEventListener("pointermove", move); if (frame) cancelAnimationFrame(frame); };
  }, []);

  return <div className="cute-home-bg" ref={root} aria-hidden="true">
    <div className="cute-dot-field" />
    <div className="cute-pointer-glow" />
    <i className="cute-blob blob-a" /><i className="cute-blob blob-b" /><i className="cute-blob blob-c" />
    <div className="cute-meteors"><i /><i /><i /></div>
    <div className="cute-magic-dust">{MAGIC_DUST.map((dust, index) => <i key={index} style={{ "--x": dust.x, "--y": dust.y, "--delay": dust.delay, "--duration": dust.duration, "--size": dust.size }} />)}</div>
    <div className="cute-orbit cute-orbit-a"><Sparkle weight="fill" /></div>
    <div className="cute-orbit cute-orbit-b"><Heart weight="fill" /></div>
    {FLOATERS.map(({ Icon, x, y, size, delay, duration, depth, tone }, index) => <span
      className={`cute-floater ${tone}`} key={`${x}-${y}`} style={{ "--x": x, "--y": y, "--delay": delay, "--duration": duration, "--depth": depth }}
    ><i><Icon size={size} weight={index % 3 === 0 ? "fill" : "duotone"} /></i></span>)}
    <div className="cute-charm charm-cat"><Cat weight="duotone" /><span>meow</span></div>
    <div className="cute-charm charm-paw"><PawPrint weight="fill" /><i /><i /><i /></div>
    <div className="cute-spark-cluster"><b>✦</b><i>·</i><em>♡</em><span>✧</span></div>
    <div className="cute-heart-bubbles"><i>♡</i><i>✦</i><i>♡</i><i>✧</i><i>♡</i></div>
  </div>;
}

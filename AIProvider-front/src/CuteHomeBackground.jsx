import { useEffect, useRef, useState } from "react";
import { CuteWorkshopArt } from "./CuteWorkshopCard";
import unit09Frame from "./assets/cockpit-unit09-frame-v2.png";
import unit09Pilot from "./assets/cockpit-unit09-pilot-v2.png";
import { RELEASE_VERSION } from "./releaseVersion";
import { createThreePlanetFlyby, THREE_FLYBY_BODIES } from "./threePlanetFlyby";
import "./CuteHomeBackground.css";

const STATUS_LIGHTS = Array.from({ length: 7 });
const CONSOLE_KEYS = Array.from({ length: 5 });
const COCKPIT_DESIGN_SIZE = { width: 2048, height: 1368 };
const FLIGHT_FOCUS = {
  x: 1800 / COCKPIT_DESIGN_SIZE.width,
  y: 285 / COCKPIT_DESIGN_SIZE.height,
};

const COCKPIT_SCENES = [
  {
    id: "unit09",
    label: "UNIT 09",
    frame: unit09Frame,
    pilot: unit09Pilot,
    instruments: [
      { id: "left", mode: "radar", style: { left: "20.2%", top: "30.7%", width: "5.45%", height: "9.45%", transform: "perspective(240px) rotateY(5deg) rotateZ(.4deg)" } },
      { id: "center", mode: "bars", style: { left: "44.95%", top: "33.45%", width: "3.35%", height: "4.55%", transform: "perspective(180px) rotateY(-3deg) rotateZ(.4deg)" } },
      { id: "right", mode: "workshop", style: { left: "70.3402%", top: "78.4032%", width: "3.198%", height: "4.2135%", transform: "matrix(1, .087452, -.210256, 1, 0, 0)" } },
    ],
    consoleKeys: { left: "63.05%", top: "44.25%", width: "7.45%", height: "1.2%", transform: "perspective(180px) rotateY(-4deg) rotateZ(.35deg)" },
    statusLights: { left: "20.35%", top: "41.15%", width: "5.2%", height: ".82%", transform: "rotate(.4deg)" },
    switchPanel: { left: "62.3%", top: "66.7%" },
  },
];

export default function CuteHomeBackground({ onOpenWorkshop }) {
  const root = useRef(null);
  const spaceCanvas = useRef(null);
  const planetCanvas = useRef(null);
  const releaseTimer = useRef(0);
  const [pressState, setPressState] = useState("");
  const [sceneIndex, setSceneIndex] = useState(0);
  const scene = COCKPIT_SCENES[sceneIndex];

  useEffect(() => {
    let frame = 0;
    const move = (event) => {
      if (frame) cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const bounds = root.current?.getBoundingClientRect();
        if (!bounds) return;
        const x = Math.max(-.5, Math.min(.5, (event.clientX - bounds.left) / bounds.width - .5));
        const y = Math.max(-.5, Math.min(.5, (event.clientY - bounds.top) / bounds.height - .5));
        root.current.style.setProperty("--cockpit-x", x.toFixed(3));
        root.current.style.setProperty("--cockpit-y", y.toFixed(3));
      });
    };
    window.addEventListener("pointermove", move, { passive: true });
    return () => {
      window.removeEventListener("pointermove", move);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    const canvas = spaceCanvas.current;
    const webglCanvas = planetCanvas.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !webglCanvas || !context) return undefined;
    const planetLayer = createThreePlanetFlyby(webglCanvas);

    let animationFrame = 0;
    let width = 0;
    let height = 0;
    let ratio = 1;
    let lastTime = performance.now();
    const colors = ["151,220,255", "124,173,255", "255,202,132", "201,242,255"];
    const stars = Array.from({ length: 320 }, (_, index) => ({
      x: Math.random(),
      y: Math.random(),
      size: .45 + Math.random() * 1.9,
      speed: .004 + Math.random() * .008,
      phase: Math.random() * Math.PI * 2,
      color: colors[index % colors.length],
    }));
    const meteors = Array.from({ length: 2 }, (_, index) => ({
      active: false,
      wait: 2.5 + index * 5 + Math.random() * 2.5,
      x: 0,
      y: 0,
      speed: 0,
      life: 0,
      duration: 0,
    }));
    const flybyTemplates = THREE_FLYBY_BODIES;
    const startedAt = performance.now();
    let flybyQueue = [];
    let activeFlyby = null;
    let previousFlybyType = "";
    let nextFlybyAt = startedAt + 900;

    const refillFlybyQueue = () => {
      flybyQueue = [...flybyTemplates];
      for (let index = flybyQueue.length - 1; index > 0; index -= 1) {
        const swapIndex = Math.floor(Math.random() * (index + 1));
        [flybyQueue[index], flybyQueue[swapIndex]] = [flybyQueue[swapIndex], flybyQueue[index]];
      }
      if (flybyQueue[0]?.type === previousFlybyType) {
        [flybyQueue[0], flybyQueue[1]] = [flybyQueue[1], flybyQueue[0]];
      }
      if (!previousFlybyType) {
        const saturnIndex = flybyQueue.findIndex((item) => item.type === "saturn");
        [flybyQueue[0], flybyQueue[saturnIndex]] = [flybyQueue[saturnIndex], flybyQueue[0]];
      }
    };

    const launchFlyby = (time) => {
      if (!flybyQueue.length) refillFlybyQueue();
      const template = flybyQueue.shift();
      const lanes = [-32, -18, 0, 20, 34];
      const lane = previousFlybyType ? lanes[Math.floor(Math.random() * lanes.length)] : 0;
      activeFlyby = {
        ...template,
        startedAt: time,
        x0: 1700 + Math.random() * 30,
        y0: 285 + lane,
        k: template.k * (.92 + Math.random() * .16),
      };
      previousFlybyType = template.type;
    };

    const launchMeteor = (meteor) => {
      meteor.active = true;
      meteor.x = .62 + Math.random() * .25;
      meteor.y = .04 + Math.random() * .34;
      meteor.speed = .025 + Math.random() * .045;
      meteor.life = 0;
      meteor.duration = 2.4 + Math.random() * 1.6;
    };

    const drawFlybyPlanet = (planet, time) => {
      const local = (time - planet.startedAt) / 1000;
      const perspectiveScale = Math.exp(planet.k * local);
      const x = (1800 + (planet.x0 - 1800) * perspectiveScale) / COCKPIT_DESIGN_SIZE.width;
      const y = (285 + (planet.y0 - 285) * perspectiveScale) / COCKPIT_DESIGN_SIZE.height;
      const radius = Math.min(width, height) * planet.radius * perspectiveScale;
      const centerX = x * width;
      const centerY = y * height;
      const halfWidth = radius * (planet.boundsX || 1);
      const halfHeight = radius * (planet.boundsY || 1);
      if (centerX + halfWidth < 0 || centerX - halfWidth > width || centerY + halfHeight < 0 || centerY - halfHeight > height) {
        planetLayer.hide();
        activeFlyby = null;
        nextFlybyAt = time + 2500 + Math.random() * 4500;
        return;
      }
      planetLayer.update({ type: planet.type, centerX, centerY, radius, elapsedTime: time });
    };

    const resize = () => {
      width = Math.max(1, canvas.clientWidth);
      height = Math.max(1, canvas.clientHeight);
      ratio = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      planetLayer.resize(width, height, ratio);
    };

    const draw = (time = performance.now()) => {
      const delta = Math.min(32, time - lastTime);
      lastTime = time;
      context.clearRect(0, 0, width, height);
      context.globalCompositeOperation = "source-over";
      context.globalCompositeOperation = "lighter";

      stars.forEach((star, index) => {
        const offsetX = star.x - FLIGHT_FOCUS.x;
        const offsetY = star.y - FLIGHT_FOCUS.y;
        const perspectiveScale = Math.exp(star.speed * delta / 1000);
        star.x = FLIGHT_FOCUS.x + offsetX * perspectiveScale;
        star.y = FLIGHT_FOCUS.y + offsetY * perspectiveScale;
        if (star.x < -.04 || star.x > 1.04 || star.y < -.04 || star.y > 1.04) {
          const angle = Math.random() * Math.PI * 2;
          const distance = .018 + Math.random() * .065;
          star.x = FLIGHT_FOCUS.x + Math.cos(angle) * distance;
          star.y = FLIGHT_FOCUS.y + Math.sin(angle) * distance * .62;
        }
        const pulse = .56 + Math.sin(time * .0014 + star.phase) * .34;
        const x = star.x * width;
        const y = star.y * height;
        context.beginPath();
        context.arc(x, y, star.size, 0, Math.PI * 2);
        context.fillStyle = `rgba(${star.color},${pulse})`;
        context.fill();
        if (index % 13 === 0) {
          const focusX = FLIGHT_FOCUS.x * width;
          const focusY = FLIGHT_FOCUS.y * height;
          const vectorX = x - focusX;
          const vectorY = y - focusY;
          const vectorLength = Math.max(1, Math.hypot(vectorX, vectorY));
          const trailLength = star.size * (2.4 + star.speed * 210);
          context.beginPath();
          context.moveTo(x - vectorX / vectorLength * trailLength, y - vectorY / vectorLength * trailLength);
          context.lineTo(x, y);
          context.strokeStyle = `rgba(${star.color},${pulse * .45})`;
          context.lineWidth = .55;
          context.stroke();
        }
      });

      context.globalCompositeOperation = "source-over";
      if (!activeFlyby && time >= nextFlybyAt) launchFlyby(time);
      if (activeFlyby) drawFlybyPlanet(activeFlyby, time);
      planetLayer.render();
      context.globalCompositeOperation = "lighter";

      meteors.forEach((meteor) => {
        const seconds = delta / 1000;
        if (!meteor.active) {
          meteor.wait -= seconds;
          if (meteor.wait <= 0) launchMeteor(meteor);
          return;
        }

        meteor.life += seconds;
        const meteorScale = Math.exp(meteor.speed * seconds);
        meteor.x = FLIGHT_FOCUS.x + (meteor.x - FLIGHT_FOCUS.x) * meteorScale;
        meteor.y = FLIGHT_FOCUS.y + (meteor.y - FLIGHT_FOCUS.y) * meteorScale;
        const progress = meteor.life / meteor.duration;
        if (progress >= 1 || meteor.x < -.12 || meteor.y > 1.12) {
          meteor.active = false;
          meteor.wait = 7 + Math.random() * 10;
          return;
        }

        const headX = meteor.x * width;
        const headY = meteor.y * height;
        const focusX = FLIGHT_FOCUS.x * width;
        const focusY = FLIGHT_FOCUS.y * height;
        const vectorX = headX - focusX;
        const vectorY = headY - focusY;
        const vectorLength = Math.max(1, Math.hypot(vectorX, vectorY));
        const trailLength = 34 + progress * 28;
        const tailX = headX - vectorX / vectorLength * trailLength;
        const tailY = headY - vectorY / vectorLength * trailLength;
        const alpha = Math.sin(progress * Math.PI) * .9;
        const trail = context.createLinearGradient(tailX, tailY, headX, headY);
        trail.addColorStop(0, "rgba(91,174,255,0)");
        trail.addColorStop(.72, `rgba(126,211,255,${alpha * .34})`);
        trail.addColorStop(1, `rgba(238,251,255,${alpha})`);
        context.save();
        context.beginPath();
        context.moveTo(tailX, tailY);
        context.lineTo(headX, headY);
        context.strokeStyle = trail;
        context.lineWidth = 1.15 + alpha * 1.25;
        context.shadowColor = "rgba(116,211,255,.9)";
        context.shadowBlur = 8 + alpha * 8;
        context.stroke();
        context.beginPath();
        context.arc(headX, headY, 1.1 + alpha * 1.5, 0, Math.PI * 2);
        context.fillStyle = `rgba(245,253,255,${alpha})`;
        context.fill();
        context.restore();
      });

      animationFrame = requestAnimationFrame(draw);
    };

    resize();
    draw();
    window.addEventListener("resize", resize);
    return () => {
      window.removeEventListener("resize", resize);
      if (animationFrame) cancelAnimationFrame(animationFrame);
      planetLayer.dispose();
    };
  }, []);

  const startHold = (event) => {
    if (event.button !== undefined && event.button !== 0) return;
    window.clearTimeout(releaseTimer.current);
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setPressState("holding");
  };

  const endHold = (event) => {
    if (pressState !== "holding") return;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    setPressState("releasing");
    releaseTimer.current = window.setTimeout(() => setPressState(""), 780);
  };

  useEffect(() => () => window.clearTimeout(releaseTimer.current), []);

  return (
    <div className="cute-home-bg cockpit-home-bg" ref={root}>
      <div className={`cockpit-layer-stage cockpit-scene-${scene.id} ${pressState ? `is-${pressState}` : ""}`}>
        <canvas
          className="cockpit-space-canvas"
          ref={spaceCanvas}
        />
        <canvas
          className="cockpit-planet-canvas"
          ref={planetCanvas}
          aria-hidden="true"
        />
        <div className="cockpit-layer-wrap cockpit-frame-wrap">
          <img className="cockpit-layer" src={scene.frame} alt="" />
        </div>
        <div className="cockpit-layer-wrap cockpit-pilot-backing">
          <img className="cockpit-layer" src={scene.pilot} alt="" />
        </div>
        <div className="cockpit-layer-wrap cockpit-pilot-wrap">
          <img className="cockpit-layer" src={scene.pilot} alt="" />
        </div>

        {scene.instruments.map((instrument) => <div
          className={`cockpit-instrument cockpit-instrument-${instrument.id} cockpit-instrument-${instrument.mode}`}
          key={instrument.id}
          style={instrument.style}
        >
          <i className="instrument-grid" /><i className="instrument-scan" />
          {instrument.mode === "radar" && <i className="instrument-radar" />}
          {instrument.mode === "bars" && <span className="instrument-bars"><b /><b /><b /><b /></span>}
          {instrument.mode === "wave" && <span className="instrument-wave" />}
          {instrument.mode === "workshop" && <button
            type="button"
            className="cockpit-workshop-tile"
            onClick={onOpenWorkshop}
            aria-label="进入图像工坊"
          ><CuteWorkshopArt /></button>}
        </div>)}
        <div className="cockpit-console-keys" style={scene.consoleKeys}>
          {CONSOLE_KEYS.map((_, index) => <i key={index} />)}
        </div>
        <div className="cockpit-status-lights" style={scene.statusLights}>
          {STATUS_LIGHTS.map((_, index) => <i key={index} />)}
        </div>
        <div className="cockpit-glass-sweep" />

        {COCKPIT_SCENES.length > 1 && <div className="cockpit-scene-switcher" style={scene.switchPanel} aria-label="切换座舱角色">
          {COCKPIT_SCENES.map((item, index) => <button
            type="button"
            className={index === sceneIndex ? "active" : ""}
            key={item.id}
            onClick={() => setSceneIndex(index)}
            aria-label={`切换至 ${item.label}`}
          ><i /><span>{String(index + 1).padStart(2, "0")}</span></button>)}
        </div>}
      </div>

      <div className="cockpit-release-version" aria-label={`前端版本 ${RELEASE_VERSION.frontend}，后端版本 ${RELEASE_VERSION.backend}`}>
        <span><b>前端</b><i>{RELEASE_VERSION.frontend}</i></span>
        <span><b>后端</b><i>{RELEASE_VERSION.backend}</i></span>
      </div>

      <button
        type="button"
        className="cockpit-hold-zone"
        aria-label="按住缓慢放大座舱画面"
        onPointerDown={startHold}
        onPointerUp={endHold}
        onPointerCancel={endHold}
        onLostPointerCapture={endHold}
      />
    </div>
  );
}

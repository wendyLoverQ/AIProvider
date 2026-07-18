import * as THREE from "three";
import callistoMap from "./assets/kylegough-solar-system/textures/callisto.jpg";
import earthBump from "./assets/kylegough-solar-system/textures/earth-bump.jpg";
import earthCloudAlpha from "./assets/kylegough-solar-system/textures/earth-clouds-alpha.jpg";
import earthClouds from "./assets/kylegough-solar-system/textures/earth-clouds.jpg";
import earthSpecular from "./assets/kylegough-solar-system/textures/earth-specular.jpg";
import earthMap from "./assets/kylegough-solar-system/textures/earth.jpg";
import europaMap from "./assets/kylegough-solar-system/textures/europa.jpg";
import ganymedeMap from "./assets/kylegough-solar-system/textures/ganymede.jpg";
import ioMap from "./assets/kylegough-solar-system/textures/io.jpg";
import jupiterMap from "./assets/kylegough-solar-system/textures/jupiter.jpg";
import marsBump from "./assets/kylegough-solar-system/textures/mars-bump.jpg";
import marsMap from "./assets/kylegough-solar-system/textures/mars.jpg";
import mercuryBump from "./assets/kylegough-solar-system/textures/mercury-bump.jpg";
import mercuryMap from "./assets/kylegough-solar-system/textures/mercury.jpg";
import moonBump from "./assets/kylegough-solar-system/textures/moon-bump.jpg";
import moonMap from "./assets/kylegough-solar-system/textures/moon.jpg";
import neptuneMap from "./assets/kylegough-solar-system/textures/neptune.jpg";
import saturnRingMap from "./assets/kylegough-solar-system/textures/saturn-ring.png";
import saturnMap from "./assets/kylegough-solar-system/textures/saturn.jpg";
import sunMap from "./assets/kylegough-solar-system/textures/sun.jpg";
import titanMap from "./assets/kylegough-solar-system/textures/titan.webp";
import uranusMap from "./assets/kylegough-solar-system/textures/uranus.jpg";
import venusBump from "./assets/kylegough-solar-system/textures/venus-bump.jpg";
import venusMap from "./assets/kylegough-solar-system/textures/venus.jpg";

const TIME_FACTOR = 0.4 * Math.PI * 2;
const ORBIT_SPEED_FACTOR = 0.18;

export const THREE_FLYBY_BODIES = [
  { type: "sun", k: 0.1, radius: 0.013, boundsX: 1.28, boundsY: 1.28 },
  { type: "mercury", k: 0.124, radius: 0.0085 },
  { type: "venus", k: 0.116, radius: 0.0115 },
  { type: "earth", k: 0.118, radius: 0.012, boundsX: 1.95, boundsY: 1.5 },
  { type: "mars", k: 0.122, radius: 0.0105 },
  { type: "jupiter", k: 0.11, radius: 0.014, boundsX: 2.15, boundsY: 1.55 },
  { type: "saturn", k: 0.105, radius: 0.01, boundsX: 2.75, boundsY: 1.75 },
  { type: "uranus", k: 0.113, radius: 0.0115 },
  { type: "neptune", k: 0.113, radius: 0.012 },
];

const BODY_CONFIG = {
  sun: { map: sunMap, daylength: 600, tilt: 0, star: true },
  mercury: { map: mercuryMap, bump: mercuryBump, daylength: 4222.6, tilt: 0.03 },
  venus: { map: venusMap, bump: venusBump, daylength: 2802, tilt: 2.64 },
  earth: { map: earthMap, bump: earthBump, specular: earthSpecular, daylength: 24, tilt: 23.44 },
  mars: { map: marsMap, bump: marsBump, daylength: 24.7, tilt: 25.19 },
  jupiter: { map: jupiterMap, daylength: 9.9, tilt: 3.13 },
  saturn: { map: saturnMap, daylength: 10.7, tilt: 26.73 },
  uranus: { map: uranusMap, daylength: 17.2, tilt: 82.23 },
  neptune: { map: neptuneMap, daylength: 16.1, tilt: 28.32 },
};

const ORBITERS = {
  earth: [
    { map: moonMap, bump: moonBump, radius: 0.27, distance: 1.72, speed: 0.62, phase: 1.1, tilt: 0.48 },
  ],
  jupiter: [
    { map: ioMap, radius: 0.11, distance: 1.45, speed: 1.05, phase: 0.2, tilt: 0.18 },
    { map: europaMap, radius: 0.09, distance: 1.68, speed: 0.76, phase: 1.4, tilt: -0.14 },
    { map: ganymedeMap, radius: 0.14, distance: 1.94, speed: 0.52, phase: 2.5, tilt: 0.12 },
    { map: callistoMap, radius: 0.13, distance: 2.2, speed: 0.35, phase: 3.6, tilt: -0.1 },
  ],
  saturn: [
    { map: titanMap, radius: 0.18, distance: 3.15, speed: 0.38, phase: 0.6, tilt: 0.3 },
  ],
};

const configureColorTexture = (texture) => {
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 8;
  return texture;
};

const makeRingGeometry = () => {
  const geometry = new THREE.RingGeometry(1.18, 2.58, 192);
  const position = geometry.attributes.position;
  const point = new THREE.Vector3();
  for (let index = 0; index < position.count; index += 1) {
    point.fromBufferAttribute(position, index);
    const radialUv = THREE.MathUtils.clamp((point.length() - 1.18) / (2.58 - 1.18), 0, 1);
    geometry.attributes.uv.setXY(index, radialUv, 1);
  }
  return geometry;
};

const makePhongMaterial = ({ map, bump, specular }, textureLoader, textures, materials) => {
  const colorMap = configureColorTexture(textureLoader.load(map));
  textures.add(colorMap);
  const material = new THREE.MeshPhongMaterial({
    map: colorMap,
    shininess: 5,
    toneMapped: true,
  });
  if (bump) {
    const bumpMap = textureLoader.load(bump);
    textures.add(bumpMap);
    material.bumpMap = bumpMap;
    material.bumpScale = 0.02;
  }
  if (specular) {
    const specularMap = textureLoader.load(specular);
    textures.add(specularMap);
    material.specularMap = specularMap;
  }
  materials.add(material);
  return material;
};

export function createThreePlanetFlyby(canvas) {
  const renderer = new THREE.WebGLRenderer({
    canvas,
    alpha: true,
    antialias: true,
    powerPreference: "high-performance",
  });
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 5000);
  camera.position.set(0, 0, 1000);
  camera.lookAt(0, 0, 0);

  const ambientLight = new THREE.AmbientLight(0xffffff, 0.1);
  const pointLight = new THREE.PointLight(0xffffff, 1.25, 0, 1.5);
  pointLight.castShadow = true;
  pointLight.shadow.mapSize.set(2048, 2048);
  pointLight.shadow.camera.near = 1;
  pointLight.shadow.camera.far = 5000;
  pointLight.shadow.radius = 12;
  pointLight.shadow.bias = -0.00015;
  scene.add(ambientLight, pointLight);

  const textureLoader = new THREE.TextureLoader();
  const textures = new Set();
  const materials = new Set();
  const geometries = new Set();
  const systems = new Map();
  const sphereGeometry = new THREE.SphereGeometry(1, 64, 64);
  geometries.add(sphereGeometry);

  Object.entries(BODY_CONFIG).forEach(([type, config]) => {
    const group = new THREE.Group();
    const axis = new THREE.Group();
    axis.rotation.z = THREE.MathUtils.degToRad(config.tilt);
    group.add(axis);

    let material;
    if (config.star) {
      const colorMap = configureColorTexture(textureLoader.load(config.map));
      textures.add(colorMap);
      material = new THREE.MeshBasicMaterial({
        map: colorMap,
        toneMapped: false,
        color: new THREE.Color(2.5, 2.5, 2.5),
      });
      materials.add(material);
    } else {
      material = makePhongMaterial(config, textureLoader, textures, materials);
    }

    const body = new THREE.Mesh(sphereGeometry, material);
    body.castShadow = !config.star;
    body.receiveShadow = !config.star;
    axis.add(body);

    const rotatingMeshes = [body];
    const orbiters = [];

    if (type === "earth") {
      const cloudMap = configureColorTexture(textureLoader.load(earthClouds));
      const cloudAlpha = textureLoader.load(earthCloudAlpha);
      textures.add(cloudMap);
      textures.add(cloudAlpha);
      const cloudMaterial = new THREE.MeshPhongMaterial({
        map: cloudMap,
        alphaMap: cloudAlpha,
        transparent: true,
        opacity: 0.84,
        depthWrite: false,
      });
      materials.add(cloudMaterial);
      const clouds = new THREE.Mesh(sphereGeometry, cloudMaterial);
      clouds.scale.setScalar(1.007);
      clouds.receiveShadow = true;
      axis.add(clouds);
      rotatingMeshes.push(clouds);
    }

    if (type === "saturn") {
      const ringTexture = configureColorTexture(textureLoader.load(saturnRingMap));
      textures.add(ringTexture);
      const ringMaterial = new THREE.MeshBasicMaterial({
        map: ringTexture,
        side: THREE.DoubleSide,
        transparent: true,
        alphaTest: 0.025,
        depthWrite: false,
        toneMapped: false,
      });
      materials.add(ringMaterial);
      const ringGeometry = makeRingGeometry();
      geometries.add(ringGeometry);
      const rings = new THREE.Mesh(ringGeometry, ringMaterial);
      rings.rotation.x = THREE.MathUtils.degToRad(58);
      axis.add(rings);
    }

    (ORBITERS[type] || []).forEach((orbiterConfig) => {
      const orbit = new THREE.Group();
      orbit.rotation.x = orbiterConfig.tilt;
      const orbiterMaterial = makePhongMaterial(orbiterConfig, textureLoader, textures, materials);
      const orbiter = new THREE.Mesh(sphereGeometry, orbiterMaterial);
      orbiter.scale.setScalar(orbiterConfig.radius);
      orbiter.position.x = orbiterConfig.distance;
      orbiter.castShadow = true;
      orbiter.receiveShadow = true;
      orbit.add(orbiter);
      group.add(orbit);
      orbiters.push({ orbit, ...orbiterConfig });
    });

    group.visible = false;
    scene.add(group);
    systems.set(type, { group, body, rotatingMeshes, orbiters, config });
  });

  let width = 1;
  let height = 1;
  let activeType = "";

  const resize = (nextWidth, nextHeight, pixelRatio = 1) => {
    width = Math.max(1, nextWidth);
    height = Math.max(1, nextHeight);
    camera.left = -width / 2;
    camera.right = width / 2;
    camera.top = height / 2;
    camera.bottom = -height / 2;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(pixelRatio, 2));
    renderer.setSize(width, height, false);
  };

  const hide = () => {
    if (!activeType) return;
    const active = systems.get(activeType);
    if (active) active.group.visible = false;
    activeType = "";
  };

  const update = ({ type, centerX, centerY, radius, elapsedTime }) => {
    if (activeType !== type) {
      hide();
      activeType = type;
    }
    const system = systems.get(type);
    if (!system) return;

    const elapsedSeconds = elapsedTime / 1000;
    system.group.visible = true;
    system.group.position.set(centerX - width / 2, height / 2 - centerY, 0);
    system.group.scale.setScalar(radius);
    system.group.rotation.z = type === "saturn" ? -0.08 : 0;

    const rotation = elapsedSeconds * TIME_FACTOR / system.config.daylength;
    system.rotatingMeshes.forEach((mesh, index) => {
      mesh.rotation.y = rotation * (index === 0 ? 1 : 1.035);
    });
    system.orbiters.forEach((orbiter) => {
      orbiter.orbit.rotation.y = elapsedSeconds * orbiter.speed * ORBIT_SPEED_FACTOR + orbiter.phase;
    });

    const lightOffset = Math.max(90, radius * 5.5);
    pointLight.position.set(
      system.group.position.x - lightOffset * 0.72,
      system.group.position.y + lightOffset * 0.55,
      lightOffset,
    );
  };

  const render = () => {
    renderer.render(scene, camera);
  };

  const dispose = () => {
    hide();
    geometries.forEach((geometry) => geometry.dispose());
    materials.forEach((material) => material.dispose());
    textures.forEach((texture) => texture.dispose());
    renderer.dispose();
  };

  return { resize, update, hide, render, dispose };
}

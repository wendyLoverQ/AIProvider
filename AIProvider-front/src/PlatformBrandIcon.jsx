import { useId } from "react";
import { siGooglegemini, siTiktok, siX, siXiaohongshu } from "simple-icons";

const ICONS = { X: siX, XIAOHONGSHU: siXiaohongshu, DOUYIN: siTiktok, GEMINI: siGooglegemini };
const LABELS = { X: "X", XIAOHONGSHU: "小红书", DOUYIN: "抖音", GEMINI: "Gemini" };

export default function PlatformBrandIcon({ platform, className = "" }) {
  const gradientId = `gemini-brand-${useId().replace(/:/g, "")}`;
  const icon = ICONS[platform];
  const label = LABELS[platform] || platform;
  if (!icon) return null;

  if (platform === "DOUYIN") return <svg className={`platform-brand-icon brand-douyin ${className}`.trim()} role="img" aria-label={`${label} 品牌图标`} viewBox="0 0 24 24">
    <path className="douyin-cyan" d={icon.path} transform="translate(-.55 .45)" />
    <path className="douyin-pink" d={icon.path} transform="translate(.55 -.45)" />
    <path className="douyin-core" d={icon.path} />
  </svg>;

  if (platform === "GEMINI") return <svg className={`platform-brand-icon brand-gemini ${className}`.trim()} role="img" aria-label={`${label} 品牌图标`} viewBox="0 0 24 24">
    <defs><linearGradient id={gradientId} x1="2" y1="22" x2="22" y2="2" gradientUnits="userSpaceOnUse"><stop stopColor="#4e8cff" /><stop offset=".52" stopColor="#8e75e8" /><stop offset="1" stopColor="#ff72b6" /></linearGradient></defs>
    <path d={icon.path} fill={`url(#${gradientId})`} />
  </svg>;

  return <svg className={`platform-brand-icon brand-${platform.toLowerCase()} ${className}`.trim()} role="img" aria-label={`${label} 品牌图标`} viewBox="0 0 24 24"><path d={icon.path} /></svg>;
}

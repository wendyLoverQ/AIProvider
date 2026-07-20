import { CheckCircle, Info, Warning, WarningCircle, X } from "@phosphor-icons/react";
import { useEffect } from "react";
import "./UiToast.css";

const ICONS = { success: CheckCircle, error: WarningCircle, warning: Warning, info: Info };

export default function UiToast({ message, tone = "info", onDismiss }) {
  useEffect(() => {
    if (!message || !onDismiss) return undefined;
    const timer = window.setTimeout(onDismiss, tone === "error" ? 4000 : 2500);
    return () => window.clearTimeout(timer);
  }, [message, onDismiss, tone]);

  if (!message) return null;
  const Icon = ICONS[tone] || Info;
  return <div className={`ui-toast is-${tone}`} role={tone === "error" ? "alert" : "status"} aria-live={tone === "error" ? "assertive" : "polite"}>
    <Icon weight="fill" aria-hidden="true" />
    <span>{message}</span>
    {onDismiss && <button type="button" aria-label="关闭消息" onClick={onDismiss}><X /></button>}
  </div>;
}

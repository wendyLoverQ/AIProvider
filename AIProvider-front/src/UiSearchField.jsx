import { MagnifyingGlass } from "@phosphor-icons/react";
import "./UiSearchField.css";

export default function UiSearchField({ className = "", children, type = "text", ...inputProps }) {
  return <div className={`ui-search-field ${className}`.trim()}>
    <input type={type} {...inputProps} />
    <MagnifyingGlass aria-hidden="true" />
    {children}
  </div>;
}

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { applyStoredUiTheme } from "../../AIProvider-front/src/uiTheme";
import "./mobile.css";

applyStoredUiTheme();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

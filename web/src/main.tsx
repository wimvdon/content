import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { RaindropCallback } from "./components/RaindropCallback";
import "./index.css";
import { language } from "./lib/i18n";
import { CALLBACK_PATH as RAINDROP_CALLBACK } from "./lib/raindrop";

document.documentElement.lang = language;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {/* Raindrop's callback belongs to the Android app (#112): a page that hands it over. */}
    {location.pathname === RAINDROP_CALLBACK ? <RaindropCallback /> : <App />}
  </StrictMode>,
);

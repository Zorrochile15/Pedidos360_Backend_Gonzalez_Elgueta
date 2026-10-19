import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import { I18n } from "aws-amplify/utils";
import { translations } from "@aws-amplify/ui-react";

import "./index.css";
import "./config/amplify";

import App from "./App.jsx";
import { AuthProvider } from "./auth/AuthContext";

I18n.putVocabularies(translations);
I18n.setLanguage("es");

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>
);
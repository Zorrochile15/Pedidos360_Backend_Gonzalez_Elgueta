import { Routes, Route } from "react-router-dom";

import Navbar from "./components/Navbar";
import ProtectedRoute from "./auth/ProtectedRoute";

import Home from "./pages/Home";
import Login from "./pages/Login";
import Admin from "./pages/Admin";
import Local from "./pages/Local";

function App() {
  return (
    <>
      <Navbar />

      <Routes>
        <Route path="/" element={<Home />} />

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={["ADMIN"]}>
              <Admin />
            </ProtectedRoute>
          }
        />

        <Route
          path="/local"
          element={
            <ProtectedRoute allowedRoles={["LOCAL"]}>
              <Local />
            </ProtectedRoute>
          }
        />
      </Routes>
    </>
  );
}

export default App;
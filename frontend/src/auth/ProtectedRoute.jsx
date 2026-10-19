import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

function ProtectedRoute({ children, allowedRoles }) {
  const { usuario, cargando } = useAuth();

  if (cargando) {
    return <p>Comprobando sesión...</p>;
  }

  if (!usuario) {
    return <Navigate to="/login" replace />;
  }

  if (
    allowedRoles &&
    !allowedRoles.includes(usuario.rol)
  ) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export default ProtectedRoute;
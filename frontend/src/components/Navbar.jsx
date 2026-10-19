import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

function Navbar() {
  const { usuario, logout } = useAuth();
  const navigate = useNavigate();

  async function cerrarSesion() {
    await logout();
    navigate("/");
  }

  return (
    <nav className="navbar">
      <Link to="/" className="navbar-logo">
        Pedidos360
      </Link>

      <ul className="navbar-links">
        <li>
          <Link to="/">Inicio</Link>
        </li>

        {!usuario && (
          <li>
            <Link to="/login">
              Iniciar sesión
            </Link>
          </li>
        )}

        {usuario?.rol === "ADMIN" && (
          <li>
            <Link to="/admin">
              Administración
            </Link>
          </li>
        )}

        {usuario && (
          <li>
            <button
              type="button"
              onClick={cerrarSesion}
            >
              Cerrar sesión
            </button>
          </li>
        )}
      </ul>
    </nav>
  );
}

export default Navbar;
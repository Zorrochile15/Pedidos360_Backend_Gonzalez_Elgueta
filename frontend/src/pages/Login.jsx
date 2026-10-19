import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { Authenticator } from "@aws-amplify/ui-react";
import "@aws-amplify/ui-react/styles.css";

import { useAuth } from "../auth/AuthContext";

function Login() {
  const { usuario, cargarUsuario } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (usuario?.rol === "ADMIN") {
      navigate("/admin", { replace: true });
    }

    if (usuario?.rol === "LOCAL") {
      navigate("/local", { replace: true });
    }
  }, [usuario, navigate]);

  async function sincronizarSesion() {
    await cargarUsuario();
  }

  return (
    <main>
      <section>
        <h1>Iniciar sesión</h1>

        <p>
          Ingresa con tu cuenta de Pedidos360.
        </p>

        <Authenticator
          loginMechanisms={["email"]}
        >
          {() => (
            <div>
              <p>
                Inicio de sesión correcto.
                Verificando permisos...
              </p>

              <button
                type="button"
                onClick={sincronizarSesion}
              >
                Continuar
              </button>
            </div>
          )}
        </Authenticator>
      </section>
    </main>
  );
}

export default Login;
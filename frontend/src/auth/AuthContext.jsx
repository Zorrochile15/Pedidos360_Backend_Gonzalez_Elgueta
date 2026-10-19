import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";

import {
  fetchAuthSession,
  signOut,
} from "aws-amplify/auth";

const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [cargando, setCargando] = useState(true);

  const cargarUsuario = useCallback(async () => {
    try {
      const session = await fetchAuthSession();

      const accessToken = session.tokens?.accessToken;
      const idToken = session.tokens?.idToken;

      if (!accessToken) {
        setUsuario(null);
        return null;
      }

      const accessClaims = accessToken.payload;
      const idClaims = idToken?.payload || {};

      const grupos =
        accessClaims["cognito:groups"] ||
        idClaims["cognito:groups"] ||
        [];

      let rol = null;

      if (grupos.includes("ADMIN")) {
        rol = "ADMIN";
      } else if (grupos.includes("LOCAL")) {
        rol = "LOCAL";
      }

      const usuarioCognito = {
        email: idClaims.email || "",
        rol: rol,
        grupos: grupos,
      };

      setUsuario(usuarioCognito);

      return usuarioCognito;
    } catch (error) {
      setUsuario(null);
      return null;
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargarUsuario();
  }, [cargarUsuario]);

  async function logout() {
    await signOut();
    setUsuario(null);
  }

  return (
    <AuthContext.Provider
      value={{
        usuario,
        cargando,
        cargarUsuario,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
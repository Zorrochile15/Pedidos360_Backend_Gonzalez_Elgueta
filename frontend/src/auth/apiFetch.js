import { getAccessToken } from "../auth/tokenService";

const API_URL = import.meta.env.VITE_API_URL;

export async function apiFetch(endpoint, options = {}) {
 const token = await getAccessToken();

const headers = {
  "Content-Type": "application/json",
  ...options.headers,
};

const esRutaPublica = endpoint.startsWith("/api/public/");

if (token && !esRutaPublica) {
  headers.Authorization = `Bearer ${token}`;
}
  const response = await fetch(
    `${API_URL}${endpoint}`,
    {
      ...options,
      headers,
    }
  );

  if (response.status === 401) {
    throw new Error("No autenticado");
  }

  if (response.status === 403) {
    throw new Error("No autorizado");
  }

  if (!response.ok) {
    throw new Error(
      `Error en la petición: ${response.status}`
    );
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}
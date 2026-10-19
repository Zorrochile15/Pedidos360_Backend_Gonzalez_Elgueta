import { apiFetch } from "../auth/apiFetch";

export function getInsumos() {
  return apiFetch("/api/public/insumos");
}

export function crearInsumo(insumo) {
  return apiFetch("/api/admin/insumos", {
    method: "POST",
    body: JSON.stringify(insumo),
  });
}

export function eliminarInsumo(id) {
  return apiFetch(`/api/admin/insumos/${id}`, {
    method: "DELETE",
  });
}

export function getLocales() {
  return apiFetch("/api/admin/locales");
}

export function crearLocal(local) {
  return apiFetch("/api/admin/locales", {
    method: "POST",
    body: JSON.stringify(local),
  });
}

export function eliminarLocal(id) {
  return apiFetch(`/api/admin/locales/${id}`, {
    method: "DELETE",
  });
}

export function getPedidos() {
  return apiFetch("/api/admin/pedidos");
}

export function crearPedido(pedido) {
  return apiFetch("/api/locales/pedidos", {
    method: "POST",
    body: JSON.stringify(pedido),
  });
}

export function getPedidosLocal(localId) {
  return apiFetch(
    `/api/locales/pedidos?localId=${localId}`
  );
}
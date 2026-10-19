import { useEffect, useState } from "react";
import { getPedidos } from "../services/api";

function PedidosAdmin() {
  const [pedidos, setPedidos] = useState([]);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    cargarPedidos();
  }, []);

  async function cargarPedidos() {
    try {
      const datos = await getPedidos();
      setPedidos(datos);
    } catch (error) {
      console.error("Error al cargar pedidos:", error);
    } finally {
      setCargando(false);
    }
  }

  function formatearFecha(fecha) {
    if (!fecha) {
      return "-";
    }

    return new Date(fecha).toLocaleString("es-CL");
  }

  if (cargando) {
    return (
      <section>
        <h2>Pedidos</h2>
        <p>Cargando pedidos...</p>
      </section>
    );
  }

  return (
    <section>
      <h2>Pedidos recibidos</h2>

      {pedidos.length === 0 ? (
        <p>No existen pedidos registrados.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Local</th>
              <th>Dirección</th>
              <th>Fecha</th>
              <th>Estado</th>
            </tr>
          </thead>

          <tbody>
            {pedidos.map((pedido) => (
              <tr key={pedido.id}>
                <td>{pedido.id}</td>

                <td>
                  {pedido.local?.nombre || "-"}
                </td>

                <td>
                  {pedido.local?.direccion || "-"}
                </td>

                <td>
                  {formatearFecha(pedido.fechaCreacion)}
                </td>

                <td>
                  {pedido.estado || "-"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

export default PedidosAdmin;
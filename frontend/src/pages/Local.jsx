import { useEffect, useState } from "react";
import {
  getInsumos,
  crearPedido,
  getPedidosLocal,
} from "../services/api";

function PedidoLocal() {
  const LOCAL_ID = 3;

  const [insumos, setInsumos] = useState([]);
  const [cantidades, setCantidades] = useState({});
  const [pedidos, setPedidos] = useState([]);
  const [mensaje, setMensaje] = useState("");
  const [cargandoPedidos, setCargandoPedidos] = useState(true);

  useEffect(() => {
    cargarInsumos();
    cargarPedidos();
  }, []);

  async function cargarInsumos() {
    try {
      const datos = await getInsumos();
      setInsumos(datos);
    } catch (error) {
      console.error("Error al cargar insumos:", error);
    }
  }

  async function cargarPedidos() {
    try {
      const datos = await getPedidosLocal(LOCAL_ID);
      setPedidos(datos);
    } catch (error) {
      console.error("Error al cargar pedidos:", error);
    } finally {
      setCargandoPedidos(false);
    }
  }

  function cambiarCantidad(id, cantidad) {
    const cantidadNumerica = Number(cantidad);

    setCantidades({
      ...cantidades,
      [id]: cantidadNumerica,
    });

    setMensaje("");
  }

  function calcularTotal() {
    return insumos.reduce((total, insumo) => {
      const cantidad = cantidades[insumo.id] || 0;

      return total + insumo.precio * cantidad;
    }, 0);
  }

  function cantidadProductos() {
    return Object.values(cantidades).reduce(
      (total, cantidad) => total + cantidad,
      0
    );
  }

  function formatearPrecio(precio) {
    return new Intl.NumberFormat("es-CL", {
      style: "currency",
      currency: "CLP",
    }).format(precio);
  }

  function formatearFecha(fecha) {
    if (!fecha) {
      return "-";
    }

    return new Date(fecha).toLocaleString("es-CL");
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const productosSeleccionados = insumos.filter(
      (insumo) => (cantidades[insumo.id] || 0) > 0
    );

    if (productosSeleccionados.length === 0) {
      setMensaje("Debes seleccionar al menos un insumo.");
      return;
    }

    const nuevoPedido = {
      local: {
        id: LOCAL_ID,
      },
    };

    try {
      await crearPedido(nuevoPedido);

      setCantidades({});
      setMensaje("Pedido generado correctamente.");

      await cargarPedidos();
    } catch (error) {
      console.error("Error al generar pedido:", error);
      setMensaje("No fue posible generar el pedido.");
    }
  }

  return (
    <section className="pedido-local">
      <div className="pedido-header">
        <div>
          <h2>Nuevo Pedido</h2>

          <p>
            Selecciona los insumos y las cantidades que necesitas.
          </p>
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="pedido-tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>Insumo</th>
                <th>Precio</th>
                <th>Stock</th>
                <th>Cantidad</th>
                <th>Subtotal</th>
              </tr>
            </thead>

            <tbody>
              {insumos.map((insumo) => {
                const cantidad =
                  cantidades[insumo.id] || 0;

                const subtotal =
                  insumo.precio * cantidad;

                return (
                  <tr key={insumo.id}>
                    <td>
                      <strong>
                        {insumo.nombre}
                      </strong>

                      <small className="producto-descripcion">
                        {insumo.descripcion}
                      </small>
                    </td>

                    <td>
                      {formatearPrecio(insumo.precio)}
                    </td>

                    <td>
                      {insumo.stock}
                    </td>

                    <td>
                      <input
                        className="cantidad-input"
                        type="number"
                        min="0"
                        max={insumo.stock}
                        value={cantidad}
                        onChange={(event) =>
                          cambiarCantidad(
                            insumo.id,
                            event.target.value
                          )
                        }
                      />
                    </td>

                    <td>
                      {formatearPrecio(subtotal)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="resumen-pedido">
          <h3>Resumen del pedido</h3>

          <div className="resumen-linea">
            <span>Productos seleccionados</span>

            <strong>
              {cantidadProductos()}
            </strong>
          </div>

          <div className="resumen-linea total">
            <span>Total</span>

            <strong>
              {formatearPrecio(calcularTotal())}
            </strong>
          </div>

          <button
            type="submit"
            className="boton-pedido"
          >
            Generar pedido
          </button>

          {mensaje && (
            <p className="mensaje-pedido">
              {mensaje}
            </p>
          )}
        </div>
      </form>

      <div className="mis-pedidos">
        <h2>Mis pedidos</h2>

        {cargandoPedidos ? (
          <p>Cargando pedidos...</p>
        ) : pedidos.length === 0 ? (
          <p>No tienes pedidos registrados.</p>
        ) : (
          <div className="pedido-tabla-contenedor">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Local</th>
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
                      {formatearFecha(
                        pedido.fechaCreacion
                      )}
                    </td>

                    <td>
                      {pedido.estado || "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

export default PedidoLocal;
import { useEffect, useState } from "react";
import {
  getInsumos,
  crearPedido,
} from "../services/api";

function PedidoLocal() {
  const [insumos, setInsumos] = useState([]);
  const [cantidades, setCantidades] = useState({});
  const [mensaje, setMensaje] = useState("");

  useEffect(() => {
    cargarInsumos();
  }, []);

  async function cargarInsumos() {
    try {
      const datos = await getInsumos();
      setInsumos(datos);
    } catch (error) {
      console.error("Error al cargar insumos:", error);
      setMensaje("No fue posible cargar los insumos.");
    }
  }

  function cambiarCantidad(id, cantidad) {
    setCantidades({
      ...cantidades,
      [id]: Number(cantidad),
    });

    setMensaje("");
  }

  function calcularTotal() {
    return insumos.reduce((total, insumo) => {
      const cantidad = cantidades[insumo.id] || 0;

      return total + insumo.precio * cantidad;
    }, 0);
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
        id: 3,
      },
    };

    try {
      await crearPedido(nuevoPedido);

      setCantidades({});
      setMensaje("Pedido generado correctamente.");
    } catch (error) {
      console.error("Error al generar pedido:", error);
      setMensaje("No fue posible generar el pedido.");
    }
  }

  return (
    <section>
      <h2>Nuevo Pedido</h2>

      <form onSubmit={handleSubmit}>
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
              const cantidad = cantidades[insumo.id] || 0;
              const subtotal = insumo.precio * cantidad;

              return (
                <tr key={insumo.id}>
                  <td>{insumo.nombre}</td>

                  <td>${insumo.precio}</td>

                  <td>{insumo.stock}</td>

                  <td>
                    <input
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

                  <td>${subtotal}</td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <h3>
          Total: ${calcularTotal()}
        </h3>

        <button type="submit">
          Generar pedido
        </button>
      </form>

      {mensaje && <p>{mensaje}</p>}
    </section>
  );
}

export default PedidoLocal;
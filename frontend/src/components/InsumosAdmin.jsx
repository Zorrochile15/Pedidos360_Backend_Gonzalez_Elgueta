import { useEffect, useState } from "react";
import {
  getInsumos,
  crearInsumo,
  eliminarInsumo,
} from "../services/api";

function InsumosAdmin() {
  const [insumos, setInsumos] = useState([]);

  const [formulario, setFormulario] = useState({
    nombre: "",
    descripcion: "",
    precio: "",
    stock: "",
  });

  useEffect(() => {
    cargarInsumos();
  }, []);

  async function cargarInsumos() {
    const datos = await getInsumos();
    setInsumos(datos);
  }

  function handleChange(event) {
    const { name, value } = event.target;

    setFormulario({
      ...formulario,
      [name]: value,
    });
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const nuevoInsumo = {
      nombre: formulario.nombre,
      descripcion: formulario.descripcion,
      precio: Number(formulario.precio),
      stock: Number(formulario.stock),
    };

    await crearInsumo(nuevoInsumo);

    setFormulario({
      nombre: "",
      descripcion: "",
      precio: "",
      stock: "",
    });

    cargarInsumos();
  }

  async function handleEliminar(id) {
    await eliminarInsumo(id);
    cargarInsumos();
  }

  return (
    <section>
      <h2>Gestión de Insumos</h2>

      <h3>Crear nuevo insumo</h3>

      <form onSubmit={handleSubmit}>
        <div>
          <label>Nombre</label>

          <input
            type="text"
            name="nombre"
            value={formulario.nombre}
            onChange={handleChange}
            required
          />
        </div>

        <div>
          <label>Descripción</label>

          <input
            type="text"
            name="descripcion"
            value={formulario.descripcion}
            onChange={handleChange}
            required
          />
        </div>

        <div>
          <label>Precio</label>

          <input
            type="number"
            name="precio"
            value={formulario.precio}
            onChange={handleChange}
            min="0"
            required
          />
        </div>

        <div>
          <label>Stock</label>

          <input
            type="number"
            name="stock"
            value={formulario.stock}
            onChange={handleChange}
            min="0"
            required
          />
        </div>

        <button type="submit">
          Crear insumo
        </button>
      </form>

      <h3>Insumos registrados</h3>

      {insumos.length === 0 ? (
        <p>No existen insumos registrados.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Nombre</th>
              <th>Descripción</th>
              <th>Precio</th>
              <th>Stock</th>
              <th>Acción</th>
            </tr>
          </thead>

          <tbody>
            {insumos.map((insumo) => (
              <tr key={insumo.id}>
                <td>{insumo.id}</td>
                <td>{insumo.nombre}</td>
                <td>{insumo.descripcion}</td>
                <td>${insumo.precio}</td>
                <td>{insumo.stock}</td>

                <td>
                  <button
                    type="button"
                    onClick={() => handleEliminar(insumo.id)}
                  >
                    Eliminar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

export default InsumosAdmin;
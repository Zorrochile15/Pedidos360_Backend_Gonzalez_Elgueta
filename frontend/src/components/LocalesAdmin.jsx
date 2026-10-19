import { useEffect, useState } from "react";
import {
  getLocales,
  crearLocal,
  eliminarLocal,
} from "../services/api";

function LocalesAdmin() {
  const [locales, setLocales] = useState([]);

  const [formulario, setFormulario] = useState({
    nombre: "",
    direccion: "",
    telefono: "",
  });

  useEffect(() => {
    cargarLocales();
  }, []);

  async function cargarLocales() {
    try {
      const datos = await getLocales();
      setLocales(datos);
    } catch (error) {
      console.error("Error al cargar locales:", error);
    }
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

    const nuevoLocal = {
      nombre: formulario.nombre,
      direccion: formulario.direccion,
      telefono: formulario.telefono,
    };

    try {
      await crearLocal(nuevoLocal);

      setFormulario({
        nombre: "",
        direccion: "",
        telefono: "",
      });

      await cargarLocales();
    } catch (error) {
      console.error("Error al crear local:", error);
    }
  }

  async function handleEliminar(id) {
    try {
      await eliminarLocal(id);

      setLocales((localesActuales) =>
        localesActuales.filter(
          (local) => local.id !== id
        )
      );
    } catch (error) {
      console.error("Error al eliminar local:", error);
    }
  }

  return (
    <section>
      <h2>Gestión de Locales</h2>

      <h3>Crear nuevo local</h3>

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
          <label>Dirección</label>

          <input
            type="text"
            name="direccion"
            value={formulario.direccion}
            onChange={handleChange}
            required
          />
        </div>

        <div>
          <label>Teléfono</label>

          <input
            type="text"
            name="telefono"
            value={formulario.telefono}
            onChange={handleChange}
            required
          />
        </div>

        <button type="submit">
          Crear local
        </button>
      </form>

      <h3>Locales registrados</h3>

      {locales.length === 0 ? (
        <p>No existen locales registrados.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Nombre</th>
              <th>Dirección</th>
              <th>Teléfono</th>
              <th>Acción</th>
            </tr>
          </thead>

          <tbody>
            {locales.map((local) => (
              <tr key={local.id}>
                <td>{local.id}</td>
                <td>{local.nombre}</td>
                <td>{local.direccion}</td>
                <td>{local.telefono}</td>

                <td>
                  <button
                    type="button"
                    onClick={() =>
                      handleEliminar(local.id)
                    }
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

export default LocalesAdmin;
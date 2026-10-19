import { useState } from "react";

import InsumosAdmin from "../components/InsumosAdmin";
import LocalesAdmin from "../components/LocalesAdmin";
import PedidosAdmin from "../components/PedidosAdmin";

function Admin() {
  const [seccionActiva, setSeccionActiva] = useState("insumos");

  return (
    <main>
      <div className="admin-header">
        <div>
          <h1>Panel de Administración</h1>

          <p>
            Gestiona los insumos, locales y pedidos de Pedidos360.
          </p>
        </div>
      </div>

      <div className="admin-menu">
        <button
          type="button"
          className={
            seccionActiva === "insumos"
              ? "admin-tab activo"
              : "admin-tab"
          }
          onClick={() => setSeccionActiva("insumos")}
        >
          Insumos
        </button>

        <button
          type="button"
          className={
            seccionActiva === "locales"
              ? "admin-tab activo"
              : "admin-tab"
          }
          onClick={() => setSeccionActiva("locales")}
        >
          Locales
        </button>

        <button
          type="button"
          className={
            seccionActiva === "pedidos"
              ? "admin-tab activo"
              : "admin-tab"
          }
          onClick={() => setSeccionActiva("pedidos")}
        >
          Pedidos
        </button>
      </div>

      <div className="admin-contenido">
        {seccionActiva === "insumos" && <InsumosAdmin />}

        {seccionActiva === "locales" && <LocalesAdmin />}

        {seccionActiva === "pedidos" && <PedidosAdmin />}
      </div>
    </main>
  );
}

export default Admin;
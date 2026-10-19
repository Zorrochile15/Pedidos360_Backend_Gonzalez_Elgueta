import { useEffect, useState } from "react";
import { getInsumos } from "../services/api";
import InsumoCard from "../components/InsumoCard";

function Home() {
  const [insumos, setInsumos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function cargarInsumos() {
      try {
        const datos = await getInsumos();
        setInsumos(datos);
      } catch (error) {
        setError("No fue posible cargar el catálogo.");
        console.error(error);
      } finally {
        setCargando(false);
      }
    }

    cargarInsumos();
  }, []);

  return (
    <main>
      <section className="hero">
        <h1>Pedidos360</h1>

        <p>
          Plataforma de gestión de abastecimiento
          para locales comerciales.
        </p>
      </section>

      <section>
        <div className="catalogo-header">
          <div>
            <h2>Catálogo de insumos</h2>
            <p>
              Revisa los productos disponibles actualmente.
            </p>
          </div>
        </div>

        {cargando && <p>Cargando insumos...</p>}

        {error && <p>{error}</p>}

        {!cargando &&
          !error &&
          insumos.length === 0 && (
            <p>No existen insumos disponibles.</p>
          )}

        <div className="catalogo-grid">
          {insumos.map((insumo) => (
            <InsumoCard
              key={insumo.id}
              insumo={insumo}
            />
          ))}
        </div>
      </section>
    </main>
  );
}

export default Home;
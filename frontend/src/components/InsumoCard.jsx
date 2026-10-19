function InsumoCard({ insumo }) {
  const precioFormateado = new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
  }).format(insumo.precio);

  return (
    <article className="insumo-card">
      <div className="insumo-card-header">
        <h3>{insumo.nombre}</h3>

        <span
          className={
            insumo.stock > 0
              ? "stock disponible"
              : "stock agotado"
          }
        >
          {insumo.stock > 0 ? "Disponible" : "Agotado"}
        </span>
      </div>

      <p className="insumo-descripcion">
        {insumo.descripcion}
      </p>

      <div className="insumo-card-footer">
        <strong className="insumo-precio">
          {precioFormateado}
        </strong>

        <span>
          Stock: {insumo.stock}
        </span>
      </div>
    </article>
  );
}

export default InsumoCard;
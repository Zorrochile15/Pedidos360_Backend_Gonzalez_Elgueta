let insumos = [
  {
    id: 1,
    nombre: "Café en grano",
    descripcion: "Bolsa de café en grano de 1 kg",
    precio: 12990,
    stock: 25,
  },
  {
    id: 2,
    nombre: "Leche entera",
    descripcion: "Caja de 12 unidades de 1 litro",
    precio: 14990,
    stock: 40,
  },
  {
    id: 3,
    nombre: "Azúcar",
    descripcion: "Bolsa de azúcar de 5 kg",
    precio: 6990,
    stock: 30,
  },
];

let locales = [
  {
    id: 1,
    nombre: "Cafetería Central",
    direccion: "Av. Principal 123",
    telefono: "+56 9 1234 5678",
  },
  {
    id: 2,
    nombre: "Restaurante El Buen Sabor",
    direccion: "Los Aromos 456",
    telefono: "+56 9 8765 4321",
  },
];

let pedidos = [
  {
    id: 1,
    local: "Cafetería Central",
    fecha: "2026-09-21",
    estado: "PENDIENTE",
    total: 25980,
  },
];

function esperar() {
  return new Promise((resolve) => {
    setTimeout(resolve, 300);
  });
}

export async function getInsumos() {
  await esperar();

  return [...insumos];
}

export async function crearInsumo(nuevoInsumo) {
  await esperar();

  const insumo = {
    id: Date.now(),
    ...nuevoInsumo,
  };

  insumos.push(insumo);

  return insumo;
}

export async function eliminarInsumo(id) {
  await esperar();

  insumos = insumos.filter((insumo) => insumo.id !== id);
}

export async function getLocales() {
  await esperar();

  return [...locales];
}

export async function crearLocal(nuevoLocal) {
  await esperar();

  const local = {
    id: Date.now(),
    ...nuevoLocal,
  };

  locales.push(local);

  return local;
}

export async function eliminarLocal(id) {
  await esperar();

  locales = locales.filter((local) => local.id !== id);
}

export async function getPedidos() {
  await esperar();

  return [...pedidos];
}

export async function crearPedido(nuevoPedido) {
  await esperar();

  const pedido = {
    id: Date.now(),
    estado: "PENDIENTE",
    ...nuevoPedido,
  };

  pedidos.push(pedido);

  return pedido;
}

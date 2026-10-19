/// <reference path="../pb_data/types.d.ts" />

// Crea las colecciones que usa la app Fichero de Motores y configura el servidor.
migrate((app) => {
  const auth = "@request.auth.id != ''";

  // Cada motor, prueba o mantenimiento es un "item" con su JSON completo.
  const items = new Collection({
    type: "base",
    name: "items",
    listRule: auth,
    viewRule: auth,
    createRule: auth,
    updateRule: auth,
    deleteRule: null, // los borrados se marcan con "borrado", nunca se eliminan
    fields: [
      { name: "uid", type: "text", required: true, max: 64 },
      { name: "tipo", type: "select", required: true, maxSelect: 1, values: ["motor", "registro"] },
      { name: "motor_uid", type: "text", max: 64 },
      { name: "datos", type: "json", maxSize: 2000000 },
      { name: "actualizado", type: "number", onlyInt: true },
      { name: "borrado", type: "bool" },
      { name: "created", type: "autodate", onCreate: true, onUpdate: false },
      { name: "updated", type: "autodate", onCreate: true, onUpdate: true },
    ],
    indexes: [
      "CREATE UNIQUE INDEX idx_items_uid ON items (uid)",
      "CREATE INDEX idx_items_updated ON items (updated)",
    ],
  });
  app.save(items);

  const fotos = new Collection({
    type: "base",
    name: "fotos",
    listRule: auth,
    viewRule: auth,
    createRule: auth,
    updateRule: null,
    deleteRule: null,
    fields: [
      { name: "nombre", type: "text", required: true, max: 100 },
      { name: "motor_uid", type: "text", max: 64 },
      {
        name: "archivo", type: "file", required: true, maxSelect: 1, maxSize: 20971520,
        mimeTypes: ["image/jpeg", "image/png", "image/webp"], protected: true,
      },
      { name: "created", type: "autodate", onCreate: true, onUpdate: false },
      { name: "updated", type: "autodate", onCreate: true, onUpdate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_fotos_nombre ON fotos (nombre)"],
  });
  app.save(fotos);

  // Solo el administrador crea usuarios: nadie puede registrarse solo.
  const users = app.findCollectionByNameOrId("users");
  users.createRule = null;
  app.save(users);

  // Nombre del servidor y respaldo automático todos los días a las 3:00 (se guardan los últimos 14).
  const settings = app.settings();
  settings.meta.appName = "Fichero de Motores";
  settings.backups.cron = "0 3 * * *";
  settings.backups.cronMaxKeep = 14;
  app.save(settings);
}, (app) => {
  for (const name of ["fotos", "items"]) {
    try { app.delete(app.findCollectionByNameOrId(name)); } catch (e) {}
  }
});

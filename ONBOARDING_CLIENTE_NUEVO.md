# Checklist: instalar el sistema para un cliente nuevo

Pensado para cuando este sistema se instala de cero para un local real (una
instancia de backend + base de datos por cliente). Seguí estos pasos en
orden antes de entregarlo.

## 1. Variables de entorno propias para esta instalación

No uses los valores de fábrica del `application.properties` en producción.
Cada cliente instalado debería tener los suyos:

- `JWT_SECRET` — una cadena larga y aleatoria, distinta por cliente.
- `MASTER_KEY` — la clave maestra para recuperar contraseñas desde el
  panel. Se la das al dueño del local, no al personal.
- `DB_URL`, `DB_USER`, `DB_PASSWORD` — los de la base de datos real de esta
  instalación.
- `BACKUP_CRON` (opcional) — por defecto corre cada hora
  (`0 0 * * * *`); ver el comentario en `application.properties` si el
  local prefiere otra frecuencia.

Si te olvidás de alguna, el sistema avisa solo por log al arrancar (ver
`CredencialesPorDefectoWarner`) — revisá los logs del primer arranque.

## 2. Cambiar las contraseñas de fábrica

El sistema arranca con 4 usuarios de prueba (admin, cajero, cocina, mozo)
con contraseñas simples, pensadas solo para poder probar la app apenas se
levanta. **La primera vez que cada uno entra con esa contraseña, el panel
los obliga a poner una propia antes de dejarlos seguir** (no hace falta que
lo hagas vos a mano) — pero igual conviene avisarle al dueño del local que
cargue el personal real (Usuarios → Crear usuario) y no siga usando las
cuentas de prueba día a día.

## 3. Arrancar la instalación SIN el menú de ejemplo

La base arranca por defecto con un menú de ejemplo (empanadas, pizzas,
etc.) y dos clientes de prueba, pensado para la instancia de demo que le
mostrás a un cliente potencial con datos falsos. Para una instalación
nueva de un cliente real, mejor evitar ese menú de ejemplo desde el
arranque en vez de cargarlo y después borrarlo:

1. Al levantar el backend de esa instalación, definí la variable de
   entorno `CARGAR_MENU_EJEMPLO=false`. Arranca sin productos ni clientes
   cargados (los 4 usuarios de prueba sí se crean igual, para poder
   entrar la primera vez — ver paso 2).
2. Cargá el menú real del local desde **Gestor de Menú**.
3. Subí el logo y ajustá colores/horarios desde **Configuración**.

Si en algún momento una instalación ya arrancó CON el menú de ejemplo
(por ejemplo, te olvidaste de poner la variable), en **Configuración →
Puesta a punto para un cliente nuevo** hay un botón **"Borrar menú y
clientes de ejemplo"** que lo limpia después. Se niega solo si ya hay
pedidos cargados, para no romper una instalación que ya está en uso —
pensalo como una red de seguridad, no como el paso principal.

## 4. Verificar antes de entregar

- [ ] Variables de entorno propias configuradas (paso 1).
- [ ] Logs del primer arranque sin avisos de "credenciales de fábrica".
- [ ] Los 4 usuarios de prueba ya cambiaron su contraseña, o fueron
      reemplazados por usuarios reales del local.
- [ ] Menú de ejemplo borrado y menú real cargado.
- [ ] Un backup manual de prueba corrido desde Configuración
      ("Hacer backup ahora") para confirmar que `mysqldump` está
      accesible en ese servidor.
- [ ] Probado un pedido de punta a punta (crear → cocina → cobrar).

## Nota sobre el modelo de instalación

Este sistema está armado para **una instancia completa por cliente**
(su propia base de datos, su propio backend, sus propias variables de
entorno) — no es una plataforma donde varios locales comparten una misma
base con los datos separados por cuenta. Si en algún momento la idea pasa
a ser vender "una cuenta más" sobre una plataforma compartida en vez de
instalar de cero por cliente, eso requiere trabajo de arquitectura
adicional (aislar los datos de cada local) que conviene planear con
tiempo, no improvisar sobre la marcha.

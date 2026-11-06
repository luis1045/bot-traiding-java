# Bot de trading spot BTC/USDT — estrategia de grillas (modo manual)

Asistente de trading **spot** para el par **BTCUSDT** basado en **grillas (grid trading)**, construido con
**Spring Boot 4 + WebFlux (reactivo) + Spring Data R2DBC + PostgreSQL (Neon)**.

En esta fase el bot **no ejecuta órdenes**: vigila el precio y **te avisa** cuando entra en la zona de
compra o de venta de un nivel. Tú haces la operación en Binance y la registras desde el panel (cantidad,
precio y, en las ventas, el profit). Todo se guarda con los mismos parámetros del proyecto
(comisiones, PnL en BTC/USDT, reinversión por nivel y reporte). No se envían claves ni dinero real.

---

## 1. Cómo funciona la estrategia

### 1.1 Definición de los niveles

El usuario define un **piso**, un **techo** y **n niveles**. Los límites se reparten de forma
**lineal**:

```
paso = (techo - piso) / n
L0 = piso, L1, L2, ... Ln = techo
```

El **nivel i** ocupa la banda `[L(i-1), L(i)]`. Cuando el precio toca el borde inferior el bot te
**avisa para comprar**; cuando toca el superior, te **avisa para vender**, repitiendo ciclos (por eso
un nivel puede acumular más operaciones que otro).

Ejemplo con piso = 78.000, techo = 88.000 y n = 5:

| Nivel | Banda           | % del inventario | BTC asignado (inventario 0,5) |
|-------|-----------------|------------------|-------------------------------|
| 1     | 78.000–80.000   | 10 %             | 0,050                         |
| 2     | 80.000–82.000   | 15 %             | 0,075                         |
| 3     | 82.000–84.000   | 20 %             | 0,100                         |
| 4     | 84.000–86.000   | 25 %             | 0,125                         |
| 5     | 86.000–88.000   | 30 %             | 0,150                         |

### 1.2 Reparto del 100 %

Los porcentajes por nivel **deben sumar 100 %**. Ese porcentaje reparte el **inventario de BTC**
entre los niveles, de modo que cada nivel opera de forma independiente con su propia cantidad:

```
btcAsignado(i) = inventarioBTC * porcentaje(i) / 100
suma(porcentaje) = 100 %  =>  suma(btcAsignado) = inventarioBTC
```

Si no se envían porcentajes, se reparten **a partes iguales**.

### 1.3 Ciclo de operación (manual)

El motor **solo vigila y avisa**; cada operación la registras tú desde el panel:

- **Nivel sosteniendo BTC** (`holding = true`) y precio ≥ borde superior → **alerta de VENTA**.
- **Nivel en efectivo** (`holding = false`) y precio ≤ borde inferior → **alerta de COMPRA**.

Se crea **como máximo una alerta pendiente por nivel** (no se repite en cada tick). Al registrarla
indicas la **cantidad de BTC** y el **precio** reales; en las ventas indicas además el **profit**
(se estima solo con el precio de entrada y las comisiones, y puedes ajustarlo). Cada operación se
guarda en `trade_history` con su ganancia en **USDT** y en **BTC**:

```
pnlUSDT = (precioVenta * qty) - comisionVenta - precioEntrada * qty * (1 + comisionCompra)
pnlBTC  = pnlUSDT / precioVenta
```

Si descartas una alerta, no se vuelve a avisar hasta que el precio **salga de esa zona** y vuelva
a entrar. Si registras una compra y la reserva declarada no alcanza, la API te lo advierte y puedes
confirmarla igualmente (la reserva puede quedar negativa, como reflejo de un aporte externo).

Las **ganancias se reinvierten por nivel**: al registrar una venta, la cantidad con la que operará
el siguiente ciclo crece (la próxima compra en el borde inferior se financia con lo recaudado en esa
venta):

```
cantidad(i) = recaudadoVenta(i) / (bordeInferior(i) * (1 + comisión))
```

Así el PnL de cada nivel se suma a su propia cantidad (compounding por nivel).

### 1.4 Cuando el precio supera el techo (estado AGOTADO)

Si **todos** los niveles completaron su venta en el ciclo actual (ya no queda BTC en la grilla) y el
precio supera el techo, todo el BTC se convirtió a USDT → el conjunto pasa a **`EXHAUSTED`**. Los
niveles que nunca compraron no cuentan como vendidos, así que un conjunto que arrancó en efectivo y
solo operó algunos niveles **no** se da por agotado. El bot deja de avisar sobre ese conjunto y
habilita dos decisiones (endpoint `POST /api/grids/{id}/resolve`):

| Acción        | Efecto                                                                 |
|---------------|------------------------------------------------------------------------|
| `CONTINUE`    | Sigue con los **mismos niveles**. Reinicia el ciclo (todos en efectivo) y volverán a comprar cuando el precio baje al piso de cada banda. |
| `NEW_SET`     | Cierra el conjunto y crea uno **nuevo** con otro piso/techo, **heredando el efectivo** (USDT) acumulado. El nuevo conjunto arranca en efectivo. |

---

## 2. Modelo de datos (PostgreSQL / Neon)

- **`grid_set`** (`GridSet`): piso, techo, nº niveles, inventario BTC, reserva USDT, comisión,
  estado (`ACTIVE` / `EXHAUSTED` / `CLOSED`), contador de ventas del ciclo, fechas y conjunto origen.
- **`grid_level`** (`GridLevel`): índice, límites inferior/superior, porcentaje, BTC asignado,
  `holding`, precio de entrada y acumulados (BTC comprado/vendido, USDT gastado/recibido,
  `pnlUsdt`, `pnlBtc`, nº de operaciones).
- **`grid_alert`** (`GridAlert`): aviso de zona de compra/venta (`type` = `BUY`/`SELL`), estado
  (`PENDING`/`APPROVED`/`DISMISSED`), precio que disparó el aviso, cantidad/precio sugeridos, nota,
  operación resultante y fechas.
- **`trade_history`** (`Trade`): cada compra/venta con precio, cantidad, importe, comisión y PnL.

El esquema se crea automáticamente desde `src/main/resources/schema.sql` (idempotente: `CREATE TABLE IF NOT EXISTS`)
al arrancar. Los datos **persisten** en PostgreSQL (Neon), así que sobreviven a los reinicios de la aplicación.

Se usa el host **directo** de Neon (`ep-super-king-b544eb5h...`, sin `-pooler`) porque R2DBC usa sentencias
preparadas; si prefieres el pooler, basta añadir `-pooler` al host de la URL.

---

## 3. API REST

Base: `http://localhost:8080`

### Panel web (Vue 3, sin build)

Con la app en marcha abre **http://localhost:8080/** (o `http://localhost:8080/index.html`).

Es un panel de una sola página hecho con **Vue 3 cargado por CDN** (`unpkg`) y servido directamente
desde `src/main/resources/static` — **no necesita Node ni compilación**. Desde ahí puedes:

- ver las **alertas pendientes** (zona de compra/venta) con un contador en la barra superior y
  registrar la operación en el momento: cantidad, precio y, en las ventas, profit y nota;
- **descartar** una alerta para que no se repita hasta que el precio salga de la zona;
- crear conjuntos, editando los **% por nivel** (valida que sumen 100 %) y viendo la banda resultante;
- ver la lista de conjuntos y el **detalle**: niveles (con aviso de compra/venta pendiente), PnL en
  BTC/USDT y el histórico de operaciones;
- ver el **reporte de ganancias por nivel** (realizado / no realizado / total) y **descargarlo en CSV**;
- **eliminar un conjunto** (niveles, alertas y operaciones) con un botón en el detalle;
- alternar el **tema claro/oscuro** (neumorfismo) desde la barra superior;
- ver el **precio real de Binance** (API pública) con refresco automático y botón para inyectarlo como `tick`;
- inyectar **precios manuales** (`tick`) y arrancar/detener el feed;
- cuando el conjunto queda **`EXHAUSTED`**, elegir **CONTINUE** o **NEW_SET** con un formulario.

> El panel se refresca solo cada 4 s. Cuando aparece una alerta nueva verás un aviso en pantalla y
> el contador del título de la pestaña. Si abres `/` y ves 404, usa `/index.html` hasta reiniciar la
> app (Spring Boot registra la *welcome page* en el arranque, y el `index.html` debe existir ya ahí).

### Documentación interactiva (Swagger UI)

Con la app en marcha, abre en el navegador:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

Desde Swagger UI puedes crear conjuntos, inyectar precios (`/api/bot/tick`), ver y registrar las
alertas de compra/venta, resolver el estado agotado y ver niveles/trades/PnL, todo con clics y sin
escribir código.

### Conjuntos

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/grids` | Crear conjunto de niveles |
| `GET`  | `/api/grids` | Listar conjuntos |
| `GET`  | `/api/grids/{id}` | Detalle de un conjunto |
| `GET`  | `/api/grids/{id}/levels` | Niveles (bandas, % y acumulados) |
| `GET`  | `/api/grids/{id}/trades` | Historial de operaciones |
| `GET`  | `/api/grids/{id}/pnl` | Ganancia consolidada BTC/USDT |
| `GET`  | `/api/grids/{id}/report` | **Reporte de ganancias por nivel** (realizado + no realizado + total) |
| `GET`  | `/api/grids/{id}/report.csv` | El mismo reporte en **CSV** para descargar |
| `POST` | `/api/grids/{id}/resolve` | `CONTINUE` o `NEW_SET` tras agotarse |
| `GET`  | `/api/grids/{id}/alerts` | Alertas (pendientes y resueltas) del conjunto |
| `DELETE` | `/api/grids/{id}` | Elimina un conjunto, sus niveles, alertas y su historial |

### Alertas manuales

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET`  | `/api/alerts/pending` | Alertas pendientes de todos los conjuntos (zona, banda, precio de disparo, cantidad/precio sugeridos) |
| `POST` | `/api/alerts/{id}/approve-buy` | Registra la **compra**: `quantityBtc`, `price`, `note` (opcional), `allowOverdraft` (opcional) |
| `POST` | `/api/alerts/{id}/approve-sell` | Registra la **venta**: `quantityBtc`, `price`, `pnlUsdt` (opcional), `pnlBtc` (opcional), `note` |
| `POST` | `/api/alerts/{id}/dismiss` | Descarta el aviso (`?note=...` opcional) sin registrar operación |

Las respuestas de `approve-buy` / `approve-sell` son la operación creada (`TradeResponse`), con los
mismos campos que el resto del proyecto. El `type` de la alerta decide qué zona se alcanzó
(`BUY`/`SELL`); una alerta solo se puede aprobar una vez y solo si el conjunto sigue `ACTIVE`.

### Bot

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/bot/start` | Arranca el feed de precios |
| `POST` | `/api/bot/stop` | Detiene el bot |
| `GET`  | `/api/bot/status` | Estado (running, feed, último precio, nº de conjuntos activos y nº de alertas pendientes) |
| `POST` | `/api/bot/tick` | Inyecta un precio manual y lo procesa (para pruebas) |

### Mercado (API pública de Binance)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/market/price` | Último precio spot del par desde Binance (gratuito, sin claves). Admite `?symbol=ETHUSDT` |

```bash
curl "http://localhost:8080/api/market/price"
# {"symbol":"BTCUSDT","price":83604.41000000,"source":"binance","time":"2026-10-12T03:45:53.626108Z"}
```

### Ejemplos

Crear un conjunto (porcentajes personalizados que suman 100 %):

```bash
curl -X POST http://localhost:8080/api/grids \
  -H "Content-Type: application/json" \
  -d '{
    "floorPrice": 78000,
    "ceilPrice": 88000,
    "levelCount": 5,
    "btcInventory": 0.5,
    "usdtReserve": 100000,
    "levelPercents": [10, 15, 20, 25, 30],
    "feeRate": 0.001,
    "startInBtc": true
  }'
```

Procesar un precio manual (genera las alertas de las zonas alcanzadas):

```bash
curl -X POST http://localhost:8080/api/bot/tick \
  -H "Content-Type: application/json" -d '{ "price": 89000 }'
```

Ver las alertas pendientes y registrar la operación:

```bash
curl "http://localhost:8080/api/alerts/pending"

# Compra manual: cantidad de BTC y precio reales
curl -X POST http://localhost:8080/api/alerts/12/approve-buy \
  -H "Content-Type: application/json" \
  -d '{ "quantityBtc": 0.1, "price": 79000, "note": "comprada en Binance" }'

# Venta manual: cantidad, precio y profit (opcional; si se omite se calcula)
curl -X POST http://localhost:8080/api/alerts/13/approve-sell \
  -H "Content-Type: application/json" \
  -d '{ "quantityBtc": 0.1, "price": 91000, "pnlUsdt": 1188.09, "note": "vendida en Binance" }'

# Descartar un aviso que no vas a ejecutar
curl -X POST "http://localhost:8080/api/alerts/14/dismiss?note=espero%20mejor%20precio"
```

Si la compra no cabe en la reserva declarada, la API responde 400 explicándolo; para registrarla
igualmente (la reserva quedará negativa), añade `"allowOverdraft": true` al cuerpo de la compra.

Continuar o crear un nuevo set cuando el conjunto quedó agotado:

```bash
curl -X POST http://localhost:8080/api/grids/1/resolve \
  -H "Content-Type: application/json" -d '{ "action": "CONTINUE" }'

curl -X POST http://localhost:8080/api/grids/1/resolve \
  -H "Content-Type: application/json" \
  -d '{ "action": "NEW_SET", "floorPrice": 90000, "ceilPrice": 100000, "levelCount": 4, "levelPercents": [40,30,20,10] }'
```

Reporte de ganancias por nivel (JSON o CSV):

```bash
curl "http://localhost:8080/api/grids/1/report"
# totalTrades, levelsTraded, realizedPnl*, unrealizedPnl*, totalPnl* y levels[] con una fila por nivel

curl -o reporte-conjunto-1.csv "http://localhost:8080/api/grids/1/report.csv"
```

Eliminar un conjunto (con sus niveles, alertas y operaciones):

```bash
curl -X DELETE "http://localhost:8080/api/grids/1"
```

---

## 4. Ejecución

```bash
# desde la carpeta del proyecto Maven (app/app)
./mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # Linux/Mac

./mvnw.cmd test                   # pruebas
./mvnw.cmd package                # construir el jar
```

---

## 5. Configuración (`application.properties`)

| Propiedad | Descripción | Por defecto |
|-----------|-------------|-------------|
| `spring.r2dbc.url` | URL R2DBC de PostgreSQL (Neon, con `sslMode=require`) | Neon `neondb` |
| `spring.r2dbc.username` | Usuario de la base de datos | `neondb_owner` |
| `spring.r2dbc.password` | Contraseña de la base de datos | *(ver `application.properties`)* |
| `bot.feed` | Fuente de precios: `binance` (real) o `simulated` | `binance` |
| `bot.symbol` | Par operado | `BTCUSDT` |
| `bot.poll-interval-ms` | Intervalo de muestreo del precio | `2000` |
| `bot.fee-rate` | Comisión por operación | `0.001` |
| `bot.initial-price` | Precio inicial del simulador | `83000` |
| `bot.simulated-volatility` | Volatilidad por tick del simulador | `0.003` |
| `bot.auto-start` | Arrancar el bot al levantar la app (vigila y avisa sin abrir la web) | `true` |

Por defecto (`bot.feed=binance`) el bot usa **precios reales** del endpoint público y gratuito
`GET https://api.binance.com/api/v3/ticker/price` (sin API keys). El bot **no envía órdenes**: solo
detecta zonas y genera alertas para que registres tus operaciones. Cambia a `bot.feed=simulated` si
prefieres el paseo aleatorio interno (útil sin internet).

Además, `GET /api/market/price` consulta ese mismo servicio público **bajo demanda** (es lo que usa
el panel para mostrar el precio aunque el bot esté detenido).

Con `bot.auto-start=true` (por defecto) el bot **arranca el feed solo** al levantar la aplicación:
vigila el precio y genera alertas **sin abrir la web ni pulsar nada**. El panel es para ver los
avisos y registrar las operaciones; **cerrar el navegador no detiene la vigilancia** (el feed corre
en el servidor). Para pararlo, usa el botón *Detener* o `POST /api/bot/stop`; para volver al arranque
manual, ponlo en `false`.

Las credenciales de Neon se pueden sobreescribir sin editar el código con las variables de entorno
`NEON_DB_URL`, `NEON_DB_USER` y `NEON_DB_PASSWORD`.

---

## 6. Pruebas

- `GridMathTest`: cálculo de límites lineales, reparto equitativo y del inventario.
- `GridTradingIntegrationTest`: 10 tests contra H2 en memoria (los tests **no** tocan Neon) que validan
  el flujo manual: el motor solo genera alertas, aprobación de compras/ventas con cantidad, precio y
  profit, descarte con silencio hasta salir de la zona, estado `EXHAUSTED` al aprobar la última venta,
  `CONTINUE`, recompras, reinversión por nivel, validaciones de porcentajes y borrado en cascada
  (niveles, alertas y operaciones).
- `BacktestAbrilTest`: **backtest histórico con datos reales de Binance**. Descarga velas horarias desde el 1-abr-2026,
  las alimenta al motor real (comisiones + reinversión) y, en cada tick, registra al instante las
  alertas con los valores sugeridos (como haría el usuario); compara tres escenarios de 500 USD
  contra comprar-y-mantener. El informe se guarda en `target/backtest-abril.txt`. Se omite
  automáticamente si no hay red; para ejecutarlo solo:

  ```bash
  ./mvnw.cmd test -Dtest=BacktestAbrilTest
  ```

---

## 7. Estructura del código

```
src/main/java/com/traiding/app
├── config/      BotProperties, PriceFeedConfiguration, OpenApiConfig
├── domain/      GridSet, GridLevel, GridAlert, Trade, GridSetStatus, TradeSide
├── repository/  GridSetRepository, GridLevelRepository, GridAlertRepository, TradeRepository
├── service/     GridService, TradingEngine, AlertService, TradingBot, GridExecutor, GridMath, PriceHolder
│   └── price/   PriceFeed, SimulatedPriceFeed, BinancePriceFeed, BinanceTicker
└── web/         GridController, AlertController, MarketController, BotController, GlobalExceptionHandler
    └── dto/     CreateGridRequest, ResolveRequest, ApproveBuyRequest, ApproveSellRequest, *Response

src/main/resources
├── static/          index.html, app.js, styles.css   (panel Vue 3 vía CDN)
├── schema.sql
└── application.properties
```

---

## 8. Próximos pasos (roadmap)

- Notificaciones fuera del panel (Telegram, email o push del navegador).
- Conexión real a Binance (órdenes `POST /api/v3/order` con API keys y testnet).
- Streaming de precios por WebSocket.
- Reintentos, límites de riesgo (kill-switch) y topes de exposición.
- Ajuste automático de piso/techo (re-anclaje de la grilla) según la volatilidad.

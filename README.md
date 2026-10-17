# StagePass Live — Plataforma de venta de entradas

Proyecto del taller de **Arquitectura Hexagonal** (StagePass Live — Event & Concert Platform).
El núcleo de dominio no conoce frameworks, HTTP ni bases de datos: solo reglas de negocio,
puertos y casos de uso. Toda la tecnología (REST, SQLite, MongoDB, pasarelas) vive en
adaptadores bajo `infrastructure/`.

## Requisitos

- **JDK 21** (target del build; probado con Java 21.0.9).
- **Docker Desktop** para MongoDB (`docker compose up -d`).
- Git y un navegador web o cliente HTTP (`curl`).

## Arranque rápido

```bash
docker compose up -d               # 1. Inicia MongoDB (órdenes y boletas)
.\mvnw.cmd spring-boot:run         # 2. Inicia la aplicación (en Linux/Mac: ./mvnw spring-boot:run)
curl http://localhost:8080/ping    # 3. Smoke test -> "pong" (200)
```

## Cómo utilizar el sistema

StagePass Live puede operarse desde su **interfaz web gráfica** o mediante **comandos cURL**:

### 1. Interfaz Web (`http://localhost:8080/`)

Abre en tu navegador [http://localhost:8080/](http://localhost:8080/) para realizar el flujo completo:

1. **Selección y reserva:** Elige comprador y recinto (`REC-01` o `REC-02`) → selecciona entre 1 y 6 asientos en el mapa en vivo → clic en **"Iniciar reserva y bloquear asientos"** (bloqueo de 10 minutos con temporizador regresivo y desglose oficial de tarifas calculado por el dominio).
2. **Confirmación de pago:** Selecciona pasarela (`Wompi`, `PayPal`, `PSE`) → ingresa referencia `OK-777` (o usa los botones de atajo) → clic en **"Confirmar pago"** (para simular error 402 usa `RECHAZADA-777`).
3. **Boleto digital y QR:** Al confirmarse el pago, se emite el e-ticket con código `SP-...`, imagen QR real servida desde `/qr/{codigo}.png` y firma SHA-256 copiable.
4. **Control de acceso:** Clic en **"Validar en puerta"** para verificar el código en el modo escáner con veredicto en tiempo real (`VÁLIDO` / `INVÁLIDO`).
5. **Consola de arquitectura en vivo:** Al pie de la página, una barra fija narra en tiempo real la cadena exacta de controladores, casos de uso, entidades y adaptadores de SQLite/Mongo que procesaron cada acción.

### 2. Terminal (cURL)

```bash
# 1. Iniciar reserva (bloquea sillas 10 min en SQLite y guarda orden en Mongo)
curl -X POST http://localhost:8080/api/v1/compras -H "Content-Type: application/json" \
     -d '{"eventoId":"EV-001","clienteId":"CL-001","asientosId":["VIP-A1","VIP-A2"]}'
# -> {"ordenId":"<ORDEN_ID>","codigoPago":"PAY-...","expiraEn":"..."}

# 2. Confirmar pago y emitir boleto firmado (SHA-256 + QR en target/qr/)
curl -X POST http://localhost:8080/api/v1/pagos/confirmar -H "Content-Type: application/json" \
     -d '{"ordenId":"<ORDEN_ID>","pasarela":"wompi","identificadorTransaccion":"OK-777"}'
# -> {"boletoId":"...","codigoRedencion":"<CODIGO_SP>","firmaHash":"...","fechaEmision":"..."}

# 3. Validar boleto en puerta (verifica firma recalculada en el núcleo)
curl http://localhost:8080/api/v1/boletos/<CODIGO_SP>
# -> {"valido":true,"motivo":"Boleto valido"}
```

> **Expiración automática:** El componente `ReservasJob` corre cada 60 s; a los 10 minutos marca la orden `EXPIRADA` y devuelve los asientos al pool de disponibles. Pagar una orden vencida responde 409 (ya barrida) o 410 (ventana vencida).

## Tests

```bash
.\mvnw.cmd test    # 84 pruebas verdes (unitarias, integración, ArchUnit y web)
```

- **Unitarias:** dominio y casos de uso con dobles en memoria (sin base de datos ni Spring).
- **Aislamiento:** 4 reglas ArchUnit (dominio puro, puertos sin tecnología, casos de uso sin frameworks, núcleo sin tocar infraestructura).
- **Integración:** repos SQLite (base en memoria con scripts reales) y repos Mongo con **Testcontainers** (`mongo:7` local).
- **E2E:** flujo completo compra → bloqueo → pago verificado → boleto firmado → validación en puerta → expiración.

## API REST (bajo `/api/v1`, JSON camelCase, RFC 7807)

| Ruta | Descripción |
|---|---|
| `GET /api/v1/recintos/{recintoId}/asientos` | Mapa de asientos del recinto en vivo → `[{id, codigoUbicacion, precio, estado}]` |
| `POST /api/v1/compras` | Inicia la reserva: bloquea asientos 10 min y aplica tramos → 201 `{ordenId, codigoPago, expiraEn}` |
| `POST /api/v1/compras/{ordenId}/seleccion` | Revalida el paquete elegido contra la orden bloqueada (montos y vigencia) |
| `POST /api/v1/pagos/confirmar` | Consulta transacción en pasarela y emite boleto firmado (SHA-256) |
| `POST /api/v1/pagos/webhook/{pasarela}` | Webhook asíncrono de pasarela: acusa recibo (200 `{recibido:true}`) |
| `GET /api/v1/boletos/{codigo}` | Validación en puerta → `{valido, motivo}` |
| `GET /qr/{codigo}.png` | Imagen PNG del código QR del boleto emitido |
| `GET /ping` | Smoke test → `"pong"` (200) |

Errores: 400 validación · 404 no existe · 409 regla de negocio · 410 orden vencida · 402 pago no verificado.

**Pasarelas mock:** `OK-*` / `APROBADA-*` aprueba, `RECHAZADA-*` rechaza (402), cualquier otra queda pendiente. Pasarelas disponibles: `paypal`, `wompi`, `pse`. Seeds en SQLite: clientes `CL-001..003` y asientos `VIP-A1..A4`, `GEN-01..06` (REC-01) y `BOX-01..03` (REC-02).

## Estructura (regla: dependencias hacia adentro)

```
com.stagepass
├─ domain/          # NÚCLEO: entidades, reglas de negocio, excepciones (solo java.*)
│  ├─ model/        # Evento, Asiento, Cliente, OrdenCompra, Boleto + enums
│  ├─ service/      # tarifas, límite 6 boletas, expiración 10 min, firma hash
│  └─ exception/    # ExcepcionNegocio y sus hijas
├─ application/     # casos de uso: iniciar compra, confirmar pago, validar boleto, listar asientos, expirar
├─ ports/
│  ├─ in/           # puertos primarios: checkout, pagos, validación, catálogo, mantenimiento
│  └─ out/          # puertos secundarios: repos, pasarela, notificador, factura, QR, reloj
└─ infrastructure/  # ADAPTADORES (exterior): todo lo tecnológico
   ├─ in/           # REST controllers, webhook de pagos, ReservasJob @Scheduled
   ├─ out/          # SQLite (asientos/clientes), MongoDB (órdenes/boletas),
   │                # pasarelas mock, Sendgrid/WhatsApp, DIAN, QR, reloj
   └─ config/       # ConfiguracionSpring: wiring de beans y recursos estáticos
```

## Reglas de oro

- En `domain/` **no se permite ningún import fuera de `java.*`** (verificado por ArchUnit en tests).
- Los puertos son interfaces puras: ningún SQL, HTTP ni DTO de pasarela.
- **Un solo puerto `PaymentGateway`**: PayPal, Wompi y PSE son tres adaptadores.
- **Un solo puerto `Notificador`**: Sendgrid y WhatsApp son dos adaptadores.
- Dinero siempre en `BigDecimal` con redondeo en un solo punto; el tiempo llega por el puerto `Reloj`, nunca con `Instant.now()` suelto.
- Adaptadores reales: REST, SQLite, MongoDB y reloj+job; los demás son mocks (ADR-0002).

## Documentación

- Decisiones arquitectónicas: `Docs/adr/0001-arquitectura-nucleo.md` y `Docs/adr/0002-adaptadores-infraestructura.md`.
- Diagramas: `Docs/diagramas/` — `puml/` fuente, `png/` y `svg/` exportados.

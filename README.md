# StagePass Live — Plataforma de venta de entradas

Proyecto del taller de **Arquitectura Hexagonal** (StagePass Live — Event & Concert Platform).
El núcleo de dominio no conoce frameworks, HTTP ni bases de datos: solo reglas de negocio,
puertos y casos de uso. Toda la tecnología (REST, SQLite, MongoDB, pasarelas) vive en
adaptadores bajo `infrastructure/`.

## Requisitos

- **JDK 21** (target del build; probado con Java 21.0.9).
- **Docker Desktop** para MongoDB (`docker compose up -d`).
- Git y un cliente HTTP (`curl`, Postman, Insomnia).

## Arranque rápido

```bash
docker compose up -d               # MongoDB: ordenes y boletas
.\mvnw.cmd spring-boot:run         # en Linux/Mac: ./mvnw spring-boot:run
curl http://localhost:8080/ping    # "pong" (200)
```

## Tests

```bash
.\mvnw.cmd test    # 80 pruebas verdes
```

- **Unitarias:** dominio y casos de uso con dobles en memoria (sin base de datos ni Spring).
- **Aislamiento:** 4 reglas ArchUnit (dominio puro, puertos sin tecnología, casos de uso sin
  frameworks, núcleo sin tocar infraestructura).
- **Integración:** repos SQLite (base en memoria con los scripts reales de producción) y repos
  Mongo con **Testcontainers** (imagen `mongo:7` local; exige Docker arriba).
- **E2E:** flujo completo compra → bloqueo → pago verificado → boleto firmado → validación en
  puerta → expiración → liberación de asientos.

## API (bajo `/api/v1`, JSON camelCase, errores `ProblemDetail` RFC 7807)

| Ruta | Descripción |
|---|---|
| `POST /api/v1/compras` | Inicia la reserva: bloquea los asientos 10 min y aplica los tramos → 201 `{ordenId, codigoPago, expiraEn}` |
| `POST /api/v1/compras/{ordenId}/seleccion` | Revalida el paquete elegido contra la orden bloqueada (montos y vigencia) |
| `POST /api/v1/pagos/confirmar` | Consulta la transacción en la pasarela y emite el boleto firmado (SHA-256) |
| `POST /api/v1/pagos/webhook/{pasarela}` | Webhook asíncrono de la pasarela: acusa recibo (200) |
| `GET /api/v1/boletos/{codigo}` | Validación en puerta → `{valido, motivo}` |
| `GET /ping` | Smoke test → "pong" |

Errores: 400 validación · 404 no existe · 409 regla de negocio · 410 orden vencida ·
402 pago no verificado.

**Pasarelas mock deterministas:** una transacción `OK-*` / `APROBADA-*` llega APROBADA,
`RECHAZADA-*` RECHAZADA y cualquier otra queda PENDIENTE. La pasarela se elige por nombre:
`paypal`, `wompi` o `pse`. Seeds listos en SQLite: clientes `CL-001..003` y asientos
`VIP-A1..A4`, `GEN-01..06` (REC-01) y `BOX-01..03` (REC-02).

## Estructura (regla: las dependencias apuntan hacia adentro)

```
com.stagepass
├─ domain/          # NÚCLEO: entidades, reglas de negocio, excepciones (solo java.*)
│  ├─ model/        # Evento, Asiento, Cliente, OrdenCompra, Boleto + enums
│  ├─ service/      # tarifas, límite 6 boletas, expiración 10 min, firma hash
│  └─ exception/    # ExcepcionNegocio y sus hijas
├─ application/     # casos de uso: iniciar compra, confirmar pago, validar boleto, expirar
├─ ports/
│  ├─ in/           # puertos primarios: los llaman REST y el job
│  └─ out/          # puertos secundarios: repos, pasarela, notificador, factura, QR, reloj
└─ infrastructure/  # ADAPTADORES (exterior): todo lo tecnológico
   ├─ in/           # REST controllers, webhook de pagos, ReservasJob @Scheduled
   ├─ out/          # SQLite (asientos/clientes), MongoDB (órdenes/boletas),
   │                # pasarelas mock, Sendgrid/WhatsApp, DIAN, QR, reloj
   └─ config/       # ConfiguracionSpring: todo el wiring de beans
```

## Reglas de oro

- En `domain/` **no se permite ningún import fuera de `java.*`** (lo verifica ArchUnit en tests).
- Los puertos son interfaces puras: ningún SQL, HTTP ni DTO de pasarela.
- **Un solo puerto `PaymentGateway`**: PayPal, Wompi y PSE son tres adaptadores.
- **Un solo puerto `Notificador`**: Sendgrid y WhatsApp son dos adaptadores.
- Dinero siempre en `BigDecimal` con redondeo en un solo punto; el tiempo llega por el puerto
  `Reloj`, nunca con `Instant.now()` suelto.
- Adaptadores reales: REST, SQLite, MongoDB y reloj+job; los demás son mocks (decisión en
  `Docs/adr/0002-adaptadores-infraestructura.md`).

## Documentación

- Contrato del equipo (firmas y tipos exactos): `Docs/CONTRATO.md`.
- Decisiones arquitectónicas: `Docs/adr/0001-arquitectura-nucleo.md` y
  `Docs/adr/0002-adaptadores-infraestructura.md`.
- Especificación cerrada del trabajo de adaptadores: `Docs/features/001-adaptadores.md`.
- Diagramas: `Docs/diagramas/` — `puml/` fuente, `png/` y `svg/` exportados.

## Demo (defensa)

```bash
curl -X POST http://localhost:8080/api/v1/compras -H "Content-Type: application/json" \
     -d "{\"eventoId\":\"EV-001\",\"clienteId\":\"CL-001\",\"asientosId\":[\"VIP-A1\",\"VIP-A2\"]}"
# → {"ordenId":"...","codigoPago":"PAY-...","expiraEn":"..."} con los asientos BLOQUEADO

curl -X POST http://localhost:8080/api/v1/pagos/confirmar -H "Content-Type: application/json" \
     -d "{\"ordenId\":\"<ORDEN>\",\"pasarela\":\"wompi\",\"identificadorTransaccion\":\"OK-777\"}"
# → boleto SP-...; asientos VENDIDO; Sendgrid/DIAN/QR dejan huella en el log (PNG en target/qr/)

curl http://localhost:8080/api/v1/boletos/<CODIGO_SP>
# → {"valido":true,"motivo":"Boleto valido"}: la firma se revalida tras releer la orden en Mongo
```

La expiración es automática: el `ReservasJob` corre cada 60 s y a los 10 minutos marca la orden
EXPIRADA y devuelve los asientos al pool; pagar una orden vencida responde 409 (ya barrida) o
410 (ventana vencida sin barrido).

# 001 — Adaptadores de StagePass Live

**status:** cerrada | **updated:** 2026-10-16 | **owner:** diego (rama `feat/adaptadores`, PR con squash)

## Objetivo

Construir la capa `infrastructure/` del taller: los adaptadores primarios (REST + job) y
secundarios (SQLite, MongoDB, pasarelas/notificador/DIAN/QR mock) que implementan los puertos
congelados del núcleo (PR #1 mergeado en `main`), más la configuración Spring que los cablea.

## Alcance

- Dentro: los 16 adaptadores de `Docs/CONTRATO.md` §10, `ConfiguracionSpring`, properties,
  schema/seed de SQLite, tests de integración y E2E.
- Fuera: nada en `domain/`, `application/` ni `ports/` salvo que un hueco lo exija (en ese caso
  se actualiza el CONTRATO primero). UI web no entra (solo JSON).

## Decisiones (detalle en ADR-0002)

1. Testcontainers con imagen local `mongo:7` para los tests de integración Mongo/E2E.
2. Mocks externos deterministas: transacciones `OK-*` → APROBADO, `RECHAZADA-*` → RECHAZADO,
   resto PENDIENTE.
3. Instantes persistidos como String ISO-8601: `Instant` tiene nanos y `Date` de BSON solo
   millis; perder precisión rompería la verificación de la firma del boleto al releer la orden.
4. Webhook de pasarela: endpoint que responde 200 y registra; la confirmación real la hace el
   front vía `POST /pagos/confirmar` (el núcleo ya documenta el no-op).
5. Doble escritura SQLite+Mongo no atómica: riesgo aceptado; el adaptador SQLite auto-libera
   bloqueos vencidos al leer (usa el método de dominio `Asiento.bloqueoVencido`).
6. API bajo `/api/v1`; errores con ProblemDetail de Spring (RFC 7807); naming camelCase.

## Contrato API

| Ruta | Request → Response |
|---|---|
| `POST /api/v1/compras` | `{eventoId, clienteId, asientosId[]}` → 201 `{ordenId, codigoPago, expiraEn}` |
| `POST /api/v1/compras/{ordenId}/seleccion` | `{asientosId[]}` → 200 resumen de la orden |
| `POST /api/v1/pagos/confirmar` | `{ordenId, pasarela, identificadorTransaccion}` → 200 boleto |
| `POST /api/v1/pagos/webhook/{pasarela}` | `{identificadorTransaccion, aprobado}` → 200 |
| `GET /api/v1/boletos/{codigo}` | → 200 `{codigo, valido, motivo}` |
| `GET /ping` | → 200 "pong" (existente) |

Errores: 400 validación, orden no existe → 404, 409 reglas de negocio
(asiento no disponible / límite excedido / orden no pendiente / pasarela no registrada),
410 orden vencida, 402 pago no verificado.

## Pasos (cada uno es una unidad de commit)

| # | Paso | Aceptación |
|---|---|---|
| 0 | PR núcleo → `main` + rama `feat/adaptadores` | main = squash del núcleo |
| 1 | esta spec versionada | doc en la rama |
| 2 | Persistencia SQLite (`SqliteAsientoRepository`, `SqliteClienteRepository`, `schema.sql`, `data.sql`) | tests de integración verdes con DB temporal |
| 3 | Persistencia Mongo (`MongoOrdenRepository`, `MongoBoletoRepository`, documentos + mapeadores, índices) + deps Testcontainers | round-trip conserva `expiraEn` exacto y la firma valida |
| 4 | Mocks externos + `ConfiguracionSpring` + properties | app arranca, `/ping` ok, suite verde |
| 5 | REST (3 controllers + `ManejadorDeErrores`) | MockMvc por endpoint |
| 6 | `ReservasJob @Scheduled` | test con Reloj controlable + corrida real |
| 7 | E2E del flujo completo + regla ArchUnit extra | compra→bloqueo→pago→boleto→expira→libera |
| 8 | CONTRATO §10 ✅ + ADR-0002 | docs sincronizados |
| 9 | Suite completa + `auditor` + PR con squash | `main` mergeado |

## Riesgos

- Doble escritura no atómica entre SQLite y Mongo (mitigación: decisión 5).
- Precisión de instantes al persistir (mitigación: decisión 3 + test de round-trip).
- Primera corrida de Testcontainers descarga nada (imagen `mongo:7` ya local), pero exige
  Docker arriba: es el mismo prerrequisito del taller.

## Definición de terminado

- `.\mvnw.cmd test` verde con los tests nuevos (unitarios + integración + E2E).
- Flujo de demostración corriendo contra SQLite temporal + Mongo de Testcontainers.
- CONTRATO y ADR al día; changelog del repo actualizado si aplica.

## Evidencia (cierre)

- `.\mvnw.cmd test` → **80 pruebas verdes**: dominio 22, ArchUnit 4 (reglas: dominio, puertos,
  casos de uso, núcleo↛infraestructura), SQLite 11, Mongo 7 (contenedor real), web 13 (MockMvc),
  job 2 (delegación + corrida real), E2E 1 (contenedor + SQLite temporal + reloj inyectado),
  contexto Spring 1.
- Ajustes del `auditor` aplicados: 400 ante JSON malformado (handler + test), índice único
  probado por comportamiento, 409 por pasarela no registrada, corrida real del job, comentarios
  corregidos y nota de producción para la clave de firma (ADR-0002 §7).
- CONTRATO §10/§12 y ADR-0002 actualizados; entrega por PR `feat/adaptadores` → `main` con squash.


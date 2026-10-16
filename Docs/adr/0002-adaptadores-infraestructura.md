# ADR-0002: Adaptadores de infraestructura de StagePass

## Estado

Aceptado (cierre del trabajo de adaptadores, oct-2026, rama `feat/adaptadores`).

## Contexto

Los puertos del núcleo quedaron congelados en `main` (PR #1, squash). Faltaba construir la capa
`infrastructure/`: persistencia dual (SQLite para el catálogo legacy, MongoDB para órdenes y
boletas), REST, job de expiración y los mocks externos (pasarelas, notificadores, DIAN, QR).
El enunciado exige al menos 2 adaptadores completos; el equipo optó por 4 reales.

## Decisiones

1. **Test de integración con Testcontainers** (`org.testcontainers:mongodb:1.21.4`) sobre la
   imagen `mongo:7` ya local: aislado por corrida y sin depender de que el compose esté arriba.
   El E2E levanta su propio contenedor por `@DynamicPropertySource`.
2. **Mocks externos deterministas:** las 3 pasarelas implementan UN solo puerto
   `PaymentGateway`; el rendimiento depende del prefijo del identificador (`OK-*` /
   `APROBADA-*` → APROBADO, `RECHAZADA-*` → RECHAZADO, resto PENDIENTE). Sendgrid y WhatsApp
   son dos implementaciones del único `Notificador` (Sendgrid primario); DIAN y QR completan
   el set de mocks. El QR dibuja una matriz determinista a partir del hash en `target/qr/`.
3. **Instantes como texto ISO-8601 de ancho fijo (9 dígitos de nanos, `InstantTexto`):**
   `Instant` maneja nanos y el `Date` de BSON solo millis; perder precisión rompería la
   verificación de la firma del boleto al releer la orden. Con ancho fijo, además, el orden
   lexicográfico coincide con el cronológico, lo que la consulta `buscarVencidas` necesita.
   Verificado por el test de round-trip con firma estable sobre el contenedor real.
4. **Webhook de pasarela:** `POST /api/v1/pagos/webhook/{pasarela}` responde 200 registrando
   el aviso; el núcleo ya documenta el no-op (el aviso no trae id de orden). La confirmación
   real del pago la hace el front con `POST /api/v1/pagos/confirmar`, que consulta la
   transacción a la pasarela elegida.
5. **Doble escritura SQLite+Mongo no atómica (riesgo aceptado de ADR-0001):** mitigado en el
   adaptador relacional, que al leer auto-libera los bloqueos cuyo vencimiento ya pasó usando
   la regla del dominio (`Asiento.bloqueoVencido`). Los `UPDATE` con guardas
   (`estado='DISPONIBLE'` al bloquear; `estado <> 'VENDIDO'` al liberar) evitan pisar el
   resultado de un pago aprobado en una carrera con el barrido.
6. **Adaptadores Mongo perezosos (`@Lazy` + inyección perezosa):** el arranque no exige la
   base (los índices se crean al primer uso). Una demo o test sin Docker sube igual.
7. **API bajo `/api/v1`, JSON camelCase, errores `ProblemDetail` (RFC 7807):** mapeo de
   excepciones en `ManejadorDeErrores`: 400 validación, 404 "no existe", 409 regla de
   negocio/estado, 410 vencida, 402 pago no verificado, 500 inesperado.

## Consecuencias

- Positivo: los 4 adaptadores reales y el flujo completo demostrable con un único E2E;
  los mocks se pueden reemplazar por SDK reales sin tocar el núcleo.
- Costos: la suite pide Docker para los tests Mongo/E2E (~segundos por contenedor, imagen ya
  local) y la doble escritura sigue sin transacción única (documentada, no bloqueante).
- La validación HTTP usa los mensajes del núcleo para distinguir 404/409 en la excepción base:
  cambio aceptable sin inventar excepciones nuevas por contrato.
- La clave de la firma viaja con valor demo por defecto en `application.yml`
  (`STAGEPASS_CLAVE_SECRETA`). En un despliegue real la variable de entorno es obligatoria y el
  arranque debería fallar sin ella, para que ningún boleto se firme con la clave de demo.

## Verificación

- `.\mvnw.cmd test` → 76 pruebas verdes (dominio, casos de uso, ArchUnit con 4 reglas,
  integración SQLite/Mongo, controllers MockMvc y E2E completo).
- Demo: `docker compose up -d`, `mvnw spring-boot:run` y la secuencia REST de compra → pago
  (`OK-*`) → validación desde la URL del QR.

## Reversión

Cada adaptador vive en su paquete bajo `infrastructure/`; quitar uno no obliga a tocar
dominio, puertos ni casos de uso. La configuración en `ConfiguracionSpring` es el único
punto de cableado.

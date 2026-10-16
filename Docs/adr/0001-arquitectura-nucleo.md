# ADR-0001: Arquitectura base del núcleo StagePass

## Estado

Aceptado (día 0 del taller).

## Contexto

El taller exige implementar StagePass Live con Arquitectura Hexagonal: núcleo soberano sin dependencias
técnicas, puertos como fronteras y adaptadores intercambiables. El taller nombra al menos 3 pasarelas de
pago (PayPal, Wompi, PSE) y exige que "al menos dos de los adaptadores estén completamente implementados;
los restantes pueden ser mocks".

## Decisiones

1. **Stack:** Java (target 21, compilado con JDK 26) + Spring Boot 4.1.1 + Maven wrapper (`mvnw`).
   Spring Boot se limita a la capa de configuración y adaptadores; nunca dentro del núcleo.
2. **Estructura de paquetes** (regla de dependencia apuntando hacia adentro):

   ```
   com.stagepass
   ├─ domain/{model, service, exception}   # núcleo: solo imports de java.*
   ├─ application/                         # casos de uso (implementan puertos primarios)
   ├─ ports/{in, out}                      # interfaces puras del dominio
   └─ infrastructure/                      # ADAPTADORES (exterior): todo lo tecnológico
      ├─ in/{web, job}                     # primarios: REST, webhook, scheduler
      ├─ out/...                           # secundarios: persistencia, pagos, notificaciones
      └─ config/                           # wiring: @Bean que inyecta adaptadores en casos de uso
   ```

   El paquete exterior se llama `infrastructure` para coincidir con `Docs/CONTRATO.md`
   (fuente de verdad del equipo).

3. **Adaptadores completamente implementados (4 ≥ mínimo de 2):**
   - REST (controllers de compras + webhook de pagos) — primario.
   - Persistencia relacional con SQLite (`JdbcTemplate`) para recintos/asientos — secundario.
   - Persistencia MongoDB para órdenes/boletas — secundario.
   - Reloj del sistema (`Clock`) inyectable — secundario trivial.
4. **Adaptadores mock:** PayPal/Wompi/PSE (3 clases finas sobre **un solo** puerto `PaymentGateway`),
   notificaciones WhatsApp/Sendgrid (un solo puerto `Notificador`), facturación DIAN, generación gráfica QR.
   La firma hash SHA-256 de la boleta es regla de negocio: vive en el dominio, no en un adaptador.
5. **SQLite en lugar de MySQL:** el enunciado menciona MySQL legacy. Se elige SQLite para eliminar la
   dependencia de un servidor relacional en el desarrollo; el puerto `AsientoRepository` abstrae el
   almacenamiento, así que migrar a MySQL es añadir una clase adaptadora nueva (sin tocar núcleo).
   Si el docente exige MySQL real, se agrega `mysql-connector-j` + servicio en `docker-compose.yml`.

## Consecuencias

- Positivo: el dominio se prueba sin base de datos ni Spring (ArchUnit lo demuestra); los tres integrantes
  pueden trabajar en paralelo una vez congelados los puertos.
- Riesgo aceptado: SQLite ≠ MySQL literal del enunciado (mitigado por el puerto intercambiable, ver punto 5).
- Escritura dual (bloquear asiento relacional + guardar orden Mongo) no es atómica: se fija orden de
  operaciones y compensación en el adaptador de persistencia.

## Verificación

- `.\mvnw.cmd test` verde (incluida prueba de aislamiento ArchUnit).
- `docker compose up -d` + `curl http://localhost:8080/ping` → 200.
- E2E: iniciar compra → asientos BLOQUEADOS → pago mock aprobado → boleta emitida → vencida → liberada.

## Reversión

Sin adaptadores reales, la configuración inyecta repositorios en memoria; el núcleo no cambia.

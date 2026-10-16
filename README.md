# StagePass Live — Plataforma de venta de entradas

Proyecto del taller de **Arquitectura Hexagonal** (StagePass Live — Event & Concert Platform).
El núcleo de dominio no conoce frameworks, HTTP ni bases de datos: solo reglas de negocio,
puertos y casos de uso.

## Requisitos

- **JDK 17+** (probado con JDK 26).
- **Docker Desktop** (solo para MongoDB; sin ella la app arranca igual con `/ping`).
- Git y un cliente HTTP (`curl`, Postman, Insomnia).

## Arranque rápido

```bash
docker compose up -d          # MongoDB (ordenes y boletas)
.\mvnw.cmd spring-boot:run    # en Linux/Mac: ./mvnw spring-boot:run
curl http://localhost:8080/ping   # debe responder "pong" (200)
```

## Estructura (regla: las dependencias apuntan hacia adentro)

```
com.stagepass
├─ domain/          # NÚCLEO: entidades, reglas de negocio, excepciones (solo java.*)
│  ├─ model/        # Evento, Asiento, Cliente, OrdenCompra, Boleto + enums
│  ├─ service/      # tarifas, límite 6 boletas, expiración 10 min, firma hash
│  └─ exception/    # ExcepcionNegocio y sus hijas
├─ application/     # casos de uso: iniciar compra, confirmar pago, liberar vencidas
├─ ports/
│  ├─ in/           # contratos que llaman los adaptadores primarios (web, job)
│  └─ out/          # contratos que la app necesita (repos, pasarela, notificaciones)
└─ infrastructure/  # ADAPTADORES (exterior): todo lo tecnológico vive aquí
   ├─ in/           # REST controllers, webhook de pagos, job de expiración
   ├─ out/          # SQLite (recintos/asientos), MongoDB (órdenes/boletas), pagos mock, etc.
   └─ config/       # wiring Spring: inyecta adaptadores reales en los casos de uso
```

## Reglas de oro

- En `domain/` **no se permite ningún import fuera de `java.*`** (lo verifica ArchUnit en tests).
- Los puertos son interfaces puras: ningún tipo de SQL, HTTP ni DTOs de pasarelas.
- Un solo puerto `PaymentGateway`; PayPal/Wompi/PSE son adaptadores (mocks por ahora).
- Dinero siempre en `BigDecimal`; el tiempo llega inyectado (`Clock`), nunca `Instant.now()` suelto.

## Flujo de trabajo

- Rama de núcleo: `diego` (estructura base ya en `origin/diego`; haz `git pull` antes de empezar).
- Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`) con commits pequeños.
- Contrato del equipo (firmas y tipos exactos): `Docs/CONTRATO.md`.
- Decisiones arquitectónicas: `Docs/adr/0001-arquitectura-nucleo.md`.
- Diagramas: `Docs/diagramas/` — `puml/` fuente, `png/` y `svg/` exportados.

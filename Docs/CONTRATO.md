# Contrato del equipo — StagePass Live

Para qué sirve: este documento es el **contrato de programación** del equipo. Define los nombres,
tipos y firmas exactos que van a escribir los tres. Si algo no está acá, se consulta antes de
inventarlo.

Cuándo leerlo: el Integrante 2 antes de escribir el núcleo, el Integrante 3 antes de escribir los
adaptadores. **Es la fuente de verdad, no el `.puml`.**

Las firmas de este documento y las de `Docs/diagrama-contratos.puml` deben coincidir. Si alguna
difiere, el que cambió se equivocó y se corrige acá primero.

---

## 1. Dónde vive cada cosa

Base: `src/main/java/com/stagepass/`

| Paquete | Dueño | Puede importar |
|---|---|---|
| `domain/model/` | Integrante 2 | nada fuera de `java.*` |
| `domain/service/` | Integrante 2 | nada fuera de `java.*` |
| `domain/exception/` | Integrante 2 | nada fuera de `java.*` |
| `application/` | Integrante 2 | `domain`, `ports` |
| `ports/in/` | Integrante 2 | `domain` |
| `ports/out/` | Integrante 2 | `domain` |
| `infrastructure/**` | Integrante 3 | `ports`, `domain` |

> Recordatorio: en `domain/`, `application/` y `ports/` **cero** Spring, JDBC, Mongo, Lombok ni
> SDKs externos. Solo el JDK. Lo verifica una prueba de ArchUnit.

---

## 2. Las entidades, campo por campo

Todas del enunciado. Nada más.

### `Evento`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `String` | UUID |
| `nombre` | `String` | |
| `tipo` | `TipoEvento` | |
| `recintoId` | `String` | apunta al recinto legacy, no es un objeto |
| `fechaHora` | `LocalDateTime` | |
| `estado` | `EstadoEvento` | |

Métodos: `cancelar()`, `marcarAgotado()`

### `Asiento`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `String` | |
| `recintoId` | `String` | |
| `codigoUbicacion` | `String` | `VIP-A12`, `General-01` |
| `precioBase` | `BigDecimal` | **nunca `double`** |
| `estado` | `EstadoAsiento` | |
| `bloqueadoHasta` | `Instant` | `null` si no está bloqueado |

Métodos: `bloquear(Instant)`, `vender()`, `liberar()`, `estaDisponible()`,
`bloqueoVencido(Instant)`

### `Cliente`

| Campo | Tipo |
|---|---|
| `id` | `String` |
| `nombreCompleto` | `String` |
| `correoElectronico` | `String` |
| `telefonoMovil` | `String` |
| `nacionalidad` | `String` |

### `Cliente`

| Campo | Tipo |
|---|---|
| `id` | `String` |
| `nombreCompleto` | `String` |
| `correoElectronico` | `String` |
| `telefonoMovil` | `String` |
| `nacionalidad` | `String` |

Método: `correoEsValido()`. **No está en el enunciado**, es opcional. Si sobra tiempo se
implementa; si no, se borra del diagrama.

### `OrdenCompra`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `String` | |
| `eventoId` | `String` | |
| `cliente` | `Cliente` | sí es un objeto, es la composición |
| `asientos` | `List<Asiento>` | objetos, **no** ids |
| `tarifaServicio` | `BigDecimal` | resultado de la regla de tramos |
| `descuentoAplicado` | `BigDecimal` | resultado de la regla de tramos |
| `montoTotal` | `BigDecimal` | lo que paga el cliente |
| `expiraEn` | `Instant` | `inicio + 10 minutos` |
| `estado` | `EstadoOrden` | |

Métodos: `aprobar()`, `rechazar()`, `expirar()`, `expiro(Instant)`

### `Boleto`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `String` | |
| `ordenId` | `String` | |
| `codigoRedencion` | `String` | alfanumérico |
| `firmaHash` | `String` | SHA-256 en hexadecimal |
| `fechaEmision` | `Instant` | |

---

## 3. Los enums

```java
enum TipoEvento   { CONCIERTO, TEATRO, FESTIVAL }
enum EstadoEvento { PROGRAMADO, AGOTADO, CANCELADO }
enum EstadoAsiento{ DISPONIBLE, BLOQUEADO, VENDIDO }
enum EstadoOrden { PENDIENTE_PAGO, APROBADA, RECHAZADA, EXPIRADA }
enum EstadoPago   { PENDIENTE, APROBADO, RECHAZADO }
```

Los valores van en `UPPER_SNAKE_CASE`. Sin tildes ni acentos en el código.

Los cinco primeros van en `domain/model/`. **`EstadoPago` NO va en el dominio**: vive en
`ports/out/` junto al puerto `PaymentGateway`, porque describe el estado de una transacción de
una pasarela, no un concepto del negocio. Por eso no aparece en `diagrama-dominio.puml`.

---

## 4. Los servicios de dominio

Son lógica que no pertenece a una sola entidad. Van en `domain/service/` y también son puros.

| Clase | Método | Qué hace |
|---|---|---|
| `CalculadoraTarifaria` | `calcular(int cantidad, BigDecimal subtotal)` | Devuelve `ResultadoCalculo` con los tres números de la regla de tramos |
| `ValidadorLimites` | `validarCompra(String clienteId, String eventoId, int yaAdquiridas, int solicitadas)` | Tope de 6 boletas. **Recibe números, no entidades**, para que el test sea trivial |
| `GeneradorFirma` | `generar(OrdenCompra orden, String claveSecreta)` | SHA-256 de los datos inmutables más la clave del evento |
| | `generarCodigoRedencion(Boleto boleto)` | Código alfanumérico del boleto |
| `PoliticaExpiracion` | `calcularVencimiento(Instant inicio)` | `inicio + 10 minutos` |
| | `expiro(OrdenCompra orden, Instant ahora)` | ¿Ya venció la ventana de pago? |

`ResultadoCalculo` lleva `tarifaServicio`, `descuento` y `montoTotal`, los tres en `BigDecimal`.

`PoliticaExpiracion.VENTANA_DE_BLOQUEO` es una constante `Duration` de 10 minutos. Va como
constante y no como número suelto, para que nadie la cambie "por un día" sin que se note.

## 5. Las excepciones de negocio

Todas heredan de `ExcepcionNegocio`, que a su vez extiende `RuntimeException`. Si el dominio
lanza `RuntimeException` sueltas, los adaptadores primarios no pueden mapearlas a un código HTTP
con criterio.

| Excepción | Se lanza cuando |
|---|---|
| `AsientoNoDisponibleException` | Alguien pidió un asiento que no está libre |
| `LimiteBoletasExcedidoException` | El cliente superaría las 6 boletas del evento |
| `PagoNoVerificadoException` | El pago no llegó aprobado por la pasarela |
| `OrdenExpiradaException` | Se intentó pagar una orden cuya ventana de 10 minutos venció |

---

## 6. Las 4 reglas de negocio, con los números exactos

Están en el enunciado. Acá van con los valores que hay que codificar, para no reinterpretarlos.

### Regla 1 — Tramos de tarifa y descuento

| Boletos | Tarifa administrativa | Descuento |
|---|---|---|
| 1 a 2 | 10% | 0% |
| 3 a 5 | 8% | 5% |
| 6 o más (corporativas) | 5% | 10% |

```
montoTotal = subtotal + tarifaServicio − descuento
```

Se redondea a **dos decimales con `HALF_UP`, en un solo punto del sistema**. Si el núcleo y el
adaptador redondean por su lado, las sumas no cuadran al final.

### Regla 2 — Bloqueo temporal

10 minutos estrictos desde el inicio de la compra. Si el pago no se confirma antes, los asientos
se liberan.

El tiempo se lee del puerto `Reloj`, nunca de `Instant.now()`. Con eso los tests fijan la hora y
no esperan 10 minutos.

### Regla 3 — Tope por cliente

Máximo **6 boletas acumuladas** para un mismo evento. Se suman las ya aprobadas más las nuevas.

Para esto hace falta `OrdenRepository.buscarActivasPorClienteYEvento(clienteId, eventoId)`. Sin
ese método la regla no se puede cumplir.

### Regla 4 — Firma del boleto

SHA-256 de la JDK. Se calcula sobre los datos **inmutables** de la orden más la clave secreta del
evento: `id`, `montoTotal`, `expiraEn` y la clave, unidos con `|`.

Los campos van en ese orden fijo, nunca con `toString()` de un record o un mapa: el orden de las
claves de un mapa no está garantizado y la firma cambiaría entre corridas.

---

## 7. Los puertos secundarios

Interfaces puras. Devuelven entidades del dominio, **nunca** DTOs ni `ResultSet`.

### `AsientoRepository`

```java
List<Asiento> listarPorRecinto(String recintoId);
Asiento buscarPorId(String asientoId);
void bloquear(List<String> asientosId, Instant hasta);
void marcarVendidos(List<String> asientosId);
void liberar(List<String> asientosId);
```

### `OrdenRepository`

```java
void guardar(OrdenCompra orden);
OrdenCompra buscarPorId(String ordenId);
List<OrdenCompra> buscarActivasPorClienteYEvento(String clienteId, String eventoId);
List<OrdenCompra> buscarVencidas(Instant ahora);
```

### `BoletoRepository`

```java
void guardar(Boleto boleto);
Boleto buscarPorCodigo(String codigoRedencion);
```

### `PaymentGateway`

```java
ReferenciaPago registrarPago(SolicitudPago solicitud);
EstadoPago consultarEstado(ReferenciaPago referencia);
```

**Un solo puerto para las tres pasarelas.** PayPal, Wompi y PSE son tres implementaciones. Si el
dominio menciona a alguna por nombre, el aislamiento está roto.

```java
record SolicitudPago(String ordenId, BigDecimal monto, String correoComprador) {}
record ReferenciaPago(String pasarela, String identificadorTransaccion) {}
```

### `TicketGenerator`

```java
RepresentacionBoleto generar(String codigo, String firmaHash, String url);
```

### `Notificador`

```java
void enviar(Notificacion notificacion);
```

**Un solo puerto, dos implementaciones:** Sendgrid y WhatsApp.

### `FacturaEmitter`

```java
Comprobante emitir(Factura factura);
```

### `Reloj`

```java
Instant ahora();
```

El *Time Provider Interface* del material del curso (línea 72 de
`Docs/13_-_Arquitectura_Hexagonal.md`). Es lo que permite testear las 4 reglas de negocio sin
esperar el tiempo real.

---

## 8. Los puertos primarios

Interfaces que **implementan los casos de uso** y que la infraestructura invoca.

```java
// CheckoutUseCase
IniciarCompraResponse iniciarCompra(SolicitudCompra solicitud);
OrdenCompra seleccionarAsientos(String ordenId, List<String> asientosId);

// PagosUseCase
Boleto confirmarPago(ConfirmacionPago confirmacion);
void notificarCambioTransaccion(String pasarela, NotificacionTransaccion notificacion);

// ValidacionUseCase
ResultadoValidacion validarBoleto(String codigoRedencion);

// MantenimientoUseCase
int expirarReservasVencidas();
```

Casos de uso que los implementan:

| Puerto | Implementado por |
|---|---|
| `CheckoutUseCase` | `IniciarCompra` |
| `PagosUseCase` | `ConfirmarPago`, `ExpirarReservas` |
| `ValidacionUseCase` | `ValidarBoleto` |
| `MantenimientoUseCase` | `ExpirarReservas` |

---

## 9. Los adaptadores y su estado

| Adaptador | Puerto que implementa | Estado |
|---|---|---|
| `SqliteAsientoRepository` | `AsientoRepository` | **real** |
| `MongoOrdenRepository` | `OrdenRepository` | **real** |
| `MongoBoletoRepository` | `BoletoRepository` | **real** |
| `QrTicketGenerator` | `TicketGenerator` | **real** |
| `SistemaReloj` | `Reloj` | **real** |
| `CheckoutController` | `CheckoutUseCase` | real |
| `PagosController` | `PagosUseCase` | real |
| `ValidacionController` | `ValidacionUseCase` | real |
| `WompiPaymentGateway` | `PaymentGateway` | mock |
| `PayPalPaymentGateway` | `PaymentGateway` | mock |
| `PsePaymentGateway` | `PaymentGateway` | mock |
| `SendgridNotificador` | `Notificador` | mock |
| `WhatsappNotificador` | `Notificador` | mock |
| `DianFacturaEmitter` | `FacturaEmitter` | mock |

El enunciado exige **al menos 2 adaptadores completos**. Los reales son SQLite, MongoDB y el
generador de QR con SHA-256.

> **Sobre SQLite en vez de MySQL.** El enunciado pide MySQL para el catálogo legacy. SQLite es
> SQL estándar en un archivo, no necesita servidor y con datos dummy da el mismo resultado. En el
> diagrama queda como catálogo legacy; cambiar a MySQL es una línea de configuración del
> `DataSource`. Se explica en la defensa en diez segundos.

---

## 10. Las tres reglas que no se rompen

1. **En `domain/` no hay un solo `import` fuera de `java.*`.** Ni una anotación.
2. **Ningún puerto menciona una tecnología.** Ni `String sql`, ni `HttpRequest`, ni
   `PayPalResponse`.
3. **Ningún adaptador calcula tarifas ni descuentos.** Si necesita un valor que el dominio no
   calcula, se agrega al dominio.

Si rompes alguna, el taller no cumple el enunciado y no hay arreglo después.

---

## 11. Lo que todavía no existe

Esto **no** está hecho y bloquea el arranque del proyecto:

- `pom.xml` y el esqueleto de `src/main/java/com/stagepass/`
- `docker-compose.yml` con MongoDB
- El endpoint `/ping` que usa el smoke test
- Maven y Docker instalados en las máquinas

Mientras eso falte, el smoke test del día 0 no se puede correr y nadie puede verificar que el
proyecto arranque.
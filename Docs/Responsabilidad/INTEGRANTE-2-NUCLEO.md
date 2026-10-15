# Integrante 2 — Núcleo de dominio y casos de uso

Para qué sirve: escribís la parte del sistema donde viven las reglas de negocio, sin una sola
línea de Spring, MySQL ni HTTP. Es el corazón del taller y lo que más se mira.

Cuándo leerlo: después de que el Integrante 1 suba el borrador del diagrama (día 1).

---

## 1. Qué entregás

| Carpeta | Qué va adentro |
|---|---|
| `domain/model/` | Las 5 entidades y sus enums |
| `domain/service/` | La calculadora de tarifas, la firma de seguridad, las validaciones |
| `domain/exception/` | Excepciones de negocio |
| `application/` | Los casos de uso que orquestan el dominio |
| `ports/in/` | Interfaces que implementan los adaptadores primarios (web) |
| `ports/out/` | Interfaces que implementan los adaptadores secundarios |

Rama: `feat/nucleo`.

## 2. Tu regla de oro

> **En `domain/` no se permite ningún `import` fuera de `java.*`.**

Ni Spring, ni `jakarta.persistence`, ni `org.mongodb`, ni `java.sql`, ni Lombok. Nada. Solo el
JDK. Si más adelante aparece un `import` sospechoso, es que la regla de negocio se te fue de
la capa.

Esto no es una cuestión de estilo: el enunciado dice que el núcleo debe ser *"independiente e
invariante frente a las tecnologías externas"*, y en la defensa te van a preguntar exactamente
eso.

---

## 3. Día 0 — Smoke test

```bash
git clone <URL_DEL_REPO>
cd stagepass
docker compose up -d          # MongoDB; vos todavía no lo vas a usar, pero el run lo pide
./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
```

```bash
curl http://localhost:8080/ping    # debe responder 200
```

Si algo falla, se arregla entre los tres antes de seguir. No avances con el build roto.

---

## 4. Día 1 — Las entidades

Arrancá por acá. Son las 5 del PDF, con los atributos del **diccionario de clases** que te pasó
el Integrante 1.

```java
package com.stagepass.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Un asiento del recinto. Cambiar de DISPONIBLE a VENDIDO sí o sí pasa por BLOQUEADO:
 * saltar el estado intermedio significa que dos personas pueden comprar la misma silla.
 */
public class Asiento {

    private final String id;
    private final String recintoId;
    private final String codigoUbicacion;
    private final BigDecimal precioBase;
    private EstadoAsiento estado;
    private Instant bloqueadoHasta;

    public Asiento(String id, String recintoId, String codigoUbicacion, BigDecimal precioBase) {
        this.id = id;
        this.recintoId = recintoId;
        this.codigoUbicacion = codigoUbicacion;
        this.precioBase = precioBase;
        this.estado = EstadoAsiento.DISPONIBLE;
    }

    public void bloquear(Instant hasta) {
        this.estado = EstadoAsiento.BLOQUEADO;
        this.bloqueadoHasta = hasta;
    }

    public void vender() {
        this.estado = EstadoAsiento.VENDIDO;
        this.bloqueadoHasta = null;
    }

    public void liberar() {
        this.estado = EstadoAsiento.DISPONIBLE;
        this.bloqueadoHasta = null;
    }

    /** True si el bloqueo temporal ya venció y hay que devolver el asiento al pool. */
    public boolean bloqueoVencido(Instant ahora) {
        return estado == EstadoAsiento.BLOQUEADO
                && bloqueadoHasta != null
                && ahora.isAfter(bloqueadoHasta);
    }

    public boolean estaDisponible() {
        return estado == EstadoAsiento.DISPONIBLE;
    }

    // getters
}
```

Repetí el mismo patrón para `Evento`, `Cliente`, `OrdenCompra` y `Boleto`. Todos con
constructor, sin setters para los campos que no cambian, y con métodos de negocio en vez de
`setEstado(...)` libre: `OrdenCompra.aprobar()` en lugar de `setEstado(APROBADA)`.

### Enums

```java
package com.stagepass.domain.model;

public enum EstadoAsiento { DISPONIBLE, BLOQUEADO, VENDIDO }
```

Y en la misma carpeta: `TipoEvento`, `EstadoEvento`, `EstadoOrden`.

---

## 5. Día 2 — Las reglas de negocio

Acá está el corazón. Son las 4 del PDF, y **cada una se testea sin base de datos ni Spring**.

### Regla 1 — Calculadora de tarifas por volumen

```java
package com.stagepass.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalculadoraTarifaria {

    private static final BigDecimal UNO = BigDecimal.ONE;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private static final BigDecimal TARIFA_PEQUEÑA = new BigDecimal("0.10");
    private static final BigDecimal TARIFA_MEDIANA = new BigDecimal("0.08");
    private static final BigDecimal TARIFA_CORPORATIVA = new BigDecimal("0.05");

    private static final BigDecimal DESCUENTO_MEDIANO = new BigDecimal("0.05");
    private static final BigDecimal DESCUENTO_CORPORATIVO = new BigDecimal("0.10");

    /**
     * Aplica la tabla de tramos del enunciado y devuelve los tres números que la OrdenCompra
     * necesita guardar. Es una función pura: mismos datos de entrada, mismo resultado siempre.
     */
    public ResultadoCalculo calcular(int cantidadBoletos, BigDecimal subtotal) {

        if (cantidadBoletos <= 0) {
            throw new IllegalArgumentException("La cantidad de boletos debe ser positiva");
        }
        if (subtotal.signum() < 0) {
            throw new IllegalArgumentException("El subtotal no puede ser negativo");
        }

        BigDecimal tarifaServicio;
        BigDecimal descuento;

        if (cantidadBoletos <= 2) {
            // 1 a 2 boletas: tarifa 10%, sin descuento. Es el tramo base.
            tarifaServicio = subtotal.multiply(TARIFA_PEQUEÑA);
            descuento = BigDecimal.ZERO;
        } else if (cantidadBoletos <= 5) {
            // 3 a 5 boletas: la tarifa baja a 8% pero aparece el descuento comercial del 5%.
            tarifaServicio = subtotal.multiply(TARIFA_MEDIANA);
            descuento = subtotal.multiply(DESCUENTO_MEDIANO);
        } else {
            // 6 o más: tarifa administrativa fija del 5% y descuento del 10%.
            tarifaServicio = subtotal.multiply(TARIFA_CORPORATIVA);
            descuento = subtotal.multiply(DESCUENTO_CORPORATIVO);
        }

        BigDecimal montoTotal = subtotal.add(tarifaServicio).subtract(descuento);

        return new ResultadoCalculo(
                redondear(tarifaServicio),
                redondear(descuento),
                redondear(montoTotal)
        );
    }

    /**
     * El dinero se redondea a dos decimales en un solo punto del sistema. Si cada adapter
     * redondea por su lado, las sumas no cuadran al final.
     */
    private BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public record ResultadoCalculo(
            BigDecimal tarifaServicio,
            BigDecimal descuento,
            BigDecimal montoTotal
    ) {}
}
```

### Regla 2 — Validación del límite de 6 boletas

```java
package com.stagepass.domain.service;

import com.stagepass.domain.exception.LimiteBoletasExcedidoException;

public class ValidadorLimites {

    // El enunciado prohíbe más de 6 boletas acumuladas para un mismo evento.
    private static final int MAXIMO_POR_EVENTO = 6;

    /**
     * Suma las boletas ya emitidas por el cliente para el evento y las nuevas que pide.
     * Recibe solo números, no entidades, para que el test sea trivial de escribir.
     */
    public void validarCompra(String clienteId, String eventoId, int yaAdquiridas, int solicitadas) {

        int total = yaAdquiridas + solicitadas;

        if (total > MAXIMO_POR_EVENTO) {
            throw new LimiteBoletasExcedidoException(
                    "El cliente %s ya tiene %d boletas para el evento %s y pidió %d más. "
                            .formatted(clienteId, yaAdquiridas, eventoId, solicitadas)
                            + "El máximo es %d.".formatted(MAXIMO_POR_EVENTO)
            );
        }
    }
}
```

### Regla 3 — Firma criptográfica del boleto

El enunciado pide *"una función de hash fácil de implementar"*. SHA-256 de la JDK sirve y no
agrega ninguna dependencia.

```java
package com.stagepass.domain.service;

import com.stagepass.domain.model.Boleto;
import com.stagepass.domain.model.OrdenCompra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class GeneradorFirma {

    /**
     * Firma los datos inmutables de la orden (id, total y expiración) mezclados con la clave
     * secreta del evento. Si alguien edita el total en la base de datos, la firma deja de
     * validar y el boleto es rechazado.
     */
    public String generar(OrdenCompra orden, String claveSecretaEvento) {
        // Se ordenan los campos a mano en vez de usar el toString del record: el orden de las
        // claves de un mapa no está garantizado y la firma cambiaría entre corridas.
        String datos = String.join("|",
                orden.getId(),
                orden.getMontoTotal().toPlainString(),
                orden.getExpiraEn().toString(),
                claveSecretaEvento
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(datos.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado por la plataforma Java; si falta, el runtime está roto.
            throw new IllegalStateException("El runtime de Java no soporta SHA-256", e);
        }
    }

    public String generarCodigoRedencion(Boleto boleto) {
        return "SP-" + boleto.getId().substring(0, 8).toUpperCase();
    }
}
```

### Regla 4 — Expiración a los 10 minutos

```java
package com.stagepass.domain.service;

import com.stagepass.domain.model.OrdenCompra;

import java.time.Duration;
import java.time.Instant;

public class PoliticaExpiracion {

    // El enunciado fija 10 minutos estrictos. Va como constante y no como número suelto
    // para que nadie lo cambie "por un día" sin que se note.
    public static final Duration VENTANA_DE_BLOQUEO = Duration.ofMinutes(10);

    public Instant calcularVencimiento(Instant inicio) {
        return inicio.plus(VENTANA_DE_BLOQUEO);
    }

    public boolean expiro(OrdenCompra orden, Instant ahora) {
        return orden.getEstado() == EstadoOrden.PENDIENTE_PAGO
                && ahora.isAfter(orden.getExpiraEn());
    }

    public void expirarSiCumplio(OrdenCompra orden, Instant ahora) {
        if (expiro(orden, ahora)) {
            orden.expirar();
        }
    }
}
```

### Excepciones

Todas heredan de una sola base. Si el dominio tira `RuntimeException` sueltas, los adaptadores
primarios no pueden mapearlas a un código HTTP con criterio.

```java
package com.stagepass.domain.exception;

public class ExcepcionNegocio extends RuntimeException {
    public ExcepcionNegocio(String mensaje) { super(mensaje); }
}
```

Y las que heredan: `AsientoNoDisponibleException`, `LimiteBoletasExcedidoException`,
`PagoNoVerificadoException`, `OrdenExpiradaException`.

---

## 6. Día 2 — Los puertos (lo que desbloquea al Integrante 3)

Esta es la parte que hace posible que los tres trabajen en paralelo. Cuando termines este
apartado, mergeá y avisá.

### Puertos secundarios (`ports/out/`) — los define el dominio

```java
package com.stagepass.ports.out;

import com.stagepass.domain.model.Asiento;
import java.time.Instant;
import java.util.List;

/**
 * El dominio declara qué necesita del almacén legacy, no cómo está guardado.
 * Notá que el retorno es List<Asiento>: entidades del dominio, nunca un ResultSet.
 */
public interface AsientoRepository {

    List<Asiento> listarPorRecinto(String recintoId);

    Asiento buscarPorId(String asientoId);

    void bloquear(List<String> asientosId, Instant hasta);

    void marcarVendidos(List<String> asientosId);

    void liberar(List<String> asientosId);
}
```

```java
package com.stagepass.ports.out;

import com.stagepass.domain.model.OrdenCompra;
import java.time.Instant;
import java.util.List;

public interface OrdenRepository {

    void guardar(OrdenCompra orden);

    OrdenCompra buscarPorId(String ordenId);

    List<OrdenCompra> buscarActivasPorClienteYEvento(String clienteId, String eventoId);

    /** Órdenes pendientes de pago cuya ventana de 10 minutos ya venció. */
    List<OrdenCompra> buscarVencidas(Instant ahora);
}
```

Y los demás, igual de simples: `BoletoRepository`, `PaymentGateway`, `TicketGenerator`,
`Notificador`, `FacturaEmitter`.

**El puerto de pagos es uno solo**, según el enunciado:

```java
package com.stagepass.ports.out;

import java.math.BigDecimal;

public interface PaymentGateway {

    ReferenciaPago registrarPago(SolicitudPago solicitud);

    EstadoPago consultarEstado(ReferenciaPago referencia);

    record SolicitudPago(String ordenId, BigDecimal monto, String correoComprador) {}
    record ReferenciaPago(String pasarela, String identificadorTransaccion) {}
    enum EstadoPago { PENDIENTE, APROBADO, RECHAZADO }
}
```

> **Esto es el punto clave del taller.** PayPal, Wompi y PSE son tres clases distintas que
> implementan **una sola interfaz**. El dominio nunca menciona ninguna de las tres.
> Si el diagrama de tu compañero muestra `PayPal` como entidad del dominio, está mal y
> hay que corregirlo antes de la defensa.

Igual con las notificaciones: **un** puerto `Notificador`, dos adaptadores (Sendgrid y
WhatsApp).

### Puertos primarios (`ports/in/`)

```java
package com.stagepass.ports.in;

import com.stagepass.domain.model.OrdenCompra;
import java.util.List;

public interface CheckoutUseCase {

    IniciarCompraResponse iniciarCompra(SolicitudCompra solicitud);

    OrdenCompra seleccionarAsientos(String ordenId, List<String> asientosId);

    record SolicitudCompra(String eventoId, String clienteId, List<String> asientosId) {}
    record IniciarCompraResponse(String ordenId, String codigoPago, String expiraEn) {}
}
```

Más `PagosUseCase`, `ValidacionUseCase` y `MantenimientoUseCase` (este último corre el
barrido de órdenes vencidas).

---

## 7. Día 2 — Los casos de uso

Los casos de uso viven en `application/`. Usan el dominio y los puertos, y **no conocen** Spring
ni JDBC.

```java
package com.stagepass.application;

import com.stagepass.domain.model.*;
import com.stagepass.ports.out.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class IniciarCompra {

    private final AsientoRepository asientos;
    private final OrdenRepository ordenes;
    private final CalculadoraTarifaria calculadora;
    private final ValidadorLimites validadorLimites;
    private final PoliticaExpiracion politicaExpiracion;

    // Clock inyectado a propósito: los tests fijan la hora con Clock.fixed() y no
    // tienen que esperar 10 minutos para probar la expiración.
    private final Clock reloj;

    public IniciarCompra(AsientoRepository asientos, OrdenRepository ordenes,
                         CalculadoraTarifaria calculadora, ValidadorLimites validadorLimites,
                         PoliticaExpiracion politicaExpiracion, Clock reloj) {
        this.asientos = asientos;
        this.ordenes = ordenes;
        this.calculadora = calculadora;
        this.validadorLimites = validadorLimites;
        this.politicaExpiracion = politicaExpiracion;
        this.reloj = reloj;
    }

    public String ejecutar(String eventoId, String clienteId, List<String> asientosId) {

        Instant ahora = reloj.instant();

        List<Asiento> seleccionados = asientosId.stream()
                .map(asientos::buscarPorId)
                .toList();

        // Si uno solo no está libre, se cae toda la transacción. Reserva media y abandona
        // los demás no tiene sentido: el cliente quiere esos asientos, no un paquete.
        for (Asiento asiento : seleccionados) {
            if (!asiento.estaDisponible()) {
                throw new AsientoNoDisponibleException(
                        "El asiento %s no está disponible".formatted(asiento.getCodigoUbicacion()));
            }
        }

        List<OrdenCompra> previas = ordenes.buscarActivasPorClienteYEvento(clienteId, eventoId);
        int yaAdquiridas = previas.stream().mapToInt(o -> o.getAsientos().size()).sum();

        validadorLimites.validarCompra(clienteId, eventoId, yaAdquiridas, seleccionados.size());

        BigDecimal subtotal = seleccionados.stream()
                .map(Asiento::getPrecioBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var resultado = calculadora.calcular(seleccionados.size(), subtotal);

        String ordenId = java.util.UUID.randomUUID().toString();
        Instant vencimiento = politicaExpiracion.calcularVencimiento(ahora);

        OrdenCompra orden = new OrdenCompra(ordenId, clienteId, eventoId, seleccionados,
                resultado.tarifaServicio(), resultado.descuento(), resultado.montoTotal(),
                vencimiento, EstadoOrden.PENDIENTE_PAGO);

        // Bloquear y guardar tienen que ser atómicos. Si el proceso se muere entre los dos,
        // el asiento queda bloqueado sin orden que lo libere: por eso el adaptador de
        // persistencia resuelve esto con una transacción y el puerto no lo expone.
        for (Asiento asiento : seleccionados) {
            asiento.bloquear(vencimiento);
        }
        ordenes.guardar(orden);

        return ordenId;
    }
}
```

Los demás casos de uso: `ConfirmarPago` (verifica con la pasarela y emite el boleto),
`ExpirarReservas` (el barrido) y `ValidarBoleto` (recalcula el hash y compara).

---

## 8. Día 2 — La prueba de aislamiento (tu gran aliado)

Esta prueba es lo que convierte "no importé nada raro" en algo **demostrable**.

```java
package com.stagepass.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PruebaAislamientoArquitectonicoTest {

    private final JavaClasses clases = new ClassFileImporter()
            .importPackages("com.stagepass.domain");

    @Test
    void elDominioNoDebeImportarNingunPaqueteExterno() {
        // La regla real: el dominio solo puede tocar el JDK. Se permiten java.lang y
        // java.util porque no se pueden evitar; cualquier otra raíz es technology leakage.
        ArchRule sinFueraDelJdk = noClasses()
                .that().resideInAPackage("com.stagepass.domain..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java.lang..", "java.util..", "java.math..",
                        "java.time..", "com.stagepass.domain..");

        sinFueraDelJdk.check(clases);
    }

    @Test
    void losPuertosNoDevenDevolverTiposTecnicos() {
        ArchRule sinSql = noClasses()
                .that().resideInAPackage("com.stagepass.ports..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "javax.persistence..", "org.springframework..");

        sinSql.check(clases);
    }
}
```

Dependencia en el `pom.xml`:

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

Si esa prueba pasa, el aislamiento no es una promesa: es un hecho. La corrés con:

```bash
./mvnw test
```

---

## 9. Día 2 — Tests de las reglas de negocio

Cuatro tests, uno por regla. Son cortos y valen más que cualquier explicación.

```java
package com.stagepass.domain.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class CalculadoraTarifariaTest {

    private final CalculadoraTarifaria calculadora = new CalculadoraTarifaria();

    @Test
    void unaBoletaCobra10Por cientoDeTarifaYSinDescuento() {
        var resultado = calculadora.calcular(1, new BigDecimal("100000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("10000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("0.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("110000.00");
    }

    @Test
    void tresBoletasCobran8PorCientoY5DeDescuento() {
        // El enunciado pide 8% de tarifa y 5% de descuento en el tramo de 3 a 5.
        var resultado = calculadora.calcular(3, new BigDecimal("300000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("24000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("15000.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("309000.00");
    }

    @Test
    void seisBoletasCobran5PorCientoY10DeDescuento() {
        var resultado = calculadora.calcular(6, new BigDecimal("600000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("30000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("60000.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("570000.00");
    }

    @Test
    void elLimitePorTramoCambiaEnElBoletoNumeroTres() {
        // Tramo 2 = 10%, tramo 3 = 8%. Si este test falla, el if está mal puesto.
        var dos = calculadora.calcular(2, new BigDecimal("200000"));
        var tres = calculadora.calcular(3, new BigDecimal("300000"));

        assertThat(dos.tarifaServicio().divide(dos.montoTotal(), 4, java.math.RoundingMode.HALF_UP))
                .isGreaterThan(tres.tarifaServicio()
                        .divide(tres.montoTotal(), 4, java.math.RoundingMode.HALF_UP));
    }
}
```

Para el test de expiración, el truco es `Clock.fixed()`: simula que pasaron 11 minutos sin
esperar de verdad.

---

## 10. Día 3 — Merge

```bash
git switch main
git pull
git switch -c feat/nucleo

# ... hacés los commits ...

git push -u origin feat/nucleo
```

Commits chicos y con mensaje claro. El docente los va a leer:

```bash
git add src/main/java/com/stagepass/domain
git commit -m "feat(nucleo): agrega entidades de dominio y sus enums"

git add src/main/java/com/stagepass/domain/service
git commit -m "feat(nucleo): implementa calculadora de tarifas por tramos"

git add src/main/java/com/stagepass/domain/exception
git commit -m "feat(nucleo): agrega excepciones de negocio"

git add src/main/java/com/stagepass/ports
git commit -m "feat(nucleo): define puertos primarios y secundarios"

git add src/main/java/com/stagepass/application
git commit -m "feat(nucleo): implementa casos de uso de compra, pago y expiracion"

git add src/test/java/com/stagepass
git commit -m "test(nucleo): agrega pruebas de reglas de negocio y aislamiento ArchUnit"
```

Abrís el PR contra `main`, lo revisa el Integrante 3, y **mergeás con squash** apenas el
Integrante 3 confirme que los puertos le sirven. Ese merge es lo que desbloquea la parte 3 del
taller: no lo aplaces.

Criterios de terminado:

- [ ] Las 5 entidades están, con los atributos del diccionario.
- [ ] Las 4 reglas de negocio implementadas y testeadas.
- [ ] Todos los money en `BigDecimal`, redondeo en un solo punto.
- [ ] `Clock` inyectado en lo que maneja el tiempo.
- [ ] Los puertos son interfaces y no mencionan ninguna tecnología.
- [ ] Un solo puerto `PaymentGateway` y un solo `Notificador`.
- [ ] `mvnw test` pasa, incluida la prueba de ArchUnit.
- [ ] PR abierto y mergeado con squash.

---

## 11. Errores que te van a hacer perder tiempo

**Anotar el dominio con `@Entity` o `@Component`.** El enunciado lo prohíbe por nombre. Si
necesitás que Spring lo construya, eso se resuelve en la capa de configuración, que es del
Integrante 3.

**Devolver DTOs desde los puertos.** `OrdenRepository.buscarPorId()` devuelve `OrdenCompra`,
no un `OrdenDocumento`. Si el adaptador necesita su propio formato interno, lo traduce
adentro de su clase.

**Meter `new Date()` o `Instant.now()`** dentro de los casos de uso. Si el tiempo lo pide el
reloj inyectado, los tests corren al instante en vez de esperar 10 minutos.

**Usar `double` para el dinero.** `0.1 + 0.2` da `0.30000000000000004`. En una boleta eso es un
soplo de plata y un reclamo.

---

## Referencias

- Diagrama y contrato: [INTEGRANTE-1-DIAGRAMAS.md](INTEGRANTE-1-DIAGRAMAS.md)
- Consumo de tus puertos: [INTEGRANTE-3-ADAPTADORES.md](INTEGRANTE-3-ADAPTADORES.md)
- Enunciado: `13 - Taller Arquitectura Hexagonal.pdf`

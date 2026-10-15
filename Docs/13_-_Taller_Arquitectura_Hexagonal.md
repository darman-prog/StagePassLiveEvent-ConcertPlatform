# ARQUITECTURA DE SOFTWARE

## Taller: Arquitectura Hexagonal

**Docente:** MSc. Feisar Moreno

---

## Sistema de Gestión y Venta de Entradas: "StagePass Live — Event & Concert Platform"

*StagePass Live* es una compañía de entretenimiento que opera eventos musicales, festivales y funciones de teatro tanto a nivel nacional como internacional. Con el fin de sustituir su antiguo sistema de taquillas físicas y soportar picos masivos de demanda, la organización ha decidido desarrollar una nueva plataforma web de venta y expedición de boletería digital.

El reto principal radica en que la aplicación debe interactuar con múltiples servicios tecnológicos heterogéneos y cambiantes tales como pasarelas de pago, motores de notificación al usuario, generadores criptográficos de credenciales de acceso y autoridades tributarias. Al mismo tiempo, la organización se encuentra en una fase de transición tecnológica: debe mantener la consulta de recintos y aforos en su base de datos relacional *legacy* (MySQL), mientras que las reservas activas y las boletas emitidas deben almacenarse en una base de datos MongoDB.

El éxito del proyecto depende de que las reglas de negocio (cálculo de tarifas dinámicas, bloqueos temporales de asientos y emisión de boletas) permanezcan totalmente blindadas, independientes e invariantes frente a las tecnologías externas, *frameworks* web o mecanismos de almacenamiento elegidos.

---

## Objetivo del Taller

Tu trabajo y el de tu equipo es el de diseñar el diagrama de clases detallado en UML para la arquitectura de *StagePass Live*, aplicando con rigor el patrón de Arquitectura Hexagonal, y posteriormente implementar la solución propuesta en una infraestructura web.

El diseño debe garantizar:

1. **La soberanía del Núcleo de Dominio (Inside):** Ninguna clase de dominio o caso de uso puede depender de librerías de infraestructura, *frameworks* web, controladores ni librerías de persistencia.
2. **El Principio de Inversión de Dependencias (DIP):** Las reglas de negocio interactúan únicamente a través de puertos diseñados para su propósito funcional, mientras que adaptadores con los detalles técnicos concretos utilizan los puertos.

---

## Especificación del Dominio y Reglas de Negocio

### 1. Entidades del Dominio

Las clases de dominio deben modelarse como objetos puros del lenguaje, sin anotaciones técnicas ni dependencias de terceros:

- **Evento / Función:** Identificador, Nombre, Tipo de evento (Concierto, Teatro, Festival), RecintoId, Fecha/Hora y Estado (Programado, Agotado, Cancelado).
- **Localidad / Asiento:** Identificador, Recinto Id, Código de ubicación (ej. VIP-A12, General-01), Precio base y Estado (Disponible, Bloqueado, Vendido).
- **Cliente:** Identificador, Nombre completo, Correo electrónico, Teléfono móvil y Nacionalidad.
- **Reserva / Orden de Compra:** Identificador, Cliente, Lista de Asientos seleccionados, Tarifa de servicio liquidada, Descuento aplicado, Monto total a pagar, Tiempo de expiración (bloqueo temporal) y Estado de la orden (Pendiente de Pago, Aprobada, Rechazada, Expirada).
- **Boleto Digital (Ticket):** Identificador único, Orden Id, Código alfanumérico de redención, Firma hash de seguridad y Fecha de emisión.

### 2. Reglas de Negocio

- **Bloqueo Temporal de Asientos:** Al iniciar el proceso de compra, los asientos se marcan como Bloqueados durante un tiempo estricto de 10 minutos. Si la confirmación del pago no se recibe antes del vencimiento, se deben liberar automáticamente los asientos.
- **Cálculo de Descuento por Volumen y Tarifa de Emisión:**
  - **Compras de 1 a 2 boletos:** Tarifa administrativa del 10% sobre el valor base; 0% de descuento.
  - **Compras de 3 a 5 boletos:** Tarifa administrativa del 8%; descuento comercial del 5% sobre el valor de las boletas.
  - **Compras corporativas (más de 5 boletos):** Tarifa administrativa fija del 5%; descuento del 10%.
- **Validación de Identidad y Límites:** Un cliente no puede tener más de 6 boletas acumuladas para un mismo evento.
- **Emisión y Validación Criptográfica:** Una boleta solo puede generarse si el pago fue exitoso y verificado; su código *hash* de seguridad se calcula a partir de los datos inmutables de la orden y la clave secreta del evento (se debe buscar una función de *hash* fácil de implementar).

---

## Requisitos de Infraestructura y Fronteras Arquitectónicas

El sistema interactúa con el mundo exterior mediante Puertos y Adaptadores, cumpliendo las siguientes directrices:

### 1. Adaptadores Primarios (Driving Adapters)

Deben capturar las solicitudes del exterior y llamar a los puertos primarios para:

- Recibir las solicitudes de inicio de compra y selección de asientos enviadas por el navegador web o la aplicación móvil del cliente.
- Recibir, por medio de un *endpoint* expuesto, las notificaciones asíncronas de las pasarelas de pago cuando una transacción bancaria cambia de estado.

### 2. Puertos Secundarios (Driven Ports) y sus Adaptadores

El dominio debe definir las interfaces que necesita para operar, y la capa de infraestructura debe proveer sus implementaciones:

- **Persistencia Dual:**
  - Consulta de recintos y asientos *legacy* a una base de datos MySQL.
  - Almacenamiento de datos de órdenes y boletas emitidas en una base de datos MongoDB.
- **Pasarelas de Pago Homogéneas:**
  - Para este proceso se debe incluir un puerto común del dominio que defina las operaciones abstractas de registro de pagos y recepción de confirmación de pagos.
  - Adaptadores específicos para PayPal, Wompi y PSE.
- **Servicios Externos Complementarios:**
  - Generación de representaciones gráficas y criptográficas de los boletos.
  - Puertos para envío de notificaciones por medio de WhatsApp y correo electrónico (Sendgrid).
  - Emisión de facturas electrónicas (DIAN).

---

## Reglas de Aislamiento Arquitectónico

El modelo debe respetar de forma estricta las fronteras hexagonales:

- **Núcleo / Dominio (Core – Inside):**
  - **Permitido:** Clases del modelo de dominio, excepciones de negocio, interfaces de casos de uso (puertos primarios) e interfaces de repositorios/servicios (puertos secundarios).
  - **Prohibido:** Anotaciones de librerías externas, clases tipo *HttpRequest*, librerías de conexión SQL o llamadas HTTP directas.
- **Puertos (Ports):**
  - **Permitido:** Interfaces puras con tipos de datos pertenecientes exclusivamente al dominio.
  - **Prohibido:** Métodos que expongan firmas tecnológicas (SQL, Http Request) o que retornen DTOs de pasarelas como PayPal.
- **Adaptadores (Adapters – Outside):**
  - **Permitido:** Conocer tecnologías concretas (MySQL, MongoDB, SDKs de PayPal, Wompi, librerías QR, etc.), mapear modelos técnicos hacia el dominio e implementar los puertos secundarios o invocar los primarios.
  - **Prohibido:** Implementar lógica de negocio o alterar los cálculos de tarifas y descuentos directamente sin pasar por el núcleo.

---

## Entregables

Cada equipo de trabajo deberá presentar:

- **Diagrama de Clases UML:** Elaborado en una herramienta formal (Draw.io, Lucidchart, etc.), identificando clases, atributos, métodos, relaciones y dependencias.
- **Código Fuente:** Implementación de los casos de uso. El núcleo y los puertos deben ser completamente funcionales. Al menos dos de los adaptadores (elección del grupo) deben estar completamente implementados; los restantes pueden ser *mocks*.

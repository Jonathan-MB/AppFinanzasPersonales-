# Decisiones

Lo que se decidió sin estar definido en el documento original.

## Tecnología
- **Android nativo (Kotlin + Jetpack Compose + Room)** en vez de React Native + Expo. El APK se genera en Android Studio, sin cuenta de Expo ni compilación en la nube. La funcionalidad es la misma.
- Base de datos SQLite con Room, versión 1. Las migraciones futuras van en `BaseDatos.MIGRACIONES` y el esquema se exporta en `app/schemas`.
- Gráficas dibujadas con Canvas, sin librerías externas.
- Android 10 (API 29) como mínimo.

## Pantallas
- La app abre en **Registrar**: primero se elige Gasto o Ingreso y luego una etiqueta. Arriba está el control de deberías tener, tengo y debo.
- Pestañas: Registrar · Inicio · Calendario · Movimientos · Más. Deudas está en Más y en las tarjetas de Inicio y Registrar.
- Los botones rápidos son la pantalla Registrar (Gasto, Ingreso y Moto) y un botón flotante "Gasto" en las demás pestañas.
- Formularios, fechas y confirmaciones se dibujan dentro de la pantalla, sin ventanas emergentes.

## Etiquetas
- Las categorías se usan como **etiquetas** con emoji y tipo (gasto, ingreso o sistema). Se pueden crear, editar y borrar.
- Una etiqueta de gasto puede **descontar de un presupuesto**: el gasto se liga a la quincena o periodo del presupuesto según la fecha.
- Las etiquetas de ingreso definen el tipo de ingreso (Salario → salario, Moto → moto, etc.).

## Calendario de pagos
- Los pagos se generan desde la fecha de inicio; antes no se muestra nada.
- Deudas con cuotas totales y pagadas: solo se generan las cuotas que faltan, contadas desde la fecha de inicio.
- Deudas sin cuota pero con fecha fin: un pago único en esa fecha por el saldo.
- Gastos quincenales y presupuestos quincenales: días 15 y último día del mes.
- Presupuesto mensual: día de "desde" o día 1. Presupuesto único: una sola fecha (la fecha o el inicio del rango) y cuenta los gastos de todo el rango.
- Si cambia el día de pago, los pagos ya hechos se pasan al pago del mismo mes.
- El "avance" topa cada pago en su presupuestado para no pasar del 100 %.

## Deudas
- Saldo estimado: último extracto con fecha hasta hoy menos los pagos posteriores. Si el extracto tiene fecha futura (préstamo que aún no llega) se usa el saldo inicial.
- Deudas tipo "buy now pay later" con fecha fin no entran al plan de ataque porque terminan solas.
- Deudas sin intereses van al final del plan salvo que se fijen arriba.
- La tasa se escribe en porcentaje E.A. (28,59) y se guarda como decimal (0.2859).

## TXT
- Se agregaron columnas al final para no perder datos al importar: `fecha_fin` y `fijada_arriba` en DEUDAS, `dia_confirmado` en GASTOS_FIJOS, `id` en DEUDA_SALDOS, y `emoji|tipo|orden|presupuesto_id|id` en CATEGORIAS. Las primeras columnas respetan el orden original.
- MOVIMIENTOS tiene la columna `liquida` al final (si el pago se marcó como completo). Los TXT viejos sin esa columna se leen como `no`.
- Booleanos como `si` / `no`. Tasas con punto decimal.
- `created_at` y `updated_at` no se exportan; al importar se ponen con la fecha actual.

## Avisos
- En vez de programar una alarma por cada pago, la app revisa todos los días a la hora de aviso los pagos de hoy y los de dentro de N días. No tiene límite de 60 días y se reprograma al reiniciar el celular.
- Las alertas de presupuesto (80 % y 100 %) salen al guardar el gasto.
- En la misma revisión diaria se avisa si hay pagos de días anteriores que siguen sin pagar (sin contar presupuestos).

## Cuentas y cuadre
- Antes el saldo real era una foto fija: después de reportarlo no se movía con los gastos y parecía que sobraba plata. Ahora cada cuenta lleva su saldo con lo registrado y la suma de las cuentas siempre es lo que deberías tener.
- Lo que no tiene cuenta (registros viejos y la plata inicial) va a la primera cuenta activa.
- Al registrar se propone la última cuenta usada.
- Cuadrar guarda un ajuste por cada cuenta que no coincide y una marca con el desfase total. Ese ajuste entra en lo que deberías tener, pero no cuenta como gasto ni ingreso del mes.
- Un envío o retiro son dos filas con el mismo grupo: una sale y otra entra. Borrar desde el historial quita las dos juntas.
- Base de datos versión 3: la migración crea las tablas de cuentas y la columna `cuentaId` en movimientos e ingresos, y agrega Davivienda, Nequi y Efectivo. En el TXT van las secciones `[CUENTAS]` y `[CUENTA_MOVS]`, y la columna `cuenta_id` al final de movimientos e ingresos. Los TXT viejos se siguen leyendo.

## Respaldos
- Respaldo automático semanal en la carpeta interna de la app (se guardan los 10 más recientes), además de antes de importar y antes de borrar todo.

## Datos personales
- Al cambiar el archivo de datos propios, la app instalada no lo vuelve a leer sola. En Exportar / importar hay un botón que toma de ahí solo deudas, gastos fijos, presupuestos, ingresos programados y eventos, y conserva lo registrado, la configuración y las etiquetas.
- Los datos propios no están en el código. Van en `app/src/main/assets/datos_personales.txt`, que está en `.gitignore`. Sin ese archivo la app arranca vacía con las etiquetas por defecto.
- Las pruebas usan datos inventados.

## Pagos variables y quincena
- Un pago puede marcarse como completo aunque el valor sea distinto al presupuestado (recibos de servicios o tarjetas). Ese mes el presupuestado pasa a ser lo pagado y el original se guarda como estimado para mostrarlo.
- Si se marca como abono queda debiendo la diferencia. Por defecto sale en pago completo.
- Los presupuestos no tienen esta opción porque son topes, no deudas.
- La quincena se arma con los días del sueldo: con el del 15 se paga del 15 al 29 y con el del 30 del 30 al 14 (en febrero el del 30 llega el 28). Si no hay dos sueldos fijos se usa del 1 al 15 y del 16 a fin de mes. El Reparto usa la misma regla para las deudas.
- En Registrar e Inicio salen primero los pagos atrasados, luego todos los del periodo actual en orden y después todo el periodo siguiente, sin límite. "Pagar todo" registra lo que falta de los atrasados y del periodo actual con la fecha de hoy.
- Base de datos versión 2: la migración agrega la columna `liquida` a movimientos sin borrar nada.
- Un pago ya guardado se puede cambiar entre pago completo y abono desde Historial.

## Revisión de calidad
- Los pagos se calculan siempre sobre el mes completo y luego se recorta al rango pedido. Antes, al pedir un rango corto (la quincena o el aviso diario), el pago de otra semana o quincena del mismo mes se contaba en la que sí estaba en el rango.
- Un día de la semana fuera de 0 a 6 (por ejemplo en un TXT editado a mano) se ajusta en vez de colgar la app.
- El botón Guardar del monto se bloquea después del primer toque para no registrar dos veces.
- La hora del aviso acepta `7:00` o `07:00`. Semanas por mes tiene que ser mayor que 0.

## Versión de datos 4 (proyección, tarjetas, pausas, bolsillo)
- Proyección: arranca con lo que deberías tener menos el colchón. Suma los ingresos esperados que no son inciertos (los estimados como la prima entran, pero marcados) y resta los pagos con su fecha efectiva, lo vencido el día de hoy y los presupuestos. Lo que falta del presupuesto vigente se reparte en partes iguales hasta el fin de su periodo, sin perder pesos por redondeo. El horizonte llega al día anterior al segundo sueldo.
- Un sueldo esperado que ya pasó y no se registró no entra en la proyección: se asume que hay que registrarlo.
- Presupuesto quincenal con dos sueldos fijos: el periodo va de un sueldo al siguiente (del 15 al 29 y del 30 al 14), igual que los pagos. La instancia lleva la fecha del día del sueldo.
- "Real disponible" de un presupuesto = lo que queda menos el faltante. La especificación decía min(restante, libre), pero la proyección ya descuenta el presupuesto y con esa fórmula se habría contado dos veces.
- Ingresos inciertos: la moto siempre lo es, al importar y al editar. No entran en la proyección; se muestran aparte como "Con moto: llegas con $X" y con una línea punteada en la gráfica.
- Compra con tarjeta: el movimiento lleva tarjetaId y no baja ninguna cuenta ni lo que deberías tener. Sube el saldo de la deuda (las compras del mismo día del extracto cuentan) y crea pagos "puente". Si la compra paga un pago programado (por ejemplo, los perros), ese pago queda saldado. En el historial no suma al total de gastos, porque lo que suma es el pago del puente cuando sale plata.
- Puentes: a 1 cuota, uno en la fecha elegida (por defecto el próximo sueldo); a N cuotas, N pagos mensuales en el día de pago de la deuda, empezando después de la compra, con la cuota de la regla 3.8. A 1 cuota se cobra interés solo si la tarjeta lo cobra.
- Cupo disponible: el reportado si es más nuevo que el último pago o compra; si no, el cupo total menos el saldo. Sin cupo total, el reportado menos las compras más los pagos posteriores.
- Una tarjeta bloqueada pide dos confirmaciones ("entiendo lo que cuesta" y "sí, quiero usarla"). Usarla igual deja "(usó tarjeta bloqueada)" en la nota, que es el aviso 16 en el historial. Si además es solo de tiendas, el mensaje dice las dos cosas.
- Pausas: se aplican sobre la fecha original del pago. Pospuestos: conservan la clave y los pagos ligados, cambian la fecha y el estado se calcula con la fecha nueva. Pueden traer al rango un pago de otro mes.
- Bolsillo: se propone apartarlo cuando ya llegó el sueldo del día configurado y aún no hay traslado a esa cuenta en el periodo. Sale de la última cuenta normal usada. El colchón no cuenta como libre en la proyección.
- La cuenta que se propone al registrar es la última normal usada: nunca el bolsillo ni el colchón.
- Abono sugerido: solo sin faltante. Primero completa el colchón; con el colchón completo, lo libre redondeado a miles va a la primera deuda del plan de ataque.
- Aviso de pago anticipado: cuando lo que se paga vence en el periodo del siguiente sueldo.
- Aviso 13: se repite solo si cambia el faltante o empieza otro periodo (se guarda en las preferencias del celular). Los avisos 14 y 15 salen el día del sueldo.
- "Actualizar desde la app" toma ahora deudas, extractos, gastos fijos, presupuestos, ingresos programados, eventos, tarjetas, pausas, pospuestos, cuentas (por id, sin borrar las que solo están en la app) y la configuración del plan (avisos, plan, bolsillo y colchón). No toca lo registrado, los cuadres, la plata inicial ni la fecha de inicio.
- Con el teclado abierto el contenido se achica (imePadding) y en la pantalla del monto el botón Guardar queda fijo encima del teclado.
- No se hizo el 11.2 (subcréditos de un rotativo). Sí está el 11.1 (saldo estimado con intereses desde el extracto).
- Base de datos versión 4: la migración crea tarjetas, pausas y pospuestos, y agrega columnas nuevas sin borrar nada. Marca la moto como incierta.

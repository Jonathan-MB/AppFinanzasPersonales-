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
- La quincena va del 1 al 15 y del 16 al fin de mes. En Inicio salen todos sus pagos en orden, los de antes sin pagar aparte, y "Pagar todo" registra lo que falta de cada uno con la fecha de hoy.
- Base de datos versión 2: la migración agrega la columna `liquida` a movimientos sin borrar nada.
- Un pago ya guardado se puede cambiar entre pago completo y abono desde Historial.

## Revisión de calidad
- Los pagos se calculan siempre sobre el mes completo y luego se recorta al rango pedido. Antes, al pedir un rango corto (la quincena o el aviso diario), el pago de otra semana o quincena del mismo mes se contaba en la que sí estaba en el rango.
- Un día de la semana fuera de 0 a 6 (por ejemplo en un TXT editado a mano) se ajusta en vez de colgar la app.
- El botón Guardar del monto se bloquea después del primer toque para no registrar dos veces.
- La hora del aviso acepta `7:00` o `07:00`. Semanas por mes tiene que ser mayor que 0.

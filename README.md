# Mis Cuentas

App Android de finanzas personales, 100 % offline, sin login y sin servidor. Pesos colombianos (COP), en español.

Al abrir la app lo primero es elegir **Gasto** o **Ingreso**, tocar una etiqueta (Comida, Gasolina, Salario, Moto…) y escribir cuánto fue. Arriba siempre se ve cuánto deberías tener, cuánto tienes de verdad y cuánto debes.

## Qué hace

- **Registrar:** gasto o ingreso en dos toques con etiquetas. Se pueden crear etiquetas nuevas y conectarlas a un presupuesto.
- **Control de la plata:** "Deberías tener hoy" = plata inicial + lo que entró − lo que salió, con ajuste contra el saldo real.
- **Inicio:** vencidos, próximos 7 días, no planeado y moto del mes, endeudamiento con semáforo, avance del mes, presupuestos y gráficas.
- **Calendario:** pagos de cualquier mes (deudas, gastos fijos, presupuestos y eventos) en lista o en cuadrícula, con estados y filtros.
- **Movimientos:** buscador, filtros, totales, edición y borrado con deshacer.
- **Deudas:** saldo estimado, intereses, meses para pagar, simulador y plan de ataque (bola de nieve o avalancha).
- **Más:** gastos fijos, presupuestos, ingresos programados, eventos, etiquetas, reparto por quincena, configuración, exportar e importar TXT.
- **Avisos:** notificación de pagos unos días antes y el mismo día, recordatorio nocturno opcional y alertas de presupuesto al 80 % y al 100 %.
- **Respaldos:** automático cada semana dentro de la app y antes de importar o borrar todo.

## Requisitos

- Android Studio (Koala o más nuevo) con el SDK de Android 35.
- JDK 17 a 21 para Gradle (si Android Studio pregunta, elegir **JVM 21**).
- Celular o emulador con Android 10 o superior.

## Correr en desarrollo

1. Abrir esta carpeta en Android Studio (**File → Open**).
2. Esperar la sincronización de Gradle.
3. Elegir un emulador o un celular con depuración USB y pulsar **Run ▶**.

Desde la terminal:

```bash
./gradlew assembleDebug
```

## Generar el APK

En Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalarlo en el celular

1. Pasar el APK al celular (cable, Drive o WhatsApp).
2. Abrirlo y permitir **instalar apps de origen desconocido**.
3. Abrir **Mis Cuentas** y aceptar el permiso de notificaciones.

## Datos personales (no van a git)

La app arranca vacía, solo con las etiquetas. Para cargar tus propios datos la primera vez:

1. Crea el archivo `app/src/main/assets/datos_personales.txt` con el formato de exportación (sección siguiente).
2. Compila e instala. En el primer arranque la app lo carga.

Ese archivo está en `.gitignore` y **nunca se sube al repositorio**. También se puede importar un TXT desde la app en **Más → Exportar / importar TXT**.

## Formato TXT

Archivo UTF-8 `finanzas_AAAA-MM-DD_HHMM.txt` con secciones `[RESUMEN]`, `[CONFIG]`, `[DEUDAS]`, `[DEUDA_SALDOS]`, `[GASTOS_FIJOS]`, `[PRESUPUESTOS]`, `[INGRESOS_PROGRAMADOS]`, `[EVENTOS]`, `[CALENDARIO]`, `[MOVIMIENTOS]`, `[INGRESOS]` y `[CATEGORIAS]`, separadas por `|`. Montos en pesos enteros y fechas `AAAA-MM-DD`. Al importar, `[RESUMEN]` y `[CALENDARIO]` se ignoran porque se recalculan.

## Pruebas

```bash
./gradlew testDebugUnitTest
```

Cubren las reglas de cálculo (estados, deberías tener, saldo estimado, tasa, plazo, simulador, endeudamiento, avance, reparto, plan de ataque) y el exportador/importador TXT (exportar → importar → exportar da lo mismo salvo `#generado`). Usan datos inventados.

## Estructura

```
app/src/main/java/com/jonathan/miscuentas/
  dominio/   modelos, calculos, calendario de pagos y TXT (sin Android, se prueba con JUnit)
  datos/     base de datos Room (SQLite) y repositorio
  avisos/    notificaciones
  ui/        pantallas en Jetpack Compose
```

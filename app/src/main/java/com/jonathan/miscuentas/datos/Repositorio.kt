package com.jonathan.miscuentas.datos

import android.content.Context
import com.jonathan.miscuentas.dominio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class Repositorio(private val context: Context) {

    private val dao = BaseDatos.obtener(context).dao()

    @Suppress("UNCHECKED_CAST")
    val datos: Flow<Datos?> = combine(
        listOf(
            dao.config(), dao.deudas(), dao.deudaSaldos(), dao.gastosFijos(), dao.presupuestos(),
            dao.ingresosProgramados(), dao.eventos(), dao.movimientos(), dao.ingresos(), dao.categorias(), dao.cuentas(), dao.cuentaMovs(),
            dao.tarjetas(), dao.pausas(), dao.pospuestos(),
        )
    ) { v ->
        val config = v[0] as Config? ?: return@combine null
        Datos(
            config = config,
            deudas = v[1] as List<Deuda>,
            deudaSaldos = v[2] as List<DeudaSaldo>,
            gastosFijos = v[3] as List<GastoFijo>,
            presupuestos = v[4] as List<Presupuesto>,
            ingresosProgramados = v[5] as List<IngresoProgramado>,
            eventos = v[6] as List<Evento>,
            movimientos = v[7] as List<Movimiento>,
            ingresos = v[8] as List<Ingreso>,
            categorias = v[9] as List<Categoria>,
            cuentas = v[10] as List<Cuenta>,
            cuentaMovs = v[11] as List<CuentaMov>,
            tarjetas = v[12] as List<Tarjeta>,
            pausas = v[13] as List<Pausa>,
            pospuestos = v[14] as List<Pospuesto>,
        )
    }

    // la primera vez carga los datos propios si estan en assets, si no arranca vacia
    suspend fun iniciar() {
        if (dao.configActual() != null) return
        dao.reemplazarTodo(datosDeArranque())
    }

    private suspend fun datosDeArranque(): Datos = withContext(Dispatchers.IO) {
        val base = Semilla.datos()
        val texto = runCatching {
            context.assets.open(Semilla.ARCHIVO_PERSONAL).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext base
        Txt.importar(texto, base).datos
    }

    suspend fun actuales(): Datos? = datos.first()

    suspend fun guardar(v: Any) {
        val ahora = Fechas.ahora()
        when (v) {
            is Config -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Deuda -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is DeudaSaldo -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is GastoFijo -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Presupuesto -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is IngresoProgramado -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Evento -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Movimiento -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Ingreso -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Categoria -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Cuenta -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is CuentaMov -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Tarjeta -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Pausa -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is Pospuesto -> dao.guardar(v.copy(updatedAt = ahora, createdAt = v.createdAt.ifBlank { ahora }))
            is List<*> -> v.forEach { it?.let { x -> guardar(x) } }
        }
    }

    suspend fun borrar(v: Any) {
        when (v) {
            is Deuda -> dao.borrar(v)
            is DeudaSaldo -> dao.borrar(v)
            is GastoFijo -> dao.borrar(v)
            is Presupuesto -> dao.borrar(v)
            is IngresoProgramado -> dao.borrar(v)
            is Evento -> dao.borrar(v)
            is Movimiento -> dao.borrar(v)
            is Ingreso -> dao.borrar(v)
            is Categoria -> dao.borrar(v)
            is Cuenta -> dao.borrar(v)
            is CuentaMov -> dao.borrar(v)
            is Tarjeta -> dao.borrar(v)
            is Pausa -> dao.borrar(v)
            is Pospuesto -> dao.borrar(v)
            is List<*> -> v.forEach { it?.let { x -> borrar(x) } }
        }
    }

    suspend fun reemplazarTodo(d: Datos) = dao.reemplazarTodo(d)

    // vuelve a los datos iniciales
    suspend fun borrarTodo() = dao.reemplazarTodo(datosDeArranque())

    // archivo para compartir
    suspend fun crearExportacion(d: Datos): File = withContext(Dispatchers.IO) {
        val ahora = LocalDateTime.now()
        val carpeta = File(context.cacheDir, "exportes").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        File(carpeta, Txt.nombreArchivo(ahora)).apply { writeText(Txt.exportar(d, LocalDate.now(), ahora), Charsets.UTF_8) }
    }

    fun carpetaRespaldos(): File = File(context.filesDir, "respaldos").apply { mkdirs() }

    suspend fun guardarRespaldo(d: Datos, prefijo: String = "respaldo"): File = withContext(Dispatchers.IO) {
        val ahora = LocalDateTime.now()
        val archivo = File(carpetaRespaldos(), prefijo + "_" + Txt.nombreArchivo(ahora))
        archivo.writeText(Txt.exportar(d, LocalDate.now(), ahora), Charsets.UTF_8)
        // deja solo los 10 mas nuevos
        carpetaRespaldos().listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
        archivo
    }

    // respaldo automatico cada 7 dias
    suspend fun respaldoSemanalSiToca(d: Datos) {
        val ultimo = Fechas.leer(d.config.ultimoRespaldo?.take(10))
        val hoy = LocalDate.now()
        if (ultimo == null || ultimo.plusDays(7) <= hoy) {
            guardarRespaldo(d, "semanal")
            guardar(d.config.copy(ultimoRespaldo = Fechas.iso(hoy)))
        }
    }

    // vuelve a leer deudas y gastos del archivo propio sin tocar lo registrado
    suspend fun actualizarDesdeArchivo(): ResultadoImport? {
        val texto = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(Semilla.ARCHIVO_PERSONAL).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        } ?: return null
        val actuales = actuales() ?: return null
        guardarRespaldo(actuales, "antes_de_actualizar")
        val res = Txt.actualizarBase(texto, actuales)
        if (res.seccionesLeidas.isNotEmpty()) dao.reemplazarTodo(res.datos)
        return res
    }

    // antes de importar guarda lo que habia
    suspend fun importar(texto: String): ResultadoImport {
        val actuales = actuales() ?: Semilla.datos()
        guardarRespaldo(actuales, "antes_de_importar")
        val res = Txt.importar(texto, actuales)
        if (res.seccionesLeidas.isNotEmpty()) dao.reemplazarTodo(res.datos)
        return res
    }
}

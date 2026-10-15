package com.jonathan.miscuentas.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathan.miscuentas.avisos.Avisos
import com.jonathan.miscuentas.datos.Repositorio
import com.jonathan.miscuentas.dominio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// lo que se eligio en la pantalla de etiquetas antes de poner el monto
sealed class Seleccion {
    data class Etiqueta(val etiqueta: Categoria) : Seleccion()
    data class Pago(val obligacion: Obligacion) : Seleccion()
    data class IngresoEtiqueta(val etiqueta: Categoria) : Seleccion()
    data class IngresoEsperadoSel(val esperado: IngresoEsperado) : Seleccion()
    data class AbonoDeuda(val deuda: Deuda) : Seleccion()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repositorio(app)
    val datos: StateFlow<Datos?> = repo.datos.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var seleccion: Seleccion? = null

    fun hoy(): LocalDate = LocalDate.now()

    init {
        viewModelScope.launch {
            repo.iniciar()
            val d = repo.datos.filterNotNull().first()
            Avisos.programar(getApplication(), d.config)
            repo.respaldoSemanalSiToca(d)
        }
    }

    fun guardar(v: Any) = viewModelScope.launch {
        repo.guardar(v)
        if (v is Config) Avisos.programar(getApplication(), v)
    }

    fun borrar(v: Any) = viewModelScope.launch { repo.borrar(v) }

    // arma el movimiento segun lo que se toco
    fun registrarGasto(sel: Seleccion, monto: Long, fecha: LocalDate, nota: String, completo: Boolean = false, cuentaId: String? = null) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val iso = Fechas.iso(fecha)
        var claveRevisar: Obligacion? = null
        val mov = when (sel) {
            is Seleccion.Pago -> {
                val o = sel.obligacion
                if (o.tipoOrigen == Origen.PRESUPUESTO) claveRevisar = o
                Movimiento(nuevoId(), iso, monto, nota, o.clave, TipoMovimiento.PAGO_PROGRAMADO, o.categoria, o.deudaId,
                    liquida = completo && o.tipoOrigen != Origen.PRESUPUESTO)
            }
            is Seleccion.AbonoDeuda ->
                Movimiento(nuevoId(), iso, monto, nota, null, TipoMovimiento.ABONO_EXTRA, Categorias.ABONO_EXTRA, sel.deuda.id)
            is Seleccion.Etiqueta -> {
                val et = sel.etiqueta
                val inst = et.presupuestoId?.let { Obligaciones.instanciaPresupuesto(d, it, fecha) }
                if (inst != null) {
                    claveRevisar = inst
                    Movimiento(nuevoId(), iso, monto, nota.ifBlank { et.nombre }, inst.clave, TipoMovimiento.PAGO_PROGRAMADO, et.nombre)
                } else {
                    Movimiento(nuevoId(), iso, monto, nota, null, TipoMovimiento.NO_PLANEADO, et.nombre)
                }
            }
            else -> return@launch
        }
        repo.guardar(mov.copy(cuentaId = cuentaId ?: Saldos.porDefecto(d)))
        claveRevisar?.let { antes ->
            val despues = antes.copy(pagado = antes.pagado + monto)
            Avisos.revisarPresupuesto(getApplication(), antes, despues)
        }
    }

    fun registrarIngreso(sel: Seleccion, monto: Long, fecha: LocalDate, nota: String, cuentaId: String? = null) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val iso = Fechas.iso(fecha)
        val ingreso = when (sel) {
            is Seleccion.IngresoEsperadoSel -> Ingreso(nuevoId(), iso, monto, sel.esperado.tipo, nota.ifBlank { sel.esperado.concepto }, sel.esperado.clave)
            is Seleccion.IngresoEtiqueta -> Ingreso(nuevoId(), iso, monto, Semilla.tipoIngreso(sel.etiqueta.nombre), nota.ifBlank { sel.etiqueta.nombre })
            else -> return@launch
        }
        repo.guardar(ingreso.copy(cuentaId = cuentaId ?: Saldos.porDefecto(d)))
    }

    // paga de una vez todo lo pendiente por lo que falta
    fun pagarTodo(lista: List<Obligacion>, fecha: LocalDate) = viewModelScope.launch {
        val cuenta = datos.value?.let { Saldos.porDefecto(it) }
        lista.filter { it.falta > 0 }.forEach { o ->
            repo.guardar(Movimiento(nuevoId(), Fechas.iso(fecha), o.falta, "", o.clave, TipoMovimiento.PAGO_PROGRAMADO, o.categoria, o.deudaId, liquida = true, cuentaId = cuenta))
        }
    }

    fun moto(monto: Long, fecha: LocalDate) = viewModelScope.launch {
        repo.guardar(Ingreso(nuevoId(), Fechas.iso(fecha), monto, TipoIngreso.MOTO, "Moto", cuentaId = datos.value?.let { Saldos.porDefecto(it) }))
    }

    // deja cada cuenta igual a lo que se tiene de verdad y anota el desfase
    fun cuadrar(reales: Map<String, Long>) = viewModelScope.launch {
        val d = datos.value ?: return@launch
        val total = reales.values.sum()
        repo.guardar(Saldos.cuadrar(d, reales, Fechas.iso(hoy())))
        repo.guardar(d.config.copy(saldoReal = total, fechaSaldoReal = Fechas.iso(hoy())))
    }

    // envio entre cuentas o retiro, no cambia el total
    fun mover(desde: String, hacia: String, monto: Long, fecha: LocalDate, nota: String) = viewModelScope.launch {
        repo.guardar(Saldos.trasladar(desde, hacia, monto, Fechas.iso(fecha), nota))
    }

    fun actualizarSaldoExtracto(deuda: Deuda, saldo: Long, fecha: LocalDate) = viewModelScope.launch {
        val iso = Fechas.iso(fecha)
        repo.guardar(deuda.copy(saldoExtracto = saldo, fechaSaldoExtracto = iso))
        repo.guardar(DeudaSaldo(nuevoId(), deuda.id, iso, saldo))
    }

    suspend fun exportar(): File? = datos.value?.let { repo.crearExportacion(it) }

    suspend fun importar(uri: Uri): ResultadoImport? = withContext(Dispatchers.IO) {
        val texto = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@withContext null
        repo.importar(texto)
    }

    suspend fun actualizarDesdeArchivo(): ResultadoImport? = repo.actualizarDesdeArchivo()

    fun borrarTodo() = viewModelScope.launch {
        datos.value?.let { repo.guardarRespaldo(it, "antes_de_borrar") }
        repo.borrarTodo()
    }
}

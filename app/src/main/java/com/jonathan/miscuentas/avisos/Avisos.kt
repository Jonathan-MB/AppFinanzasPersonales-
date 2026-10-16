package com.jonathan.miscuentas.avisos

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jonathan.miscuentas.MainActivity
import com.jonathan.miscuentas.R
import com.jonathan.miscuentas.datos.Repositorio
import com.jonathan.miscuentas.dominio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// una revision diaria a la hora de aviso y otra en la noche
object Avisos {
    private const val CANAL = "pagos"
    const val ACCION_PAGOS = "com.jonathan.miscuentas.PAGOS"
    const val ACCION_NOCHE = "com.jonathan.miscuentas.NOCHE"

    fun programar(context: Context, config: Config) {
        crearCanal(context)
        val hora = runCatching { LocalTime.parse(config.horaAviso) }.getOrDefault(LocalTime.of(7, 0))
        alarma(context, ACCION_PAGOS, 1, hora)
        if (config.recordatorioDiario) alarma(context, ACCION_NOCHE, 2, LocalTime.of(21, 0))
        else cancelar(context, ACCION_NOCHE, 2)
    }

    private fun pendiente(context: Context, accion: String, codigo: Int) = PendingIntent.getBroadcast(
        context, codigo, Intent(context, AvisosReceiver::class.java).setAction(accion),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun alarma(context: Context, accion: String, codigo: Int, hora: LocalTime) {
        val ahora = LocalDateTime.now()
        var cuando = ahora.toLocalDate().atTime(hora)
        if (!cuando.isAfter(ahora)) cuando = cuando.plusDays(1)
        val millis = cuando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendiente(context, accion, codigo))
    }

    private fun cancelar(context: Context, accion: String, codigo: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendiente(context, accion, codigo))
    }

    private fun crearCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Pagos y gastos", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun notificar(context: Context, id: Int, titulo: String, texto: String) {
        crearCanal(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    // pagos que vencen hoy y en los dias de aviso
    fun revisarPagos(context: Context, datos: Datos, hoy: LocalDate) {
        val aviso = hoy.plusDays(datos.config.diasAviso.toLong())
        val lista = Obligaciones.generar(datos, hoy, aviso).filter { it.falta > 0 && (it.fecha == hoy || it.fecha == aviso) }
        val deHoy = lista.filter { it.fecha == hoy }
        val proximos = lista.filter { it.fecha == aviso }
        if (deHoy.isNotEmpty()) {
            notificar(context, 10, "Pagos de hoy", deHoy.joinToString("\n") { "${it.concepto}: ${Formato.pesos(it.falta)}" })
        }
        if (proximos.isNotEmpty()) {
            notificar(context, 11, "En ${datos.config.diasAviso} días pagas",
                proximos.joinToString("\n") { "${it.concepto}: ${Formato.pesos(it.falta)} (${Formato.fecha(Fechas.iso(it.fecha))})" })
        }
        // los que ya pasaron y siguen sin pagar
        val inicio = Fechas.leer(datos.config.fechaInicio) ?: hoy
        val vencidos = Obligaciones.generar(datos, inicio, hoy.minusDays(1)).filter { it.falta > 0 && it.tipoOrigen != Origen.PRESUPUESTO }
        if (vencidos.isNotEmpty()) {
            notificar(context, 12, "Tienes ${vencidos.size} pagos vencidos",
                vencidos.joinToString("\n") { "${it.concepto}: ${Formato.pesos(it.falta)} (${Formato.fecha(Fechas.iso(it.fecha))})" })
        }
        revisarPlan(context, datos, hoy)
    }

    // faltante hasta el sueldo, puentes y bolsillo el dia del sueldo, y cupos casi llenos
    private fun revisarPlan(context: Context, datos: Datos, hoy: LocalDate) {
        val prefs = context.getSharedPreferences("avisos", Context.MODE_PRIVATE)
        val proy = Proyecciones.calcular(datos, hoy)
        val per = Obligaciones.periodo(datos, hoy)
        // 13: solo si cambio el faltante o es el primero del periodo
        val falta = proy.faltanteHastaSueldo
        val clavePer = Fechas.iso(per.desde)
        if (falta > 0 && (prefs.getLong("faltante", -1) != falta || prefs.getString("periodo", null) != clavePer)) {
            notificar(context, 13, "Te faltan ${Formato.pesos(falta)} para llegar al ${proy.proximoSueldo.dayOfMonth}", "Revisa la app: puedes posponer un pago, usar la tarjeta de puente o hacer moto.")
        }
        prefs.edit().putLong("faltante", falta).putString("periodo", clavePer).apply()
        if (per.desde == hoy) {
            // 14: puentes que vencen antes del siguiente sueldo
            val puentes = Obligaciones.pagosQuincena(datos, hoy).filter { it.tipoOrigen == Origen.PUENTE && it.falta > 0 }
            if (puentes.isNotEmpty()) {
                notificar(context, 14, "Paga hoy ${Formato.pesos(puentes.sumOf { it.falta })} de puentes de tarjeta",
                    puentes.joinToString("\n") { "${it.concepto}: ${Formato.pesos(it.falta)}" })
            }
            // 15: apartar el bolsillo con el sueldo que lo alimenta
            val c = datos.config
            if (c.bolsilloDia != null && per.diaSueldo == c.bolsilloDia && c.bolsilloMonto > 0 && c.bolsilloCuenta != null) {
                notificar(context, 15, "Aparta ${Formato.pesos(c.bolsilloMonto)} al bolsillo", "Es para la quincena siguiente. Ábrelo en la app con un toque.")
            }
        }
        // 17: cupo usado mas del 90 %
        val llenas = datos.tarjetas.filter { (Tarjetas.uso(datos, it, hoy) ?: 0.0) > 0.9 }
        if (llenas.isNotEmpty()) {
            notificar(context, 17, "Cupo casi lleno", llenas.joinToString("\n") { "${it.nombre}: ${Formato.porcentaje(Tarjetas.uso(datos, it, hoy) ?: 0.0)} usado" })
        }
    }

    // aviso cuando un presupuesto pasa del 80 y del 100
    fun revisarPresupuesto(context: Context, antes: Obligacion?, despues: Obligacion?) {
        if (antes == null || despues == null || despues.presupuestado <= 0) return
        fun pct(o: Obligacion) = o.pagado.toDouble() / o.presupuestado
        when {
            pct(antes) < 1.0 && pct(despues) >= 1.0 ->
                notificar(context, 20, "Te pasaste del presupuesto", "${despues.concepto}: ${Formato.pesos(despues.pagado)} de ${Formato.pesos(despues.presupuestado)}")
            pct(antes) < 0.8 && pct(despues) >= 0.8 ->
                notificar(context, 21, "Vas en el 80 % del presupuesto", "${despues.concepto}: ${Formato.pesos(despues.pagado)} de ${Formato.pesos(despues.presupuestado)}")
        }
    }
}

class AvisosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val resultado = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val datos = Repositorio(context).actuales()
                if (datos != null) {
                    when (intent.action) {
                        Avisos.ACCION_PAGOS -> Avisos.revisarPagos(context, datos, LocalDate.now())
                        Avisos.ACCION_NOCHE -> Avisos.notificar(context, 30, "Mis Cuentas", "¿Registraste tus gastos de hoy?")
                    }
                    Avisos.programar(context, datos.config)
                }
            } finally {
                resultado.finish()
            }
        }
    }
}

class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val resultado = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Repositorio(context).actuales()?.let { Avisos.programar(context, it.config) }
            } finally {
                resultado.finish()
            }
        }
    }
}

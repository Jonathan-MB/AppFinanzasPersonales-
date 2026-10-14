package com.jonathan.miscuentas.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class Pestana(val ruta: String, val texto: String, val icono: ImageVector)

private val pestanas = listOf(
    Pestana("registrar", "Registrar", Icons.Filled.ShoppingCart),
    Pestana("inicio", "Inicio", Icons.Filled.Home),
    Pestana("calendario", "Calendario", Icons.Filled.DateRange),
    Pestana("movimientos", "Historial", Icons.Filled.List),
    Pestana("mas", "Más", Icons.Filled.Menu),
)

@Composable
fun App(vm: AppViewModel) {
    val datos by vm.datos.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val confirmador = remember { Confirmador() }
    val context = LocalContext.current
    var resultadoImport by remember { mutableStateOf<String?>(null) }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun pedirPermiso() {
        if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) { pedirPermiso() }

    fun mensaje(texto: String) = scope.launch { snackbar.showSnackbar(texto) }

    // borra y deja deshacer por 5 segundos
    fun borrarConDeshacer(item: Any) {
        vm.borrar(item)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = withTimeoutOrNull(5000) {
                snackbar.showSnackbar("Borrado", actionLabel = "Deshacer", duration = SnackbarDuration.Indefinite)
            }
            if (r == SnackbarResult.ActionPerformed) vm.guardar(item)
            else snackbar.currentSnackbarData?.dismiss()
        }
    }

    val importador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = vm.importar(uri)
            resultadoImport = when {
                res == null -> "No se pudo leer el archivo."
                res.seccionesLeidas.isEmpty() -> res.errores.joinToString("\n").ifBlank { "El archivo no tiene datos." }
                res.errores.isEmpty() -> "Importado: ${res.seccionesLeidas.filter { it != "RESUMEN" && it != "CALENDARIO" }.joinToString(", ")}"
                else -> "Importado con ${res.errores.size} líneas con error:\n" + res.errores.take(20).joinToString("\n")
            }
        }
    }

    fun exportar() = scope.launch {
        val archivo = vm.exportar() ?: return@launch
        val uri = FileProvider.getUriForFile(context, context.packageName + ".archivos", archivo)
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(enviar, "Compartir ${archivo.name}"))
    }

    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    val enPestana = pestanas.any { it.ruta == rutaActual }

    TemaMisCuentas {
        CompositionLocalProvider(LocalConfirmador provides confirmador) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (enPestana) NavigationBar {
                            pestanas.forEach { p ->
                                NavigationBarItem(
                                    selected = rutaActual == p.ruta,
                                    onClick = { irPestana(nav, p.ruta) },
                                    icon = { Icon(p.icono, null) },
                                    label = { Text(p.texto, maxLines = 1) },
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (enPestana && rutaActual != "registrar") {
                            ExtendedFloatingActionButton(onClick = { nav.navigate("gasto") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Gasto") })
                        }
                    },
                ) { padding ->
                    val d = datos
                    if (d == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        NavHost(nav, startDestination = "registrar", modifier = Modifier.padding(padding)) {
                            composable("registrar") {
                                PantallaRegistrar(vm, d, { nav.navigate("gasto") }, { nav.navigate("ingreso") }, { nav.navigate("monto") }, { nav.navigate("deudas") }) { o -> nav.navigate("obligacion/" + Uri.encode(o.clave)) }
                            }
                            composable("inicio") {
                                PantallaInicio(vm, d, { nav.navigate("monto") }, { nav.navigate("deudas") }, { irPestana(nav, "calendario") }) { o -> nav.navigate("obligacion/" + Uri.encode(o.clave)) }
                            }
                            composable("calendario") { PantallaCalendario(vm, d) { o -> nav.navigate("obligacion/" + Uri.encode(o.clave)) } }
                            composable("movimientos") {
                                PantallaMovimientos(vm, d, { r -> nav.navigate("registro/${r.id}") }, ::borrarConDeshacer)
                            }
                            composable("mas") {
                                PantallaMas({ nav.navigate("deudas") }, { nav.navigate("lista/$it") }, { nav.navigate("reparto") }, { nav.navigate("config") }, { nav.navigate("datos") }, { nav.navigate("fechas") })
                            }
                            composable("gasto") { PantallaEtiquetasGasto(vm, d, { nav.popBackStack() }, { nav.navigate("monto") }) }
                            composable("ingreso") { PantallaEtiquetasIngreso(vm, d, { nav.popBackStack() }, { nav.navigate("monto") }) }
                            composable("monto") { e ->
                                PantallaMonto(vm, d, { volver(nav, e) }) { texto ->
                                    nav.popBackStack("registrar", inclusive = false)
                                    if (nav.currentDestination?.route != "registrar") irPestana(nav, "registrar")
                                    mensaje(texto)
                                }
                            }
                            composable("deudas") {
                                PantallaDeudas(vm, d, { nav.popBackStack() }, { nav.navigate("deuda/$it") }, { nav.navigate("editarDeuda/nuevo") }, { nav.navigate("plan") })
                            }
                            composable("deuda/{id}") { e ->
                                val id = e.arguments?.getString("id") ?: ""
                                PantallaDetalleDeuda(vm, d, id, { volver(nav, e) }, { nav.navigate("editarDeuda/$id") }, { nav.navigate("monto") })
                            }
                            composable("editarDeuda/{id}") { e -> PantallaEditarDeuda(vm, d, e.arguments?.getString("id") ?: "nuevo") { nav.popBackStack() } }
                            composable("plan") { PantallaPlan(vm, d) { nav.popBackStack() } }
                            composable("obligacion/{clave}") { e ->
                                val clave = Uri.decode(e.arguments?.getString("clave") ?: "")
                                PantallaDetalleObligacion(vm, d, clave, { nav.popBackStack() }, { nav.navigate("monto") }) { tipo, id ->
                                    when (tipo) {
                                        "deuda" -> nav.navigate("editarDeuda/$id")
                                        "gasto" -> nav.navigate("editar/${TipoLista.GASTO}/$id")
                                        "presupuesto" -> nav.navigate("editar/${TipoLista.PRESUPUESTO}/$id")
                                        else -> nav.navigate("editar/${TipoLista.EVENTO}/$id")
                                    }
                                }
                            }
                            composable("registro/{id}") { e ->
                                PantallaEditarRegistro(vm, d, e.arguments?.getString("id") ?: "", { volver(nav, e) }, ::borrarConDeshacer)
                            }
                            composable("lista/{tipo}") { e ->
                                val tipo = e.arguments?.getString("tipo") ?: TipoLista.GASTO
                                PantallaLista(vm, d, tipo, { nav.popBackStack() }) { id -> nav.navigate("editar/$tipo/$id") }
                            }
                            composable("editar/{tipo}/{id}") { e ->
                                PantallaEditar(vm, d, e.arguments?.getString("tipo") ?: "", e.arguments?.getString("id") ?: "nuevo") { nav.popBackStack() }
                            }
                            composable("fechas") { PantallaFechasPago(vm, d) { nav.popBackStack() } }
                            composable("reparto") { PantallaReparto(vm, d) { nav.popBackStack() } }
                            composable("config") { PantallaConfig(vm, d, { nav.popBackStack() }, ::pedirPermiso) }
                            composable("datos") {
                                val respaldos = remember(d) {
                                    vm.repo.carpetaRespaldos().listFiles()?.sortedByDescending { it.lastModified() }?.map { it.name } ?: emptyList()
                                }
                                PantallaDatos(
                                    exportar = { exportar() },
                                    importar = {
                                        confirmador.preguntar("Importar", "Esto reemplaza todo lo actual. Antes se guarda un respaldo automático.", "Elegir archivo") {
                                            importador.launch(arrayOf("text/*", "application/octet-stream"))
                                        }
                                    },
                                    actualizar = {
                                        confirmador.preguntar(
                                            "Actualizar deudas y gastos",
                                            "Se cambian las deudas, gastos fijos, presupuestos, ingresos programados y eventos por los de esta versión. Lo que registraste se conserva y antes se guarda un respaldo.",
                                            "Actualizar",
                                        ) {
                                            scope.launch {
                                                val res = vm.actualizarDesdeArchivo()
                                                resultadoImport = when {
                                                    res == null -> "Esta versión no trae datos propios para actualizar."
                                                    res.errores.isEmpty() -> "Listo: deudas, gastos fijos, presupuestos, ingresos y eventos actualizados. Tus registros se conservaron."
                                                    else -> "Actualizado con ${res.errores.size} líneas con error:\n" + res.errores.take(20).joinToString("\n")
                                                }
                                            }
                                        }
                                    },
                                    resultado = resultadoImport, respaldos = respaldos, alVolver = { nav.popBackStack() },
                                )
                            }
                        }
                    }
                }
                CapaConfirmacion(confirmador)
            }
        }
    }
}

// solo vuelve si esa pantalla sigue al frente; asi un segundo aviso de volver no saca la de atras
private fun volver(nav: NavHostController, entrada: NavBackStackEntry) {
    if (nav.currentBackStackEntry?.id == entrada.id) nav.popBackStack()
}

private fun irPestana(nav: NavHostController, ruta: String) {
    nav.navigate(ruta) {
        popUpTo("registrar") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

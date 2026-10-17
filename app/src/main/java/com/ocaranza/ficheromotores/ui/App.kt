@file:OptIn(ExperimentalMaterial3Api::class)

package com.ocaranza.ficheromotores.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocaranza.ficheromotores.data.KIND_MANT
import com.ocaranza.ficheromotores.data.KIND_PRUEBA
import com.ocaranza.ficheromotores.util.today

@Composable
fun FicheroApp(vm: AppVM) {
    val ctx = LocalContext.current
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }
    when (val s = vm.current) {
        Screen.Lista -> ListScreen(vm)
        is Screen.Ficha -> MotorScreen(vm, s.motorId)
        is Screen.MotorForm -> MotorFormScreen(vm, s.motorId)
        is Screen.RegForm -> RegFormScreen(vm, s.kind, s.motorId, s.regId)
        Screen.Ajustes -> AjustesScreen(vm)
    }
}

@Composable
fun AjustesScreen(vm: AppVM) {
    val e = C.x
    val ctx = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            vm.say(if (text == null) "No se pudo leer el archivo." else vm.importBackup(text))
        }
    }
    val nPruebas = vm.regs.count { it.kind == KIND_PRUEBA }
    val nMants = vm.regs.count { it.kind == KIND_MANT }

    Scaffold(
        containerColor = e.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                title = { Text("AJUSTES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = e.panel),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecCard("Técnico") {
                Text("Este nombre queda guardado en cada prueba y mantenimiento que registres desde este teléfono.",
                    fontSize = 14.sp, color = e.muted)
                OutlinedTextField(
                    value = vm.tecnico, onValueChange = { vm.setTecnicoName(it) }, singleLine = true,
                    label = { Text("Nombre del técnico") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            SecCard("Respaldo") {
                Text("Los datos se guardan solo en este teléfono: ${vm.motors.size} motores, $nPruebas pruebas y $nMants mantenimientos. " +
                    "Exportá un respaldo cada tanto y guardalo en Drive o mandalo por WhatsApp. Las fotos no se incluyen en el respaldo.",
                    fontSize = 14.sp, color = e.muted)
                Button(onClick = { shareText(ctx, "respaldo-fichero-motores-${today()}.json", vm.backupJson(), "application/json") },
                    modifier = Modifier.fillMaxWidth()) { Text("Exportar respaldo") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Importar respaldo") }
                Text("Importar suma los datos del archivo a los que ya tenés. Si un mismo registro está en los dos lados, queda la versión más reciente.",
                    fontSize = 12.sp, color = e.muted)
            }
            SecCard("Acerca de") {
                Text("Fichero de Motores ${com.ocaranza.ficheromotores.BuildConfig.VERSION_NAME} · Registro de motores, pruebas de laboratorio y mantenimiento.", fontSize = 14.sp, color = e.muted)
            }
        }
    }
}

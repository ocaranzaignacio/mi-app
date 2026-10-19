package com.ocaranza.ficheromotores.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ocaranza.ficheromotores.data.Fichero
import com.ocaranza.ficheromotores.data.Filtros
import com.ocaranza.ficheromotores.data.Foto
import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.Registro
import com.ocaranza.ficheromotores.data.Repo
import com.ocaranza.ficheromotores.data.ficheroFromJson
import com.ocaranza.ficheromotores.data.toJson
import com.ocaranza.ficheromotores.sync.Borrado
import com.ocaranza.ficheromotores.sync.PbClient
import com.ocaranza.ficheromotores.sync.PbException
import com.ocaranza.ficheromotores.sync.SyncEngine
import com.ocaranza.ficheromotores.sync.SyncItem
import com.ocaranza.ficheromotores.sync.SyncState
import com.ocaranza.ficheromotores.sync.stamp
import com.ocaranza.ficheromotores.util.nowIso
import com.ocaranza.ficheromotores.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Screen {
    data object Lista : Screen
    data class Ficha(val motorId: String) : Screen
    data class MotorForm(val motorId: String?) : Screen
    data class RegForm(val kind: String, val motorId: String, val regId: String?) : Screen
    data object Ajustes : Screen
}

private data class Paso(val pushed: List<Pair<String, Long>>, val subidas: Set<String>, val remote: List<SyncItem>, val cursor: String?)

/** Servidor configurado en Ajustes. */
data class Servidor(val url: String, val email: String, val pass: String)

class AppVM(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val writeLock = Mutex()

    val motors = mutableStateListOf<Motor>()
    val regs = mutableStateListOf<Registro>()
    var loaded by mutableStateOf(false)
        private set

    val stack = mutableStateListOf<Screen>(Screen.Lista)
    val current: Screen get() = stack.last()

    var filtros by mutableStateOf(Filtros())
    var showFiltros by mutableStateOf(false)
    var tab by mutableStateOf("placa")
    var tecnico by mutableStateOf(prefs.getString("tecnico", "") ?: "")
        private set
    var toast by mutableStateOf<String?>(null)

    /* ---------- Sincronización ---------- */
    private var sync = SyncState()
    var servidor by mutableStateOf(loadServidor())
        private set
    var syncing by mutableStateOf(false)
        private set
    /** Último error de sincronización, o null si la última salió bien. */
    var syncError by mutableStateOf<String?>(null)
        private set
    var lastSync by mutableStateOf<Long?>(null)
        private set
    var pendientes by mutableIntStateOf(0)
        private set
    /** Cambia cuando se bajan fotos nuevas, para refrescar las miniaturas. */
    var fotosRev by mutableIntStateOf(0)
        private set
    private var client: PbClient? = null
    private val syncLock = Mutex()
    private val trigger = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            val (f, s) = withContext(Dispatchers.IO) { repo.load() to repo.loadSync() }
            motors.addAll(f.motores)
            regs.addAll(f.registros)
            sync = s
            pendientes = s.pendientes.size
            loaded = true
            // Sincroniza al abrir, cada minuto y poco después de cada cambio.
            while (true) {
                if (servidor != null) runSync()
                withTimeoutOrNull(60_000) { trigger.receive() }
                delay(1500)
            }
        }
    }

    fun go(s: Screen) {
        if (s is Screen.Ficha && current !is Screen.RegForm && current !is Screen.MotorForm) tab = "placa"
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Reemplaza la pantalla actual (por ejemplo, del formulario a la ficha recién creada). */
    fun replace(s: Screen) {
        stack.removeAt(stack.lastIndex)
        stack.add(s)
    }

    fun say(msg: String) { toast = msg }

    fun setTecnicoName(n: String) {
        tecnico = n
        prefs.edit().putString("tecnico", n).apply()
    }

    private fun persist() {
        val snap = Fichero(motors.toList(), regs.toList())
        val s = sync
        viewModelScope.launch(Dispatchers.IO) {
            writeLock.withLock {
                repo.save(snap)
                repo.saveSync(s)
            }
        }
    }

    private fun markPending(uids: Collection<String>) {
        if (uids.isEmpty()) return
        sync = sync.copy(pendientes = sync.pendientes + uids)
        pendientes = sync.pendientes.size
        if (servidor != null) trigger.trySend(Unit)
    }

    fun motor(id: String): Motor? = motors.firstOrNull { it.id == id }
    fun regsOf(motorId: String, kind: String): List<Registro> =
        regs.filter { it.motorId == motorId && it.kind == kind }.sortedByDescending { it.fecha }

    fun saveMotor(m: Motor) {
        val i = motors.indexOfFirst { it.id == m.id }
        if (i >= 0) motors[i] = m else motors.add(m)
        markPending(listOf(m.id))
        persist()
    }

    fun deleteMotor(id: String) {
        motor(id)?.fotos?.forEach { repo.borrarFoto(it.file) }
        val now = System.currentTimeMillis()
        val regIds = regs.filter { it.motorId == id }.map { it.id }
        val b = sync.borrados.toMutableMap()
        b[id] = Borrado("motor", null, now)
        regIds.forEach { b[it] = Borrado("registro", id, now) }
        sync = sync.copy(borrados = b)
        motors.removeAll { it.id == id }
        regs.removeAll { it.motorId == id }
        markPending(regIds + id)
        persist()
    }

    fun saveReg(r: Registro) {
        val i = regs.indexOfFirst { it.id == r.id }
        if (i >= 0) regs[i] = r else regs.add(r)
        markPending(listOf(r.id))
        persist()
    }

    fun deleteReg(id: String) {
        val r = regs.firstOrNull { it.id == id }
        regs.removeAll { it.id == id }
        sync = sync.copy(borrados = sync.borrados + (id to Borrado("registro", r?.motorId, System.currentTimeMillis())))
        markPending(listOf(id))
        persist()
    }

    fun addFoto(motorId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { repo.importarFoto(uri) }
            if (name == null) { say("No se pudo guardar la foto."); return@launch }
            val m = motor(motorId) ?: return@launch
            saveMotor(m.copy(fotos = m.fotos + Foto(name, "Foto", today()), act = nowIso()))
            say("Foto agregada")
        }
    }

    fun removeFoto(motorId: String, file: String) {
        val m = motor(motorId) ?: return
        saveMotor(m.copy(fotos = m.fotos.filter { it.file != file }, act = nowIso()))
        repo.borrarFoto(file)
        say("Foto quitada")
    }

    fun backupJson(): String = Fichero(motors.toList(), regs.toList()).toJson().toString(2)

    /** Suma un respaldo a los datos actuales. Si un registro ya existe, queda el más reciente. */
    fun importBackup(text: String): String {
        val f = try { ficheroFromJson(text) } catch (e: Exception) { return "El archivo no es un respaldo válido." }
        if (f.motores.isEmpty() && f.registros.isEmpty()) return "El respaldo no tiene datos."
        val changed = mutableListOf<String>()
        var nm = 0; var nr = 0
        for (m in f.motores) {
            val i = motors.indexOfFirst { it.id == m.id }
            if (i < 0) { motors.add(m.copy(fotos = m.fotos.filter { repo.fotoFile(it.file).exists() })); nm++; changed += m.id }
            else if (m.stamp > motors[i].stamp) {
                // Conserva las fotos locales: el respaldo no las trae.
                motors[i] = m.copy(fotos = motors[i].fotos); nm++; changed += m.id
            }
        }
        for (r in f.registros) {
            val i = regs.indexOfFirst { it.id == r.id }
            if (i < 0) { regs.add(r); nr++; changed += r.id }
            else if (r.stamp > regs[i].stamp) { regs[i] = r; nr++; changed += r.id }
        }
        markPending(changed)
        persist()
        return "Respaldo importado: $nm motores y $nr registros nuevos o actualizados."
    }

    /* ---------- Servidor ---------- */

    private fun loadServidor(): Servidor? {
        val url = prefs.getString("srv_url", null) ?: return null
        return Servidor(url, prefs.getString("srv_email", "") ?: "", prefs.getString("srv_pass", "") ?: "")
    }

    private fun clientFor(s: Servidor): PbClient {
        client?.let { if (it.base == s.url) return it }
        return PbClient(s.url, s.email, s.pass, prefs.getString("srv_token", null)).also { client = it }
    }

    /** Prueba el acceso y, si funciona, guarda el servidor y empieza a sincronizar. */
    fun conectar(url: String, email: String, pass: String, onDone: (String?) -> Unit) {
        if (url.isBlank() || email.isBlank() || pass.isBlank()) { onDone("Completá la dirección, el usuario y la contraseña."); return }
        viewModelScope.launch {
            val c = PbClient(url, email.trim(), pass)
            val err = withContext(Dispatchers.IO) {
                try { c.login(); null } catch (e: PbException) { e.message } catch (e: Exception) { "No se pudo conectar: ${e.message}" }
            }
            if (err != null) { onDone(err); return@launch }
            val s = Servidor(c.base, email.trim(), pass)
            prefs.edit().putString("srv_url", s.url).putString("srv_email", s.email).putString("srv_pass", s.pass)
                .putString("srv_token", c.token).apply()
            client = c
            val key = "${s.url}|${s.email}"
            if (sync.servidor != key) {
                // Servidor nuevo: se sube todo lo que hay en el teléfono y se baja todo lo del servidor.
                val all = motors.map { it.id } + regs.map { it.id } + sync.borrados.keys
                sync = sync.copy(pendientes = sync.pendientes + all, cursor = null, fotosSubidas = emptySet(), servidor = key)
                pendientes = sync.pendientes.size
                persist()
            }
            servidor = s
            syncError = null
            if (tecnico.isBlank()) c.userName?.let { setTecnicoName(it) }
            onDone(null)
            trigger.trySend(Unit)
        }
    }

    fun desconectar() {
        prefs.edit().remove("srv_url").remove("srv_email").remove("srv_pass").remove("srv_token").apply()
        servidor = null
        client = null
        syncError = null
        lastSync = null
    }

    fun sincronizarAhora() {
        if (servidor == null) return
        trigger.trySend(Unit)
    }

    private fun currentStamp(uid: String): Long? =
        motors.firstOrNull { it.id == uid }?.stamp ?: regs.firstOrNull { it.id == uid }?.stamp ?: sync.borrados[uid]?.ts

    private fun buildPending(): List<SyncItem> = sync.pendientes.mapNotNull { uid ->
        motors.firstOrNull { it.id == uid }?.let { SyncItem(uid, "motor", null, it.toJson(), it.stamp, false) }
            ?: regs.firstOrNull { it.id == uid }?.let { SyncItem(uid, "registro", it.motorId, it.toJson(), it.stamp, false) }
            ?: sync.borrados[uid]?.let { SyncItem(uid, it.tipo, it.motorUid, null, it.ts, true) }
    }

    private suspend fun runSync() = syncLock.withLock {
        val srv = servidor ?: return@withLock
        syncing = true
        try {
            val c = clientFor(srv)
            val pending = buildPending()
            val motorsSnap = motors.toList()
            val st = sync
            val r = withContext(Dispatchers.IO) {
                c.ensureAuth()
                prefs.edit().putString("srv_token", c.token).apply()
                val pushed = SyncEngine.push(c, pending)
                val subidas = mutableSetOf<String>()
                for (m in motorsSnap) for (f in m.fotos) {
                    if (f.file in st.fotosSubidas) continue
                    val file = repo.fotoFile(f.file)
                    if (!file.exists()) continue
                    try {
                        c.uploadFoto(f.file, m.id, file)
                    } catch (e: PbException) {
                        if (e.status != 400 || c.findFoto(f.file) == null) throw e
                    }
                    subidas += f.file
                }
                val (remote, cursor) = SyncEngine.pull(c, st.cursor)
                Paso(pushed, subidas, remote, cursor)
            }

            // Se aplican los cambios sobre los datos actuales (pueden haber cambiado mientras tanto).
            val pend = sync.pendientes.toMutableSet()
            for ((uid, ts) in r.pushed) if (currentStamp(uid) == ts) pend.remove(uid)
            val res = SyncEngine.merge(motors.toList(), regs.toList(), sync.borrados, r.remote)
            if (res.cambios > 0) {
                motors.clear(); motors.addAll(res.motores)
                regs.clear(); regs.addAll(res.registros)
            }
            pend += res.repush
            sync = sync.copy(pendientes = pend, borrados = res.borrados, cursor = r.cursor, fotosSubidas = sync.fotosSubidas + r.subidas)
            persist()

            // Fotos que otros técnicos sacaron y todavía no están en este teléfono.
            val faltan = motors.flatMap { m -> m.fotos.map { it.file } }.distinct().filter { !repo.fotoFile(it).exists() }
            if (faltan.isNotEmpty()) {
                val bajadas = withContext(Dispatchers.IO) {
                    faltan.filter { name ->
                        val rec = c.findFoto(name) ?: return@filter false
                        c.downloadFoto(rec, repo.fotoFile(name))
                        true
                    }
                }
                if (bajadas.isNotEmpty()) {
                    sync = sync.copy(fotosSubidas = sync.fotosSubidas + bajadas)
                    fotosRev++
                    persist()
                }
            }
            lastSync = System.currentTimeMillis()
            syncError = null
        } catch (e: PbException) {
            syncError = e.message
        } catch (e: Exception) {
            syncError = "Error al sincronizar: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            syncing = false
            pendientes = sync.pendientes.size
        }
    }
}

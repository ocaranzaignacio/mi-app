package com.ocaranza.ficheromotores.sync

import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.Registro
import com.ocaranza.ficheromotores.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Base64

/** Foto JPEG de 16×12 px para las pruebas. */
private const val JPEG_PRUEBA = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAoHBwgHBgoICAgLCgoLDhgQDg0NDh0VFhEYIx8lJCIfIiEmKzcvJik0KSEiMEExNDk7Pj4+JS5ESUM8SDc9Pjv/2wBDAQoLCw4NDhwQEBw7KCIoOzs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozv/wAARCAAMABADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDmKKKK+kPBP//Z"

private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
private fun motorItem(m: Motor) = SyncItem(m.id, "motor", null, m.toJson(), m.stamp, false)
private fun regItem(r: Registro) = SyncItem(r.id, "registro", r.motorId, r.toJson(), r.stamp, false)

/** Reglas de combinación: no necesitan servidor. */
class MergeTest {
    private val t0 = 1_760_000_000_000L
    private val m = Motor("m1", mapOf("codigo" to "MT-1"), creado = iso(t0))

    @Test fun remotoMasNuevoReemplaza() {
        val remoto = m.copy(d = m.d + ("marca" to "WEG"), act = iso(t0 + 1000))
        val r = SyncEngine.merge(listOf(m), emptyList(), emptyMap(), listOf(motorItem(remoto)))
        assertEquals("WEG", r.motores.single().d["marca"])
        assertEquals(1, r.cambios)
    }

    @Test fun localMasNuevoSeConservaYSeVuelveASubir() {
        val local = m.copy(d = m.d + ("marca" to "Local"), act = iso(t0 + 5000))
        val remoto = m.copy(d = m.d + ("marca" to "Remoto"), act = iso(t0 + 1000))
        val r = SyncEngine.merge(listOf(local), emptyList(), emptyMap(), listOf(motorItem(remoto)))
        assertEquals("Local", r.motores.single().d["marca"])
        assertTrue("m1" in r.repush)
    }

    @Test fun borradoRemotoEliminaYDejaMarca() {
        val b = SyncItem("m1", "motor", null, null, t0 + 2000, true)
        val r = SyncEngine.merge(listOf(m), emptyList(), emptyMap(), listOf(b))
        assertTrue(r.motores.isEmpty())
        assertEquals(t0 + 2000, r.borrados["m1"]?.ts)
    }

    @Test fun borradoLocalNoResucita() {
        val viejo = motorItem(m)
        val r = SyncEngine.merge(emptyList(), emptyList(), mapOf("m1" to Borrado("motor", null, t0 + 3000)), listOf(viejo))
        assertTrue(r.motores.isEmpty())
        assertTrue("m1" in r.repush)
    }

    @Test fun estadoSeGuardaYLee() {
        val s = SyncState(setOf("a", "b"), mapOf("c" to Borrado("registro", "m1", 5)), "2026-10-18 01:02:03.000Z", setOf("f.jpg"), "http://x|y")
        assertEquals(s, SyncState.fromJson(s.toJson().toString()))
    }

    @Test fun direccionSeNormaliza() {
        assertEquals("http://192.168.1.50:8090", PbClient.normalizeUrl(" 192.168.1.50:8090/ "))
        assertEquals("https://x.ts.net", PbClient.normalizeUrl("https://x.ts.net/"))
    }
}

/**
 * Sincronización completa contra un PocketBase real. En GitHub Actions se levanta un servidor de prueba
 * y se pasan PB_URL, PB_USER y PB_PASS; si no están, la prueba se omite.
 */
class PocketBaseIntegrationTest {
    private val url: String? = System.getenv("PB_URL")
    private val user: String = System.getenv("PB_USER") ?: ""
    private val pass: String = System.getenv("PB_PASS") ?: ""

    @Test fun dosTelefonosSincronizan() {
        assumeTrue("Sin servidor de prueba", url != null)
        val a = PbClient(url!!, user, pass).apply { ensureAuth() }
        val b = PbClient(url, user, pass).apply { ensureAuth() }
        val now = System.currentTimeMillis()
        val id = "t$now"
        val m = Motor(id, mapOf("codigo" to "MT-$now", "tipo" to "tri", "pot" to "7,5"), creado = iso(now))
        val r = Registro("r$now", id, "prueba", mapOf("fecha" to "2026-10-18", "resultado" to "Aprobado", "ais_ut_1" to "450"),
            secciones = listOf("ais"), tecnico = "Prueba", creado = iso(now))

        // Teléfono A sube un motor y una prueba.
        assertEquals(2, SyncEngine.push(a, listOf(motorItem(m), regItem(r))).size)

        // Teléfono B, vacío, baja todo.
        val (remote1, cursorB) = SyncEngine.pull(b, null)
        val enB = SyncEngine.merge(emptyList(), emptyList(), emptyMap(), remote1)
        assertEquals("7,5", enB.motores.first { it.id == id }.d["pot"])
        assertEquals("450", enB.registros.first { it.id == r.id }.v["ais_ut_1"])

        // B edita el motor y lo sube.
        val mB = enB.motores.first { it.id == id }.let { it.copy(d = it.d + ("marca" to "WEG"), act = iso(now + 10_000)) }
        SyncEngine.push(b, listOf(motorItem(mB)))

        // A intenta subir su versión vieja: el servidor no la acepta y A recibe la de B.
        SyncEngine.push(a, listOf(motorItem(m)))
        val (remote2, _) = SyncEngine.pull(a, cursorB)
        val enA = SyncEngine.merge(listOf(m), listOf(r), emptyMap(), remote2)
        assertEquals("WEG", enA.motores.first { it.id == id }.d["marca"])

        // A borra la prueba; B se entera.
        SyncEngine.push(a, listOf(SyncItem(r.id, "registro", id, null, now + 20_000, true)))
        val (remote3, _) = SyncEngine.pull(b, cursorB)
        val enB2 = SyncEngine.merge(enB.motores, enB.registros, emptyMap(), remote3)
        assertFalse(enB2.registros.any { it.id == r.id })
        assertTrue(enB2.borrados.containsKey(r.id))

        // Fotos: A sube, B baja el mismo archivo.
        val img = File.createTempFile("foto", ".jpg").apply { writeBytes(Base64.getDecoder().decode(JPEG_PRUEBA)) }
        val nombre = "f$now.jpg"
        a.uploadFoto(nombre, id, img)
        try { a.uploadFoto(nombre, id, img); fail("La foto repetida debería rechazarse") } catch (e: PbException) { assertEquals(400, e.status) }
        val rec = b.findFoto(nombre)
        assertNotNull(rec)
        val dest = File(img.parentFile, "bajada-$nombre")
        b.downloadFoto(rec!!, dest)
        assertEquals(img.length(), dest.length())

        // Contraseña equivocada.
        try { PbClient(url, user, "mala-clave").ensureAuth(); fail("Debería rechazar la contraseña") } catch (e: PbException) {
            assertEquals(400, e.status)
        }
    }
}

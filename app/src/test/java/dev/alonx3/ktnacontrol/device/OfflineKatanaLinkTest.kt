package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El supuesto sobre el que descansa toda la edición offline (CLAUDE.md §4.5): que un
 * [KatanaRepository] montado sobre un [OfflineKatanaLink] se comporta igual que uno montado
 * sobre el cable, **sin que ninguno de los dos haya tenido que cambiar**.
 *
 * Mismo estilo que [KatanaRepositoryTest]: `runBlocking` y un debounce corto de tiempo real,
 * en vez de traer `kotlinx-coroutines-test` para un puñado de casos (CLAUDE.md §6).
 */
class OfflineKatanaLinkTest {

    private val debounce = 20L

    private fun imageWith(vararg pairs: Pair<Address, Int>): MemoryImage =
        MemoryImage.empty().apply {
            pairs.forEach { (address, value) -> write(address, byteArrayOf(value.toByte())) }
        }

    private fun repository(image: MemoryImage, scope: kotlinx.coroutines.CoroutineScope) =
        KatanaRepository(OfflineKatanaLink(image), scope, debounceMillis = debounce)

    @Test
    fun `un SET escribe en la imagen en vez de en el cable`() = runBlocking {
        val image = MemoryImage.empty()
        val repo = repository(image, this)

        repo.gainLevel.setLevel(75)
        delay(debounce * 4)

        assertEquals(75, image.byteAt(KatanaAddresses.GAIN_LEVEL))
        repo.close()
    }

    @Test
    fun `un GET se contesta desde la imagen`() = runBlocking {
        val repo = repository(imageWith(KatanaAddresses.GAIN_LEVEL to 42), this)

        assertEquals(42, repo.gainLevel.read())
        assertEquals(42, repo.gainLevel.displayValue)
        repo.close()
    }

    @Test
    fun `una direccion que la imagen no tiene no contesta, y eso no es un error`() = runBlocking {
        val repo = repository(MemoryImage.empty(), this)

        assertNull(repo.gainLevel.read())
        assertNull(repo.gainLevel.state.value)
        repo.close()
    }

    @Test
    fun `loadFromImage puebla los controles desde la imagen, sin amplificador`() = runBlocking {
        val image = imageWith(
            KatanaAddresses.GAIN_LEVEL to 50,
            KatanaAddresses.VOLUME_LEVEL to 60,
            KatanaAddresses.BASS_LEVEL to 70,
            KatanaAddresses.AMP_TYPE_FULL to 0x08,
        )
        val repo = repository(image, this)

        val loaded = repo.loadFromImage(image)

        assertEquals(4, loaded)
        assertEquals(50, repo.gainLevel.displayValue)
        assertEquals(60, repo.volumeLevel.displayValue)
        assertEquals(70, repo.bassLevel.displayValue)
        assertEquals(0x08, repo.ampType.state.value)
        // Lo que la imagen no traía se queda sin valor: "no cubierto", nunca cero.
        assertNull(repo.trebleLevel.displayValue)
        repo.close()
    }

    /**
     * ⚠️ El motivo por el que existe `loadFromImage` y no se reutiliza `loadFromDump` offline.
     *
     * `loadFromDump` cae a un GET individual **en serie, hasta 800 ms cada uno**, por cada
     * control que el dump no cubra. Contra una imagen en RAM eso es a la vez inútil (se
     * pregunta otra vez a la misma fuente que acaba de no tener el valor) y carísimo: con
     * cientos de controles registrados y un preset casi vacío son minutos, no segundos.
     * Este test lo deja escrito en números para que nadie "simplifique" volviendo al otro
     * camino.
     */
    @Test
    fun `loadFromImage no hace ni un GET de respaldo`() = runBlocking {
        val link = OfflineKatanaLink(MemoryImage.empty())
        val repo = KatanaRepository(link, this, debounceMillis = debounce)

        val started = System.currentTimeMillis()
        val loaded = repo.loadFromImage(MemoryImage.empty())
        val elapsed = System.currentTimeMillis() - started

        assertEquals("una imagen vacía no puebla nada", 0, loaded)
        assertTrue("hay controles registrados de sobra", repo.controlCount > 100)
        assertTrue("no debe esperar ningún timeout: tardó $elapsed ms", elapsed < 500)
        assertTrue("no debe haber preguntado nada", link.missedReads.isEmpty())
        repo.close()
    }

    @Test
    fun `lo que se edita se vuelve a leer - la ida y vuelta cierra`() = runBlocking {
        val image = MemoryImage.empty()
        val repo = repository(image, this)

        repo.gainLevel.setLevel(33)
        repo.ampType.set(0x0C)
        repo.boostEnabled.set(KatanaAddresses.SWITCH_ON)
        delay(debounce * 4)
        repo.close()

        val reread = repository(image, this)
        assertEquals(33, reread.gainLevel.read())
        assertEquals(0x0C, reread.ampType.read())
        assertEquals(KatanaAddresses.SWITCH_ON, reread.boostEnabled.read())
        reread.close()
    }

    @Test
    fun `un SET no se reenvia como entrante - la regla anti-eco se cumple sola`() = runBlocking {
        val link = OfflineKatanaLink(MemoryImage.empty())
        val seen = mutableListOf<ByteArray>()
        val watcher = launch { link.incoming.collect { seen += it } }

        val repo = KatanaRepository(link, this, debounceMillis = debounce)
        repo.gainLevel.setLevel(10)
        delay(debounce * 4)

        assertEquals("un SET no debe generar tráfico entrante", 0, seen.size)
        watcher.cancel()
        repo.close()
    }

    @Test
    fun `una respuesta larga se trocea igual que la del amplificador`() = runBlocking {
        // 300 bytes contiguos: más que MAX_REPLY_PAYLOAD, así que el camino de reensamblado
        // se ejercita offline igual que en vivo.
        val image = MemoryImage.empty()
        image.write(KatanaAddresses.MEMORY_DUMP, ByteArray(300) { (it % 100).toByte() })

        val link = OfflineKatanaLink(image)
        val seen = mutableListOf<ByteArray>()
        val watcher = launch { link.incoming.collect { seen += it } }
        kotlinx.coroutines.yield()

        link.send(
            dev.alonx3.ktnacontrol.protocol.RolandSysEx.get(KatanaAddresses.MEMORY_DUMP, 300)
        )
        delay(debounce)

        assertEquals("300 B en trozos de 241 son 2 mensajes", 2, seen.size)
        watcher.cancel()
    }
}

package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.tsl.TslBlockMap
import dev.alonx3.ktnacontrol.protocol.tsl.TslParseResult
import dev.alonx3.ktnacontrol.protocol.tsl.TslParser
import dev.alonx3.ktnacontrol.protocol.tsl.TslWriter
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mandar un preset entero por un [KatanaLink] falso (CLAUDE.md §6): sin amplificador y sin USB.
 *
 * La prueba que importa es la **ida y vuelta**: lo que sale por el cable se vuelve a montar en
 * una imagen de memoria y tiene que coincidir con la de partida, byte a byte. Si el troceado se
 * equivocara de dirección o perdiera el último trozo, el amplificador recibiría un preset
 * distinto del que se le pidió mandar **sin que nada fallara** — no hay confirmación que lo
 * delate.
 */
class PresetSendTest {

    private class FakeLink(private val failFrom: Int = Int.MAX_VALUE) : KatanaLink {
        val sent = mutableListOf<ByteArray>()
        override val incoming: Flow<ByteArray> = MutableSharedFlow()
        override suspend fun send(message: ByteArray): Boolean {
            if (sent.size >= failFrom) return false
            sent += message
            return true
        }
    }

    /** El envío, remontado: cada SET escrito en su dirección, como haría el amplificador. */
    private fun rebuild(messages: List<ByteArray>): MemoryImage {
        val image = MemoryImage.empty()
        messages.forEach { message ->
            val parsed = RolandSysEx.parse(message)
            assertTrue("todo lo enviado debería ser un SET válido: $parsed", parsed is RolandMessage.Data)
            parsed as RolandMessage.Data
            image.write(parsed.address, parsed.data)
        }
        return image
    }

    private fun referencePreset() =
        (TslParser.parse(File("../reference/FxFloorboard/default_mk2.tsl").readText())
            as TslParseResult.Parsed).presets.single()

    // Sin margen: el envío real deja ~30 ms entre mensajes y eso aquí solo sería esperar.
    private suspend fun send(link: KatanaLink, image: MemoryImage) =
        KatanaRepository(link, CoroutineScope(kotlin.coroutines.EmptyCoroutineContext))
            .sendPreset(image, gapMillis = 0L)

    @Test
    fun `un tsl real llega entero al otro lado`() = runBlocking {
        val original = MemoryImage.from(referencePreset().memory)
        val link = FakeLink()

        val result = send(link, original)
        val received = rebuild(link.sent)

        assertTrue(result.verdict, result.complete)
        TslBlockMap.blocks.filter { it.trusted }.forEach { block ->
            assertArrayEquals(
                "${block.key} debería llegar byte a byte",
                original.bytesAt(block.address, block.size),
                received.bytesAt(block.address, block.size),
            )
        }
        assertEquals(
            "y no debería llegar nada de los bloques en disputa",
            null,
            received.bytesAt(Address(0x60, 0x00, 0x0F, 0x30), 2),
        )
    }

    @Test
    fun `un preset en blanco llega entero, troceado incluido`() = runBlocking {
        val blank = TslWriter.blank("PRUEBA")
        val link = FakeLink()

        val result = send(link, blank)
        val received = rebuild(link.sent)

        assertEquals(20, link.sent.size)
        assertEquals(listOf("UserPatch%Fx(1)", "UserPatch%Fx(2)"), result.chunkedBlocks)
        TslBlockMap.blocks.filter { it.trusted }.forEach { block ->
            assertArrayEquals(
                block.key,
                blank.bytesAt(block.address, block.size),
                received.bytesAt(block.address, block.size),
            )
        }
    }

    @Test
    fun `un fallo del cable corta el envio y se dice donde`() = runBlocking {
        val link = FakeLink(failFrom = 5)

        val result = send(link, TslWriter.blank("PRUEBA"))

        assertEquals(5, link.sent.size)
        assertFalse(result.complete)
        assertTrue("debería nombrar el bloque donde falló: ${result.failedAt}", result.failedAt != null)
        assertTrue(
            "y el veredicto debería decir que el preset queda a medias: ${result.verdict}",
            result.verdict.contains("a medias"),
        )
    }

    @Test
    fun `el veredicto no afirma que el amplificador aceptara nada`() = runBlocking {
        val result = send(FakeLink(), TslWriter.blank("PRUEBA"))

        assertTrue(result.verdict, result.verdict.contains("no confirma"))
        // Los cuatro bloques en disputa se quedan como estaban, y hay que decirlo.
        assertEquals(4, result.plan.skipped.size)
        assertTrue(result.verdict, result.verdict.contains("se quedan como"))
    }
}

package dev.alonx3.ktnacontrol.ui.screens

import dev.alonx3.ktnacontrol.device.KatanaRepository
import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.tsl.TslBlockMap
import dev.alonx3.ktnacontrol.protocol.tsl.TslConfidence
import dev.alonx3.ktnacontrol.protocol.tsl.TslTransfer
import dev.alonx3.ktnacontrol.protocol.tsl.TslWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La secuencia de mandar un preset al amplificador, entera en JVM: sin Compose, sin Android y
 * sin amplificador.
 *
 * ⚠️ **Lo que estos tests NO cubren, dicho explícitamente**: que el botón se vea, que el diálogo
 * salga, y que tocarlo llame a lo que debe. Eso necesita un dispositivo y no se puede fingir
 * aquí; por eso la lógica se sacó del composable, para que lo único sin cubrir sea el dedo.
 * Y desde luego no cubren si el amplificador acepta los SET — eso es "Pendiente por probar" 15.
 */
class PresetSendFlowTest {

    /** Unconfined: el envío corre en el hilo del test hasta que se suspende de verdad. */
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val image = TslWriter.blank("PRUEBA")

    // ⚠️ Sin aserciones dentro: esto se llama desde el lambda de envío, y una aserción que
    // falle ahí la atraparía el catch del flujo y saldría como `Failed` en vez de como un test
    // roto — costó exactamente una vez descubrirlo.
    private fun result(sent: Int, failedAt: String? = null): KatanaRepository.PresetSendResult =
        KatanaRepository.PresetSendResult(
            plan = TslTransfer.plan(image),
            messagesSent = sent,
            dataBytesSent = sent * 100,
            failedAt = failedAt,
        )

    private fun flow(send: suspend (MemoryImage) -> KatanaRepository.PresetSendResult?) =
        PresetSendFlow(scope = scope, send = send)

    // --- Los dos pasos de confirmación --------------------------------------------------

    @Test
    fun `pedir un envio no manda nada, solo abre el paso de revision`() {
        var calls = 0
        val flow = flow { calls++; result(20) }

        assertTrue(flow.request("PRUEBA", image))

        assertTrue(flow.state.value is PresetSendState.Review)
        assertEquals("no debería haberse mandado nada todavía", 0, calls)
    }

    @Test
    fun `hacen falta dos si para que salga algo`() {
        var calls = 0
        val flow = flow { calls++; result(20) }
        flow.request("PRUEBA", image)

        // Desde el paso 1, confirmar no hace nada: hay que pasar por el 2.
        flow.onConfirmed()
        assertTrue(flow.state.value is PresetSendState.Review)
        assertEquals(0, calls)

        flow.onContinue()
        assertTrue(flow.state.value is PresetSendState.Confirm)
        assertEquals(0, calls)

        flow.onConfirmed()
        assertEquals(1, calls)
        assertTrue(flow.state.value is PresetSendState.Finished)
    }

    @Test
    fun `se puede volver del paso 2 al 1 sin cancelar`() {
        val flow = flow { result(20) }
        flow.request("PRUEBA", image)
        flow.onContinue()

        flow.onBack()

        assertTrue(flow.state.value is PresetSendState.Review)
    }

    @Test
    fun `cancelar en cualquiera de los dos pasos no manda nada y lo dice`() {
        listOf(0, 1).forEach { stepsForward ->
            var calls = 0
            val flow = flow { calls++; result(20) }
            flow.request("PRUEBA", image)
            repeat(stepsForward) { flow.onContinue() }

            flow.onCancel()

            val finished = flow.state.value as PresetSendState.Finished
            assertEquals(
                "cancelar tiene que dejar un resultado explícito, no un silencio",
                PresetSendOutcome.Cancelled,
                finished.outcome,
            )
            assertEquals("y no debería haber salido nada", 0, calls)
        }
    }

    // --- Lo que NO se va a escribir, antes de escribir -----------------------------------

    @Test
    fun `el paso de revision lista los bloques en disputa que no se van a mandar`() {
        val flow = flow { result(20) }
        flow.request("PRUEBA", image)

        val review = flow.state.value as PresetSendState.Review
        val disputed = TslBlockMap.blocks.filter { it.confidence == TslConfidence.DISPUTED }
        assertEquals(4, disputed.size)
        disputed.forEach { block ->
            assertTrue(
                "${block.key} tiene que avisarse antes de mandar, no después",
                review.plan.skipped.any { it.key == block.key },
            )
        }
        assertEquals(18, review.plan.writes.size)
    }

    @Test
    fun `un bloque incompleto tambien se avisa antes de mandar`() {
        val partial = MemoryImage.empty()
        partial.write(Address(0x60, 0x00, 0x06, 0x50), ByteArray(10)) // Status son 18, no 10
        val flow = flow { result(0) }

        flow.request("PARCIAL", partial)

        val review = flow.state.value as PresetSendState.Review
        assertTrue(review.plan.writes.isEmpty())
        assertTrue(review.plan.skipped.any { it.key == "UserPatch%Status" })
    }

    // --- Nunca dos envíos a la vez -------------------------------------------------------

    @Test
    fun `mientras hay un envio en curso no se admite otro`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val flow = flow { calls++; gate.await(); result(20) }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        assertTrue(flow.inFlight)
        assertFalse("el segundo envío se rechaza, no se encola", flow.request("OTRO", image))
        assertTrue("y el estado sigue siendo el del primero", flow.state.value is PresetSendState.Sending)

        gate.complete(Unit)

        assertEquals("solo se mandó una vez", 1, calls)
        assertTrue(flow.state.value is PresetSendState.Finished)
        assertFalse(flow.inFlight)
    }

    @Test
    fun `cancelar no aborta un envio ya en curso`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val flow = flow { gate.await(); result(20) }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        flow.onCancel()

        // Los mensajes que ya salieron no se pueden devolver: cancelar a mitad no existe.
        assertTrue(flow.state.value is PresetSendState.Sending)
        gate.complete(Unit)
        assertTrue(flow.state.value is PresetSendState.Finished)
    }

    // --- Cómo acaba ----------------------------------------------------------------------

    @Test
    fun `un envio completo se marca como completo y sigue sin afirmar exito`() {
        val flow = flow { result(20) }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        val outcome = (flow.state.value as PresetSendState.Finished).outcome
        val completed = outcome as PresetSendOutcome.Completed
        assertTrue(completed.result.complete)
        assertTrue(
            "el veredicto del repositorio no debe afirmar que el amp aceptara: ${completed.result.verdict}",
            completed.result.verdict.contains("no confirma"),
        )
    }

    @Test
    fun `un corte a mitad se distingue de un envio completo y conserva donde fallo`() {
        val flow = flow { result(sent = 7, failedAt = "UserPatch%Fx(1) (trozo 2 de 2)") }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        val outcome = (flow.state.value as PresetSendState.Finished).outcome
        val cut = outcome as PresetSendOutcome.Cut
        assertFalse(cut.result.complete)
        assertEquals(7, cut.result.messagesSent)
        assertEquals(20, cut.result.plan.messageCount)
        assertEquals("UserPatch%Fx(1) (trozo 2 de 2)", cut.result.failedAt)
        assertTrue(
            "y el veredicto tiene que decir que el amp queda a medias: ${cut.result.verdict}",
            cut.result.verdict.contains("a medias"),
        )
    }

    @Test
    fun `si el USB desaparece a mitad no se finge saber cuantos mensajes llegaron`() {
        val flow = flow { throw IllegalStateException("se cerró el endpoint") }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        val outcome = (flow.state.value as PresetSendState.Finished).outcome
        assertEquals(PresetSendOutcome.Failed("se cerró el endpoint"), outcome)
    }

    @Test
    fun `sin amplificador no sale nada y se dice que el amp esta intacto`() {
        val flow = flow { null }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        val outcome = (flow.state.value as PresetSendState.Finished).outcome
        assertEquals(PresetSendOutcome.NoTransport, outcome)
    }

    @Test
    fun `tras leer el resultado se vuelve al principio y se puede mandar otra vez`() {
        val flow = flow { result(20) }
        flow.request("PRUEBA", image)
        flow.onContinue()
        flow.onConfirmed()

        flow.onResultShown()

        assertEquals(PresetSendState.Idle, flow.state.value)
        assertTrue(flow.request("OTRA VEZ", image))
    }
}

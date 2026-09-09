package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.device.KatanaRepository
import dev.alonx3.ktnacontrol.device.OfflineKatanaLink
import dev.alonx3.ktnacontrol.protocol.ChainBlock
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El camino inverso al de [TslParserTest]: serializar y volver a leer.
 *
 * La prueba que de verdad importa es la **ida y vuelta sobre el fichero real** del repo: si
 * parsear y volver a escribir no conserva los bytes, la edición offline estaría perdiendo
 * información sin avisar, que es justo lo que la política de CLAUDE.md §4.5 quiere impedir.
 */
class TslWriterTest {

    private val referenceFile = File("../reference/FxFloorboard/default_mk2.tsl")

    private fun reference(): TslPreset {
        val text = referenceFile.readText()
        val result = TslParser.parse(text)
        assertTrue("el fichero de referencia debería parsear", result is TslParseResult.Parsed)
        return (result as TslParseResult.Parsed).presets.single()
    }

    private fun paramSetOf(json: String): JsonObject =
        Json.parseToJsonElement(json).jsonObject["data"]!!.jsonArray[0]
            .jsonArray[0].jsonObject["paramSet"]!!.jsonObject

    private fun JsonArray.hex(): List<String> = map { it.jsonPrimitive.content }

    // --- Ida y vuelta ------------------------------------------------------------------

    @Test
    fun `ida y vuelta sobre el fichero real conserva las 22 claves`() {
        val original = reference()

        val written = TslWriter.write(MemoryImage.from(original.memory), original.name, source = original)
        val params = paramSetOf(written.json)

        assertEquals(
            "deberían salir las 22 claves del mapa",
            TslBlockMap.BLOCK_COUNT,
            params.keys.count { TslBlockMap.forKey(it) != null },
        )
        assertTrue("no debería omitirse nada: ${written.omitted}", written.omitted.isEmpty())
    }

    @Test
    fun `ida y vuelta conserva los bytes de cada bloque, uno por uno`() {
        val original = reference()
        // El paramSet del fichero real tal cual está en disco, para comparar contra él.
        val originalParams = paramSetOf(referenceFile.readText())

        val written = TslWriter.write(MemoryImage.from(original.memory), original.name, source = original)
        val params = paramSetOf(written.json)

        TslBlockMap.blocks.forEach { block ->
            val before = originalParams[block.key]?.jsonArray?.hex()
            val after = params[block.key]?.jsonArray?.hex()
            assertEquals("el bloque ${block.key} cambió al reescribirlo", before, after)
        }
    }

    @Test
    fun `la ida y vuelta se puede volver a parsear y da el mismo AmpState`() {
        val original = reference()
        val before = dev.alonx3.ktnacontrol.device.model.AmpState.from(original.memory)

        val written = TslWriter.write(MemoryImage.from(original.memory), original.name, source = original)
        val reparsed = TslParser.parse(written.json)
        assertTrue("lo escrito debería volver a parsear", reparsed is TslParseResult.Parsed)
        val after = dev.alonx3.ktnacontrol.device.model.AmpState
            .from((reparsed as TslParseResult.Parsed).presets.single().memory)

        assertEquals(before, after)
    }

    // --- La política de lo que no es de fiar --------------------------------------------

    @Test
    fun `los bloques en disputa se copian verbatim en vez de perderse`() {
        val original = reference()
        val disputed = TslBlockMap.blocks.filter { !it.trusted }.map { it.key }
        assertTrue("debería haber bloques en disputa", disputed.isNotEmpty())

        val written = TslWriter.write(MemoryImage.from(original.memory), original.name, source = original)
        val params = paramSetOf(written.json)

        disputed.forEach { key ->
            assertTrue(
                "$key debería copiarse verbatim, no omitirse",
                key in written.passedThrough,
            )
            assertNotNull("$key debería estar en el fichero escrito", params[key])
        }
    }

    @Test
    fun `sin fichero de origen, un bloque en disputa se OMITE en vez de escribir ceros`() {
        // Es la decisión explícita del proyecto: un Contour a ceros es un valor legal e
        // indistinguible de uno elegido a mano, y cargarlo cambiaría el sonido en silencio.
        // Una clave ausente, como mucho, hace que el lector se queje.
        val written = TslWriter.write(TslWriter.blank("Vacio"), "Vacio", source = null)
        val params = paramSetOf(written.json)

        TslBlockMap.blocks.filter { !it.trusted }.forEach { block ->
            assertNull("${block.key} no debería escribirse a ciegas", params[block.key])
            assertTrue(
                "${block.key} debería estar declarado como omitido",
                written.omitted.any { it.key == block.key },
            )
        }
    }

    @Test
    fun `una clave desconocida del fichero de origen tambien se conserva`() {
        val original = reference().copy(
            rawParamSet = reference().rawParamSet + ("UserPatch%Inventada" to listOf("AA", "BB")),
        )

        val written = TslWriter.write(MemoryImage.from(original.memory), original.name, source = original)
        val params = paramSetOf(written.json)

        assertEquals(listOf("AA", "BB"), params["UserPatch%Inventada"]?.jsonArray?.hex())
    }

    // --- El nombre ----------------------------------------------------------------------

    @Test
    fun `el nombre se escribe en la envoltura y en 60 00 00 00`() {
        val written = TslWriter.write(TslWriter.blank("x"), "Mi Preset")
        val root = Json.parseToJsonElement(written.json).jsonObject

        assertEquals("Mi Preset", root["name"]?.jsonPrimitive?.content)
        val reparsed = TslParser.parse(written.json) as TslParseResult.Parsed
        assertEquals("Mi Preset", reparsed.presets.single().name)
    }

    @Test
    fun `un nombre con enie se sanea igual que al guardar en el amplificador`() {
        val written = TslWriter.write(TslWriter.blank("x"), "Distorsión")
        val reparsed = TslParser.parse(written.json) as TslParseResult.Parsed

        // Los datos SysEx son de 7 bits: la eñe no cabe, y se sustituye en vez de romper.
        assertEquals("Distorsi?n", reparsed.presets.single().name)
    }

    // --- El preset en blanco -------------------------------------------------------------

    @Test
    fun `un preset en blanco se puede parsear como cualquier otro`() {
        val written = TslWriter.write(TslWriter.blank("Nuevo"), "Nuevo")
        val result = TslParser.parse(written.json)

        assertTrue("un preset nuevo debería ser un .tsl válido", result is TslParseResult.Parsed)
        assertEquals("Nuevo", (result as TslParseResult.Parsed).presets.single().name)
    }

    @Test
    fun `la cadena de un preset en blanco es una permutacion valida, no veinte ceros`() {
        val blank = TslWriter.blank("Nuevo")
        val slots = List(ChainBlock.SLOT_COUNT) { slot ->
            blank.byteAt(KatanaAddresses.chainSlot(slot))
        }

        assertEquals("las 20 ranuras deberían tener valor", ChainBlock.SLOT_COUNT, slots.count { it != null })
        assertEquals(
            "deberían ser los 20 identificadores distintos, no el mismo veinte veces",
            ChainBlock.SLOT_COUNT,
            slots.toSet().size,
        )
        assertEquals(List(ChainBlock.SLOT_COUNT) { it }, slots)
    }

    /**
     * ⚠️ La afirmación que hace [TslWriter.blank] al sembrar todo a cero: que ningún selector
     * con huecos rechaza el `0x00`. Se comprueba montando el repositorio de verdad sobre el
     * preset en blanco, porque `KatanaEnumParameter` **rechaza en silencio** un valor que no
     * esté en su tabla (§4.3) — un preset nuevo con un selector inválido no daría error, solo
     * dejaría el control en blanco.
     */
    @Test
    fun `ningun selector rechaza los valores de un preset en blanco`() = runBlocking {
        val blank = TslWriter.blank("Nuevo")
        val repo = KatanaRepository(OfflineKatanaLink(blank), this)

        repo.loadFromImage(blank)

        assertEquals("Natural Clean es el 0x00", 0x00, repo.ampType.state.value)
        assertEquals(0x00, repo.boostTypeActive.state.value)
        assertEquals(0x00, repo.modTypeActive.state.value)
        assertEquals(0x00, repo.fxTypeActive.state.value)
        assertEquals(0x00, repo.delayTypeActive.state.value)
        assertEquals(0x00, repo.reverbTypeActive.state.value)
        assertEquals(0x00, repo.boostColor.state.value)
        assertEquals(0x00, repo.chainType.state.value)
        repo.chainSlots.forEachIndexed { slot, control ->
            assertEquals("la ranura $slot", slot, control.state.value)
        }
        repo.close()
    }

    @Test
    fun `un preset en blanco no trae los bloques en disputa`() {
        val blank = TslWriter.blank("Nuevo")
        TslBlockMap.blocks.filter { !it.trusted }.forEach { block ->
            assertNull(
                "${block.key} no debería sembrarse: su dirección está en disputa",
                blank.byteAt(block.address),
            )
        }
    }

    // --- La edición ----------------------------------------------------------------------

    @Test
    fun `editar un valor cambia ese byte y solo ese`() = runBlocking {
        val original = reference()
        val image = MemoryImage.from(original.memory)
        val before = TslWriter.write(image.copy(), original.name, source = original)

        val repo = KatanaRepository(OfflineKatanaLink(image), this, debounceMillis = 0)
        repo.gainLevel.setLevel(99)
        kotlinx.coroutines.delay(50)
        repo.close()

        val after = TslWriter.write(image, original.name, source = original)
        val beforeStatus = paramSetOf(before.json)["UserPatch%Status"]!!.jsonArray.hex()
        val afterStatus = paramSetOf(after.json)["UserPatch%Status"]!!.jsonArray.hex()

        assertEquals("99 en hex", "63", afterStatus[1])
        assertEquals(
            "solo debería cambiar un byte",
            1,
            beforeStatus.indices.count { beforeStatus[it] != afterStatus[it] },
        )
        // Y el resto de bloques, intactos.
        val beforeFx = paramSetOf(before.json)["UserPatch%Fx(1)"]!!.jsonArray.hex()
        val afterFx = paramSetOf(after.json)["UserPatch%Fx(1)"]!!.jsonArray.hex()
        assertEquals("Mod no debería tocarse al mover el Gain", beforeFx, afterFx)
    }

    @Test
    fun `el JSON escrito declara device y formatRev de Katana Mk2`() {
        val written = TslWriter.write(TslWriter.blank("x"), "x")
        val root = Json.parseToJsonElement(written.json).jsonObject

        assertEquals(TslParser.EXPECTED_DEVICE, root["device"]?.jsonPrimitive?.content)
        assertEquals(TslParser.FORMAT_REV_MK2V2, root["formatRev"]?.jsonPrimitive?.content)
        assertFalse("no debería quedar vacío", written.json.isBlank())
    }
}

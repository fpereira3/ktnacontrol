package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.device.model.AmpState
import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lectura de `.tsl` contra el fichero real `reference/FxFloorboard/default_mk2.tsl`.
 *
 * Es un test de **integración documental**: comprueba que el mapa de claves→dirección de
 * CLAUDE.md §5 produce, sobre un fichero de verdad, los mismos valores que el propio CLAUDE.md
 * predijo al analizarlo a mano. Si alguien corrige una dirección del mapa creyendo mejorarlo,
 * esto lo delata.
 *
 * ⚠️ Depende de un fichero de `reference/`, que **no forma parte del build** y está en
 * `.gitignore` (CLAUDE.md §7). Si no está, los tests se saltan en vez de fallar: quien clone el
 * repo sin las referencias no tiene por qué ver rojo.
 */
class TslParserTest {

    private val referenceFile = File("../reference/FxFloorboard/default_mk2.tsl")
        .takeIf { it.exists() }
        ?: File("reference/FxFloorboard/default_mk2.tsl").takeIf { it.exists() }

    private fun parsedReference(): TslParseResult.Parsed? {
        val file = referenceFile ?: return null
        return TslParser.parse(file.readText()) as? TslParseResult.Parsed
            ?: throw AssertionError("el fichero de referencia debería parsear")
    }

    // --- El mapa de bloques, sin depender de ningún fichero -------------------------------

    @Test
    fun `el mapa tiene las 22 claves y ninguna direccion se solapa con otra`() {
        assertEquals(TslBlockMap.BLOCK_COUNT, TslBlockMap.blocks.size)
        assertEquals(TslBlockMap.blocks.size, TslBlockMap.blocks.map { it.key }.toSet().size)

        // Un solapamiento sería un bloque pisando los bytes de otro: el valor que se leyera
        // dependería del orden de la lista, que es exactamente la clase de bug silencioso que
        // este formato invita a tener.
        val occupied = mutableMapOf<Int, String>()
        TslBlockMap.blocks.forEach { block ->
            (0 until block.size).forEach { offset ->
                val cell = block.address.value + offset
                val previous = occupied.put(cell, block.key)
                assertNull("${block.key} pisa a $previous en ${block.address} + $offset", previous)
            }
        }
    }

    @Test
    fun `Patch_1 son 91 bytes, no los 50 del yaml`() {
        // La corrección que más se nota: con 50 se perderían la cadena de efectos entera, Solo,
        // Contour general y la posición de EQ2 (CLAUDE.md §5).
        val block = TslBlockMap.forKey("UserPatch%Patch_1")
        assertNotNull(block)
        assertEquals(91, block!!.size)
        assertEquals(Address(0x60, 0x00, 0x05, 0x40), block.address)

        // Y con 91 el bloque llega hasta `60 00 06 1A`, el último control de ese tramo.
        val last = block.address + (block.size - 1)
        assertEquals(Address(0x60, 0x00, 0x06, 0x1A), last)
    }

    @Test
    fun `los bloques en disputa no se marcan como de fiar`() {
        val disputed = TslBlockMap.blocks.filter { it.confidence == TslConfidence.DISPUTED }
        assertEquals(
            listOf(
                "UserPatch%GafcExp1AsgnMinMax",
                "UserPatch%Contour(1)",
                "UserPatch%Contour(2)",
                "UserPatch%Contour(3)",
            ),
            disputed.map { it.key },
        )
        assertTrue(disputed.none { it.trusted })
        // Y cada uno explica por qué, porque el aviso llega hasta la UI.
        assertTrue(disputed.all { !it.note.isNullOrBlank() })
    }

    // --- El fichero de referencia ----------------------------------------------------------

    @Test
    fun `el tsl de referencia trae un preset llamado KATANA Mk2`() {
        val parsed = parsedReference() ?: return
        assertEquals(1, parsed.presets.size)
        assertEquals("KATANA Mk2", parsed.presets.single().name)
        assertEquals("", parsed.presets.single().memo)
    }

    @Test
    fun `produce el AmpState que CLAUDE punto md predijo al analizar el fichero a mano`() {
        val parsed = parsedReference() ?: return
        val state = AmpState.from(parsed.presets.single().memory)

        // Las seis perillas del panel, todas a 50 en el preset por defecto. El Gain es el que
        // CLAUDE.md §5 verificó a mano: `60 00 06 51` cae en `Status[1]` y vale 0x32 = 50.
        assertEquals(50, state.gain)
        assertEquals(50, state.volume)
        assertEquals(50, state.bass)
        assertEquals(50, state.middle)
        assertEquals(50, state.treble)
        assertEquals(50, state.presence)

        // `60 00 00 21` cae en `Patch_0[17]` y vale 0x08 = Clean — el otro dato que CLAUDE.md
        // comprobó byte a byte.
        assertEquals(AmpType.CLEAN, state.ampType)
        assertEquals(AmpCategory.CLEAN, state.ampCategory)
        assertEquals(false, state.variationOn)

        // Los cinco efectos: apagados, en verde, y con el nivel a 0.
        state.effects.forEach { (name, effect) ->
            assertEquals("$name: debería venir apagado", false, effect.enabled)
            assertEquals("$name: debería venir en verde", EffectColor.GREEN, effect.color)
            assertEquals("$name: nivel", 0, effect.level)
        }

        // Los 24 parámetros que modela AmpState vienen todos en el fichero.
        assertEquals(24, state.knownCount)
    }

    @Test
    fun `la comprobacion de consistencia del tipo activo contra el slot de color cuadra`() {
        // El chequeo que CLAUDE.md §5.2 describe y que un fichero real confirma: el color de
        // Booster es verde, el tipo del slot verde vale 0x0A y el "tipo activo" vale también
        // 0x0A. O sea que el tipo activo refleja el slot del color encendido.
        val parsed = parsedReference() ?: return
        val memory = parsed.presets.single().memory

        assertEquals(EffectColor.GREEN.value, memory.byteAt(KatanaAddresses.BOOST_COLOR))
        assertEquals(0x0A, memory.byteAt(KatanaAddresses.BOOST_TYPE_BY_COLOR.first()))
        assertEquals(0x0A, memory.byteAt(KatanaAddresses.BOOST_TYPE_ACTIVE))
    }

    @Test
    fun `carga 1141 bytes menos los de los bloques en disputa`() {
        val parsed = parsedReference() ?: return
        val preset = parsed.presets.single()

        // El fichero trae 1141 bytes en 22 bloques (CLAUDE.md §5). Se cargan todos menos los
        // cuatro en disputa: 76 de GafcExp1AsgnMinMax + 3 × 2 de los Contour = 82.
        assertEquals(1141 - 82, preset.loadedBytes)
    }

    @Test
    fun `los bloques en disputa se reportan como no disponibles en vez de cargarse mal`() {
        val parsed = parsedReference() ?: return
        val preset = parsed.presets.single()

        listOf(
            "UserPatch%Contour(1)", "UserPatch%Contour(2)", "UserPatch%Contour(3)",
            "UserPatch%GafcExp1AsgnMinMax",
        ).forEach { key ->
            assertTrue(
                "$key debería aparecer como no disponible",
                preset.unavailable.any { it.what == key && it.reason.contains("disputa") },
            )
        }

        // Y sus direcciones no tienen bytes: mejor "—" que un número del bloque de al lado.
        assertNull(preset.memory.byteAt(Address(0x60, 0x00, 0x0F, 0x30)))
        assertNull(preset.memory.byteAt(Address(0x60, 0x00, 0x0F, 0x38)))
        assertNull(preset.memory.byteAt(Address(0x60, 0x00, 0x0F, 0x40)))
    }

    @Test
    fun `el hueco documentado del formato se reporta aunque el fichero este completo`() {
        val parsed = parsedReference() ?: return
        val preset = parsed.presets.single()

        // Pedal Bend de Mod/FX no está en el formato, pase lo que pase con el fichero.
        assertTrue(preset.unavailable.any { it.reason.contains("Pedal Bend") })
        // Y de hecho su dirección no tiene bytes.
        assertNull(preset.memory.byteAt(Address(0x60, 0x00, 0x02, 0x5D)))
    }

    @Test
    fun `la cadena de efectos SI se carga, que es lo que el size 50 se dejaba fuera`() {
        val parsed = parsedReference() ?: return
        val memory = parsed.presets.single().memory

        // Las 20 posiciones viven en Patch_1[64..83], o sea dentro de los 91 bytes y fuera de
        // los 50. Que estén es la prueba de que la corrección del tamaño sirve para algo.
        val chain = (0 until 20).map { slot ->
            memory.byteAt(KatanaAddresses.chainSlot(slot))
        }
        assertTrue("la cadena debería venir entera", chain.all { it != null })
        // Y es una permutación completa de los 20 identificadores, como describe CLAUDE.md §5.
        assertEquals((0..19).toSet(), chain.filterNotNull().toSet())
    }

    // --- Ficheros que no se deben leer -----------------------------------------------------

    @Test
    fun `un tsl de la serie GT se rechaza en vez de parsearse como si fuera Katana`() {
        val gt = File("../reference/FxFloorboard/default.tsl").takeIf { it.exists() }
            ?: File("reference/FxFloorboard/default.tsl").takeIf { it.exists() }
            ?: return
        val result = TslParser.parse(gt.readText())
        assertTrue("debería rechazarse", result is TslParseResult.Invalid)
        assertTrue((result as TslParseResult.Invalid).reason.contains("GT"))
    }

    @Test
    fun `un json que no es un tsl se rechaza con un motivo legible`() {
        listOf(
            "" to "JSON",
            "no soy json" to "JSON",
            """{"name":"x"}""" to "device",
            """{"device":"KATANA MkII"}""" to "data",
            """{"device":"KATANA MkII","data":[]}""" to "vacío",
        ).forEach { (text, expected) ->
            val result = TslParser.parse(text)
            assertTrue("«$text» debería rechazarse", result is TslParseResult.Invalid)
            assertTrue(
                "el motivo de «$text» debería mencionar «$expected»: ${(result as TslParseResult.Invalid).reason}",
                result.reason.contains(expected, ignoreCase = true),
            )
        }
    }

    @Test
    fun `un bloque con bytes corruptos se reporta y no tumba el resto del preset`() {
        // Un `.tsl` mínimo con dos bloques, uno de ellos con basura donde van los hex.
        val text = """
            {"name":"T","formatRev":"0002","device":"KATANA MkII","data":[[{
              "memo":{"memo":"nota"},
              "paramSet":{
                "UserPatch%PatchName":[${(1..16).joinToString(",") { "\"41\"" }}],
                "UserPatch%Status":[${(1..18).joinToString(",") { "\"ZZ\"" }}]
              }}]]}
        """.trimIndent()

        val result = TslParser.parse(text)
        assertTrue(result is TslParseResult.Parsed)
        val preset = (result as TslParseResult.Parsed).presets.single()

        // El nombre sí se leyó — 16 bytes de 'A'.
        assertEquals("AAAAAAAAAAAAAAAA", preset.name)
        assertTrue(
            preset.unavailable.any { it.what == "UserPatch%Status" && it.reason.contains("hexadecimal") },
        )
        // Y el Gain, que vive en Status, queda desconocido en vez de valer cualquier cosa.
        assertNull(AmpState.from(preset.memory).gain)
    }

    @Test
    fun `un bloque con un tamano inesperado se carga pero se avisa`() {
        // El tamaño del mapa es el esperado; el que manda es el del fichero. Truncar sería
        // inventar, y rechazar el fichero entero por un bloque raro sería desproporcionado.
        val text = """
            {"name":"T","formatRev":"0002","device":"KATANA MkII","data":[[{
              "memo":{"memo":""},
              "paramSet":{"UserPatch%Status":["01","02","03"]}
            }]]}
        """.trimIndent()

        val result = TslParser.parse(text) as TslParseResult.Parsed
        val preset = result.presets.single()
        assertTrue(
            preset.unavailable.any { it.what == "UserPatch%Status" && it.reason.contains("se esperaban 18") },
        )
        // Los 3 bytes que sí trae se colocan igual.
        assertEquals(0x01, preset.memory.byteAt(KatanaAddresses.AMP_TYPE_PANEL))
        // Y el nombre cae al del fichero, porque el bloque del nombre no vino.
        assertEquals("T", preset.name)
    }

    @Test
    fun `una clave desconocida se avisa en vez de ignorarse en silencio`() {
        val text = """
            {"name":"T","formatRev":"0002","device":"KATANA MkII","data":[[{
              "memo":{"memo":""},
              "paramSet":{"UserPatch%LoQueSea":["01"]}
            }]]}
        """.trimIndent()

        val preset = (TslParser.parse(text) as TslParseResult.Parsed).presets.single()
        assertTrue(
            preset.unavailable.any {
                it.what == "UserPatch%LoQueSea" && it.reason.contains("desconocida")
            },
        )
    }

    @Test
    fun `una revision distinta se lee igual, avisando`() {
        val text = """
            {"name":"T","formatRev":"0001","device":"KATANA MkII","data":[[{
              "memo":{"memo":""},"paramSet":{}
            }]]}
        """.trimIndent()

        val result = TslParser.parse(text)
        assertTrue("no debería rechazarse por la revisión", result is TslParseResult.Parsed)
        val preset = (result as TslParseResult.Parsed).presets.single()
        assertTrue(preset.unavailable.any { it.what.contains("formatRev 0001") })
        // Sin bloques, todo queda desconocido y nada explota.
        assertEquals(0, AmpState.from(preset.memory).knownCount)
        assertFalse(preset.unavailable.isEmpty())
    }
}

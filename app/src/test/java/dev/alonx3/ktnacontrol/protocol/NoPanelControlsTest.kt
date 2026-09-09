package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.ModFxInternalParams

/**
 * Los controles **sin perilla física** (CLAUDE.md §5): Noise Gate, Contour, las posiciones de
 * EQ1/EQ2, los dos bloques de EQ y la cadena de efectos.
 *
 * Comparten un problema con los 31 tipos de Mod/FX y no lo comparten con los once niveles del
 * panel: **no se pueden descubrir girando una perilla y mirando qué reporta el amplificador**,
 * porque no hay perilla que girar. Todo sale de una transcripción de `midi.xml`, así que lo que
 * estos tests pueden comprobar es que la transcripción es coherente consigo misma y con el
 * resto del proyecto — el checksum de cada SET, que ningún bloque pise a otro, y que las
 * escalas hacen lo que dicen.
 */
class NoPanelControlsTest {

    // --- Noise Gate, Contour y las posiciones -------------------------------------------

    @Test
    fun `las direcciones sueltas son las que documenta midi punto xml`() {
        assertEquals(Address(0x60, 0x00, 0x05, 0x66), KatanaAddresses.NOISE_GATE_ENABLED)
        assertEquals(Address(0x60, 0x00, 0x05, 0x67), KatanaAddresses.NOISE_GATE_THRESHOLD)
        assertEquals(Address(0x60, 0x00, 0x05, 0x68), KatanaAddresses.NOISE_GATE_RELEASE)
        assertEquals(Address(0x60, 0x00, 0x06, 0x16), KatanaAddresses.CONTOUR_ENABLED)
        assertEquals(Address(0x60, 0x00, 0x06, 0x17), KatanaAddresses.CONTOUR_SELECT)
        assertEquals(Address(0x60, 0x00, 0x06, 0x1A), KatanaAddresses.CONTOUR_FREQ_SHIFT)
        assertEquals(Address(0x60, 0x00, 0x06, 0x22), KatanaAddresses.EQ1_POSITION)
        assertEquals(Address(0x60, 0x00, 0x06, 0x19), KatanaAddresses.EQ2_POSITION)
        assertEquals(Address(0x60, 0x00, 0x06, 0x20), KatanaAddresses.CHAIN_TYPE)
        assertEquals(Address(0x60, 0x00, 0x06, 0x21), KatanaAddresses.LOOP_POSITION)
        assertEquals(Address(0x60, 0x00, 0x06, 0x23), KatanaAddresses.PEDAL_FX_POSITION)
    }

    @Test
    fun `las posiciones de EQ son selectores de DOS valores, no de tres`() {
        // Es la diferencia explícita con casi todos los demás selectores del proyecto, y con el
        // EQ **global** (que sí tiene cuatro posiciones y vive en otro espacio de direcciones).
        assertEquals(2, KatanaAddresses.EQ1_POSITION_VALUES.size)
        assertEquals(2, KatanaAddresses.EQ2_POSITION_VALUES.size)
        assertEquals(listOf(0x00, 0x01), KatanaAddresses.EQ1_POSITION_VALUES)
        assertEquals(listOf(0x00, 0x01), KatanaAddresses.EQ2_POSITION_VALUES)
        // Y las dos son direcciones distintas, pese a llamarse igual en la fuente.
        assertNotEquals(KatanaAddresses.EQ1_POSITION, KatanaAddresses.EQ2_POSITION)
    }

    @Test
    fun `los tres slots de Contour van de ocho en ocho desde 0F 30`() {
        val expected = listOf(0x30, 0x38, 0x40)
        expected.forEachIndexed { slot, shapeByte ->
            val (shape, freq) = KatanaAddresses.contourSlot(slot)
            assertEquals(Address(0x60, 0x00, 0x0F, shapeByte), shape)
            assertEquals(Address(0x60, 0x00, 0x0F, shapeByte + 1), freq)
        }
    }

    @Test
    fun `los slots de Contour caen fuera del rango del dump`() {
        // No es una curiosidad: son los únicos controles del proyecto que el dump no puede
        // poblar, así que dependen del GET individual de respaldo. Si algún día el rango del
        // dump crece y los cubre, este test falla y hay que revisar la nota que dice que no.
        val dumpStart = KatanaAddresses.MEMORY_DUMP.value
        val dumpEnd = dumpStart + KatanaAddresses.MEMORY_DUMP_SIZE
        (0 until KatanaAddresses.CONTOUR_SLOT_COUNT).forEach { slot ->
            val (shape, freq) = KatanaAddresses.contourSlot(slot)
            assertTrue(
                "el slot de contour $slot ya cae dentro del dump: revisar la nota de §5",
                shape.value >= dumpEnd && freq.value >= dumpEnd,
            )
        }
    }

    @Test
    fun `solo los seis controles de Contour por slot caen fuera del dump, y son exactamente seis`() {
        // Fija el precio de la decisión, para que no crezca sin que nadie se entere:
        // `loadFromDump` hace los GET de respaldo **en serie**, uno por control que el dump no
        // cubra, con un tope de DEFAULT_REPLY_TIMEOUT_MS (800 ms) cada uno. Seis controles son
        // hasta 4,8 s añadidos a cada conexión **y a cada cambio de canal** si esa región no
        // contesta. Si alguien cablea más controles fuera del dump, este test lo delata antes
        // de que el cambio de canal se vuelva lento sin explicación.
        val dumpEnd = KatanaAddresses.MEMORY_DUMP.value + KatanaAddresses.MEMORY_DUMP_SIZE

        val outsideDump = mutableListOf<Address>()
        (0 until KatanaAddresses.CONTOUR_SLOT_COUNT).forEach { slot ->
            val (shape, freq) = KatanaAddresses.contourSlot(slot)
            listOf(shape, freq).filterTo(outsideDump) { it.value >= dumpEnd }
        }
        assertEquals(6, outsideDump.size)

        // Y el resto de lo que se cableó en esta tanda sí entra en el dump, así que no añade
        // ni un GET de respaldo más.
        val insideDump = buildList {
            add(KatanaAddresses.NOISE_GATE_ENABLED)
            add(KatanaAddresses.NOISE_GATE_THRESHOLD)
            add(KatanaAddresses.NOISE_GATE_RELEASE)
            add(KatanaAddresses.CONTOUR_ENABLED)
            add(KatanaAddresses.CONTOUR_SELECT)
            add(KatanaAddresses.CONTOUR_FREQ_SHIFT)
            add(KatanaAddresses.EQ1_POSITION)
            add(KatanaAddresses.EQ2_POSITION)
            add(KatanaAddresses.CHAIN_TYPE)
            add(KatanaAddresses.LOOP_POSITION)
            add(KatanaAddresses.PEDAL_FX_POSITION)
            (0 until ChainBlock.SLOT_COUNT).forEach { add(KatanaAddresses.chainSlot(it)) }
            EqParams.SPECS.forEach {
                add(it.address)
                add(it.address + EqParams.EQ2_OFFSET)
            }
        }
        insideDump.forEach { address ->
            assertTrue(
                "$address debería caer dentro del dump y no añadir un GET de respaldo",
                address.value < dumpEnd,
            )
        }
    }

    @Test
    fun `la escala centrada del Freq Shift va de -50 a +50 sobre el crudo 00-64`() {
        val scale = KatanaAddresses.CONTOUR_FREQ_SHIFT_SCALE
        assertEquals(-50..50, scale.displayRange)
        assertEquals(0x00..0x64, scale.rawRange)
        assertEquals(0x00, scale.toRaw(-50))
        assertEquals(0x32, scale.toRaw(0))
        assertEquals(0x64, scale.toRaw(50))
    }

    // --- La cadena de efectos ------------------------------------------------------------

    @Test
    fun `la cadena son 20 posiciones consecutivas desde 06 00`() {
        assertEquals(20, ChainBlock.SLOT_COUNT)
        (0 until ChainBlock.SLOT_COUNT).forEach { slot ->
            assertEquals(Address(0x60, 0x00, 0x06, slot), KatanaAddresses.chainSlot(slot))
        }
    }

    @Test
    fun `los 20 identificadores de bloque corren de 00 a 13 sin huecos ni repetidos`() {
        val values = ChainBlock.VALUES
        assertEquals(20, values.size)
        assertEquals((0x00..0x13).toList(), values)
        assertEquals(values.size, values.toSet().size)
        assertEquals(values.size, ChainBlock.entries.map { it.displayName }.toSet().size)
    }

    @Test
    fun `la cadena predefinida tiene siete valores`() {
        assertEquals(7, KatanaAddresses.CHAIN_TYPE_VALUES.size)
        assertEquals((0x00..0x06).toList(), KatanaAddresses.CHAIN_TYPE_VALUES)
    }

    @Test
    fun `una posicion de la cadena fuera de rango se rechaza en vez de calcular una direccion`() {
        listOf(-1, ChainBlock.SLOT_COUNT).forEach { slot ->
            val failed = runCatching { KatanaAddresses.chainSlot(slot) }.isFailure
            assertTrue("chainSlot($slot) debería rechazarse", failed)
        }
    }

    // --- Los dos bloques de EQ -----------------------------------------------------------

    @Test
    fun `un bloque de EQ son 24 parametros contiguos desde 00 40`() {
        assertEquals(EqParams.BLOCK_SIZE, EqParams.SPECS.size)
        EqParams.SPECS.forEachIndexed { index, spec ->
            assertEquals(
                "el parámetro ${spec.label} no está donde toca",
                Address(0x60, 0x00, 0x00, 0x40 + index),
                spec.address,
            )
        }
    }

    @Test
    fun `EQ2 es EQ1 mas 0x20 y los dos bloques no se solapan`() {
        // ⚠️ Aquí el literal hexadecimal **sí** es el entero a sumar, al revés que en
        // FX = Mod + 0x0200: EQ2 mueve el cuarto byte (el de menor peso) y FX el tercero.
        assertEquals(0x20, EqParams.EQ2_OFFSET)

        val eq1 = EqParams.SPECS.map { it.address.value }
        val eq2 = EqParams.SPECS.map { it.address.value + EqParams.EQ2_OFFSET }
        assertEquals(eq1.size, eq1.toSet().size)
        assertEquals(eq2.size, eq2.toSet().size)
        eq2.forEach { address ->
            assertTrue("una dirección de EQ2 ($address) pisa una de EQ1", address !in eq1.toSet())
        }
        // EQ1 acaba en `00 57` y EQ2 empieza en `00 60`: quedan 8 direcciones libres entre medio.
        assertEquals(8, eq2.min() - eq1.max() - 1)
    }

    @Test
    fun `el SET de cada parametro de los dos bloques de EQ tiene checksum correcto`() {
        listOf(0 to "EQ1", EqParams.EQ2_OFFSET to "EQ2").forEach { (offset, name) ->
            EqParams.SPECS.forEach { spec ->
                val raw = when (val kind = spec.kind) {
                    is ParamKind.Enum -> kind.values.first()
                    is ParamKind.Centered -> kind.radius
                    is ParamKind.Fractional -> kind.rawRange.first
                    is ParamKind.Direct -> kind.range.first
                    is ParamKind.TwoByteDirect -> kind.range.first
                    is ParamKind.OffThenOneBased -> 1
                }
                val address = spec.address + offset
                val message = RolandSysEx.set(address, MidiBytes.encode(raw, 1))
                when (val parsed = RolandSysEx.parse(message)) {
                    is RolandMessage.Data -> {
                        assertEquals("$name/${spec.label}: dirección", address, parsed.address)
                        assertEquals("$name/${spec.label}: dato", raw, MidiBytes.decode(parsed.data))
                    }

                    is RolandMessage.Invalid ->
                        throw AssertionError("$name/${spec.label}: ${parsed.reason}")
                }
            }
        }
    }

    @Test
    fun `el bloque de EQ se parte en 13 del parametrico y 11 del grafico`() {
        val parametric = EqParams.SPECS.take(13)
        val graphic = EqParams.SPECS.drop(13)
        assertEquals(13, parametric.size)
        assertEquals(11, graphic.size)
        // El gráfico entero es fraccionario; el paramétrico no tiene ni un fraccionario.
        assertTrue(graphic.all { it.kind is ParamKind.Fractional })
        assertTrue(parametric.none { it.kind is ParamKind.Fractional })
        // Y el gráfico empieza justo en `00 4D`, donde `midi.xml` cambia de `PEQ1:` a `GEQ1:`.
        assertEquals(Address(0x60, 0x00, 0x00, 0x4D), graphic.first().address)
    }

    @Test
    fun `los cuatro Gain y el Level del parametrico son centrados enteros de 20`() {
        // ⚠️ Son **cuatro** Gain (Low, Lo Mid, Hi Mid, Hi) más el Level: cinco en total.
        // CLAUDE.md §5 decía "los cinco Gain y el Level", que daría seis — corregido allí el
        // 2026-09-06 después de que este test lo cazara. `midi.xml:37590/37631/37672/37675/37695`.
        val centered = EqParams.SPECS.filter { it.kind is ParamKind.Centered }
        assertEquals(5, centered.size)
        assertTrue(centered.all { (it.kind as ParamKind.Centered).radius == 20 })
        assertEquals(
            listOf("Low Gain", "Lo Mid Gain", "Hi Mid Gain", "Hi Gain", "Level"),
            centered.map { it.label },
        )
        // Y el Level del gráfico NO está entre ellos: es fraccionario y tiene su propia etiqueta.
        val graphicLevel = EqParams.SPECS.first { it.label == "Level (gráfico)" }
        assertTrue(graphicLevel.kind is ParamKind.Fractional)
    }

    // --- La escala de 0,5 dB del EQ gráfico ----------------------------------------------

    @Test
    fun `el grafico son 49 valores crudos sobre 24 dB, o sea pasos de medio dB`() {
        val scale = EqParams.GRAPHIC_SCALE
        assertEquals(0x00..0x30, scale.rawRange)
        assertEquals(49, scale.rawRange.count())
        assertEquals(0.5, scale.step, 0.0)
        assertEquals(-12.0, scale.toDisplay(0x00), 0.0001)
        assertEquals(0.0, scale.toDisplay(0x18), 0.0001)
        assertEquals(12.0, scale.toDisplay(0x30), 0.0001)
        // El recorrido completo cubre exactamente los 24,0 dB documentados.
        assertEquals(24.0, scale.toDisplay(scale.rawRange.last) - scale.toDisplay(scale.rawRange.first), 0.0001)
    }

    @Test
    fun `NO es la misma escala que el Graphic EQ interno de Mod-FX, que es entero`() {
        // `midi.xml` da `00/28/-20/+20` para el de Mod/FX y `00/30/-12.0/+12.0` para estos.
        // Se llaman igual y no lo son; confundirlos daría el doble de rango y la mitad de
        // resolución.
        val modGraphicEq = ModFxInternalParams.byType.getValue(ModFxType.GRAPHIC_EQ)
        assertTrue(modGraphicEq.all { it.kind is ParamKind.Centered })
        assertTrue(modGraphicEq.all { (it.kind as ParamKind.Centered).radius == 20 })
        assertTrue(EqParams.SPECS.drop(13).all { it.kind is ParamKind.Fractional })
    }

    @Test
    fun `los 49 pasos del grafico van y vuelven sin perder ni un valor`() {
        val scale = EqParams.GRAPHIC_SCALE
        scale.rawRange.forEach { raw ->
            assertEquals(
                "el crudo $raw no sobrevive la ida y vuelta",
                raw,
                scale.toRaw(scale.toDisplay(raw)),
            )
        }
    }

    @Test
    fun `el paso de medio dB no acumula error de redondeo en viajes repetidos`() {
        // Mismo chequeo que se hizo para el Pre Delay de 2x2 Chorus y Reverb Time: si `toRaw`
        // acumulara sobre la conversión anterior en vez de partir siempre del entero, 50
        // vueltas moverían el valor. Se prueba en los tres sitios donde más dolería —los dos
        // extremos y el centro— y en un valor con parte fraccionaria "fea".
        val scale = EqParams.GRAPHIC_SCALE
        listOf(0x00, 0x01, 0x17, 0x18, 0x19, 0x2F, 0x30).forEach { start ->
            var raw = start
            repeat(50) { raw = scale.toRaw(scale.toDisplay(raw)) }
            assertEquals("el crudo $start derivó tras 50 vueltas", start, raw)
        }
    }

    @Test
    fun `un valor de pantalla a medio paso cae siempre en el mismo crudo`() {
        val scale = EqParams.GRAPHIC_SCALE
        // -11,75 dB está entre -12,0 (crudo 0) y -11,5 (crudo 1): redondea a uno de los dos y
        // **siempre al mismo**, que es lo que hace que arrastrar un slider no tiemble.
        val once = scale.toRaw(-11.75)
        repeat(20) { assertEquals(once, scale.toRaw(-11.75)) }
        assertTrue("el redondeo debería caer en un crudo legal", once in scale.rawRange)
    }

    @Test
    fun `fuera de rango clampea en vez de dar la vuelta`() {
        val scale = EqParams.GRAPHIC_SCALE
        assertEquals(scale.rawRange.first, scale.toRaw(-99.0))
        assertEquals(scale.rawRange.last, scale.toRaw(99.0))
        assertEquals(-12.0, scale.toDisplay(-5), 0.0001)
        assertEquals(12.0, scale.toDisplay(999), 0.0001)
    }

    // --- Los catálogos de frecuencia -----------------------------------------------------

    @Test
    fun `los catalogos del EQ coinciden con los del Parametric EQ de Mod-FX`() {
        // Verificado sobre `midi.xml`: son byte a byte las mismas listas. Se definen por
        // separado a propósito (bloques distintos del amplificador, ver el KDoc de EqParams),
        // así que este test es lo que avisará el día que una diverja.
        val peq = ModFxInternalParams.byType.getValue(ModFxType.PARAMETRIC_EQ)
        fun labelsOf(label: String): List<String> =
            (peq.first { it.label == label }.kind as ParamKind.Enum).labels

        assertEquals(labelsOf("Lo Cut Off"), EqParams.LOW_CUT_LABELS)
        assertEquals(labelsOf("Lo Mid Freq"), EqParams.MID_FREQ_LABELS)
        assertEquals(labelsOf("Hi Mid Freq"), EqParams.MID_FREQ_LABELS)
        assertEquals(labelsOf("Lo Mid Q"), EqParams.Q_LABELS)
        assertEquals(labelsOf("Hi Mid Q"), EqParams.Q_LABELS)
        assertEquals(labelsOf("Hi Cut Off"), EqParams.HIGH_CUT_LABELS)
    }

    @Test
    fun `el Hi Cut del EQ dice 6 punto 00k, del lado de Reverb y no de Delay`() {
        // La contradicción de `midi.xml` consigo mismo en `0x0A` (CLAUDE.md §5.2). Este es el
        // tercer sitio que dice `6.00k`; solo el bloque de Delay 1 dice `6.30K`.
        assertEquals("6.00k", EqParams.HIGH_CUT_LABELS[0x0A])
        assertEquals("6.00k", ReverbHighCutFrequency.entries[0x0A].displayName)
        assertEquals("6.30K", DelayHighCutFrequency.entries[0x0A].displayName)
    }

    @Test
    fun `todos los selectores del EQ corren sin huecos`() {
        EqParams.SPECS.mapNotNull { it.kind as? ParamKind.Enum }.forEach { kind ->
            assertEquals(kind.values.size, kind.labels.size)
            assertEquals((0 until kind.values.size).toList(), kind.values)
        }
    }

    @Test
    fun `EqSelection dice cual de las dos mitades suena`() {
        assertEquals(EqSelection.PARAMETRIC, EqSelection.fromValue(0x00))
        assertEquals(EqSelection.GRAPHIC, EqSelection.fromValue(0x01))
        assertEquals(null, EqSelection.fromValue(0x02))
    }

    /**
     * ⚠️ El reparto que hace la UI del EQ: `take(2)` cabecera, `subList(2, 13)` paramétrico y
     * `drop(13)` gráfico (rediseño de 2026-09-06 — perillas y barras).
     *
     * Son índices fijos sobre [EqParams.SPECS], así que reordenar la tabla movería controles de
     * una mitad a la otra **sin fallar al compilar**: las ganancias del paramétrico aparecerían
     * como barras de frecuencia y al revés. Esto lo fija por etiqueta, no por posición.
     */
    @Test
    fun `el reparto del EQ en cabecera, parametrico y grafico es el que espera la UI`() {
        val header = EqParams.SPECS.take(2).map { it.label }
        val parametric = EqParams.SPECS.subList(2, 13).map { it.label }
        val graphic = EqParams.SPECS.drop(13).map { it.label }

        assertEquals(listOf("On/Off", "Selection"), header)
        assertEquals(
            listOf(
                "Low Cut", "Low Gain", "Lo Mid Freq", "Lo Mid Q", "Lo Mid Gain",
                "Hi Mid Freq", "Hi Mid Q", "Hi Mid Gain", "Hi Gain", "Hi Cut", "Level",
            ),
            parametric,
        )
        // ⚠️ El Level del gráfico se llama distinto del Level del paramétrico **a propósito**:
        // los valores viajan en un mapa por etiqueta, así que dos "Level" en el mismo bloque
        // se pisarían y la mitad inactiva movería la activa.
        assertEquals(EqParams.GRAPHIC_BAND_LABELS + "Level (gráfico)", graphic)
        assertEquals(
            "las dos mitades no pueden compartir ninguna etiqueta",
            emptySet<String>(),
            parametric.toSet() intersect graphic.toSet(),
        )
        assertEquals("las tres partes suman los 24 specs", 24, EqParams.SPECS.size)
    }

    /**
     * El paramétrico de EQ1/EQ2 y el tipo `PARAMETRIC_EQ` de Mod/FX **son el mismo juego de
     * once parámetros**, que es lo que permite dibujarlos con las mismas perillas.
     *
     * Se comprueba por nombre y no por dirección: las direcciones son distintas a propósito
     * (`60 00 00 42` contra `60 00 01 2C`), lo que coincide es qué controla cada uno.
     */
    @Test
    fun `el parametrico de EQ y el de Mod-FX tienen los mismos once parametros`() {
        val eq = EqParams.SPECS.subList(2, 13).map { it.label }
        val modFx = ModFxInternalParams.byType[ModFxType.PARAMETRIC_EQ].orEmpty().map { it.label }

        assertEquals(11, eq.size)
        assertEquals(11, modFx.size)
        // Las etiquetas difieren en cosmética entre las dos fuentes ("Low Cut" contra
        // "Lo Cut Off"), así que se comparan las formas, no las cadenas literales.
        assertEquals(
            eq.count { it.contains("Gain") || it == "Level" },
            modFx.count { it.contains("Gain") || it == "Level" },
        )
        assertEquals(
            eq.count { it.contains("Freq") || it.contains("Cut") },
            modFx.count { it.contains("Freq") || it.contains("Cut") },
        )
        assertEquals(eq.count { it.endsWith(" Q") }, modFx.count { it.endsWith(" Q") })
    }

    /** El gráfico de Mod/FX tiene sus 10 bandas más el Level, igual que el de EQ1/EQ2. */
    @Test
    fun `el grafico de Mod-FX tiene once controles como el de EQ`() {
        val modFx = ModFxInternalParams.byType[ModFxType.GRAPHIC_EQ].orEmpty()
        assertEquals(11, modFx.size)
        assertEquals(11, EqParams.SPECS.drop(13).size)
        // ⚠️ Pero **no** comparten escala: ±20 dB entero contra ±12 dB en pasos de 0,5.
        assertTrue(
            "el gráfico de Mod/FX es entero",
            modFx.first().kind is ParamKind.Centered,
        )
        assertTrue(
            "el de EQ1/EQ2 es fraccionario",
            EqParams.SPECS.drop(13).first().kind is ParamKind.Fractional,
        )
    }
}

> Archivado desde CLAUDE.md — §5.1 "El canal/preset activo" y §5.2 "Tipo de efecto por slot de color"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

### 5.1 El canal/preset activo: `00 01 00 00` (✅ implementado y confirmado)

Investigación documental del 2026-09-03, implementada el mismo día como
`KatanaAddresses.ACTIVE_CHANNEL` / `KatanaRepository.channel`, un `KatanaEnumParameter` más de
9 valores — ver §4.3 y la nota de "generalizar `KatanaControl` a `byteWidth`" más abajo.

✅ **Confirmado contra el amplificador real en las dos direcciones** (2026-09-03): elegir un
canal en la app cambia el canal real del amplificador, en los 8 canales (1A–4A, 1B–4B) más
Panel; y cambiar de canal físicamente en el amplificador actualiza el selector de la app. Lo
que sigue documenta la investigación que llevó hasta ahí, con cita de archivo y línea.

**Un solo valor de 2 bytes, no un banco + un canal.** No existe dirección separada de banco
A/B: los ocho canales son valores consecutivos de un único campo en `00 01 00 00`.

| Valor SysEx | Canal físico | Program Change |
| --- | --- | --- |
| `00 00` | PANEL | 4 |
| `00 01`…`00 04` | BANK A, CH1–CH4 | 0, 1, 2, 3 |
| `00 05`…`00 08` | BANK B, CH1–CH4 | 5, 6, 7, 8 |

⚠️ **Las dos numeraciones no coinciden**: en SysEx el panel es el `00` y los canales van
`01`..`08` seguidos; en Program Change el panel es el **4**, en medio de los dos bancos. No se
puede reutilizar el mismo índice para las dos vías.

Evidencia, por fuente:

- [reference/TuxKatana/widgets/switcher.py:75-88](reference/TuxKatana/widgets/switcher.py) —
  al recibir `channel-changed` con `ch_num`, `ch_num <= 4` indexa los botones del banco A y el
  resto `ch_num - 5` los del banco B. Es la prueba de que un solo número recorre 1..8.
- [reference/TuxKatana/params/config.yaml:8-17](reference/TuxKatana/params/config.yaml) — los
  ocho valores (`00 01 7e` … `00 08 77`, o sea dato + checksum), y cada uno **comentado con su
  Program Change equivalente**: `CH_5` lleva `# "Program_Change:BANK_B:CH_1"`. De ahí sale la
  tabla de arriba.
- [reference/TuxKatana/params/midi.yaml:23-34](reference/TuxKatana/params/midi.yaml) — la tabla
  PC completa: BANK_A 0–3, PANEL 4, BANK_B 5–8. Confirmada tal cual.
- [reference/katana-midi-bridge/globals.py:14-15](reference/katana-midi-bridge/globals.py) —
  `CURRENT_PRESET_ADDR = (0x00,0x01,0x00,0x00)` con `CURRENT_PRESET_LEN = 0x02`.
- [reference/TuxKatana/HOW.md:83-85](reference/TuxKatana/HOW.md) — "Channel Change: send 3
  MIDIBytes at Address('00 01 00 00')", o sea 2 bytes de dato + checksum.
- [reference/FxFloorboard/midi.xml:943-951](reference/FxFloorboard/midi.xml) — fuente **de
  Mk2**: `LSB 01 "system 2" → LSB 00 "page 1" → PARAM 00 "patch number"`, con `desc="CH"`. Es
  decir, `00 01 00 00` se llama "patch number" también en el editor oficialoide.

**Es de 2 bytes, no de 1.** Tres fuentes independientes coinciden (`CURRENT_PRESET_LEN = 0x02`,
"3 MIDIBytes" de HOW.md, y el `00 0N` de config.yaml). Hasta esta implementación, los
controles de `device/` escribían y leían siempre **un** byte; `KatanaControl` se generalizó
con un `byteWidth: Int = 1` (por defecto 1, sin tocar los 24 controles existentes) en vez de
crear un tipo aparte solo para este caso — ver §4.3 y BACKLOG.md. ✅ **Confirmado**: el SET de
2 bytes funciona tal cual contra el amplificador real, así que la duda de si 1 byte también
habría bastado queda cerrada sin necesidad de probarlo — el checksum no desempataba —el byte
extra es `00` y no altera la suma— pero ya no hace falta, la implementación manda los 2.

**Se puede leer, por dos vías distintas.** No hacía falta adivinar el canal:

- **GET explícito.** `katana_sysex.txt:180-198` documenta "[Determine Current Preset]": QUERY a
  `00 01 00 00` con longitud `00 00 00 02` → respuesta `00 xx`. Es MK1, pero está en el bloque
  de direcciones de sistema que sí vale para el Mk2 (ver el aviso de abajo). Exige edit mode.
  Es la vía que usa el GET de respaldo de `KatanaRepository.loadFromDump`, porque el canal no
  vive en el dump — ver más abajo.
- **Reporte espontáneo.** ✅ **Confirmado**: cambiar de canal con los botones físicos del
  amplificador actualiza el selector de la app al instante, igual que con el resto de
  selectores. `reference/TuxKatana/lib/controller.py:95-98` documentaba esto mismo: cualquier
  mensaje entrante cuya dirección sea `00 01 00 00` se emite como `channel-changed`.

**El canal NO está en el dump.** El dump va de `60 00 00 00`; `00 01 00 00` es otra región.
Curiosamente TuxKatana **nunca hace el GET**: deduce el canal inicial comparando el nombre del
preset actual del dump contra la lista de los ocho nombres
([presets.py:59-62](reference/TuxKatana/widgets/presets.py) +
[memory.py:97-99](reference/TuxKatana/lib/memory.py)). No consta si es porque el GET les
falló o simplemente porque ya tenían el dump a mano.

⚠️ **`Adresses.txt:140-149` etiqueta este bloque como `MIDI_CHANNELS: # write` y eso es un
nombre equivocado, dos veces.** El canal MIDI de recepción es `00 02 00 00`
(`katana_sysex.txt:1984-1985`, y `midi.xml:953-971` con sus 16 valores); `00 01 00 00` es
"Preset Recall" (`katana_sysex.txt:1979-1980`). Y el `# write` describe lo que hace TuxKatana,
no lo que permite el amplificador: su propio `controller.py` recibe mensajes por esa dirección.
Es otro caso de lo ya visto con `60 00 06 51`: la anotación de una fuente no es un dato.

### 5.2 Tipo de efecto por slot de color (✅ confirmado en Booster)

Investigación documental del 2026-09-03, implementada y **confirmada el mismo día en Booster**.

✅ **De las dos candidatas, la que acepta la escritura es la de "tipo activo"** (la "baja":
`60 00 00 11`). Un SET ahí cambia el sonido, se corresponde con el color encendido en el
panel, y la sincronización va en las dos direcciones: cambiar el color o el tipo en el
amplificador actualiza la app, y al revés. Las direcciones `06 24`–`06 26` por color **no**
hicieron falta para escribir; se conservan registradas porque vienen en el dump y reportan,
así que dan gratis el contenido de los tres slots.

✅ **Mod, FX, Delay y Reverb usan la misma dirección "gemela" y están los cuatro confirmados
con audio** (2026-09-04, con Edit Mode activado): Mod `60 00 01 01`, FX `60 00 03 01`,
Delay 1 `60 00 05 01`, Reverb `60 00 05 41`. **Los cinco efectos siguen el mismo patrón**, así
que la analogía que se implementó a propósito sin darla por buena resultó correcta — esta vez;
la regla de §5 sigue en pie, porque la vez del reverb no lo fue.

**El color es un slot con su propio tipo, y hay una dirección por color.** No es una sola
dirección que cambie de significado: cada efecto tiene **tres** direcciones de tipo —una por
color— **más** una dirección de "tipo activo" que refleja el color seleccionado.

| Efecto | Tipo activo | Verde | Rojo | Amarillo | Color activo (ya implementado) |
| --- | --- | --- | --- | --- | --- |
| Booster | `60 00 00 11` | `60 00 06 24` | `06 25` | `06 26` | `60 00 06 39` |
| Mod | `60 00 01 01` | `60 00 06 27` | `06 28` | `06 29` | `60 00 06 3A` |
| FX | `60 00 03 01` | `60 00 06 2A` | `06 2B` | `06 2C` | `60 00 06 3B` |
| Delay 1 | `60 00 05 01` | `60 00 06 2D` | `06 2E` | `06 2F` | `60 00 06 3C` |
| Reverb | `60 00 05 41` | `60 00 06 30` | `06 31` | `06 32` | `60 00 06 3D` |
| Delay 2 | `60 00 05 21` | `60 00 06 33` | `06 34` | `06 35` | — |
| RV/DD2 layer | — | `60 00 06 36` | `06 37` | `06 38` | — |

El bloque `06 24`–`06 38` es contiguo y perfectamente regular: 3 colores × 7 familias.

**Dos fuentes de Mk2 coinciden** en las quince primeras:
[booster.yaml:11-13](reference/TuxKatana/params/booster.yaml) (`bo_type_G/R/Y`), y sus
equivalentes en `mod.yaml:4-6`, `fx.yaml:4-6`, `delay.yaml:25-27`, `reverb.yaml:4-6`; y
[midi.xml:43567-43959](reference/FxFloorboard/midi.xml), que nombra cada dirección con su
efecto y su color explícitos (`desc="Booster" customdesc="GREEN"`). **Delay 2 (`06 33`–`06 35`)
solo lo da `midi.xml`**: TuxKatana no implementa el segundo delay (su `reverb.yaml:38` lo deja
en `Unimplemented: # Delay_2 < !!!`). Las de modo RV/DD2 (`06 36`–`06 38`) son los
`re_mode_G/R/Y` de `reverb.yaml:9-11`.

**El "tipo activo" refleja el color, y hay evidencia observacional de ello.**
[Adresses.txt:72-74 y 85-87](reference/TuxKatana/doc/Adresses.txt) anota, para su propio
amplificador, qué valor toma la dirección de tipo activo con cada color:
`60 00 00 11: [0A|0B|0E]` (Boost verde/rojo/amarillo = Blues Drive / Over Drive / Distortion)
y `60 00 01 01 -> [1D|14|13]` (Mod = Chorus / Flanger / Phaser). Es decir: el autor cambió de
color y vio cambiar esa dirección. Lo que **ninguna fuente dice** es si escribir en el tipo
activo persiste en el slot del color, o si hay que escribir en `06 24`–`06 32`.

**Los parámetros internos se estructuran de dos maneras distintas**, y la diferencia importa
para el diseño:

- **"DSP simple" — Booster, Delay, Reverb: un único juego de parámetros fijo**, con el mismo
  significado para todos sus tipos. Cambiar de tipo cambia el sonido, no el mapa de memoria.
  Booster (`midi.xml:37109-37304`, y `booster.yaml:2-10`):

  | Dirección | Parámetro | Rango | Estado |
  | --- | --- | --- | --- |
  | `60 00 00 10` | On/Off | `00`/`01` | ✅ confirmado |
  | `60 00 00 11` | Type | 23 valores, ver abajo | ✅ confirmado |
  | `60 00 00 12` | Drive | `00`..`78` (0..120) | ✅ confirmado |
  | `60 00 00 13` | Bottom | `00/64` mostrado `-50..+50` | ✅ confirmado |
  | `60 00 00 14` | Tone | `00/64` mostrado `-50..+50` | ✅ confirmado |
  | `60 00 00 15` | Solo Sw | `00`/`01` | ✅ confirmado |
  | `60 00 00 16` | Solo Level | `00/64` = 0..100 | ✅ confirmado |
  | `60 00 00 17` | Effect Level | `00/64` = 0..100 | ✅ confirmado |
  | `60 00 00 18` | Direct Mix | `00/64` = 0..100 | ✅ confirmado |
  | `60 00 00 19`–`1E` | Custom Type + Bottom/Top/Low/High/Character | pedal "custom" | ❌ sin implementar (a propósito) |

  ✅ **Confirmados con audio el 2026-09-04** (con Edit Mode activado):
  `KatanaAddresses.BOOST_DRIVE`/`BOOST_BOTTOM`/`BOOST_TONE`/`BOOST_SOLO_ENABLED`/
  `BOOST_SOLO_LEVEL`/`BOOST_EFFECT_LEVEL`/`BOOST_DIRECT_MIX`, todas con dos fuentes de Mk2 de
  acuerdo (`booster.yaml:4-9` y `midi.xml:37109-37304`).
  `60 00 00 12` estaba antes documentada como `BOOST_LEVEL_LOW`, "alternativa baja de
  [BOOST_LEVEL], sin probar" — con el bloque interno completo entendido, no era una
  alternativa a la perilla del panel, sino este mismo parámetro de Drive, y **la prueba de
  audio lo confirma**. Igual queda confirmada la escala centrada `-50..+50` de Bottom/Tone,
  que se modeló con `LevelScale.centered(50)` a partir de `midi.xml` y nada más.

  **Bottom y Tone usan una escala centrada que resultó no necesitar tocar `LevelScale`**: el
  `rawOffset` que ya existía para las escalas `direct`/`offThenOneBased` es exactamente lo que
  hace falta para `raw = display + 50` — solo hizo falta una tercera factoría,
  `LevelScale.centered(radius)`, sin cambiar la clase.

  Custom Type y sus cinco parámetros (`60 00 00 1A`–`1E`) quedan **sin implementar a
  propósito**: es el modo "pedal custom", con su propio sub-catálogo, y menos prioritario.

- **"DSP complejo" — Mod y FX: cada tipo tiene su propio bloque contiguo de direcciones.**
  `midi.xml:37920-40155` los enumera: tras la cabecera común (`01 00` On/Off, `01 01` Type),
  vienen Touch Wah (`01 02`–`01 08`), Auto Wah (`01 09`–`01 0F`), Pedal Wah (`01 10`–`01 15`),
  Compressor (`01 16`–`01 1A`), Limiter (`01 1B`–`01 20`), Graphic EQ (`01 21`…), etc. El
  bloque **desborda** `60 00 01 xx` y sigue en `60 00 02 xx` (por eso `midi.xml` etiqueta las
  dos LSB como "FX1"); FX hace lo mismo en `60 00 03 xx`–`60 00 04 xx`.

Esa división no es una interpretación propia: `katana-midi-bridge` la tiene explícita en
[color_assign.json](reference/katana-midi-bridge/parameters/color_assign.json), con
`"dspSimple": ["boost","delay","reverb"]` y `"dspComplex": ["mod","fx"]`, y sus dos ficheros
`simple_dsp.json` / `complex_dsp.json` reflejan las dos formas: el primero da un juego plano
de parámetros por efecto, el segundo un `baseAddr` **por tipo**.

⚠️ **Ese JSON es del MK1 y sus direcciones no valen** (mismo aviso de siempre), pero **la
estructura sí transfiere y eso está triplemente confirmado**: `simple_dsp.json` describe el
booster con `length: 9` y los offsets `onOff, select, drive, bottom, tone, solo, …`, con
`bottom` y `tone` como `centeredByteRange` de `-50..+50` — exactamente los 9 parámetros y los
mismos rangos centrados que `midi.xml` da para el Mk2, solo que en `60 00 00 30` en vez de
`60 00 00 10`. Los desplazamientos entre mapas **no son uniformes** (booster `-0x20`, mod
`-0x40`), así que la estructura se copia y las direcciones no.

**Catálogo de tipos de Booster — 23 valores, dos fuentes de Mk2 de acuerdo byte a byte**
([booster.yaml:20-43](reference/TuxKatana/params/booster.yaml) y
[midi.xml:43568-43590](reference/FxFloorboard/midi.xml)):

`00` Mid Boost · `01` Clean Boost · `02` Treble Boost · `03` Crunch OD · `04` Natural OD ·
`05` Warm OD · `06` Fat DS · `08` Metal DS · `09` Oct Fuzz · `0A` Blues Drive · `0B` Over
Drive · `0C` TubeScreamer · `0D` Turbo OD · `0E` Distortion · `0F` Rat · `10` GuV DS ·
`11` DST+ · `12` Metal Zone · `13` '60s Fuzz · `14` Muff Fuzz · `15` HM-2 · `16` Metal Core ·
`17` Centa OD

⚠️ **El `07` no existe**, y falta en las **dos** fuentes — no es un error de transcripción.
Es el mismo caso que el `0x19` de `AmpType`: un selector con huecos, que es justo lo que
`KatanaEnumParameter` está hecho para rechazar en vez de adivinar.

Los otros catálogos, también con dos fuentes de acuerdo: **Delay** 11 tipos `00`..`0A`
(`delay.yaml:36-47`, `midi.xml:43841-43851`; única discrepancia, cosmética: el `08` es "Tap
Echo" en uno y "Tape" en el otro); **Reverb** 7 tipos `00`..`06` (`reverb.yaml:24-31`,
`midi.xml:43880-43886`); **Mod y FX** comparten el mismo catálogo de 30 tipos con huecos
(`mod.yaml:11-42` = `fx.yaml:11-42`); **RV/DD2 layer** 3 modos `00` Delay / `01` Dly+Rev /
`02` Reverb (`midi.xml:43946-43949`, `reverb.yaml:33-36`).

**Es por preset, y ya lo estamos leyendo sin saberlo.** Todo el bloque vive en
`60 00 xx xx`, el mismo "bloque efectivo" del dump (§4.4). La aritmética lo confirma: el trozo
que empieza en `60 00 05 53` y del que ya sacamos las perillas `06 50`–`06 5B` (offsets
125–136) cubre también `06 24`–`06 38` (offsets 81–101). O sea que **el dump que la app ya
pide contiene los tipos por color de los cinco efectos**; solo falta interpretarlos.

**Lo que las fuentes no responden:**

1. ✅ **Resuelto: se escribe en el tipo activo, no en el slot de color.** Era la duda
   principal y ninguna fuente la documentaba. El precedente que obligaba a probarlo —`06 5C`,
   del mismo bloque `06 xx`, que resultó de solo lectura— no se repitió: aquí la que manda es
   la "baja", al revés que en la variación. **Dos direcciones plausibles, y solo el hardware
   dice cuál**: es el mismo patrón que con el reverb y con Gain.
2. ✅ **Resuelto: extraído entero** (2026-09-04). Eran 237 direcciones por efecto, no ~256.
   Sigue siendo cierto que **solo hay una fuente de Mk2** —`mod.yaml` y `fx.yaml` no listan
   ningún parámetro interno—, así que todo lo de abajo sale de `midi.xml` y de nada más. Ver
   el apartado siguiente.
3. Si cambiar el tipo de un color **resetea** los parámetros de ese slot a los de fábrica del
   nuevo tipo, o los conserva.


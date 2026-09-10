> Archivado desde CLAUDE.md — §5.2 "El mapa de parámetros internos de Mod y FX" (extracción documental completa)
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

#### El mapa de parámetros internos de Mod y FX (extracción documental, 2026-09-04 / 2026-09-05)

Extracción completa desde `midi.xml`, **sin implementar nada todavía**: el objetivo es que
cablear cada tipo después sea mecánico en vez de volver a leer 2.300 líneas de XML cada vez.
Nada de esto está probado contra el amplificador.

**Hecho en dos tandas.** El 2026-09-04 se fijó el método, el índice de los 31 bloques y la
regla FX = Mod + `0x0200`, y se extrajeron cuatro tipos como muestra. El **2026-09-05** se
completaron los 27 restantes: ahora los 31 están abajo parámetro a parámetro, en
"Extracción completa de los 31 tipos", con su catálogo de selectores. Esa segunda tanda
**corrigió dos afirmaciones** de la primera — ver la lista de anomalías, puntos 1 y 2.

**Cómo se extrae (el truco que lo vuelve mecánico).** Cada `<DATA>` del bloque lleva un
atributo `desc` con un **prefijo por tipo**, y los nodos de un mismo tipo son consecutivos:

```
39763: <DATA value="03" name="MOD" desc="MOD PH:"  customdesc="Type">
39792: <DATA value="0B" name="MOD" desc="MOD FL:"  customdesc="Rate">
```

Así que agrupar por `desc` da los bloques ya delimitados, sin adivinar dónde acaba uno y
empieza el siguiente. El `customdesc` es el nombre del parámetro y el `<PARAM>` hijo su rango.

**Regla FX = Mod con el tercer byte +2.** No hace falta extraer FX por separado:

> `dirección FX = dirección Mod + 0x0200` — es decir, `60 00 01 xx`→`60 00 03 xx` y
> `60 00 02 xx`→`60 00 04 xx`.

✅ **Verificado mecánicamente sobre los 237 nodos de cada bloque**, no inferido de unas
muestras: comparando `(LSB relativa, 4.º byte, tipo, nombre del parámetro)` los dos bloques
salen **idénticos**, con 14 diferencias que son **solo de etiqueta** y ninguna de dirección
(ver las dos rarezas de la fuente al final). Mod ocupa `midi.xml:37918-40155`, FX
`midi.xml:40156-42393`.

##### Índice de los 31 bloques

En el **mismo orden que el catálogo de `ModFxType`** — comprobado entrada por entrada contra
`ModFxType.kt`, no supuesto. Tras la cabecera común (`01 00` On/Off, `01 01` Type):

| Tipo (`ModFxType`) | Bloque Mod | Bloque FX | Params | `desc` |
| --- | --- | --- | --- | --- |
| Touch Wah `00` | `60 00 01 02`–`01 08` | `60 00 03 02`–`03 08` | 7 | `MOD TW` |
| Auto Wah `01` | `60 00 01 09`–`01 0F` | `60 00 03 09`–`03 0F` | 7 | `MOD AW` |
| Pedal Wah `02` | `60 00 01 10`–`01 15` | `60 00 03 10`–`03 15` | 6 | `MOD SWAH` |
| Compressor `03` | `60 00 01 16`–`01 1A` | `60 00 03 16`–`03 1A` | 5 | `MOD ACS` |
| Limiter `04` | `60 00 01 1B`–`01 20` | `60 00 03 1B`–`03 20` | 6 | `MOD LM` |
| Graphic EQ `06` | `60 00 01 21`–`01 2B` | `60 00 03 21`–`03 2B` | 11 | `MOD GEQ` |
| Parametric EQ `07` | `60 00 01 2C`–`01 36` | `60 00 03 2C`–`03 36` | 11 | `MOD PEQ` |
| Guitar Sim `09` | `60 00 01 37`–`01 3B` | `60 00 03 37`–`03 3B` | 5 | `MOD GS` |
| Slow Gear `0A` | `60 00 01 3C`–`01 3E` | `60 00 03 3C`–`03 3E` | 3 | `MOD SG` |
| Wave Synth `0C` | `60 00 01 3F`–`01 46` | `60 00 03 3F`–`03 46` | 8 | `MOD WSY` |
| Octave `0E` | `60 00 01 47`–`01 49` | `60 00 03 47`–`03 49` | 3 | `MOD OC` |
| ⚠️ Pitch Shifter `0F` | `60 00 01 4A`–`01 58` | `60 00 03 4A`–`03 58` | 13 (15 dir.) | `MOD PS` |
| ⚠️ Harmonist `10` | `60 00 01 59`–`01 7B` | `60 00 03 59`–`03 7B` | 9 (11 dir.) + 24 escala | `MOD HR` |
| ⚠️ Acu Processor `12` | `60 00 01 7C`–`02 02` | `60 00 03 7C`–`04 02` | 7 | `MOD AC` |
| Phaser `13` | `60 00 02 03`–`02 0A` | `60 00 04 03`–`04 0A` | 8 | `MOD PH` |
| Flanger `14` | `60 00 02 0B`–`02 12` | `60 00 04 0B`–`04 12` | 8 | `MOD FL` |
| Tremolo `15` | `60 00 02 13`–`02 16` | `60 00 04 13`–`04 16` | 4 | `MOD TR` |
| Rotary `16` | `60 00 02 17`–`02 1D` | `60 00 04 17`–`04 1D` | 7 | `MOD RT` |
| Uni-V `17` | `60 00 02 1E`–`02 20` | `60 00 04 1E`–`04 20` | 3 | `MOD UV` |
| Slicer `19` | `60 00 02 21`–`02 25` | `60 00 04 21`–`04 25` | 5 | `MOD SL` |
| Vibrato `1A` | `60 00 02 26`–`02 2A` | `60 00 04 26`–`04 2A` | 5 | `MOD VB` |
| Ring Modulate `1B` | `60 00 02 2B`–`02 2E` | `60 00 04 2B`–`04 2E` | 4 | `MOD RM` |
| Humanizer `1C` | `60 00 02 2F`–`02 36` | `60 00 04 2F`–`04 36` | 8 | `MOD HU` |
| ⚠️ 2x2 Chorus `1D` | `60 00 02 37`–`02 40` | `60 00 04 37`–`04 40` | 10 | `MOD 2CE` |
| AC Guitar Sim `1F` | `60 00 02 41`–`02 45` | `60 00 04 41`–`04 45` | 5 | `MOD ACS`/`AGS` |
| Phaser 90E `23` | `60 00 02 46`–`02 47` | `60 00 04 46`–`04 47` | 2 | `MOD PH90` |
| Flanger 117E `24` | `60 00 02 48`–`02 4B` | `60 00 04 48`–`04 4B` | 4 | `MOD FL117` |
| Wah 95E `25` | `60 00 02 4C`–`02 50` | `60 00 04 4C`–`04 50` | 5 | `MOD WAH95` |
| ⚠️ DC30 `26` | `60 00 02 51`–`02 59` | `60 00 04 51`–`04 59` | 8 (9 dir.) | `MOD DC30` |
| Heavy Octave `27` | `60 00 02 5A`–`02 5C` | `60 00 04 5A`–`04 5C` | 3 | `MOD HOC` |
| Pedal Bend `28` | `60 00 02 5D`–`02 60` | `60 00 04 5D`–`04 60` | 4 | `MOD PBEND` |

Las ⚠️ marcan los que **no encajan en el patrón "DSP simple"**; se detallan abajo. Los otros
25 son un juego plano de parámetros de un byte, exactamente como el Booster.

⚠️ **"Params" cuenta parámetros, no direcciones, y en tres tipos no coinciden**: Pitch
Shifter, Harmonist y DC30 tienen parámetros de 2 bytes, así que ocupan más direcciones que
parámetros (por eso el "(N dir.)"). **DC30 lleva ⚠️ desde el 2026-09-05**: la primera tanda
lo marcó solo por sus nombres repetidos, pero su `Repeat Rate` también es de 2 bytes — punto
2 de las anomalías.

##### Extracción completa de los 31 tipos (2026-09-05)

Los 31 bloques, parámetro a parámetro, con dirección, rango, escala y línea de `midi.xml`.
Sustituye a la muestra de cuatro tipos que había aquí antes (Tremolo, Phaser, Flanger,
2x2 Chorus, que siguen abajo en su sitio del catálogo): el objetivo declarado era que cablear
un tipo fuese mecánico **sin volver a abrir el XML**, y con solo cuatro extraídos no lo era.
**Nada de esto está probado contra el amplificador.**

**Método**, el mismo de la primera tanda, aplicado a los 27 restantes: agrupar los `<DATA>` por
su atributo `desc` (que lleva el prefijo por tipo), leer el `customdesc` como nombre del
parámetro y el `<PARAM>` hijo como rango. Los bloques de un tipo son consecutivos, así que el
agrupado los delimita solo.

✅ **La regla FX = Mod + `0x0200` se volvió a verificar, y esta vez el resultado es más fuerte
de lo que decía la nota anterior.** Comparando los 237 nodos de cada bloque por
`(LSB relativa, 4.º byte, profundidad)` contra `(desc, customdesc, lista de PARAM)`:

| Comprobación | Resultado |
| --- | --- |
| Nº de nodos | 237 vs 237 |
| Diferencias de **dirección** | **0** |
| Diferencias de **nombre de parámetro** (`customdesc`) | **0** |
| Diferencias de **rango / lista de valores** (`PARAM`) | **0** |
| Diferencias de etiqueta de bloque (`desc`) | 205, de las cuales 191 son el prefijo `MOD `→`FX ` |

Las 14 diferencias que no son el prefijo son las dos rarezas ya documentadas al final de esta
sección (`ACS:`/`AGS:`, 5 nodos; y los `Pre Delay` anidados mal etiquetados en FX, 9 nodos).
El recuento de 14 coincide exactamente con el de la extracción de 2026-09-04, obtenido por
separado — **dos extracciones independientes dan el mismo número**, que es la única razón por
la que la regla se puede usar sin volver a comprobarla tipo por tipo.

**Cómo leer las tablas.** «Rango» está en la notación de `midi.xml` traducida: crudo es lo que
viaja por SysEx, mostrado es lo que ve el usuario. «Escala» dice qué clase de
`dev.alonx3.ktnacontrol.protocol.LevelScale` corresponde, o `enum N` si es un selector
(`KatanaEnumParameter`, con la lista completa de valores en el catálogo de más abajo). Para la
dirección de FX, sumar `0x0200` a la de Mod. La columna `midi.xml` es la línea del `<DATA>`.

⚠️ **Todas estas direcciones solo significan lo que dice la tabla mientras el tipo activo del
slot sea el de la tabla.** Mod y FX son "DSP complejo" (§5.2): cada tipo reutiliza el mismo
espacio de direcciones. Es la misma salvedad que ya trae el Pre Delay de 2x2 Chorus, el único
parámetro interno de Mod cableado hasta ahora — con cualquier otro tipo activo, `60 00 02 3A`
no es un Pre Delay, es otra cosa.

###### Touch Wah — `ModFxType.TOUCH_WAH` (`0x00`) — `MOD TW:` — 7 params, 7 direcciones

Mod `60 00 01 02`–`01 08` · FX `60 00 03 02`–`03 08` · midi.xml 37957-37977

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 02` | Mode | `00` LPF · `01` BPF | enum 2 | 37957 |
| `60 00 01 03` | Polarity | `00` Down · `01` Up | enum 2 | 37961 |
| `60 00 01 04` | Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37965 |
| `60 00 01 05` | Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37968 |
| `60 00 01 06` | Peak | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37971 |
| `60 00 01 07` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37974 |
| `60 00 01 08` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37977 |

###### Auto Wah — `ModFxType.AUTO_WAH` (`0x01`) — `MOD AW:` — 7 params, 7 direcciones

Mod `60 00 01 09`–`01 0F` · FX `60 00 03 09`–`03 0F` · midi.xml 37980-37999

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 09` | Mode | `00` LPF · `01` BPF | enum 2 | 37980 |
| `60 00 01 0A` | Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37984 |
| `60 00 01 0B` | Peak | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37987 |
| `60 00 01 0C` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37990 |
| `60 00 01 0D` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37993 |
| `60 00 01 0E` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37996 |
| `60 00 01 0F` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 37999 |

###### Pedal Wah — `ModFxType.PEDAL_WAH` (`0x02`) — `MOD SWAH:` — 6 params, 6 direcciones

Mod `60 00 01 10`–`01 15` · FX `60 00 03 10`–`03 15` · midi.xml 38002-38022

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 10` | Type | `00` CRY WAH … `05` Reso WAH | enum 6 | 38002 |
| `60 00 01 11` | Pedal Pos | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38010 |
| `60 00 01 12` | Pedal Min | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38013 |
| `60 00 01 13` | Pedal Max | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38016 |
| `60 00 01 14` | Effect Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38019 |
| `60 00 01 15` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38022 |

###### Compressor — `ModFxType.COMPRESSOR` (`0x03`) — `MOD ACS:` — 5 params, 5 direcciones

Mod `60 00 01 16`–`01 1A` · FX `60 00 03 16`–`03 1A` · midi.xml 38025-38043

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 16` | Type | `00` BOSS Comp … `06` Mild | enum 7 | 38025 |
| `60 00 01 17` | Sustain | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38034 |
| `60 00 01 18` | Attack | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38037 |
| `60 00 01 19` | Tone | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38040 |
| `60 00 01 1A` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38043 |

###### Limiter — `ModFxType.LIMITER` (`0x04`) — `MOD LM:` — 6 params, 6 direcciones

Mod `60 00 01 1B`–`01 20` · FX `60 00 03 1B`–`03 20` · midi.xml 38046-38080

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 1B` | Type | `00` BOSS Limiter · `01` Rack 160D · `02` Vtg Rack U | enum 3 | 38046 |
| `60 00 01 1C` | Attack | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38051 |
| `60 00 01 1D` | Thresh | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38054 |
| `60 00 01 1E` | Ratio | `00` 1:1 … `11` oo:1 | enum 18 | 38057 |
| `60 00 01 1F` | Release | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38077 |
| `60 00 01 20` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38080 |

###### Graphic EQ — `ModFxType.GRAPHIC_EQ` (`0x06`) — `MOD GEQ:` — 11 params, 11 direcciones

Mod `60 00 01 21`–`01 2B` · FX `60 00 03 21`–`03 2B` · midi.xml 38083-38113

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 21` | 31Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38083 |
| `60 00 01 22` | 62Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38086 |
| `60 00 01 23` | 125Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38089 |
| `60 00 01 24` | 250Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38092 |
| `60 00 01 25` | 500Hz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38095 |
| `60 00 01 26` | 1KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38098 |
| `60 00 01 27` | 2KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38101 |
| `60 00 01 28` | 4KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38104 |
| `60 00 01 29` | 8KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38107 |
| `60 00 01 2A` | 16KHz | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38110 |
| `60 00 01 2B` | Level | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38113 |

###### Parametric EQ — `ModFxType.PARAMETRIC_EQ` (`0x07`) — `MOD PEQ:` — 11 params, 11 direcciones

Mod `60 00 01 2C`–`01 36` · FX `60 00 03 2C`–`03 36` · midi.xml 38116-38241

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 2C` | Lo Cut Off | `00` FLAT … `11` 800Hz | enum 18 | 38116 |
| `60 00 01 2D` | Lo Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38136 |
| `60 00 01 2E` | Lo Mid Freq | `00` 20.0Hz … `1B` 10.0k | enum 28 | 38139 |
| `60 00 01 2F` | Lo Mid Q | `00` 0.5 … `05` 16 | enum 6 | 38169 |
| `60 00 01 30` | Lo Mid Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38177 |
| `60 00 01 31` | Hi Mid Freq | `00` 20.0Hz … `1B` 10.0k | enum 28 | 38180 |
| `60 00 01 32` | Hi Mid Q | `00` 0.5 … `05` 16 | enum 6 | 38210 |
| `60 00 01 33` | Hi Mid Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38218 |
| `60 00 01 34` | Hi Gain | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38221 |
| `60 00 01 35` | Hi Cut Off | `00` 630Hz … `0E` FLAT | enum 15 | 38224 |
| `60 00 01 36` | Level | crudo `00`..`28` = `-20`..`+20` dB | `LevelScale.centered(20)` | 38241 |

###### Guitar Sim — `ModFxType.GUITAR_SIM` (`0x09`) — `MOD GS:` — 5 params, 5 direcciones

Mod `60 00 01 37`–`01 3B` · FX `60 00 03 37`–`03 3B` · midi.xml 38244-38263

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 37` | Type | `00` S->H … `07` P->AC | enum 8 | 38244 |
| `60 00 01 38` | Low | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38254 |
| `60 00 01 39` | High | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38257 |
| `60 00 01 3A` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38260 |
| `60 00 01 3B` | Body | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38263 |

###### Slow Gear — `ModFxType.SLOW_GEAR` (`0x0A`) — `MOD SG:` — 3 params, 3 direcciones

Mod `60 00 01 3C`–`01 3E` · FX `60 00 03 3C`–`03 3E` · midi.xml 38266-38272

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 3C` | Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38266 |
| `60 00 01 3D` | Rise Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38269 |
| `60 00 01 3E` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38272 |

###### Wave Synth — `ModFxType.WAVE_SYNTH` (`0x0C`) — `MOD WSY:` — 8 params, 8 direcciones

Mod `60 00 01 3F`–`01 46` · FX `60 00 03 3F`–`03 46` · midi.xml 38275-38297

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 3F` | Wave | `00` SAW · `01` SQUARE | enum 2 | 38275 |
| `60 00 01 40` | Cutoff Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38279 |
| `60 00 01 41` | Reson. | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38282 |
| `60 00 01 42` | FLT.Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38285 |
| `60 00 01 43` | FLT.Decay | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38288 |
| `60 00 01 44` | FLT.Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38291 |
| `60 00 01 45` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38294 |
| `60 00 01 46` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38297 |

###### Octave — `ModFxType.OCTAVE` (`0x0E`) — `MOD OC:` — 3 params, 3 direcciones

Mod `60 00 01 47`–`01 49` · FX `60 00 03 47`–`03 49` · midi.xml 38300-38309

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 47` | Range | `00` 1 · `01` 2 · `02` 3 · `03` 4 | enum 4 | 38300 |
| `60 00 01 48` | Octave | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38306 |
| `60 00 01 49` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38309 |

###### Pitch Shifter — `ModFxType.PITCH_SHIFTER` (`0x0F`) — `MOD PS:` — 13 params, 15 direcciones

Mod `60 00 01 4A`–`01 58` · FX `60 00 03 4A`–`03 58` · midi.xml 38312-38375

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 4A` | Voice | `00` 1-Voice · `01` 2-Mono | enum 2 | 38312 |
| `60 00 01 4B` | Mode | `00` Fast · `01` Medium · `02` Slow · `03` Mono | enum 4 | 38316 |
| `60 00 01 4C` | Pitch | crudo `00`..`30` = `-24`..`+24` | `LevelScale.centered(24)` | 38322 |
| `60 00 01 4D` | Fine | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38325 |
| `60 00 01 4E`+`4F` | Pre Delay (Voice 1) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38328-38337 |
| `60 00 01 50` | Voice 1 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38341 |
| `60 00 01 51` | Mode | `00` Fast · `01` Medium · `02` Slow · `03` Mono | enum 4 | 38344 |
| `60 00 01 52` | Pitch | crudo `00`..`30` = `-24`..`+24` | `LevelScale.centered(24)` | 38350 |
| `60 00 01 53` | Fine | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 38353 |
| `60 00 01 54`+`55` | Pre Delay (Voice 2) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38356-38365 |
| `60 00 01 56` | Voice 2 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38369 |
| `60 00 01 57` | Feedback | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38372 |
| `60 00 01 58` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38375 |


###### Harmonist — `ModFxType.HARMONIST` (`0x10`) — `MOD HR:` — 9 params, 11 direcciones

Mod `60 00 01 59`–`01 63` · FX `60 00 03 59`–`03 63` · midi.xml 38378-38481

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 59` | Voice | `00` 1-Voice · `01` 2-Voice | enum 2 | 38378 |
| `60 00 01 5A` | Harmony | `00` -2oct … `1D` User | enum 30 | 38382 |
| `60 00 01 5B`+`5C` | Pre Delay (Voice 1) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38414-38423 |
| `60 00 01 5D` | Voice 1 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38427 |
| `60 00 01 5E` | Harmony | `00` -2oct … `1D` User | enum 30 | 38430 |
| `60 00 01 5F`+`60` | Pre Delay (Voice 2) | crudo 2 bytes = `0`..`300` ms | `direct(0..300)`, **`byteWidth = 2`** | 38462-38471 |
| `60 00 01 61` | Voice 2 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38475 |
| `60 00 01 62` | FeedBack | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38478 |
| `60 00 01 63` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 38481 |


###### Acu Processor — `ModFxType.ACU_PROCESSOR` (`0x12`) — `MOD AC:` — 7 params, 7 direcciones

Mod `60 00 01 7C`–`02 02` · FX `60 00 03 7C`–`04 02` · midi.xml 39708-39760

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 01 7C` | Type | `00` Small · `01` Medium · `02` Bright · `03` Power | enum 4 | 39708 |
| `60 00 01 7D` | Bass | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39714 |
| `60 00 01 7E` | Middle | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39717 |
| `60 00 01 7F` | Mid.Freq | `00` 20.0Hz … `1B` 10.0kHz | enum 28 | 39720 |
| `60 00 02 00` | Treble | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39754 |
| `60 00 02 01` | Presence | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 39757 |
| `60 00 02 02` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39760 |

###### Phaser — `ModFxType.PHASER` (`0x13`) — `MOD PH:` — 8 params, 8 direcciones

Mod `60 00 02 03`–`02 0A` · FX `60 00 04 03`–`04 0A` · midi.xml 39763-39789

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 03` | Type | `00` 4stage · `01` 8stage · `02` 12stage · `03` Bi-Phase | enum 4 | 39763 |
| `60 00 02 04` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39769 |
| `60 00 02 05` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39772 |
| `60 00 02 06` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39775 |
| `60 00 02 07` | Reson. | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39778 |
| `60 00 02 08` | Step Rate | `00` = Off, luego crudo `01`..`65` = `00`..`100` | `LevelScale.offThenOneBased()` | 39781 |
| `60 00 02 09` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39786 |
| `60 00 02 0A` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39789 |

###### Flanger — `ModFxType.FLANGER` (`0x14`) — `MOD FL:` — 8 params, 8 direcciones

Mod `60 00 02 0B`–`02 12` · FX `60 00 04 0B`–`04 12` · midi.xml 39792-39823

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 0B` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39792 |
| `60 00 02 0C` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39795 |
| `60 00 02 0D` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39798 |
| `60 00 02 0E` | Reson. | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39801 |
| `60 00 02 0F` | Separ | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39804 |
| `60 00 02 10` | Low Cut | `00` FLAT … `0A` 800Hz | enum 11 | 39807 |
| `60 00 02 11` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39820 |
| `60 00 02 12` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39823 |

###### Tremolo — `ModFxType.TREMOLO` (`0x15`) — `MOD TR:` — 4 params, 4 direcciones

Mod `60 00 02 13`–`02 16` · FX `60 00 04 13`–`04 16` · midi.xml 39826-39835

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 13` | Shape | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39826 |
| `60 00 02 14` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39829 |
| `60 00 02 15` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39832 |
| `60 00 02 16` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39835 |

###### Rotary — `ModFxType.ROTARY` (`0x16`) — `MOD RT:` — 7 params, 7 direcciones

Mod `60 00 02 17`–`02 1D` · FX `60 00 04 17`–`04 1D` · midi.xml 39838-39857

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 17` | Speed | `00` Slow · `01` Fast | enum 2 | 39838 |
| `60 00 02 18` | Rate (Slow) | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39842 |
| `60 00 02 19` | Rate (Fast) | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39845 |
| `60 00 02 1A` | Rise Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39848 |
| `60 00 02 1B` | Fall Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39851 |
| `60 00 02 1C` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39854 |
| `60 00 02 1D` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39857 |

###### Uni-V — `ModFxType.UNI_V` (`0x17`) — `MOD UV:` — 3 params, 3 direcciones

Mod `60 00 02 1E`–`02 20` · FX `60 00 04 1E`–`04 20` · midi.xml 39860-39866

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 1E` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39860 |
| `60 00 02 1F` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39863 |
| `60 00 02 20` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39866 |

###### Slicer — `ModFxType.SLICER` (`0x19`) — `MOD SL:` — 5 params, 5 direcciones

Mod `60 00 02 21`–`02 25` · FX `60 00 04 21`–`04 25` · midi.xml 39869-39900

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 21` | Pattern | `00` P1 … `13` P20 | enum 20 | 39869 |
| `60 00 02 22` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39891 |
| `60 00 02 23` | Trig.Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39894 |
| `60 00 02 24` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39897 |
| `60 00 02 25` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39900 |

###### Vibrato — `ModFxType.VIBRATO` (`0x1A`) — `MOD VB:` — 5 params, 5 direcciones

Mod `60 00 02 26`–`02 2A` · FX `60 00 04 26`–`04 2A` · midi.xml 39903-39916

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 26` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39903 |
| `60 00 02 27` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39906 |
| `60 00 02 28` | Off/On | `00` Off · `01` On | enum 2 | 39909 |
| `60 00 02 29` | Rise Time | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39913 |
| `60 00 02 2A` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39916 |

###### Ring Modulate — `ModFxType.RING_MODULATE` (`0x1B`) — `MOD RM:` — 4 params, 4 direcciones

Mod `60 00 02 2B`–`02 2E` · FX `60 00 04 2B`–`04 2E` · midi.xml 39919-39929

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 2B` | Mode | `00` Normal · `01` Intelligent | enum 2 | 39919 |
| `60 00 02 2C` | Freq | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39923 |
| `60 00 02 2D` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39926 |
| `60 00 02 2E` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39929 |

###### Humanizer — `ModFxType.HUMANIZER` (`0x1C`) — `MOD HU:` — 8 params, 8 direcciones

Mod `60 00 02 2F`–`02 36` · FX `60 00 04 2F`–`04 36` · midi.xml 39932-39962

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 2F` | Mode | `00` Picking · `01` Auto | enum 2 | 39932 |
| `60 00 02 30` | Vowel 1 | `00` A … `04` U | enum 5 | 39936 |
| `60 00 02 31` | Vowel 2 | `00` A … `04` U | enum 5 | 39943 |
| `60 00 02 32` | Sens | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39950 |
| `60 00 02 33` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39953 |
| `60 00 02 34` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39956 |
| `60 00 02 35` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39959 |
| `60 00 02 36` | Effect | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39962 |

###### 2x2 Chorus — `ModFxType.CHORUS` (`0x1D`) — `MOD 2CE:` — 10 params, 10 direcciones

Mod `60 00 02 37`–`02 40` · FX `60 00 04 37`–`04 40` · midi.xml 39965-40008

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 37` | Xover Freq | `00` 100Hz … `10` 4.00kHz | enum 17 | 39965 |
| `60 00 02 38` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39984 |
| `60 00 02 39` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39987 |
| `60 00 02 3A` | Pre Delay | crudo `00`..`50` = `0.0`..`40.0` ms | **fraccionaria** | 39990 |
| `60 00 02 3B` | Low | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39993 |
| `60 00 02 3C` | Rate | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39996 |
| `60 00 02 3D` | Depth | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 39999 |
| `60 00 02 3E` | Pre Delay | crudo `00`..`50` = `0.0`..`40.0` ms | **fraccionaria** | 40002 |
| `60 00 02 3F` | High | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40005 |
| `60 00 02 40` | Direct | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40008 |

###### AC Guitar Sim — `ModFxType.AC_GUITAR_SIM` (`0x1F`) — `MOD ACS:` — 5 params, 5 direcciones

Mod `60 00 02 41`–`02 45` · FX `60 00 04 41`–`04 45` · midi.xml 40011-40023

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 41` | Top | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 40011 |
| `60 00 02 42` | Body | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40014 |
| `60 00 02 43` | Low | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 40017 |
| `60 00 02 44` | High | crudo `00`..`64` = `-50`..`+50` | `LevelScale.centered(50)` | 40020 |
| `60 00 02 45` | Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40023 |

###### Phaser 90E — `ModFxType.PHASER_90E` (`0x23`) — `MOD PH90:` — 2 params, 2 direcciones

Mod `60 00 02 46`–`02 47` · FX `60 00 04 46`–`04 47` · midi.xml 40026-40030

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 46` | Script | `00` Off · `01` On | enum 2 | 40026 |
| `60 00 02 47` | Speed | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40030 |

###### Flanger 117E — `ModFxType.FLANGER_117E` (`0x24`) — `MOD FL117:` — 4 params, 4 direcciones

Mod `60 00 02 48`–`02 4B` · FX `60 00 04 48`–`04 4B` · midi.xml 40033-40042

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 48` | Manual | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40033 |
| `60 00 02 49` | Width | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40036 |
| `60 00 02 4A` | Speed | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40039 |
| `60 00 02 4B` | Regeneration | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40042 |

###### Wah 95E — `ModFxType.WAH_95E` (`0x25`) — `MOD WAH95:` — 5 params, 5 direcciones

Mod `60 00 02 4C`–`02 50` · FX `60 00 04 4C`–`04 50` · midi.xml 40045-40057

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 4C` | Pedal Pos | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40045 |
| `60 00 02 4D` | Pedal Min | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40048 |
| `60 00 02 4E` | Pedal Max | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40051 |
| `60 00 02 4F` | Effect Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40054 |
| `60 00 02 50` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40057 |

###### DC30 — `ModFxType.DC30` (`0x26`) — `MOD DC30:` — 8 params, 9 direcciones

Mod `60 00 02 51`–`02 59` · FX `60 00 04 51`–`04 59` · midi.xml 40060-40098

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 51` | Selector | `00` Chorus · `01` Echo | enum 2 | 40060 |
| `60 00 02 52` | Input | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40064 |
| `60 00 02 53` | Intensity | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40067 |
| `60 00 02 54`+`55` | Repeat Rate | crudo 2 bytes = `40`..`600` rpm | `direct(40..600)`, **`byteWidth = 2`** | 40070-40085 |
| `60 00 02 56` | Intensity | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40089 |
| `60 00 02 57` | Volume | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40092 |
| `60 00 02 58` | Tone | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40095 |
| `60 00 02 59` | Output Select | `00` D+E · `01` D/E | enum 2 | 40098 |


###### Heavy Octave — `ModFxType.HEAVY_OCTAVE` (`0x27`) — `MOD HOC:` — 3 params, 3 direcciones

Mod `60 00 02 5A`–`02 5C` · FX `60 00 04 5A`–`04 5C` · midi.xml 40102-40108

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 5A` | Octave -1 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40102 |
| `60 00 02 5B` | Octave -2 | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40105 |
| `60 00 02 5C` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40108 |

###### Pedal Bend — `ModFxType.PEDAL_BEND` (`0x28`) — `MOD PBEND:` — 4 params, 4 direcciones

Mod `60 00 02 5D`–`02 60` · FX `60 00 04 5D`–`04 60` · midi.xml 40111-40120

| Dirección Mod | Parámetro | Rango | Escala | midi.xml |
| --- | --- | --- | --- | --- |
| `60 00 02 5D` | Pitch | crudo `00`..`30` = `-24`..`+24` | `LevelScale.centered(24)` | 40111 |
| `60 00 02 5E` | Pedal Posn | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40114 |
| `60 00 02 5F` | Effect Level | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40117 |
| `60 00 02 60` | Direct Mix | crudo `00`..`64` = `00`..`100` | `LevelScale.direct(0..100)` | 40120 |

##### Catálogo de valores de los selectores internos

Todos los `enum N` de las tablas de arriba, con su lista completa, para no tener que volver al
XML. Las direcciones son las de **Mod**; para FX, `+0x0200`.

✅ **Los 36 selectores internos corren sin huecos**, comprobado por programa sobre la lista de
valores de cada uno. Es una diferencia real con los catálogos de *tipo* del proyecto —
`ModFxType` (10 huecos), `BoostType` (`07`) y `AmpType` (`19`) —: ahí `KatanaEnumParameter`
rechaza valores ilegales porque los hay; aquí un rango contiguo `0..N-1` describe el selector
entero. Aun así conviene registrarlos igual como `KatanaEnumParameter`, no como niveles: lo que
muestran es texto (`FLAT`, `1.60k`, `oo:1`), no un número en una escala.

- **`60 00 01 02` MOD TW: — Mode** (2 valores, L37957): `00` LPF, `01` BPF
- **`60 00 01 03` MOD TW: — Polarity** (2 valores, L37961): `00` Down, `01` Up
- **`60 00 01 09` MOD AW: — Mode** (2 valores, L37980): `00` LPF, `01` BPF
- **`60 00 01 10` MOD SWAH: — Type** (6 valores, L38002): `00` CRY WAH, `01` VO WAH, `02` Fat WAH, `03` Light WAH, `04` 7string WAH, `05` Reso WAH
- **`60 00 01 16` MOD ACS: — Type** (7 valores, L38025): `00` BOSS Comp, `01` Hi-BAND, `02` Light, `03` D-Comp, `04` Orange, `05` Fat, `06` Mild
- **`60 00 01 1B` MOD LM: — Type** (3 valores, L38046): `00` BOSS Limiter, `01` Rack 160D, `02` Vtg Rack U
- **`60 00 01 1E` MOD LM: — Ratio** (18 valores, L38057): `00` 1:1, `01` 1.2:1, `02` 1.4:1, `03` 1.6:1, `04` 1.8:1, `05` 2:1, `06` 2.3:1, `07` 2.6:1, `08` 3:1, `09` 3.5:1, `0A` 4:1, `0B` 5:1, `0C` 6:1, `0D` 8:1, `0E` 10:1, `0F` 12:1, `10` 20:1, `11` oo:1
- **`60 00 01 2C` MOD PEQ: — Lo Cut Off** (18 valores, L38116): `00` FLAT, `01` 20.0Hz, `02` 25.0Hz, `03` 31.5Hz, `04` 40.0Hz, `05` 50.0Hz, `06` 63.0Hz, `07` 80.0Hz, `08` 100Hz, `09` 125Hz, `0A` 160Hz, `0B` 200Hz, `0C` 250Hz, `0D` 315Hz, `0E` 400Hz, `0F` 500Hz, `10` 630Hz, `11` 800Hz
- **`60 00 01 2E` MOD PEQ: — Lo Mid Freq** (28 valores, L38139): `00` 20.0Hz, `01` 25.0Hz, `02` 31.5Hz, `03` 40.0Hz, `04` 50.0Hz, `05` 63.0Hz, `06` 80.0Hz, `07` 100Hz, `08` 125Hz, `09` 160Hz, `0A` 200Hz, `0B` 250Hz, `0C` 315Hz, `0D` 400Hz, `0E` 500Hz, `0F` 630Hz, `10` 800Hz, `11` 1.00k, `12` 1.25k, `13` 1.60k, `14` 2.00k, `15` 2.50k, `16` 3.15k, `17` 4.00k, `18` 5.00k, `19` 6.30k, `1A` 8.00k, `1B` 10.0k
- **`60 00 01 2F` MOD PEQ: — Lo Mid Q** (6 valores, L38169): `00` 0.5, `01` 1, `02` 2, `03` 4, `04` 8, `05` 16
- **`60 00 01 31` MOD PEQ: — Hi Mid Freq** (28 valores, L38180): `00` 20.0Hz, `01` 25.0Hz, `02` 31.5Hz, `03` 40.0Hz, `04` 50.0Hz, `05` 63.0Hz, `06` 80.0Hz, `07` 100Hz, `08` 125Hz, `09` 160Hz, `0A` 200Hz, `0B` 250Hz, `0C` 315Hz, `0D` 400Hz, `0E` 500Hz, `0F` 630Hz, `10` 800Hz, `11` 1.00k, `12` 1.25k, `13` 1.60k, `14` 2.00k, `15` 2.50k, `16` 3.15k, `17` 4.00k, `18` 5.00k, `19` 6.30k, `1A` 8.00k, `1B` 10.0k
- **`60 00 01 32` MOD PEQ: — Hi Mid Q** (6 valores, L38210): `00` 0.5, `01` 1, `02` 2, `03` 4, `04` 8, `05` 16
- **`60 00 01 35` MOD PEQ: — Hi Cut Off** (15 valores, L38224): `00` 630Hz, `01` 800Hz, `02` 1.00k, `03` 1.25k, `04` 1.60k, `05` 2.00k, `06` 2.50k, `07` 3.15k, `08` 4.00k, `09` 5.00k, `0A` 6.00k, `0B` 8.00k, `0C` 10.0k, `0D` 12.5k, `0E` FLAT
- **`60 00 01 37` MOD GS: — Type** (8 valores, L38244): `00` S->H, `01` H->S, `02` H->HF, `03` S->Hollow, `04` H->Hollow, `05` S->AC, `06` H->AC, `07` P->AC
- **`60 00 01 3F` MOD WSY: — Wave** (2 valores, L38275): `00` SAW, `01` SQUARE
- **`60 00 01 47` MOD OC: — Range** (4 valores, L38300): `00` 1, `01` 2, `02` 3, `03` 4
- **`60 00 01 4A` MOD PS: — Voice** (2 valores, L38312): `00` 1-Voice, `01` 2-Mono
- **`60 00 01 4B` MOD PS: — Mode** (4 valores, L38316): `00` Fast, `01` Medium, `02` Slow, `03` Mono
- **`60 00 01 59` MOD HR: — Voice** (2 valores, L38378): `00` 1-Voice, `01` 2-Voice
- **`60 00 01 5A` MOD HR: — Harmony** (30 valores, L38382): `00` -2oct, `01` -14th, `02` -13th, `03` -12th, `04` -11th, `05` -10th, `06` -9th, `07` -1oct, `08` -7th, `09` -6th, `0A` -5th, `0B` -4th, `0C` -3rd, `0D` -2nd, `0E` Unison, `0F` +2nd, `10` +3rd, `11` +4th, `12` +5th, `13` +6th, `14` +7th, `15` +1oct, `16` +9th, `17` +10th, `18` +11th, `19` +12th, `1A` +13th, `1B` +14th, `1C` +2oct, `1D` User
- **`60 00 01 7C` MOD AC: — Type** (4 valores, L39708): `00` Small, `01` Medium, `02` Bright, `03` Power
- **`60 00 02 03` MOD PH: — Type** (4 valores, L39763): `00` 4stage, `01` 8stage, `02` 12stage, `03` Bi-Phase
- **`60 00 02 10` MOD FL: — Low Cut** (11 valores, L39807): `00` FLAT, `01` 55.0Hz, `02` 110Hz, `03` 165Hz, `04` 200Hz, `05` 280Hz, `06` 340Hz, `07` 400Hz, `08` 500Hz, `09` 630Hz, `0A` 800Hz
- **`60 00 02 17` MOD RT: — Speed** (2 valores, L39838): `00` Slow, `01` Fast
- **`60 00 02 21` MOD SL: — Pattern** (20 valores, L39869): `00` P1, `01` P2, `02` P3, `03` P4, `04` P5, `05` P6, `06` P7, `07` P8, `08` P9, `09` P10, `0A` P11, `0B` P12, `0C` P13, `0D` P14, `0E` P15, `0F` P16, `10` P17, `11` P18, `12` P19, `13` P20
- **`60 00 02 28` MOD VB: — Off/On** (2 valores, L39909): `00` Off, `01` On
- **`60 00 02 2B` MOD RM: — Mode** (2 valores, L39919): `00` Normal, `01` Intelligent
- **`60 00 02 2F` MOD HU: — Mode** (2 valores, L39932): `00` Picking, `01` Auto
- **`60 00 02 30` MOD HU: — Vowel 1** (5 valores, L39936): `00` A, `01` E, `02` I, `03` O, `04` U
- **`60 00 02 37` MOD 2CE: — Xover Freq** (17 valores, L39965): `00` 100Hz, `01` 125Hz, `02` 160Hz, `03` 200Hz, `04` 250Hz, `05` 315Hz, `06` 400Hz, `07` 500Hz, `08` 630Hz, `09` 800Hz, `0A` 1.00kHz, `0B` 1.25kHz, `0C` 1.60kHz, `0D` 2.00kHz, `0E` 2.50kHz, `0F` 3.15kHz, `10` 4.00kHz
- **`60 00 02 46` MOD PH90: — Script** (2 valores, L40026): `00` Off, `01` On
- **`60 00 02 51` MOD DC30: — Selector** (2 valores, L40060): `00` Chorus, `01` Echo
- **`60 00 02 59` MOD DC30: — Output Select** (2 valores, L40098): `00` D+E, `01` D/E

Son **33 listas distintas sobre 36 selectores**: los tres que faltan aquí repiten una de
arriba y no se duplican para no dar la impresión de que son otra cosa — `MOD PS: Mode` de la
segunda voz (`60 00 01 51`, misma lista que `01 4B`), `MOD HR: Harmony` de la segunda voz
(`60 00 01 5E`, misma lista que `01 5A`) y `MOD HU: Vowel 2` (`60 00 02 31`, misma lista que
Vowel 1). Son parámetros reales y hay que cablearlos; solo su catálogo está compartido.

⚠️ **`MOD PEQ: Hi Cut Off` (`60 00 01 35`) es la misma lista de 15 frecuencias que
`DelayHighCutFrequency` / `ReverbHighCutFrequency`, y desempata la contradicción documentada
entre ellas.** El proyecto mantiene esos dos enums separados a propósito porque `midi.xml` da
`0A` = `"6.30K"` en el bloque de Delay 1 y `"6.00k"` en el de Reverb (§5.2, "Parámetros
internos fijos de Delay 1 y Reverb"). Este tercer sitio, independiente de los dos, dice
**`0A` = `6.00k`** — dos fuentes contra una dentro del mismo fichero. No cambia la decisión de
tener dos enums (sigue sin haber prueba de hardware, y la fuente sigue contradiciéndose sola),
pero si algún día hay que apostar, `6.00k` es la que va ganando. Lo mismo con
`MOD PEQ: Lo Cut Off` (`60 00 01 2C`), que coincide byte a byte con `ReverbLowCutFrequency`.

##### Lo que NO encaja en el patrón "DSP simple", explícitamente

Reescrito con la extracción completa. **Dos entradas de la lista anterior eran incorrectas y se
corrigen aquí**; el resto se confirma y hay una nueva.

1. ⚠️ **`Pre Delay` de paso fraccionario: solo 2x2 Chorus.** `range 00/50/0.0/40.0` es crudo
   `0x00`..`0x50` mostrado como `0.0`..`40.0` ms, en pasos de 0,5 ms. Afecta a `60 00 02 3A` y
   `60 00 02 3E`, y **a nada más**. Ya resuelto con `FractionalLevelScale` /
   `KatanaFractionalParameter` (§5.2, "Escala de paso fraccionario"), que es justamente el
   parámetro que se cableó.
   ❌ **Corrección**: la versión anterior de esta lista añadía aquí "y a los Pre Delay de Pitch
   Shifter y Harmonist". **Es falso.** Los de PS y HR son `00/7F/00/127 ms` + `128/255` +
   `256/300`: milisegundos enteros, paso 1, sin parte fraccionaria. Son el punto 2, no este.
   El error venía de agrupar por el nombre del parámetro (`Pre Delay`) en vez de por su rango.
2. ⚠️ **Parámetros de 2 bytes: cinco, no cuatro.** El esquema es el MSB×128+LSB que ya usan
   `ACTIVE_CHANNEL` (§5.1) y `DELAY_TIME`, y está resuelto en código con el `byteWidth = 2` de
   `KatanaControl`. `midi.xml` los modela con `<DATA>` anidados, un hijo por tramo del MSB, y
   por eso los bloques tienen huecos en la numeración principal:

   | Parámetro | Dirección (MSB+LSB) | Hueco | Rango | Unidad | midi.xml |
   | --- | --- | --- | --- | --- | --- |
   | PS Pre Delay Voice 1 | `60 00 01 4E`+`4F` | `4E`→`50` | `0`..`300` | ms | 38328-38337 |
   | PS Pre Delay Voice 2 | `60 00 01 54`+`55` | `54`→`56` | `0`..`300` | ms | 38356-38365 |
   | HR Pre Delay Voice 1 | `60 00 01 5B`+`5C` | `5B`→`5D` | `0`..`300` | ms | 38414-38423 |
   | HR Pre Delay Voice 2 | `60 00 01 5F`+`60` | `5F`→`61` | `0`..`300` | ms | 38462-38471 |
   | **DC30 Repeat Rate** | `60 00 02 54`+`55` | `54`→`56` | **`40`..`600`** | **rpm** | 40070-40085 |

   🆕 **El de DC30 es nuevo: no estaba en la lista anterior, que daba cuatro.** Y es el más
   raro de los cinco, por dos motivos. Primero, **su mínimo crudo no es cero**: el tramo
   `MSB=00` arranca en `28` (`range 28/7F/40/127`), así que los valores `0`..`39` no existen y
   una escala que asuma que el recorrido empieza en `0` se sale por abajo. Segundo, **la unidad
   son rpm**, no ms — es la velocidad del altavoz rotatorio que emula el pedal, no un tiempo de
   retardo. Los cinco tramos: `00`→40-127, `01`→128-255, `02`→256-383, `03`→384-511,
   `04`→512-599, más el valor suelto `04`/`58`→600.
   ⚠️ Y su bloque anidado **está mal etiquetado en la fuente**: `midi.xml` lo llama
   `SDD:Delay Time(LSB)`, copiado del bloque de delay (SDD es una unidad de delay, no parte del
   DC30). La etiqueta miente; la dirección, el rango y la unidad `rpm` de los `<PARAM>` no.
3. ⚠️ **Harmonist arrastra 24 direcciones de escala de usuario**, `60 00 01 64`–`01 7B`: doce
   notas cromáticas × dos voces, 49 valores cada una, desde `midi.xml:38484`. Confirmado. Son
   una tabla de mapeo musical, no controles de panel — se pueden dejar fuera sin perder el
   efecto, igual que se dejó fuera Custom Type del Booster. Solo se activan cuando `Harmony`
   vale `1D` (`User`).
4. ⚠️ **Acu Processor cruza el límite de página**, de `60 00 01 7C` a `60 00 02 02`.
   Confirmado, y sigue siendo el único bloque que lo hace. La aritmética base 128 de `Address`
   lo cubre sola (`01 7F + 1 = 02 00`); solo rompería si alguien calculara direcciones sumando
   al último byte a mano.
5. ⚠️ **Nombres repetidos dentro de un bloque, que solo la posición desambigua.** Tres casos,
   uno más que antes:
   - **2x2 Chorus**: `Rate`/`Depth`/`Pre Delay` aparecen dos veces (`02 38`-`3A` y
     `02 3C`-`3E`). Los delimitan los niveles de banda que cierran cada grupo, `02 3B` Low y
     `02 3F` High. El "(banda Low/High)" de la tabla es interpretación nuestra, razonada pero no
     literal de la fuente — si alguna vez suena cruzado, es el primer sitio donde mirar.
   - **DC30**: `Intensity` aparece en `02 53` y otra vez en `02 56`, separadas por el
     `Repeat Rate` de 2 bytes. El pedal tiene dos mitades y `02 51` `Selector` (`00` Chorus /
     `01` Echo) dice cuál está sonando, así que lo más probable es que `53` sea la del chorus y
     `56` la del echo — otra vez posición, no fuente.
   - 🆕 **Pitch Shifter y Harmonist**: `Mode`, `Pitch`, `Fine`, `Pre Delay` y `Harmony` se
     repiten por voz. El reparto lo marcan los niveles `PS Voice 1` (`01 50`) y `PS Voice 2`
     (`01 56`), `HR Voice 1` (`01 5D`) y `HR Voice 2` (`01 61`), que cierran cada grupo igual
     que en 2x2 Chorus. Ojo con `60 00 01 4A`, que se llama `Voice` a secas y **no** es un
     nivel: es el selector de cuántas voces suenan (`00` 1-Voice, `01` 2-Mono).
6. ⚠️ **Los "sneaky" tienen 2–5 parámetros**, no el juego completo: Phaser 90E dos, Flanger
   117E cuatro, Wah 95E cinco. Confirmado. Son emulaciones de pedales concretos con un par de
   perillas.
7. 🆕 **Vibrato lleva su propio `Off/On` interno** en `60 00 02 28`, en mitad del bloque y
   distinto del on/off del slot de Mod (`60 00 01 00`). Es el switch del propio pedal emulado.
   No confundirlos: apagar uno no es apagar el otro.
8. 🆕 **Cuatro escalas centradas distintas conviven en estos bloques**, y la radio no se puede
   suponer por el nombre del parámetro: `-50..+50` (`LevelScale.centered(50)`, crudo `00`..`64`
   — Tone, Low, High, Top, Bass, Middle, Treble, Presence, Fine), `-20..+20` en dB
   (`centered(20)`, crudo `00`..`28` — todo Graphic EQ y las ganancias del Parametric EQ) y
   `-24..+24` (`centered(24)`, crudo `00`..`30` — Pitch de Pitch Shifter y de Pedal Bend). El
   ancho del crudo es lo que las distingue, no la etiqueta.
9. 🆕 **`Step Rate` del Phaser (`60 00 02 08`) es el único `offThenOneBased` del lote**, con la
   forma `00` = Off y luego crudo `01`..`65` = `0`..`100` — exactamente la escala que ya existe
   para los cinco niveles de efecto del panel. Ningún otro parámetro interno de Mod/FX la usa.

**Dos rarezas de la fuente**, ninguna de dirección, encontradas al comparar Mod contra FX y
confirmadas en la extracción completa — son las 14 diferencias de etiqueta del cuadro de
verificación:

- **`midi.xml` llama `ACS:` a dos tipos distintos en el bloque Mod**: Compressor (`01 16`) y
  AC Guitar Sim (`02 41`). El bloque **FX desambigua** y usa `AGS:` para el segundo
  (`03 16` vs `04 41`). Se resuelve por posición y por los nombres de parámetro, que no se
  parecen en nada (Sustain/Attack/Tone/Effect contra Top/Body/Low/High/Level); el catálogo de
  `ModFxType` ya los tiene como tipos separados. **5 nodos.**
- Las etiquetas de los `Pre Delay` anidados están **copiadas mal en FX**: donde Mod dice
  `PS :Voice2:Pre Delay(LSB)`, `HR :Voice1:Pre Delay` y `HR :Voice2:Pre Delay`, FX repite
  `PS :Voice1:Pre Delay` en los tres. Direcciones, rangos y estructura idénticas; solo el texto
  está mal. **9 nodos.** (Y en Mod, el de Voice 2 es el único de los cuatro que lleva `(LSB)`
  en el nombre — la fuente tampoco es consistente consigo misma.)


> Archivado desde CLAUDE.md — §5 "Controles sin perilla física"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

#### Controles sin perilla física (investigado 2026-09-05, cableado 2026-09-06)

Los cinco grupos que la §"Visión de alcance" listaba como punto 3 —Noise Gate, Solo, Contour,
posición IN/OUT de EQ1 y EQ2, y cadena de efectos— existen todos y **todos tienen dirección
documentada**.

⚠️ **Cableados el 2026-09-06 salvo el Solo, pendientes de confirmar con audio** (BACKLOG.md,
"Pendiente por probar"). El **Solo se queda fuera a propósito**: sigue en instrumentación de
diagnóstico aparte, con sus dos candidatas sin desempatar, y cablearlo antes de saber cuál
responde sería elegir una al azar.

✅ **Lo que sí se verificó al cablearlo**, por programa contra `midi.xml` y no por relectura:
EQ2 es EQ1 `+ 0x20` byte a byte (24 nodos, 0 diferencias de dirección, nombre ni rango); las 20
posiciones de la cadena ofrecen el mismo catálogo de 20 identificadores y van seguidas de `00`
a `13`; y los seis catálogos de frecuencia/Q del EQ coinciden byte a byte con los del
Parametric EQ de Mod/FX.

**Todo sale de `midi.xml`**, que es la única fuente de Mk2 que los cubre. `Adresses.txt` no
menciona ninguno (su sección `EQ:` es en realidad el Bass/Middle/Treble del panel, no un
ecualizador); los `*.yaml` de TuxKatana solo aportan un dato indirecto de Contour y otro de
EQ2; y `katana-midi-bridge` es MK1, así que corrobora **estructura pero no direcciones**.

##### El truco que resolvió esto: la tabla de destinos de asignación

Ninguno de los cinco aparece en las secciones de parámetros que ya se habían recorrido. Todos
salen de un sitio distinto: la **tabla de destinos de asignación** (knob/pedal assign) en
`midi.xml:3700-3995`, donde cada destino codifica su dirección en dos atributos:

```
midi.xml:3957  <PARAM value="1E" name="Contour: Off/On"  desc="06" customdesc="16"/>
                                                          ↑ 3.er byte  ↑ 4.º byte
```

> `desc` = tercer byte de la dirección, `customdesc` = cuarto. Es decir `60 00 <desc> <customdesc>`.

✅ **La regla está verificada contra direcciones que este proyecto ya confirmó por audio**, no
supuesta: `"Panel Knob: Gain"` da `desc="06" customdesc="51"` → `60 00 06 51`;
`"Booster GRY color select"` da `06`/`39` → `60 00 06 39`; `"Panel Knob: Reverb/Delay2"` da
`06`/`5B` → `60 00 06 5B`. Las tres coinciden con lo medido (§5, bloque de perillas). Además,
cada dirección obtenida así **vuelve a aparecer como `<DATA>` en el bloque `<Structure>`**, con
su rango y sus valores — dos sitios independientes del mismo fichero que concuerdan.

##### Tabla resumen

Confianza: **`midi.xml` ×2** = la dirección aparece en la tabla de asignación *y* como `<DATA>`
en `<Structure>`; **`midi.xml` ×1** = solo como `<DATA>`; **+MK1** = `katana-midi-bridge` o
`katana_sysex.txt` corroboran la *estructura* con otra dirección; **+tsl** = los offsets de
`presets_addrs.yaml` corroboran el tamaño.

| Control | Dirección | Tipo | Rango / opciones | Ámbito | Confianza |
| --- | --- | --- | --- | --- | --- |
| **Noise Gate** On/Off | `60 00 05 66` | on/off | `00` Off · `01` On | preset | `midi.xml` ×2 +MK1 |
| Noise Gate Threshold | `60 00 05 67` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×2 +MK1 |
| Noise Gate Release | `60 00 05 68` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×2 +MK1 |
| **Solo** On/Off (panel) | `60 00 06 14` | on/off | `00` Off · `01` On | preset | `midi.xml` ×1 ⚠️ |
| Solo Level (panel) | `60 00 06 15` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×1 ⚠️ |
| **Solo** Sw (preamp) | `60 00 00 2B` | on/off | `00` Off · `01` On | preset | `midi.xml` ×1 ⚠️ |
| Solo Level (preamp) | `60 00 00 2C` | nivel | crudo `00`..`64` = `0`..`100` | preset | `midi.xml` ×1 ⚠️ |
| **Contour** Off/On | `60 00 06 16` | on/off | `00` Off · `01` On | preset | `midi.xml` ×2 |
| Contour Select | `60 00 06 17` | selector | `00` 1 · `01` 2 · `02` 3 | preset | `midi.xml` ×2 |
| Contour Freq Shift (activo) | `60 00 06 1A` | nivel centrado | crudo `00`..`64` = `-50`..`+50` | preset | `midi.xml` ×1 |
| Contour 1 Shape | `60 00 0F 30` ⚠️ | selector | `00` 1 · `01` 2 · `02` 3 · `03` 4 | preset | `midi.xml` ×2 +tsl |
| Contour 1 Freq Shift | `60 00 0F 31` ⚠️ | nivel centrado | crudo `00`..`64` = `-50`..`+50` | preset | `midi.xml` ×2 +tsl |
| Contour 2 Shape / Freq | `60 00 0F 38` / `0F 39` ⚠️ | ídem | ídem | preset | `midi.xml` ×2 +tsl |
| Contour 3 Shape / Freq | `60 00 0F 40` / `0F 41` ⚠️ | ídem | ídem | preset | `midi.xml` ×2 +tsl |
| **EQ1 posición** | `60 00 06 22` | selector 2 | `00` Amp In · `01` Amp Out | preset | `midi.xml` ×2 |
| **EQ2 posición** | `60 00 06 19` | selector 2 | `00` PreAmp In · `01` Pre Amp Out | preset | `midi.xml` ×2 |
| EQ1 bloque interno | `60 00 00 40`–`00 57` | 24 bytes | ver abajo | preset | `midi.xml` ×1 |
| EQ2 bloque interno | `60 00 00 60`–`00 77` | 24 bytes | ver abajo | preset | `midi.xml` ×1 +tsl |
| **Chain** (orden) | `60 00 06 00`–`06 13` | 20 selectores | 20 IDs de bloque, ver abajo | preset | `midi.xml` ×1 |
| Chain tipo 1~7 | `60 00 06 20` | selector 7 | crudo `00`..`06` | preset | `midi.xml` ×2 |
| Loop posición | `60 00 06 21` | selector 2 | `00` Post Amp · `01` Post Reverb | preset | `midi.xml` ×2 |
| Pedal/FX posición | `60 00 06 23` | selector 2 | `00` Input · `01` Post Amp | preset | `midi.xml` ×2 |

**Hallazgos sueltos del mismo barrido**, no pedidos pero del mismo bloque: `60 00 05 70`
Master Patch Level (`0`..`100` %), `60 00 05 71` Master Key (enum 12, `00` C(Am) … `0B` B(G#m)),
y `60 00 06 43` Cabinet Resonance.

##### 1. Noise Gate — `60 00 05 66`–`68`

Tres parámetros, el patrón "DSP simple" de siempre: un on/off y dos niveles `0..100`.
`midi.xml:43022-43031` los da como `<DATA>` con `desc="NS:"` (Noise **Suppressor**, que es como
lo llama Boss; "Noise Gate" es el nombre de la spec MK1). La tabla de asignación los repite en
`midi.xml:3928-3930` como `NS: On/Off` / `Threshold` / `Release` con `desc="05"`.

✅ **La estructura la corrobora el MK1 por partida doble**, con otra dirección:
`katana-midi-bridge/parameters/amplifier.json:106-127` define `noiseGate` con
`baseAddr [96,0,6,99]` (= `60 00 06 63`), `length: 3` y exactamente los mismos tres campos
(`gateActive` booleano, `threshold` y `release` `0..100`); y
`katana-midi-bridge/doc/katana_sysex.txt:328-337` da los mismos tres en `60 00 06 63`–`65`.
Es el caso de libro de §5.2: **la estructura transfiere del MK1, la dirección no**
(`06 63` → `05 66`).

##### 2. Solo — dos candidatas, y hay que probar cuál

⚠️ **Este es el único de los cinco con ambigüedad real**, y es exactamente el patrón que ya
costó tres intentos con el reverb: dos direcciones plausibles, ninguna fuente que desempate.

| Candidata | midi.xml | Etiqueta en la fuente | Bloque |
| --- | --- | --- | --- |
| `60 00 06 14` / `06 15` | 43514 / 43518 | `name="Solo"` `desc="Solo"` — On/Off y Level | `panel` (LSB `06`) |
| `60 00 00 2B` / `00 2C` | 37492 / 37496 | `desc="PREAMP:"` — Solo Sw y Solo Level | `PRE` (LSB `00`) |

Las dos tienen la misma forma (on/off + nivel `0..100`) y las dos son **distintas del Solo del
Booster** (`60 00 00 15`/`16`), que ya está implementado y confirmado por audio (§5.2).

**Ninguna de las dos aparece en la tabla de destinos de asignación** — se comprobó
explícitamente: en `midi.xml:3700-3995` no hay ni una entrada con "Solo" ni con "PREAMP". Así
que aquí falta el segundo testigo que sí tienen Contour y las posiciones de EQ, y **la regla
del proyecto se aplica entera: solo el audio decide**.

Si hubiera que apostar, `60 00 06 14`/`15` está en el bloque `panel`, que es donde viven las
once perillas confirmadas y los cinco selectores de color confirmados — pero eso es una
analogía, no un dato, y §5 tiene el precedente de `60 00 06 5C` (variación), que está en ese
mismo bloque y resultó de solo lectura. **TBD, probar con amplificador.**

##### 3. Contour — dos niveles, y el segundo cae fuera del dump

El Contour del Katana Mk2 son **tres slots** (como los colores de los efectos), con un
seleccionador de cuál está activo:

- `60 00 06 16` — Contour Off/On (`midi.xml:43521`, y la tabla de asignación en `:3957`).
- `60 00 06 17` — cuál de los tres está activo, `00`/`01`/`02` (`:43525`, asignación `:3958`).
- `60 00 06 1A` — "Contour: Freq Shift" del activo, escala centrada `-50..+50` (`:43544`).
- Por slot, en **otro bloque**: Shape (enum de 4) y Freq Shift (centrada `-50..+50`), con
  paso de 8 bytes entre slots:

  | Slot | Shape | Freq Shift | midi.xml | asignación |
  | --- | --- | --- | --- | --- |
  | Contour 1 | `60 00 0F 30` | `60 00 0F 31` | 50135 / 50141 | 3959 / 3960 |
  | Contour 2 | `60 00 0F 38` | `60 00 0F 39` | 50150 / 50156 | 3961 / 3962 |
  | Contour 3 | `60 00 0F 40` | `60 00 0F 41` | 50165 / 50171 | 3963 / 3964 |

✅ **El tamaño lo corrobora TuxKatana**: `presets_addrs.yaml:58-66` define
`UserPatch%Contour(1)`, `(2)` y `(3)` con `size: 2` cada uno — dos bytes por slot, que son
justo Shape + Freq Shift. Pero su campo `addr` está **vacío**: TuxKatana sabe que existen y
cuánto ocupan, y no sabe dónde están. Es la única cosa que aporta cualquier fuente que no sea
`midi.xml`, y aun así confirma la forma.

⚠️ **`60 00 0F 3x`/`4x` está FUERA del dump y esto sí cambia el diseño.** El dump pide
`60 00 00 00` con tamaño `00 00 0F 00` (1920 bytes), o sea hasta `60 00 0E 7F`; y el
amplificador real devolvió 1860, hasta `60 00 0E 43` (§4.4). Los Contour por slot están en el
offset 1968-1985: **fuera de lo pedido y fuera de lo devuelto**. Son los primeros controles
del proyecto que `loadFromDump` no puede poblar — caen al GET individual de respaldo, que ya
existe y funciona. Todo lo demás de esta sección (Noise Gate, Solo en las dos candidatas, EQ1,
EQ2, Chain) **sí cae dentro del dump** — comprobado offset por offset, y ahora también por un
test.

⚠️ **Y al cablearlo (2026-09-06) apareció el precio, que la nota anterior no anticipaba: son
seis GET de respaldo EN SERIE, en cada recarga.** `loadFromDump` recorre los controles que el
dump no cubrió con un `forEach { control.read() }` secuencial, y cada `read()` espera hasta
`DEFAULT_REPLY_TIMEOUT_MS` (800 ms). Seis controles (3 slots × Shape + Freq Shift) son, si esa
región **no** contesta, **hasta 4,8 s añadidos a cada conexión y a cada cambio de canal** —
porque el cambio de canal dispara la misma recarga (§4.4). Si contesta rápido el coste es
despreciable, así que **lo que decide entre "gratis" y "inaceptable" es justo lo que está sin
probar**: si `60 00 0F 3x` responde al GET.

Se descubrió porque un test de regresión de concurrencia que ya existía empezó a agotar su
margen de 800 ms — el invariante que probaba (nunca dos dumps a la vez) seguía cumpliéndose, lo
que cambió fue cuánto tarda una recarga. Hay ahora un test que **fija en seis** el número de
controles fuera del dump, para que nadie añada un séptimo sin enterarse de lo que cuesta.

**Si resulta que esa región no contesta**, las salidas son ampliar el rango del dump para
cubrir `60 00 0F xx`, o hacer los GET de respaldo en paralelo en vez de en serie. Ninguna de
las dos se ha hecho: las dos son cambios reales al camino de recarga, y hacerlos antes de saber
si hacen falta sería optimizar a ciegas.

##### 4. EQ1 y EQ2 — la posición son dos valores, no tres

**La posición IN/OUT es un selector de dos posiciones**, no de tres, y son dos direcciones
distintas y bastante separadas:

- `60 00 06 22` — EQ1: `00` Amp In · `01` Amp Out (`midi.xml:43559`; asignación `:3967`,
  literalmente `name="Signal chain position: EQ1"`).
- `60 00 06 19` — EQ2: `00` PreAmp In · `01` Pre Amp Out (`midi.xml:43540`; asignación `:3968`,
  `name="Signal chain position: EQ2"`).

⚠️ **Las dos etiquetas dicen lo mismo con palabras distintas** ("Amp In/Out" contra "PreAmp
In/Pre Amp Out") y `midi.xml` escribe `Postion` en los dos sitios. Es cosmético: el rango es
`00`/`01` en ambos y la tabla de asignación los llama a los dos "Signal chain position".

**Los dos EQ tienen bloque interno propio, de 24 bytes cada uno**, y cada uno es en realidad
**dos ecualizadores con un selector**: paramétrico o gráfico.

| | EQ1 | EQ2 |
| --- | --- | --- |
| On/Off | `60 00 00 40` | `60 00 00 60` |
| Selection (`00` Paramétrico · `01` Gráfico) | `00 41` | `00 61` |
| Paramétrico (11 params) | `00 42`–`00 4C` | `00 62`–`00 6C` |
| Gráfico (10 bandas + Level) | `00 4D`–`00 57` | `00 6D`–`00 77` |
| midi.xml | 37562-37728 | 37739-37905 |

Los 11 del paramétrico, en orden: Low Cut (enum 18, `FLAT`..`800Hz`), Low Gain, Lo Mid Freq
(enum 28), Lo Mid Q (enum 6), Lo Mid Gain, Hi Mid Freq (enum 28), Hi Mid Q (enum 6), Hi Mid
Gain, Hi Gain, Hi Cut (enum 15), Level.

⚠️ **Corregido el 2026-09-06: son CUATRO "Gain" más el "Level", cinco escalas centradas en
total, no seis.** Esta línea decía "los cinco Gain y el Level", que da seis; los Gain son Low,
Lo Mid, Hi Mid y Hi — cuatro. Lo cazó un test al cablearlo (`NoPanelControlsTest`, "los cuatro
Gain y el Level del paramétrico son centrados enteros de 20"), no una relectura: la cuenta mal
hecha estaba en la prosa, y el código, que sale de la extracción, siempre tuvo cinco. Las cinco
son `range 00/28/-20/+20 dB` → `LevelScale.centered(20)`
(`midi.xml:37590`/`37631`/`37672`/`37675`/`37695`), la misma escala que el Graphic EQ **interno
de Mod/FX** (§5.2). ✅ Los seis catálogos de frecuencia y Q son **byte a byte los mismos** que
los ya extraídos para el Parametric EQ de Mod/FX — verificado por programa, y hay un test que
lo fija para que una divergencia futura no pase inadvertida.

Las 11 del gráfico (31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1KHz, 2KHz, 4KHz, 8KHz, 16KHz, Level) son
todas `range 00/30/-12.0/+12.0 dB`.

⚠️ **Ojo: el EQ gráfico es de paso fraccionario, y no es el mismo que el de Mod/FX.** Crudo
`0x00`..`0x30` (49 valores) mostrado como `-12.0`..`+12.0` dB da **pasos de 0,5 dB**, no de 1.
El Graphic EQ interno de Mod/FX es `00/28/-20/+20` (entero). Así que estas 22 bandas necesitan
`FractionalLevelScale` (§5.2, "Escala de paso fraccionario"), no `LevelScale.centered`.

✅ **La extensión del bloque de EQ2 la corrobora TuxKatana**: `presets_addrs.yaml:7-9` define
`UserPatch%Eq(2)` con `addr: '60 00 00 60'` y `size: 24` — dirección de inicio y tamaño
idénticos a lo que da `midi.xml`. Es la única dirección de esta sección entera confirmada por
una fuente distinta. EQ1 no tiene entrada propia porque cae dentro de
`UserPatch%Patch_0` (`60 00 00 10`, `size: 72` → `00 10`..`00 57`), que sí lo cubre.

**No confundirlos con el EQ global**, que existe y vive en otro espacio de direcciones: el
bloque `<System>` de `midi.xml` (`00 <sistema> <página> <param>`, el mismo esquema del canal
activo `00 01 00 00` de §5.1) tiene en `00 00 00 10`–`00 00 00 28` un ecualizador de sistema, y
en `00 00 00 2E`–`00 00 01 07` tres slots EQ1/EQ2/EQ3 etiquetados verde/rojo/amarillo con Type
y Position propios (`midi.xml:87-95, 284-287, 300-304, 479-483, 658-662`). **Ese sí tiene
Position de cuatro valores** (`00` Input · `01` Output · `02` Line Out Only · `03` Speaker Out
Only) y es **global, no por preset**. Nada de eso está investigado más allá de constatar que
existe; si algún día se quiere el EQ global, empezar por ahí.

##### 5. Cadena de efectos — sí se puede reordenar, y de dos maneras

**No es una posición fija.** Hay dos mecanismos, y conviven:

- **`60 00 06 20`** — "Chain position" / "Signal Chain order: Type 1~7": un selector con rango
  `range 00/06/00/06`, o sea **siete cadenas predefinidas** (`midi.xml:43552`; asignación
  `:3971`). Es el control simple.
- **`60 00 06 00`–`60 00 06 13`** — **veinte direcciones consecutivas, cada una un selector de
  los mismos 20 identificadores de bloque** (`midi.xml:43074-43427`). Es un **array de
  permutación**: cada posición de la cadena dice qué bloque va ahí. Este es el control fino, y
  es lo que permite un orden arbitrario.

Los 20 identificadores, idénticos en las 20 direcciones: `00` CS · `01` LP · `02` CH_A ·
`03` CH_B · `04` EQ1 · `05` FX1 · `06` FX2 · `07` DD1 · `08` DD2 · `09` RV · `0A` EQ2 ·
`0B` PDL · `0C` FV · `0D` NS_1 · `0E` NS_2 · `0F` OD · `10` USB · `11` CN_S · `12` CAB ·
`13` CN_M.

En el vocabulario del proyecto: `OD` es el Booster, `FX1` es Mod, `FX2` es FX, `DD1`/`DD2` los
dos delays, `RV` la reverb, `NS_1`/`NS_2` el noise gate, `LP` el loop de send/return, `PDL` el
Pedal FX, `FV` el foot volume, `CAB` el cabinet y `CS` el compresor.

Además hay tres selectores de punto de inserción, que son parte del mismo asunto y ya salen en
la tabla resumen: `60 00 06 21` (Loop: Post Amp / Post Reverb), `60 00 06 22` (EQ1) y
`60 00 06 23` (Pedal/FX: Input / Post Amp).

⚠️ **El MK1 no sirve de referencia aquí, y por una vez la diferencia es de fondo, no de
dirección.** `katana_sysex.txt:318-325` y `amplifier.json:129-140` dan la cadena del MK1 como
**una sola dirección** (`60 00 12 00`) con **tres valores** (`One`/`Two`/`Three`). El Mk2 tiene
siete tipos *y* un array de 20. Es un caso donde ni la estructura transfiere.

##### Lo que esto resuelve de paso: `60 00 06 18`

`60 00 06 18` es **`FS2 Func: Function`**, un selector de 8 valores (`00`=1 … `07`=8) —
`midi.xml:43530`. Merece la pena anotarlo porque esa dirección ya aparece en §5 como la primera
candidata fallida del nivel de reverb, sacada de `katana_sysex.txt` (MK1): *"❌ nada: ni sonido
ni estado; el GET devuelve `07` fijo"*. Ahora se entiende el `07`: no era basura ni una
dirección muerta, era **el valor 8 de la función del footswitch 2**. La dirección siempre
estuvo viva; lo que estaba mal era suponer qué había en ella.

##### Qué queda por probar

Nada de esta sección está confirmado con el amplificador. En orden de riesgo:

1. **Solo: cuál de las dos candidatas responde** (`06 14`/`15` contra `00 2B`/`2C`). Es la
   única ambigüedad real, y no hay fuente que la resuelva.
2. **Que la regla `desc`/`customdesc` de la tabla de asignación valga también para direcciones
   que el proyecto no ha medido.** Está verificada contra tres direcciones confirmadas por
   audio, lo cual es un buen indicio, pero las tres son del bloque `06 5x` — no prueba que la
   codificación sea igual de fiable en `05 6x` o `0F 3x`.
3. **Los Contour por slot (`60 00 0F 3x`/`4x`), que además caen fuera del dump**: hay que
   comprobar que el GET individual los devuelve, antes de asumir que el respaldo los cubre.
4. **El paso de 0,5 dB del EQ gráfico**, que es una lectura del rango de `midi.xml` y ya falló
   una vez en un caso parecido (el tramo raro de Delay Time, §5.2).


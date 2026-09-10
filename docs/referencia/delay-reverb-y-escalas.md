> Archivado desde CLAUDE.md — §5.2 "Parámetros internos fijos de Delay 1 y Reverb" y "Escala de paso fraccionario"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

#### Parámetros internos fijos de Delay 1 y Reverb (implementado, 2026-09-05)

⚠️ **Implementado, pendiente de confirmar con audio** — ver BACKLOG.md, "Pendiente por
probar". Delay 1 y Reverb son "DSP simple" igual que Booster (§5.2 arriba): un único bloque
fijo de direcciones, el mismo para cualquier tipo activo, sin el desdoblamiento por tipo de
Mod/FX. **Fuente única**: `reference/FxFloorboard/midi.xml:42413-42488` (Delay 1, bloque
`desc="DD1:"`) y `:42860-42935` (Reverb, bloque `desc="REV:"`/`"REVERB:"`) — ninguno de estos
parámetros aparece en `reference/TuxKatana/params/delay.yaml` ni `reverb.yaml` más allá de lo
ya implementado (On/Off, Type, color, nivel de panel), ni en `Adresses.txt`.

**Delay 1** (`KatanaAddresses.DELAY_TIME`–`DELAY_DIRECT_MIX`):

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 05 02`–`03` | Time (2 bytes) | `1..2000` ms, directo | 42413-42461 |
| `60 00 05 04` | Feedback | `00/64` = 0..100 | 42465 |
| `60 00 05 05` | High Cut | enum 15: `00` 630Hz…`0E` FLAT | 42468-42482 |
| `60 00 05 06` | Effect | `00/78` = 0..120 | 42485 |
| `60 00 05 07` | Direct | `00/64` = 0..100 | 42488 |

**Reverb** (`KatanaAddresses.REVERB_PRE_DELAY`–`REVERB_DIRECT_MIX`):

| Dirección | Parámetro | Rango | midi.xml |
| --- | --- | --- | --- |
| `60 00 05 42` | Time | ✅ cableado (2026-09-05), escala fraccionaria | 42873 |
| `60 00 05 43`–`44` | Pre Delay (2 bytes) | `0..500` ms, directo | 42876-42891 |
| `60 00 05 45` | Low Cut | enum 18: `00` FLAT…`11` 800Hz | 42892-42906 |
| `60 00 05 46` | High Cut | enum 15: `00` 630Hz…`0E` FLAT | 42912-42922 |
| `60 00 05 47` | Density | `00/0A` = 0..10 (no 0..100) | 42925 |
| `60 00 05 48` | Effect | ❌ **no cableado**, ver abajo | 42928 |
| `60 00 05 49` | Direct Mix | `00/64` = 0..100 | 42931 |

Tap Time y los parámetros específicos de tipo (X/Y-channel, Mod, SDE) quedan fuera de Delay 1
a propósito, igual que Spring Color queda fuera de Reverb: son sub-modos de un tipo concreto,
no parte del bloque fijo.

**Delay Time (`60 00 05 02`) tiene un tramo de la fuente que no cuadra, y no bloqueó la
implementación.** El esquema es el mismo MSB×128+LSB de 2 bytes que ya usa
`KatanaAddresses.ACTIVE_CHANNEL` (§5.1): 16 posiciones del MSB (`00`..`0F`), cada una con un
sub-rango de milisegundos del LSB. 15 de los 16 tramos son limpios —anchura del crudo igual a
la del mostrado—, pero `MSB=0x0E` documenta crudo `00`..`4F` (80 valores) mostrado como
`1792`-`1919` ms (128 valores): un ancho que no coincide con ningún otro tramo, ni con el de
al lado (`MSB=0x0F`: crudo `00`..`4F` → `1920`-`1999`, ese sí encaja). La hipótesis más
probable es un error de copia en esa fila de `midi.xml` (el hueco de 48 es justo lo que
sobraría si el crudo real fuera `0x7F`, no `0x4F`) y no una resolución real distinta — pero es
solo una lectura, no una prueba. Se cableó igual con `LevelScale.direct(1..2000)` porque
`MidiBytes` ya decodifica cualquier valor del rango entero de 14 bits sin escala nueva; lo
pendiente es confirmar con audio específicamente el tramo `1792`-`1999` ms. Ver el KDoc de
`KatanaAddresses.DELAY_TIME`.

`REVERB_PRE_DELAY`, con la misma estructura de 4 tramos, **no tiene esta irregularidad**: los
cuatro son limpios de punta a punta, el último incluido (`00`..`73` → `384`-`499`, más el
valor especial `74` → `500` que encaja exacto). Eso refuerza que lo de Delay Time es un fallo
puntual de la fuente y no una propiedad del esquema de 2 bytes en sí.

✅ **Reverb Time (`60 00 05 42`), cableado el 2026-09-05 tras extender la escala.**
`midi.xml:42873` da `range 00/63/0.1/10.0 sec`: crudo `0x00`..`0x63` mostrado como
`0.1`..`10.0` segundos, es decir `mostrado = (crudo + 1) / 10` — un paso de 0.1s, exactamente
el mismo tipo de anomalía que el Pre Delay de 0.5ms de Mod (más arriba en esta sección).
`LevelScale` solo sabe sumar un desplazamiento entero, no dividir, así que no podía
representar este paso; se quedó documentado y sin cablear una temporada, por instrucción
explícita del proyecto, hasta que se resolviera la escala en vez de forzarlo como si fuera
`0..100` directo. Ver "Escala de paso fraccionario" más abajo para cómo se resolvió.
⚠️ **Implementado, sin confirmar con audio.**

❌ **Reverb Effect Level (`60 00 05 48`) es la misma dirección que `REVERB_LEVEL_DERIVED`,
ya probada como no funcional (más arriba, "Intento descartado 2").** No es una dirección
nueva sin identificar: es el "Effect Level" del bloque interno de Reverb, entre Density
(`47`) y Direct Mix (`49`). Esto explica el porqué de aquel resultado — no es que la dirección
esté muerta, es que **no es la perilla del panel** (`REVERB_LEVEL`, `60 00 06 5B`), es un
parámetro interno de la reverb en sí, y el valor "derivado y retardado" que se observaba
entonces era, con esta lectura, probablemente el nivel interno tras aplicar el propio efecto.
No se reintroduce como control nuevo: sigue sin haber prueba de que aceptar escritura ahí
cambie el sonido.

#### Escala de paso fraccionario (implementado, 2026-09-05)

✅ **`FractionalLevelScale`, nueva clase en `protocol/`, junto a `KatanaFractionalParameter` en
`device/`.** Desbloquea los dos parámetros que se habían quedado documentados y sin cablear
por la misma razón: un paso que no es "un byte crudo = una unidad mostrada" (0.5 ms para el
Pre Delay de 2x2 Chorus, 0.1 s para Reverb Time), que `LevelScale` no puede representar
porque solo suma un desplazamiento entero (`rawOffset`), nunca multiplica.

**Deliberadamente un tipo aparte, no una ampliación de `LevelScale` a `Double`.** Los ~230
tests que ya existían cuando se escribió esto asumen que todo control muestra un `Int`, y
son la inmensa mayoría de los parámetros del proyecto — forzar `Double` en todos ellos habría
sido cambiar el contrato de lo que funciona por dos casos minoritarios. La fórmula en sí no
necesitó un campo de desplazamiento aparte: `mostrado = displayRange.start + (crudo -
rawRange.first) × step` ya captura el "+1" de Reverb Time con solo que `displayRange`
empiece en `0.1`, no en `0.0`.

**Redondeo sin arrastre de error de punto flotante**: `toRaw` siempre parte del valor de
pantalla ya fijado y redondea una sola vez a un entero — nunca acumula sobre una conversión
anterior — así que una ida y vuelta repetida (crudo → mostrado → crudo → …) no puede
degradarse: cada conversión arranca limpia desde un entero, y el ruido de multiplicar por
`step` en coma flotante queda muchos órdenes de magnitud por debajo del `0.5` que haría falta
para cruzar un límite de redondeo. Verificado con un test que repite el viaje de ida y vuelta
50 veces sobre el mismo valor y comprueba que nunca se mueve del crudo original.

`KatanaFractionalParameter` es el mellizo de `KatanaParameter` con `displayValue`/`setLevel`
en `Double` en vez de `Int`; comparte toda la maquinaria de `KatanaControl` (caché, GET, SET
optimista, regla anti-eco) sin cambiar nada de lo que ya existía.

**Mod's Pre Delay de 2x2 Chorus (`60 00 02 3A` banda Low, `60 00 02 3E` banda High)** es el
**primer parámetro interno de Mod cableado en código**, y por eso trae una salvedad que
Booster/Delay/Reverb no tenían: Mod es "DSP complejo" (cada tipo activo tiene su propio
bloque de direcciones), así que estas dos direcciones **solo significan "Pre Delay" mientras
el tipo activo de Mod sea 2x2 Chorus** (`ModFxType.CHORUS`, `0x1D`); con cualquier otro tipo
activo son parámetros de ese otro tipo. La UI condiciona la visibilidad de estos dos sliders
al tipo activo en vez de mostrarlos siempre — a diferencia de Booster/Delay/Reverb, donde el
control siempre significa lo mismo sin importar el tipo. El repositorio registra el control
igual que todos los demás: la decisión de quién puede verlo/editarlo vive en la UI, no en
`device/`.

⚠️ **Ambos, implementados, sin confirmar con audio.**

**Dos catálogos de frecuencia nuevos, `protocol/`, con tests JVM**: `DelayHighCutFrequency`
(15 valores) y `ReverbHighCutFrequency` (15 valores) — **parecen la misma lista y no lo son**:
`0x0A` es `"6.30K"` en el bloque de Delay 1 y `"6.00k"` en el de Reverb, una fila donde la
propia fuente se contradice consigo misma. Se mantienen como dos enums separados a propósito
en vez de compartir uno: reutilizar la misma lista para los dos habría escondido la
discrepancia el día que cualquiera de las dos resultara ser la incorrecta — ver "el ruido de
las fuentes no predice el resultado" más abajo. `ReverbLowCutFrequency` (18 valores) no tiene
gemela en Delay. Las tres corren sin huecos.


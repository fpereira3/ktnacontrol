> Archivado desde CLAUDE.md — §5 "Las direcciones por parámetro del MK1 NO valen para el Mk2" (proceso de descubrimiento, bloque de perillas, amp type/color/on-off, escalas)
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

### ⚠️ Las direcciones por parámetro del MK1 NO valen para el Mk2

`katana_sysex.txt` dice en su primera línea **"Boss Katana 100 Combo — v1.7 - 2017-03-23"**:
documenta el **MK1**. Su formato de mensaje, checksum y las direcciones "de sistema"
(`10 xx`, `60 00 00 00`, `7F 00 00 01`) sí valen y están verificadas contra el Mk2. Pero
**el mapa de parámetros por efecto es distinto** y no se puede copiar.

Para direcciones de parámetros, las fuentes de Mk2 son `reference/TuxKatana/params/*.yaml`,
`reference/TuxKatana/doc/Adresses.txt` y `reference/FxFloorboard/midi.xml`.

#### Cómo encontrar la dirección de un parámetro (proceso, no atajo)

El nivel de reverb costó **tres candidatas** y solo el oído las distinguió:

| Candidata | Fuente | Resultado real |
| --- | --- | --- |
| `60 00 06 18` | katana_sysex.txt (MK1) | ❌ nada: ni sonido ni estado; el GET devuelve `07` fijo — **resuelto el 2026-09-05: es `FS2 Func`**, ver §5 "Controles sin perilla física" |
| `60 00 05 48` | reverb.yaml:17, sección `SEND` | ❌ escribir no hace nada; sí **reporta** un valor derivado y retardado |
| **`60 00 06 5B`** | reverb.yaml:19, sección `SEND` | ✅ **lectura y escritura**, cambio audible |

Presence, en cambio, salió a la primera con la candidata alta **`60 00 06 56`** (2026-09-02):
cambio audible de brillo, y la perilla física reporta por esa misma dirección. La baja
(`60 00 00 27`) quedó **sin probar** y se conserva documentada solo por si Presence resultara
tener el mismo problema que el reverb más adelante.

##### El bloque `60 00 06 50`–`60 00 06 5B` es la lista de perillas del panel

Esto es lo que explica por qué la dirección "alta" es la de control, y sale de
[reference/FxFloorboard/midi.xml:3981-3992](reference/FxFloorboard/midi.xml), donde el
bloque aparece nombrado uno a uno y **en el orden físico del panel**:

| Dirección | Nombre en `midi.xml` | Estado |
| --- | --- | --- |
| `60 00 06 50` | Panel Knob: Amp Type | sin probar |
| `60 00 06 51` | Panel Knob: Gain | ✅ confirmado por audio |
| `60 00 06 52` | Panel Knob: Volume | ✅ confirmado por audio |
| `60 00 06 53` | Panel Knob: Bass | ✅ confirmado por audio |
| `60 00 06 54` | Panel Knob: Middle | ✅ confirmado por audio |
| `60 00 06 55` | Panel Knob: Treble | ✅ confirmado por audio |
| `60 00 06 56` | Panel Knob: Presence | ✅ confirmado por audio |
| `60 00 06 57` | Panel Knob: Booster | ✅ confirmado por audio |
| `60 00 06 58` | Panel Knob: MOD | ✅ confirmado por audio |
| `60 00 06 59` | Panel Knob: FX | ✅ confirmado por audio |
| `60 00 06 5A` | Panel Knob: Delay 1 | ✅ confirmado por audio |
| `60 00 06 5B` | Panel Knob: Reverb/Delay2 | ✅ confirmado por audio |

**Once de las doce entradas del bloque están confirmadas por oído** (`06 51`–`06 5B`), cada
una en la posición que la tabla predice. Solo queda `06 50` (Amp Type) — que además no es un
nivel continuo, así que ni siquiera el rango `0..100` se le puede suponer. Documentado en
`KatanaAddresses.AMP_TYPE`, deliberadamente fuera del modelo de niveles de `device/`.

Lecciones del proceso, útiles para lo que quede por descubrir (selectores de color, Amp Type):

- **Que una fuente de Mk2 liste una dirección bajo `SEND` no basta.** `60 00 05 48` lo está
  y no funciona. En `set_mapping.py:39-43` TuxKatana fusiona `SEND` y `RECV` en el mismo
  mapa, así que esa separación es organizativa, no semántica.
- **La única prueba que vale es el audio.** Checksum correcto, bytes bien formados y una
  respuesta al GET no demuestran nada: `60 00 06 18` cumplía las tres cosas.
- **Prueba mínima**: SET a los extremos (0 y 100) → ¿cambia el sonido? Luego GET a la misma
  dirección → ¿cambió el estado? Y mover la perilla física → ¿reporta por esa dirección?
- **Que el patrón lleve once aciertos de once no lo convierte en regla universal**, aunque sí
  es la mejor apuesta posible para cualquier dirección nueva del mismo bloque. Ninguna de las
  "bajas" documentadas llegó a hacer falta —todas siguen sin probar—, y `60 00 05 48` sigue
  ahí para recordar que una dirección plausible puede aceptar el mensaje y no hacer nada. La
  única sorpresa real fue Gain/Volume (ver más abajo): las fuentes se contradecían y la alta
  ganó igual.
- **Una fuente puede afirmar justo lo contrario y seguir estando equivocada.**
  `Adresses.txt:36-41` decía, con flechas explícitas — el único sitio del fichero anotado
  así —, que `60 00 06 51` era *read status* y `60 00 00 22` la de escritura: exactamente el
  patrón "escritura baja / reporte alto" que ya había fallado con el reverb. El audio dijo lo
  contrario. Ni siquiera una anotación inequívoca sustituye la prueba.
- **El ruido de las fuentes no predice el resultado.** `booster.yaml` repite `60 00 06 57`
  bajo `Unimplemented:`, y funcionó igual. Al revés que `60 00 05 48`, que estaba limpiamente
  en `SEND` y no funcionó. Las anotaciones de las fuentes de Mk2 no ordenan nada: solo el
  audio.
- **Los rangos casi nunca están documentados para estas direcciones.** Ninguna fuente de Mk2
  da rango explícito para las seis; se usa `0..100` por analogía con el mapa MK1
  (`amplifier.json`) y con el formato `normal` de `slider_formats.yaml`. Es una **suposición
  razonada**, no un dato — pero **probada y aceptada** (2026-09-03): ver abajo.
##### Sobre los rangos `0..100` y sobre qué es realmente el slider de Delay

Dos cosas que salieron de usar la app contra el amplificador y que ninguna fuente decía:

- **El tramo que sobraba al final del recorrido eran dos cosas sumadas.** Se observó que el
  slider llegaba a 100 con la perilla física *a punto* del tope, y se anotó sin resolver: no
  se sabía si el 100 real estaba en el tope o si ese resto era holgura mecánica, y de oído no
  se distingue. **Resuelto el 2026-09-03**: era **las dos cosas**. Los cinco niveles de efecto
  tenían además un desfase de uno (ver más abajo); al corregirlo el hueco se redujo pero no
  desapareció. Lo que queda es holgura mecánica, y lo demuestra Presence, que nunca tuvo
  desfase —escala directa— y mostraba el mismo hueco desde el principio.
- **`60 00 06 5A` no es "el nivel del delay 1": es el mix global de los dos delays.** El
  Katana Mk2 tiene **dos** delays y **una sola perilla DELAY**; esa perilla ajusta el mix
  entre ambos. O sea que la línea comentada `# "60 00 06 5A": glob_mix_lvl` de `delay.yaml`
  probablemente sea el nombre correcto, y el `Panel Knob: Delay 1` de `midi.xml` sea el nombre
  de la perilla, no de lo que hay detrás. La prueba de audio **no distinguió** las dos cosas:
  se hizo con el delay 2 apagado, y así el mix global se comporta igual que el nivel del
  delay 1. Importa el día que se quiera controlar cada delay por separado — entonces esta no
  es la dirección.

- **La perilla no es lo único físico.** Cada efecto tiene además un botón de color
  (verde/rojo/amarillo) que vive en **otra** dirección —`60 00 06 39` para Boost,
  `06 3A` Mod, `06 3B` FX, `06 3C` Delay, `06 3D` Reverb—, y un on/off propio
  (`60 00 00 10` para Boost). Al pulsar el botón de color **no** deben llegar mensajes por la
  dirección de la perilla; eso es lo esperado, no un fallo.

#### Controles que no son niveles: amp type, color y on/off

Investigado e implementado el 2026-09-03, y **confirmado contra el amplificador el mismo
día** salvo un caso: `60 00 06 5C` (variación) resultó ser de solo lectura.

**Amp Type tiene dos direcciones con dos espacios de valores distintos**, y confundirlas es
el error fácil:

| Dirección | Qué es | Valores |
| --- | --- | --- |
| `60 00 06 50` | posición de la perilla AMP TYPE | `00`..`04` = Acoustic/Clean/Crunch/Lead/Brown |
| `60 00 00 21` | modelo de amplificador | 30 valores, `0x00`..`0x20` con huecos |
| `60 00 06 5C` | LED de variación | `00` off, `01` on |

`amplifier.yaml:3` llama a la primera `am_num` —un *número*— y a la segunda `am_type`. La
tabla de los 30 modelos sale de [midi.xml:37311-37341](reference/FxFloorboard/midi.xml), el
bloque `<DATA value="21" desc="PREAMP:" customdesc="Type">`, que es **la fuente más completa**:
`amplifier.yaml` y `Adresses.txt` tienen 29 entradas porque **les falta `BG Lead` (`0x10`)**.
Un 31.º valor, `0x19` "Custom", aparece solo en el bloque de conversión de `midi.xml` y no en
la tabla de la dirección, así que queda fuera y documentado.

**Selector de color por efecto** — `midi.xml` los llama "GRY color select", GRY por
Green/Red/Yellow. Tres fuentes de Mk2 coinciden en direcciones y en `00|01|02`:

| Efecto | Color | On/off |
| --- | --- | --- |
| Boost | `60 00 06 39` | `60 00 00 10` |
| Mod | `60 00 06 3A` | `60 00 01 00` |
| FX | `60 00 06 3B` | `60 00 03 00` |
| Delay | `60 00 06 3C` | `60 00 05 00` |
| Reverb | `60 00 06 3D` | `60 00 05 40` |

Los on/off salen de [midi.xml:3959-3963 y la lista de assign](reference/FxFloorboard/midi.xml)
y de los `*_sw` de los YAML. **El de reverb no está en `Adresses.txt`**: solo lo dan
`reverb.yaml:2` y `midi.xml`.

✅ **Las dos dudas que había quedaron resueltas al probar:**
- **Las direcciones "bajas" también se escriben.** Las diez de color y on/off, más
  `60 00 00 21`, funcionan pese a estar fuera del bloque `06 5x`. Ese bloque no tenía nada de
  especial: es simplemente donde están las *perillas del panel*, no el único sitio escribible.
  Conviene borrar esa regla de trabajo implícita.
- **`00` = off, `01` = on**, como en todo lo demás. La lectura literal de
  `Adresses.txt:81` (`[00|01] # [ON|OFF]`) era engañosa.

##### `60 00 06 5C` (variación) es de solo lectura, y cómo se reconoce

La única del lote que no acepta escritura. **Reporta** bien —pulsar el botón físico de
variación actualiza la app al instante— pero un SET se ignora.

El síntoma vale la pena saber leerlo, porque volverá a aparecer: al mover el switch, la UI se
encendía y **volvía sola a apagado un instante después**. Eso no es un fallo del control, es
la app funcionando bien. La escritura optimista pone la caché en `01`, el amplificador ignora
el SET y sigue reportando su `00` real por esa misma dirección, y el camino de mensajes
espontáneos lo aplica. **Un valor que rebota solo es la firma de una dirección de solo
reporte**, y solo se ve porque el edit mode y la actualización desde el amp están cableados.

`midi.xml:44107-44110` la etiqueta `abbr="led state"` —el estado de un LED, no un control—,
que en retrospectiva ya lo decía. Lo mismo cabe esperar de `06 5D`–`06 61`, los otros cinco
`led state` del bloque.

**La solución no fue buscar otra dirección de variación, sino usar el modelo**: los cinco
canales base tienen su gemelo `Var [...]` (`0x1C`–`0x20`) en la lista de
[AMP_TYPE_FULL], que sí acepta escritura. Así que el switch **lee `06 5C` y escribe
`00 21`**. Es un caso real de "se lee en una dirección y se escribe en otra" — el patrón que
se descartó para el reverb por ser una suposición. La diferencia es que aquí está medido en
las dos direcciones, no supuesto.

##### El bloque `06 57`–`06 5B` no es `0..100`, es `Off` + `1..101`

De [midi.xml:44062-44112](reference/FxFloorboard/midi.xml), que trae los rangos del bloque de
perillas:

- `06 51`–`06 56` (Gain, Volume, Bass, Middle, Treble, Presence): `range 00/64/00/100` —
  crudo `0x00..0x64` mostrado como `0..100`. Crudo y mostrado son el mismo número.
- `06 57`–`06 5B` (Booster, MOD, FX, Delay1, Rev/Delay2): `00 = Off` y luego
  `range 01/65/00/100` — el `0..100` que se muestra vive en el crudo `1..101`.

✅ **Corroborado de forma independiente** (2026-09-03): la UI de PC de **Boss Tone Studio**
muestra esos cinco como "Off" y después 0..100. Implementado en `LevelScale`.

✅ **Verificado en el amplificador tras implementarlo (2026-09-03)**: el hueco entre el slider
al 100 y el tope de la perilla física **se hizo más pequeño**, que es justo lo que predice un
desfase de un paso. El crudo 100 que mandaba la app era el 99 del amplificador. Es también el
motivo de que el rango se documentara tanto tiempo como "suposición razonada": lo era, y
estaba desplazada en uno.

El hueco no desapareció del todo, y no hay por qué buscar un segundo desfase: **Presence
(`06 56`) tiene escala directa, nunca estuvo desplazado, y mostraba el mismo hueco**. Lo que
queda es holgura mecánica del propio potenciómetro.

⚠️ **Una ambigüedad que queda a propósito**: en una escala con Off, el crudo `0` y el crudo
`1` se muestran los dos como `0`. Con un slider de 0..100 no se puede hacer mejor, y no hace
falta: lo que distingue "apagado" de "al mínimo" es el switch on/off del efecto, que tiene su
propia dirección y está confirmado. Por lo mismo, **el slider no puede llegar a Off**: mover
al mínimo manda crudo `1`.

También responde a **Program Change** (0–8: BANK_A CH1-4, PANEL, BANK_B CH1-4) y a
**Control Change** (CC16 booster, CC17 mod, CC18 fx, CC19 delay, CC20 reverb, CC7 volumen global),
tabulados en [reference/TuxKatana/params/midi.yaml:23-34](reference/TuxKatana/params/midi.yaml).

**Nada de eso está implementado, y no es gratis.** Todo lo que la app envía hoy es SysEx, y
`packUsbMidi` lo refleja: solo emite los CIN `0x4`–`0x7` y da por hecho una trama `F0…F7`.
Mandar un PC o un CC exigiría una segunda ruta de empaquetado (CIN `0xC` con 2 bytes, `0xB`
con 3). Lo que sí está resuelto es la dirección contraria: `payloadLengthOf` ya cubre la tabla
CIN completa, así que un PC/CC **entrante** se desempaqueta bien. Además, PC y CC viajan por un
canal MIDI y el amplificador solo atiende el que tenga configurado en `00 02 00 00`, que es un
ajuste global del usuario; el SysEx no depende de eso. Ver §5.1.

Al arrancar, la secuencia que usa TuxKatana y que conviene replicar:
Identity Request → nombre del device → nombres de los 8 presets → edit mode ON → dump de memoria.


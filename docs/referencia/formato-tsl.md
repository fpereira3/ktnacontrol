> Archivado desde CLAUDE.md — §5 "Formato `.tsl`"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

#### Formato `.tsl` (investigado 2026-09-05, importación y exportación cableadas 2026-09-06)

⚠️ **Importación, edición offline y exportación implementadas el 2026-09-06 y pendientes de
probar** (`protocol/tsl/`, `library/PresetLibrary`, sección "Biblioteca").

- **Importar y ver** un `.tsl` — §5 "Formato `.tsl`", abajo.
- **Editar sin amplificador** y **crear uno desde cero** — la arquitectura está en §4.5, y lo
  escribe `TslWriter` con la política de bloques poco fiables de más abajo.
- **Exportar** el estado del amplificador conectado a un `.tsl` nuevo
  (`KatanaRepository.exportImage`, botón en Sliders). ⚠️ **Esta sí necesita hardware para
  probarse**; las otras dos no.

##### La política de lo que no es de fiar, al escribir

Al serializar hay tres casos y **cada uno tiene una decisión explícita**, no un valor por
defecto silencioso:

| Caso | Qué se hace |
| --- | --- |
| La imagen tiene los bytes del bloque | **se escriben** |
| No los tiene, pero el fichero de origen sí traía la clave | **se copia verbatim** |
| Ni una cosa ni la otra | **se omite la clave** y se avisa |

⚠️ **La copia verbatim es lo que hace que editar no pierda nada.** Los cuatro bloques en
disputa (los tres `Contour` y `GafcExp1AsgnMinMax`, 82 bytes) no se cargan a propósito al
importar; sin este paso, abrir un preset y volver a guardarlo los **borraría**. Se copian sin
mirarlos: no hace falta saber a qué dirección van para saber que pertenecen a ese preset. Lo
mismo con cualquier clave que el mapa no conozca.

⚠️ **Omitir es deliberado, y la alternativa —escribir ceros— es peor.** Un bloque de Contour a
ceros es un Shape 1 con Freq Shift −50: un valor legal, indistinguible de uno elegido a mano,
que al cargarlo en el amplificador **cambiaría el sonido en silencio**. Una clave que falta,
como mucho, hace que el lector se queje. Entre un fallo ruidoso y uno callado, el ruidoso — es
la misma regla que ya rige la importación, aplicada en la otra dirección. ⚠️ **Sin probar
contra Boss Tone Studio**: ninguna fuente dice si BTS acepta un `.tsl` con claves ausentes. Lo
que sí es seguro es que esta app lo relee sin problema.

##### El preset en blanco

`TslWriter.blank(name)` construye un punto de partida **sin fichero y sin amplificador**.
⚠️ **No es un preset de fábrica de Boss y no lo pretende**: copiar uno de `reference/` metería
material GPL en la app (§7), y ninguna fuente dice cuál sería "el preset vacío" correcto.

Todo a cero, **con dos excepciones documentadas** donde el cero no es neutro sino
estructuralmente inválido:

1. **La cadena de efectos** (`60 00 06 00`–`06 13`) es un **array de permutación**: veinte
   ceros significarían "el compresor veinte veces". Se siembra con la identidad `00`..`13`.
2. **El nombre**, que se rellena con el elegido.

✅ Todo lo demás a cero es legal: los catálogos con huecos del proyecto (`AmpType` sin el `19`,
`BoostType` sin el `07`, `ModFxType`) **sí incluyen el `0x00`** — comprobado con un test que
monta el repositorio real sobre el preset en blanco, porque `KatanaEnumParameter` rechaza en
silencio (§4.3) y un selector inválido no daría error, solo dejaría el control vacío.



**La reutilización que hizo esto barato**: un `.tsl` y un dump de memoria terminan en la misma
forma —trozos de bytes, cada uno con su dirección base—, así que el parser produce un
[`MemoryDump`] y `AmpState.from(dump)` lee **sin cambiar una línea**. Ni `AmpState` ni
`MemoryDump` se tocaron; lo único nuevo es el mapa de claves→dirección y el JSON.

**Tres decisiones al cablearlo:**

1. **Las claves en disputa NO se cargan**, se listan como no disponibles: los tres `Contour`
   (`midi.xml` dice `0F 30`/`38`/`40`, la aritmética de FxFloorboard `0F 2E`/`36`/`3E`) y
   `GafcExp1AsgnMinMax` (`09 30` vs `09 34`). Un desfase de dos daría el Freq Shift donde va el
   Shape y lo enseñaría como si fuera bueno — peor que no enseñar nada. Son 82 de los 1141
   bytes; el resto sí se carga.
2. **`Patch_1` se lee con 91 bytes**, no con los 50 del yaml, que es lo que hace que la cadena
   de efectos, Solo, Contour general y la posición de EQ2 **sí** lleguen. Hay un test que
   comprueba que las 20 posiciones de la cadena vienen enteras.
3. **El tamaño del fichero manda sobre el del mapa.** Si un bloque trae otra longitud, se cargan
   los bytes del fichero y se avisa, en vez de truncar o rellenar — otra revisión del formato
   podría traer bloques distintos y ajustarlos a ciegas sería inventar.

⚠️ **`kotlinx-serialization-json` añadido** (§6 lo permitía "solo si el parseo de presets `.tsl`
lo justifica"). El motivo concreto es que el `org.json` de la plataforma **está apagado en los
tests JVM** —devuelve valores por defecto o lanza— y el parseo del `.tsl` es justo lo que hay
que poder probar sin amplificador.

⚠️ **Corrección de partida: un `.tsl` es un fichero JSON de texto, no un volcado binario.** No
hay "offset en bytes desde el inicio del fichero" que valga: los datos no están en posiciones
fijas del fichero, están en **claves de un objeto JSON**, y la posición de cada clave dentro del
texto cambia con el nombre del preset, los espacios y el orden. La unidad de direccionamiento
del `.tsl` es **el nombre de la clave**, no un offset.

Eso no rompe el plan: lo que hace de "mapa de offsets" es
[presets_addrs.yaml](reference/TuxKatana/params/presets_addrs.yaml), que **mapea cada clave del
JSON a una dirección SysEx y un tamaño**. Es exactamente el puente que hacía falta.

Comprobado sobre un fichero real: `reference/FxFloorboard/default_mk2.tsl` (6381 B, JSON).

##### La envoltura

```json
{ "name": "KATANA Mk2", "formatRev": "0002", "device": "KATANA MkII",
  "data": [ [ { "memo":    { "memo": "", "isToneCentralPatch": true },
                "paramSet": { "UserPatch%PatchName": ["4B","41","54",…],
                              "UserPatch%Patch_0":   ["00","0A","32",…], … } } ] ] }
```

| Campo | Tipo | Qué es |
| --- | --- | --- |
| `name` | string | Nombre del fichero/preset, legible. Redundante con `UserPatch%PatchName`. |
| `formatRev` | string | Revisión del formato. `"0002"` en Mk2. |
| `device` | string | **`"KATANA MkII"`** — es el discriminante del modelo. |
| `data` | array | `data[0]` es la **lista de presets**; un `.tsl` puede llevar varios. |
| `memo` | objeto | Nota libre del usuario + `isToneCentralPatch` (booleano). |
| `paramSet` | objeto | **Los datos del preset**: 22 claves → array de bytes. |

✅ **`device` y `formatRev` son discriminantes reales, no decorativos.**
[tsl.py:35](reference/TuxKatana/lib/tsl.py) rechaza el fichero si
`data['device'] != "KATANA MkII"`, y [tsl.py:26](reference/TuxKatana/lib/tsl.py) anota que la
revisión `"0002"` es lo que *"seem to tell the `UserPatch%Patch_Mk2V2` presence"* — o sea, el
bloque extra de firmware 2 solo está a partir de esa revisión.

⚠️ **No todo `.tsl` es de Katana.** El otro fichero del repo,
`reference/FxFloorboard/default.tsl`, tiene `device: "GT"` y un esquema **completamente
distinto** (`liveSetData` + `patchList`, con `params` en vez de `paramSet`). Es de la serie GT.
Comprobar `device` antes de parsear no es paranoia: son formatos que solo comparten la
extensión.

##### Codificación de los datos

Cada valor de `paramSet` es un **array JSON de strings de dos caracteres hexadecimales en
mayúsculas**, un string por byte:

```json
"UserPatch%PatchName": ["4B","41","54","41","4E","41","20","4D","6B","32","20","20","20","20","20","20"]
```

que es `KATANA Mk2` seguido de espacios (`20`). **Son los bytes SysEx crudos, sin transformar**:
el mismo `4B 41 …` que viajaría por el cable. No hay checksum, ni compresión, ni escapado, ni
reordenación — el checksum es de la trama SysEx, y en el fichero no hay tramas.

##### El mapa: clave → dirección SysEx

Esta es la tabla que convierte el `.tsl` en direcciones. **Los tamaños están verificados contra
el fichero real**, no solo leídos del yaml.

| Clave `paramSet` | Dirección SysEx | Bytes | Tipo | Qué contiene |
| --- | --- | --- | --- | --- |
| `UserPatch%PatchName` | `60 00 00 00`–`00 0F` | 16 | ASCII | Nombre del preset, rellenado con `20` |
| `UserPatch%Patch_0` | `60 00 00 10`–`00 57` | 72 | binario | Booster completo + PREAMP completo + EQ1 |
| `UserPatch%Eq(2)` | `60 00 00 60`–`00 77` | 24 | binario | EQ2 (paramétrico + gráfico) |
| `UserPatch%Fx(1)` | `60 00 01 00`–`02 5C` | 221 | binario | **MOD**: on/off, tipo y los bloques internos de los tipos |
| `UserPatch%Fx(2)` | `60 00 03 00`–`04 5C` | 221 | binario | **FX**: ídem, la imagen +`0x0200` de la anterior |
| `UserPatch%Delay(1)` | `60 00 05 00`–`05 19` | 26 | binario | Delay 1 completo |
| `UserPatch%Delay(2)` | `60 00 05 20`–`05 39` | 26 | binario | Delay 2 completo |
| `UserPatch%Patch_1` | `60 00 05 40`–`06 1A` | **91** | binario | Reverb + Noise Gate + Master + **cadena** + Solo + Contour + posición EQ2 |
| `UserPatch%Patch_2` | `60 00 06 20`–`06 43` | 36 | binario | Tipo de cadena, posiciones, **tipos por color** de los 5 efectos, colores activos |
| `UserPatch%Status` | `60 00 06 50`–`06 61` | 18 | binario | **Las 12 perillas del panel** + los 6 `led state` |
| `UserPatch%KnobAsgn` | `60 00 07 00`–`07 20` | 33 | binario | Asignación de perillas |
| `UserPatch%ExpPedalAsgn` | `60 00 08 00`–`08 20` | 33 | binario | Asignación del pedal de expresión |
| `UserPatch%ExpPedalAsgnMinMax` | `60 00 08 30`–`08 7B` | 76 | binario | Mín/máx de esa asignación |
| `UserPatch%GafcExp1Asgn` | `60 00 09 00`–`09 20` | 33 | binario | GA-FC pedal 1 |
| `UserPatch%GafcExp1AsgnMinMax` | `60 00 09 30`–`09 7B` ⚠️ | 76 | binario | Mín/máx GA-FC 1 |
| `UserPatch%GafcExp2Asgn` | `60 00 0A 00`–`0A 20` | 33 | binario | GA-FC pedal 2 |
| `UserPatch%GafcExp2AsgnMinMax` | `60 00 0A 30`–`0A 7B` | 76 | binario | Mín/máx GA-FC 2 |
| `UserPatch%FsAsgn` | `60 00 0F 08`–`0F 09` | 2 | binario | Asignación de footswitch |
| `UserPatch%Patch_Mk2V2` | `60 00 0F 10`–`0F 25` | 22 | binario | Añadidos del firmware 2 (solo con `formatRev` ≥ `"0002"`) |
| `UserPatch%Contour(1)` | `60 00 0F 30`–`0F 31` ⚠️ | 2 | binario | Contour 1: Shape + Freq Shift |
| `UserPatch%Contour(2)` | `60 00 0F 38`–`0F 39` ⚠️ | 2 | binario | Contour 2 |
| `UserPatch%Contour(3)` | `60 00 0F 40`–`0F 41` ⚠️ | 2 | binario | Contour 3 |

**Total: 1141 bytes en 22 bloques.**

##### ⚠️ Tres correcciones a `presets_addrs.yaml`

El yaml es la guía correcta, pero tiene fallos concretos que hay que arreglar antes de usarlo:

1. ❌ **`UserPatch%Patch_1` dice `size: 50` y son 91.** El fichero real trae 91 bytes en esa
   clave, y [sysxWriter.cpp:363-366](reference/FxFloorboard/sysxWriter.cpp) lo construye como
   `64 + 27 = 91` bytes. **Con 50 se perderían el bloque de cadena entero, Solo, Contour y la
   posición de EQ2** — o sea, casi todo lo que se documentó en "Controles sin perilla física".
   91 bytes desde `60 00 05 40` llegan exactamente a `60 00 06 1A`, que es el último control de
   ese tramo (Contour Freq Shift). Cuadra a la perfección.
2. ✅ **Nueve claves tienen `addr` vacío en el yaml y ahora tienen dirección.** TuxKatana sabía
   sus tamaños pero no dónde vivían. Se resolvieron con la aritmética de
   [sysxWriter.cpp:377-390](reference/FxFloorboard/sysxWriter.cpp) sobre el volcado de patch
   (`default.syx`), que es una tira de mensajes SysEx de 128 bytes de datos cada uno (12 de
   cabecera + 128 + checksum + `F7` = 142): `KnobAsgn` → `60 00 07 00`, `ExpPedalAsgn` →
   `08 00`, `GafcExp1Asgn` → `09 00`, `GafcExp2Asgn` → `0A 00`, `FsAsgn` → `0F 08`,
   `Patch_Mk2V2` → `0F 10`, y los tres `Contour` en `0F 30`/`0F 38`/`0F 40`.
3. ⚠️ **`GafcExp1AsgnMinMax`: el yaml dice `09 30` y la aritmética de FxFloorboard da `09 34`.**
   Sus dos hermanas caen limpiamente en `08 30` y `0A 30`, así que el `09 30` del yaml es el que
   encaja con el patrón y el `09 34` parece un desliz del escritor. El propio yaml marca esa
   línea, y solo esa, con el comentario `# ⚠️ Sequence not valuable +1` — su autor ya había
   visto que ahí había algo raro. **Se documenta `09 30` y queda como TBD.**

⚠️ **Y una discrepancia que NO se resuelve sola: los tres `Contour`.** La aritmética de
FxFloorboard da `0F 2E`, `0F 36`, `0F 3E`; `midi.xml` dice `0F 30`, `0F 38`, `0F 40` (§5,
"Controles sin perilla física"). Difieren en 2, con el mismo paso de 8. **Se documenta la de
`midi.xml`** por dos razones: es una afirmación directa (`<DATA value="30" … desc="Contour 1:"
customdesc="Contour Shape">`, `midi.xml:50135`) frente a una aritmética de offsets, y esa misma
aritmética ya falla en `GafcExp1AsgnMinMax` — un escritor con un desfase demostrado no es buen
árbitro para otro desfase. **TBD, y es de las primeras cosas a comprobar al leer un `.tsl` real
exportado desde Boss Tone Studio.**

##### ¿Volcado directo o envuelto? Ni una cosa ni la otra

La pregunta admite una respuesta precisa, y son tres afirmaciones distintas:

1. **Los bytes de dentro sí son 1:1 con SysEx.** Cada array es el contenido crudo de un rango de
   direcciones, sin transformar. Escribirlos al amplificador es "por cada clave, un SET a su
   dirección con esos bytes" — nada más.
2. **Pero el fichero NO es un volcado de memoria.** No hay una imagen contigua ni un offset
   global: hay 22 bloques con nombre, y **entre ellos hay huecos reales de direcciones** que el
   fichero simplemente no guarda (8 B entre `Patch_0` y `Eq(2)`, 35 B entre `Fx(2)` y
   `Delay(1)`, 12 B antes de `Status`, 206 B antes de `ExpPedalAsgnMinMax`…). Un parser que
   asuma continuidad se desalinea en el segundo bloque.
3. **Y guarda poco más de la mitad del preset**: 1141 bytes de los 1992 que pide el editor en un
   volcado de patch (`patchRequestDataSize = "00000F48"`,
   [globalVariables.h:65](reference/FxFloorboard/globalVariables.h)), o sea el **57 %**. Lo que
   falta son rangos sin parámetros o no guardables.

O sea: **envoltura JSON ligera + contenido crudo por bloques**. No hay checksums, ni longitudes,
ni compresión, ni cabeceras binarias que descifrar.

##### Qué parte del `.tsl` es cada cosa

- **Metadatos**: `name`, `formatRev`, `device` (envoltura) y `memo` (por preset). ⚠️ **No hay
  fecha ni versión de firmware** en ninguna parte del formato — si la app quiere fechar un
  export, tendrá que ponerlo en `memo`, que es el único campo libre.
- **Los 24+ controles ya implementados**: repartidos entre `Status` (las 12 perillas del panel,
  `60 00 06 50`–`06 61`), `Patch_0` (modelo de amplificador `00 21`, y el Booster entero
  `00 10`–`00 18`) y `Patch_2` (los cinco selectores de color). ✅ **Verificado byte a byte**
  sobre `default_mk2.tsl`: `60 00 06 51` (Gain) cae en `Status[1]` y vale `0x32` = 50;
  `60 00 00 21` cae en `Patch_0[17]` y vale `0x08` = Clean.
- **Efectos, tipos activos y parámetros internos**: `Fx(1)` es Mod completo, `Fx(2)` es FX,
  `Delay(1)`/`Delay(2)` los dos delays, y la reverb va dentro de `Patch_1`. Los **tipos por
  color** (`06 24`–`06 38`) están en `Patch_2`.
  ✅ **Un chequeo de consistencia que sale bien**: en el fichero, el color de Booster
  (`06 39` → `Patch_2[25]`) vale `00` = verde, el tipo del slot verde (`06 24` → `Patch_2[4]`)
  vale `0A` y el tipo activo (`00 11` → `Patch_0[1]`) vale también `0A` = Blues Drive. Es
  justo lo que §5.2 predice: **el tipo activo refleja el slot del color encendido.** Un fichero
  real confirma el modelo.
- **Cadena de efectos y posiciones de EQ**: sí están, y no en un sitio obvio — la cadena de 20
  ranuras (`60 00 06 00`–`06 13`) cae **dentro de `UserPatch%Patch_1`, en los índices 64–83**,
  que es precisamente la parte que se perdería con el `size: 50` del yaml. Las posiciones:
  EQ2 en `Patch_1[89]` (`06 19`) y EQ1 en `Patch_2[2]` (`06 22`).
  ✅ **La cadena del fichero por defecto es una permutación completa de los 20 identificadores**,
  y en un orden que tiene sentido musical:
  `PDL → OD → FX1 → FX2 → EQ1 → EQ2 → CH_A → CS → NS_1 → FV → LP → DD1 → CN_S → DD2 → RV → CAB
  → CH_B → NS_2 → USB → CN_M`
  (booster antes del previo, delays y reverb después). Confirma con un dato real el modelo de
  "array de permutación" que §5 dedujo de `midi.xml`.

##### Lo que el `.tsl` NO guarda

⚠️ **Pedal Bend de Mod y de FX se quedan fuera.** `Fx(1)` acaba en `60 00 02 5C` (Heavy Octave
Direct Mix) y el bloque de Mod sigue hasta `02 60` con los cuatro parámetros de Pedal Bend
(§5.2); igual en `Fx(2)` con `04 5D`–`04 60`. Son 8 direcciones documentadas que un
export/import por `.tsl` **perdería**. Puede que vivan en `Patch_Mk2V2` (22 bytes sin desglosar,
justo el bloque que el firmware 2 añadió), pero **eso es una conjetura sin comprobar** — el
contenido de `Patch_Mk2V2` no está documentado en ninguna fuente.

Tampoco está el canal activo (`00 01 00 00`), y es correcto que no esté: un preset describe un
sonido, no en qué ranura se carga. El destino se elige al guardar (§5, "Guardado de presets").

##### Cómo queda el trabajo de implementación

Con el mapa de arriba, leer y escribir `.tsl` es mecánico:

```
Importar:  JSON.parse → comprobar device == "KATANA MkII"
           → por cada clave de paramSet: dirección = MAPA[clave]
           → bytes = array.map { it.toInt(16) }
           → SET a esa dirección  (+ commit con 7F 00 01 04 si se quiere fijar en un canal)
Exportar:  por cada entrada del MAPA: GET dirección/tamaño (o leerlo del dump ya cacheado)
           → array de "%02X" → JSON.stringify con la envoltura
```

**El proyecto ya tiene todas las piezas**: `Address` hace la aritmética base 128, `RolandSysEx`
construye los SET, `MemoryDump` ya sostiene la mayor parte de estos rangos, y `KatanaRepository`
sabe escribir. Lo único nuevo es el mapa de 22 entradas y el JSON — y para el JSON, §6 dice que
`org.json` de la plataforma puede bastar y que `kotlinx-serialization-json` solo se justifica si
el parseo lo pide.

⚠️ **Una nota de tamaño**: dos bloques superan los 128 bytes (`Fx(1)` y `Fx(2)` con 221;
`ExpPedalAsgnMinMax` y sus dos hermanas se quedan en 76). Un SET de 221 bytes cruza el límite de
página de direcciones, así que trocearlo obliga a sumar en base 128 y no al último byte.

✅ **Troceado implementado el 2026-09-08** en `RolandSysEx.setChunked` + `protocol/tsl/TslTransfer`
+ `KatanaRepository.sendPreset`, con **128 bytes de datos por mensaje**. Ese 128 **no sale de
ninguna fuente que diga cuál es el máximo** —eso sigue siendo TBD— sino del único tamaño de SET
masivo que se observa en una fuente de Mk2: el volcado de patch de `sysxWriter.cpp:377-390` es una
tira de mensajes de 128 bytes de datos (12 + 128 + checksum + `F7` = 142).

❌ **Corrección: la frase que decía que 221 bytes "no caben en un solo paquete USB de 512 B" era
falsa, y la aritmética que la acompañaba también.** Un SET de 221 bytes de datos son `14 + 221 =
235` bytes de mensaje; empaquetados en tramas USB-MIDI de 4 bytes dan `ceil(235/3) × 4 = **316**`
bytes en el cable, no "300+ que no caben": **caben de sobra** en los 512 de `wMaxPacketSize`
(§4.1). Así que trocear **no lo obliga el transporte**; lo aconseja no ser el primero en probar si
el amplificador digiere un SET de 221 bytes de una sentada. Sigue sin haber fuente que lo diga:
**TBD, y es justo lo que la prueba con amplificador tiene que despejar** — si 221 de una vez
también funciona, el troceado sobra (pero no estorba).


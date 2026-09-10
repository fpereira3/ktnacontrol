> Archivado desde CLAUDE.md — §5 "Guardado de presets"
> Fecha de archivado: 2026-09-10. Registro fiel: copiado tal cual, sin reescribir ni corregir.

#### Guardado de presets (investigado 2026-09-05, cableado 2026-09-06)

✅ **`7F 00 01 04` es correcto y vale para el Mk2**, no solo para el MK1. Y hay **una segunda
vía completamente distinta**, por Control Change, que ninguna nota previa del proyecto
mencionaba.

⚠️ **La vía SysEx está cableada desde el 2026-09-06 y sin probar contra el amplificador**
(`protocol/PresetSave.kt`, `KatanaRepository.savePreset`, botón "Guardar preset…" en Sliders).
La vía por Control Change **no**: sigue necesitando una segunda ruta de empaquetado que el
proyecto no tiene.

**Tres decisiones al cablearlo, todas por la misma razón —es destructivo y no se confirma—:**

1. **Solo los ocho canales; PANEL (`00`) no se ofrece.** Sigue siendo TBD qué hace el
   amplificador con él, y un destino de efecto desconocido no es algo que convenga poner en un
   desplegable al lado de los que sí se entienden. `PresetSave.commitMessage` **rechaza**
   cualquier valor fuera de `01`..`08` en vez de clampearlo: clampear elegiría un canal por su
   cuenta en una operación sin deshacer.
2. **No se toca el edit mode**, aunque la secuencia del MK1 lo ponga como paso 1. Es un ajuste
   explícito del usuario (§4.2) y encenderlo de tapadillo sería moverle un interruptor por la
   espalda; en su lugar la UI **exige** que esté encendido (el botón cae bajo `canEdit`, como
   cualquier otro parámetro y a diferencia del selector de canal).
3. **La verificación es releer el nombre del canal destino** en `10 0N 00 00` y compararlo. Es
   lo más parecido a una confirmación que existe. ⚠️ Y se informa con cuidado: que el nombre
   **no** cuadre no prueba que el guardado fallara —puede que el amp tarde en actualizar esa
   tabla, cosa que ninguna fuente aclara— así que el log dice "o el guardado no entró, o el amp
   no actualizó todavía esa tabla", no "falló".

⚠️ **El margen entre el nombre y el commit (50 ms) es criterio propio, no un dato.** Ninguna
fuente documenta ni ese intervalo ni el mínimo entre dos guardados; se eligió por analogía con
el único margen documentado, que es el de alrededor del **edit mode**. Por lo mismo la app no
deja dos guardados solapados.

##### Tabla resumen

| | Vía SysEx | Vía Control Change |
| --- | --- | --- |
| Dirección / mensaje | `7F 00 01 04` (SET, `12`) | CC#8 y CC#9 |
| Dato | **2 bytes**: `00 xx` | CC#8 valor `127`; CC#9 valor `1`..`8` |
| Qué es `xx` | canal destino, `00` PANEL … `08` CH B4 | CC#9: canal `1`..`8` (sin PANEL) |
| Guarda en el canal actual | no, hay que decirlo | **sí, eso hace CC#8** |
| ¿Confirma? | sin documentar; todo apunta a fire-and-forget | sin documentar |
| Ámbito | commit del búfer de edición al canal destino | ídem |
| Fuente | `katana_sysex.txt` (MK1) + `patchWriteDialog.cpp` (**Mk2**) | PDF MIDX-20 + `midi.yaml` (comentado) |
| Confianza | **dos fuentes, una de ellas Mk2 y en código** | dos fuentes de Mk2, ninguna en código |

##### 1. Qué dato se envía

**No se envían los datos del preset.** El amplificador ya tiene el estado editado en su búfer
(es lo que la app viene modificando parámetro a parámetro); `7F 00 01 04` es un **commit**: le
dice "coge lo que tienes ahora y escríbelo en el canal *xx*". Son **2 bytes de dato**, igual
que el canal activo de §5.1.

✅ **Confirmado para el Mk2 en código**, no por analogía con el MK1:
[patchWriteDialog.cpp:346](reference/FxFloorboard/patchWriteDialog.cpp) construye literalmente

```cpp
sysxMsg = "F0410000000033127F00010400"+addr+"00F7";
```

que se descompone como `F0 41 00 00 00 00 33` (prefijo Roland con model id `33`, el del
Katana) · `12` SET · **`7F 00 01 04`** dirección · `00 <addr>` dato de 2 bytes · `00` checksum
· `F7`.

⚠️ **Ese `00` final es un marcador, no el checksum real.**
[midiIO.cpp:504-512](reference/FxFloorboard/midiIO.cpp) recalcula el checksum de todo mensaje
saliente antes de mandarlo, sumando desde el byte 8 (`checksumOffset = 8` en
`globalVariables.h:49`, que es exactamente el primer byte de la dirección) con la fórmula
`128 - suma % 128` de siempre. O sea: **el mismo checksum que ya calcula `RolandSysEx`**, sin
nada especial. Los mensajes reales quedan así:

| Destino | Dato | Mensaje completo |
| --- | --- | --- |
| PANEL | `00 00` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 00 7C F7` |
| CH A1 | `00 01` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 01 7B F7` |
| CH A2 | `00 02` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 02 7A F7` |
| CH A3 | `00 03` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 03 79 F7` |
| CH A4 | `00 04` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 04 78 F7` |
| CH B1 | `00 05` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 05 77 F7` |
| CH B2 | `00 06` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 06 76 F7` |
| CH B3 | `00 07` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 07 75 F7` |
| CH B4 | `00 08` | `F0 41 00 00 00 00 33 12 7F 00 01 04 00 08 74 F7` |

##### 2. ¿Confirma el amplificador?

**Ninguna fuente documenta una respuesta**, y la evidencia disponible apunta a que **no la
hay**: es fire-and-forget, con la misma semántica DT1 del edit mode (§4.2).

Lo más informativo es lo que FxFloorboard **dejó comentado** justo alrededor del envío
(`patchWriteDialog.cpp:348-350`):

```cpp
//QObject::disconnect(sysxIO, SIGNAL(sysxReply(QString)));
//QObject::connect(sysxIO, SIGNAL(sysxReply(QString)), sysxIO, SLOT(resetDevice(QString)));
sysxIO->sendSysx(sysxMsg);   // Send the data.
```

Manda y cierra el diálogo acto seguido, sin esperar nada. Que el enganche a `sysxReply` esté
ahí escrito y anulado sugiere que alguien intentó tratar una respuesta y acabó quitándolo —
pero eso es una lectura de la intención, no un dato. **TBD, probar con amplificador.**

##### 3. ¿Hay que decir a qué canal, y con qué numeración?

**Con la vía SysEx, sí: el canal destino es el dato.** No existe un "guardar donde estoy" por
SysEx; para eso está CC#8.

✅ **La numeración es la misma que la del canal activo `00 01 00 00` (§5.1)**: `00` PANEL,
`01`..`08` los ocho canales en orden A1-A4, B1-B4. Se deduce de FxFloorboard cruzando dos
sitios del mismo fichero:

- La lista de destinos (`patchWriteDialog.cpp:174-190`) etiqueta la fila `z`: `z==0` →
  `"PANEL: "`, `z==1` → `"CH A1: "`, … `z==8` → `"CH B4: "`.
- El envío (`:327` y `:341`) hace `patch = currentRow()+1` y luego `addr = num-1`, o sea
  **`addr` = el índice de fila `z` tal cual**.

Y lo corrobora el ajuste para el Katana 50 (`:336-339`), que tiene 5 canales:

```cpp
if(model<1 && patch>3) { num = num+2; }  // kat50 channels: panel==0, A1==1, A2==2, B1==5, B2==6
```

Es decir: incluso en el amplificador de 5 canales, los canales B siguen valiendo `05` y `06`,
no `03` y `04`. **La numeración 0..8 es del protocolo, no de la lista de la interfaz** — que es
justo lo que hacía falta saber.

⚠️ **Corrección a lo que decía §5 hasta ahora.** La tabla de direcciones clave describía
`7F 00 01 04` como "(`00 xx`, xx = 01..04)". Ese `01..04` es el rango del **MK1** copiado tal
cual de [katana_sysex.txt:150-155](reference/katana-midi-bridge/doc/katana_sysex.txt), donde el
amplificador solo tenía cuatro canales. En el Mk2 llega hasta `08`. Los dos coinciden en el
tramo que comparten (Ch1 = `01`), así que no era una contradicción, solo un rango incompleto.

⚠️ **Lo que sigue sin estar claro es si `00` (PANEL) es un destino legal o un artefacto de la
interfaz de FxFloorboard.** Guardar "en el panel" no tiene un significado obvio —el panel es
precisamente el estado no guardado—, pero su lista incluye la fila y el código no la excluye.
**TBD, probar con amplificador**, y si hay que apostar, empezar por los canales `01`..`08`.

##### 4. ¿Hace falta un tiempo mínimo entre guardados?

⚠️ **Ninguna fuente documenta un intervalo mínimo entre guardados sucesivos.** No hay nada, ni
en el MK1, ni en FxFloorboard, ni en el PDF. Lo que sí hay es un dato de temporización **de
otra cosa**, y conviene no confundirlos: el retardo documentado es alrededor del **edit mode**,
no del guardado.

- [katana_sysex.txt:142-143](reference/katana-midi-bridge/doc/katana_sysex.txt): *"The amp
  needs a short settling period after the control mode command. 50-100 msec. seems
  reasonable."*
- [bankTreeList.cpp:337-340](reference/FxFloorboard/bankTreeList.cpp) hace exactamente eso en
  el Mk2: `SLEEP(50)` → SET de edit mode → `SLEEP(50)`.

Dos fuentes independientes, una de Mk2, coinciden en ~50 ms alrededor del edit mode. Para el
guardado en sí, **TBD**. Lo prudente al implementarlo es dejar un margen y no permitir
guardados en ráfaga, pero eso es criterio propio, no un dato de las fuentes.

##### 5. El "Saving in progress..." del panel

❌ **Ninguna fuente lo documenta, en ningún sentido**: ni una dirección que lo reporte, ni un
GET que lo consulte, ni un flag de "ocupado". Se buscó explícitamente por `saving`,
`in progress`, `busy` y `write in progress` en las cuatro fuentes y no aparece nada.

Lo único adyacente es que FxFloorboard muestra su propio texto `"Writing Patch"` en la barra de
estado (`patchWriteDialog.cpp:326`), que es un mensaje **de su interfaz**, generado localmente
al pulsar el botón — no algo que el amplificador reporte.

**Hipótesis por defecto: es solo visual, del panel del amplificador, y no consultable.** Es
coherente con que el guardado sea fire-and-forget. Pero es una hipótesis: **TBD**, y la forma
de comprobarlo es mirar si llega algún mensaje espontáneo por el endpoint de entrada mientras
el panel muestra ese texto (con edit mode activo, que es lo que hace que el amplificador
reporte, §4.2).

##### La vía alternativa: Control Change #8 y #9

**Es la respuesta directa a "¿hace falta especificar el canal?": con CC#8, no.**

| Mensaje | Efecto | Valor |
| --- | --- | --- |
| CC#8 | guardar en el **canal actual** | `127` |
| CC#9 | guardar en un canal concreto | `1`..`8` |

Dos fuentes de Mk2 coinciden byte a byte:

- El PDF de implementación MIDI,
  [MIDX_20_KatanaMKIIV1.pdf](reference/TuxKatana/doc/MIDX_20_KatanaMKIIV1.pdf), sección
  "Miscellaneous CC's": `STORE TO CURRENT PRESET = CC# 8 (value=127)` y
  `STORE TO PRESET = CC# 9 (1-8)`.
- [reference/TuxKatana/params/midi.yaml:67-71](reference/TuxKatana/params/midi.yaml), que los
  tiene **comentados** (sin implementar) pero con los mismos valores:
  `Store_Current_Preset: # CC #8 / val: 0x7f` y `Store_To_Preset: # CC #9 / start: 0x01,
  end: 0x08`.

Que estén comentados no dice nada del hardware: es exactamente el caso de `60 00 06 57`, que
`booster.yaml` listaba bajo `Unimplemented:` y funcionó igual (§5, "el ruido de las fuentes no
predice el resultado").

⚠️ **Dos cautelas serias con esta vía**, las dos de peso:

1. **El PDF es del MIDX-20, un puente MIDI de terceros, no la implementación MIDI oficial de
   Boss.** Su portada dice "MIDX Boss Katana™ MKII Bridge — MIDI Implementation" y avisa de que
   "Text in RED indicate features not available with BOSS Tone Studio". Buena parte de su tabla
   coincide con los CC nativos que ya documenta `midi.yaml` (CC#16-20, CC#7), lo que sugiere
   que describe lo que el amplificador acepta y no lo que el puente inventa — pero **no está
   probado**, y podría ser que CC#8/#9 los implemente el puente traduciéndolos a SysEx.
2. **El proyecto no puede mandar CC hoy.** `packUsbMidi` solo emite los CIN `0x4`–`0x7` y da por
   hecho una trama `F0…F7` (§5, final). Mandar un CC exige una segunda ruta de empaquetado
   (CIN `0xB`, 3 bytes). Además PC y CC viajan por un canal MIDI y el amplificador solo atiende
   el que tenga en `00 02 00 00`, un ajuste global del usuario; el SysEx no depende de eso.

**Por eso la vía SysEx es la que conviene implementar primero**: no necesita nada nuevo del
transporte, y está confirmada en código de una app de Mk2.

##### El nombre del preset, que es parte del guardado

⚠️ **El MK1 dice que hay que escribir el nombre ANTES de guardar**, y el Mk2 lo hace de otra
manera. [katana_sysex.txt:145-147](reference/katana-midi-bridge/doc/katana_sysex.txt) lo pone
como paso explícito de la secuencia:

```
Send name of amp first:
60 00 00 00 --> 4B 41 54 41 4E 41 20 20 20 20 20 20 20 20 20 20      ("KATANA" + 10 espacios)
```

y lo repite en `:1989` (*"Amp Name (Write here before preset store)"*).

FxFloorboard **no manda el nombre dentro de la rutina de guardado**: su `writeToMemory()` usa
`getCurrentPatchName()` solo para refrescar su propia lista. El nombre viaja antes, como un
parámetro normal más: `renameWidget.cpp:68` escribe los 16 bytes ASCII en el origen
`"Structure"` con `nameAddress = "00"` (`globalVariables.h:76`), o sea `60 00 00 00`, y se
sincroniza por el camino habitual de cambio de parámetro.

Las dos formas acaban en lo mismo — **los 16 bytes del nombre están en `60 00 00 00` antes de
mandar el commit**— y coincide con `presets_addrs.yaml:1-3`
(`UserPatch%PatchName: addr '60 00 00 00', size 16`) y con `HOW.md:10`. Para esta app significa
que **el guardado son dos pasos**: escribir el nombre y después el commit, no solo el commit.

Esto además toca un cabo suelto conocido: §5 anota que los primeros 16 bytes de `60 00 00 00`
"serían el nombre del preset actual — pero lo que devolvió el amplificador ahí no se parece a
un nombre" (ver BACKLOG). Tres fuentes dicen que ahí va el nombre, así que la discrepancia está
en la lectura, no en la dirección.

##### Secuencia completa propuesta (sin probar)

Juntando el MK1 (que da el orden) y FxFloorboard (que da los bytes del Mk2):

```
1. Edit mode ON        SET 7F 00 00 01 -> 01        (ya implementado, §4.2)
2. esperar ~50 ms                                    (katana_sysex.txt + bankTreeList.cpp)
3. Nombre del preset   SET 60 00 00 00 -> 16 bytes ASCII
4. Commit              SET 7F 00 01 04 -> 00 xx      (xx = canal destino, 01..08)
5. (opcional) Edit mode OFF  SET 7F 00 00 01 -> 00
```

⚠️ El paso 5 lo hace el MK1 al final de la secuencia. **En esta app no conviene copiarlo tal
cual**: el edit mode es un ajuste explícito y visible del usuario (§4.2), y apagarlo como
efecto secundario de guardar dejaría la UI sin poder confirmar nada de lo que escriba después.

##### Qué no dice ninguna fuente

Resumido, para no volver a buscarlo:

- Si el guardado responde algo. **TBD.**
- Si `00` (PANEL) es un destino legal. **TBD.**
- Cuánto hay que esperar entre dos guardados. **TBD.**
- Qué dirección reporta "Saving in progress...". **Ninguna fuente lo documenta**; la hipótesis
  es que es solo del panel.
- Si CC#8/#9 los atiende el amplificador o el puente MIDX-20. **TBD.**
- Qué pasa si se guarda con el edit mode apagado. Ninguna fuente lo dice; el MK1 lo pone dentro
  de la secuencia de BTS mode, así que lo prudente es asumir que hace falta.


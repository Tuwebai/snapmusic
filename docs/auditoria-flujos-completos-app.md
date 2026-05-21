# Auditoría integral de flujos de la app — SnapMusic

## Fecha
- 2026-05-21

## Alcance real de esta auditoría
- Build auditada: `1.0.112`
- Dispositivo detectado por ADB: `cff29cc6`
- Base usada para esta auditoría:
  - app instalada y proceso activo en el teléfono
  - `adb shell am start -W`
  - `adb shell dumpsys gfxinfo com.juan.snapmusic`
  - `adb logcat -d`
  - auditorías previas ya persistidas en `docs/`
  - revisión del estado actual del código

## Resumen ejecutivo
- La app sigue teniendo una deuda real de fluidez.
- El dato más duro de esta auditoría es `gfxinfo`:
  - `118` frames renderizados
  - `108` frames con jank (`91.53%`)
  - p50 `101ms`
  - p90 `350ms`
  - p95 `450ms`
  - `99` eventos de `Slow UI thread`
  - `108` eventos de `Slow issue draw commands`
- Eso confirma que el problema ya no es un bug visual aislado: sigue habiendo trabajo pesado en UI, navegación y reproducción.
- En logs del dispositivo aparecen ciclos repetidos de `AudioTrack pause/stop/start`, compatibles con reconfiguraciones de playback todavía demasiado frecuentes.

## Evidencia directa tomada del teléfono

### Arranque / entrega de actividad
- `adb shell am start -W -n com.juan.snapmusic/.MainActivity`
- Resultado:
  - `Status: ok`
  - `WaitTime: 38`
  - el proceso ya estaba vivo y la activity se reentregó a la instancia superior

### Render / jank
- `adb shell dumpsys gfxinfo com.juan.snapmusic`
- Resultado relevante:
  - `Janky frames: 108 (91.53%)`
  - `50th percentile: 101ms`
  - `90th percentile: 350ms`
  - `95th percentile: 450ms`
  - `99th percentile: 1450ms`
  - `Slow UI thread: 99`
  - `Slow issue draw commands: 108`

### Logs de reproducción
- `adb logcat -d`
- Resultado relevante:
  - secuencias repetidas de `AudioTrack pause`
  - `AudioTrack stop`
  - `AudioTrack start`
- Lectura práctica:
  - todavía hay cambios de estado o recreaciones del playback que no deberían pasar tan seguido en flujos normales

## Estado por flujo

### 1. Inicio / Home
**Estado:** mejorado, no cerrado

#### Síntomas
- la raíz de la app sigue pesada cuando conviven:
  - tab superior
  - mini player
  - watch shell
  - overlays de búsqueda

#### Causas raíz probables
- observación amplia en `SnapMusicApp` y `SnapMusicNavHost`
- render costoso del árbol principal cuando cambia reproducción o navegación
- shell de Home todavía comparte demasiado costo con tabs hermanas

#### Severidad
- alta

---

### 2. Tab YouTube / feed
**Estado:** mejorado, no cerrado

#### Síntomas
- el feed ya dejó de sentirse tan corto, pero sigue siendo sensible a:
  - scroll largo
  - load more
  - apertura del watch
  - enriquecimiento de relacionados

#### Causas raíz probables
- ranking y continuidad de feed todavía mezclan demasiadas fuentes
- la paginación ya existe, pero sigue habiendo puntos donde el lote siguiente depende de resolución de continuaciones o búsquedas paralelas
- el feed y watch-next todavía comparten parte de la presión de composición

#### Severidad
- alta

---

### 3. Búsqueda YouTube
**Estado:** mejorado, no cerrado

#### Síntomas
- el submit mejoró, pero todavía no se siente tan inmediato como YouTube real
- la latencia visible depende mucho del primer request y de cómo se enruta el resultado al tab YouTube

#### Causas raíz probables
- costo real de red + extracción
- todavía hay trabajo de transformación de resultados en el camino UI
- el flujo Buscar → YouTube sigue acoplado al árbol de Home

#### Severidad
- media/alta

---

### 4. Watch screen / apertura de video
**Estado:** crítico

#### Síntomas
- algunos videos tardan varios segundos en arrancar
- a veces se ven como pausados mientras realmente están cargando
- hubo regresiones recientes ligadas a:
  - cambio de calidad
  - actualización de relacionados
  - autoplay al siguiente

#### Causas raíz probables
- fallback a `MergingMediaSource` en videos sin manifest adaptativo
- reconfiguración de pistas o de fuente en el arranque
- cambios de metadata/cola/relacionados muy cerca del arranque del stream
- buffer y heurísticas todavía más costosas que YouTube oficial

#### Severidad
- crítica

---

### 5. Selector de calidad
**Estado:** corregido estructuralmente, no cerrado UX/perf

#### Síntomas actuales
- ya no quedó clavado siempre en 360p en los casos corregidos
- sigue pendiente que el cambio de calidad se sienta casi instantáneo
- todavía puede haber pausa negra visible al subir o bajar calidad

#### Causas raíz probables
- cambio real de source/pistas
- necesidad de reconstrucción parcial cuando se fusiona video+audio separados
- ausencia de un warm path más agresivo para la calidad siguiente

#### Severidad
- alta

---

### 6. Relacionados / “Sigue”
**Estado:** mejorado, no cerrado

#### Síntomas
- hubo avances en relevancia, pero sigue siendo un área inestable
- el usuario reportó refresh molesto o carga tardía del bloque relacionado

#### Causas raíz probables
- enriquecimiento posterior al arranque del watch
- mezcla entre cola viva, historial, búsquedas de apoyo y diversidad
- la lista se recalcula más de lo deseable después de abierto el video

#### Severidad
- alta

---

### 7. Reproducir / biblioteca local
**Estado:** mejorado, no cerrado

#### Síntomas
- listas largas siguen siendo sensibles en gama media/baja
- todavía hay edge cases en:
  - thumbnails reales post-descarga
  - rename/delete/share
  - sincronía entre item activo y detalle

#### Causas raíz probables
- carga de miniaturas locales
- MediaStore + merge con historial
- lista local todavía costosa cuando hay muchas filas visibles

#### Severidad
- alta

---

### 8. Reproducción local
**Estado:** mejorado, no cerrado

#### Síntomas
- ya se corrigieron varias pausas y problemas de siguiente/anterior
- aun así los logs muestran que el pipeline de audio sigue sufriendo pausas/stops/starts frecuentes

#### Causas raíz probables
- reconfiguración del player o de la cola
- sincronía incompleta entre service, snapshot persistido y UI local

#### Severidad
- alta

---

### 9. Descargas
**Estado:** mejorado, no cerrado

#### Síntomas
- se corrigió el retraso de notificación y duplicados
- sigue habiendo deuda en:
  - persistencia perfecta de miniatura real
  - continuidad entre historial de descarga y biblioteca local

#### Causas raíz probables
- pipeline de portada e historial todavía depende de varios puntos de matching
- sincronización entre archivo final, nombre esperado y thumbnail guardada

#### Severidad
- media

---

### 10. Fullscreen / mini player / restore
**Estado:** mejorado, no cerrado

#### Síntomas
- se arreglaron varios bugs visibles, pero esta familia sigue siendo delicada
- cualquier cambio de playback o navegación todavía puede sentirse pesado

#### Causas raíz probables
- demasiadas transiciones vivas alrededor del mismo player compartido
- shells ya separados, pero no del todo libres de recomposición lateral

#### Severidad
- media/alta

## Top de bugs y janks activos hoy

### P0
1. Apertura de video todavía lenta en parte de los casos reales
2. Jank global del árbol principal confirmado por `gfxinfo`
3. Reconfiguraciones de playback visibles en logs (`pause/stop/start`)

### P1
1. Feed YouTube todavía no tan estable como YouTube real en scroll largo y continuación
2. Watch-next todavía se recalcula más de lo deseable
3. Biblioteca local sigue pesada en listas grandes
4. Cambio de calidad todavía no se siente suficientemente inmediato

### P2
1. Persistencia de thumbnails reales de descargas no está totalmente cerrada
2. Búsqueda aún puede sentirse lenta frente a YouTube oficial
3. Fullscreen/mini player todavía necesitan hardening fino

## Causas raíz agrupadas por dominio

### Render/UI
- recomposición amplia en raíz
- tabs superiores todavía caras
- listas largas con miniaturas y estados activos
- trabajo de draw demasiado alto para el árbol actual

### Playback
- player compartido con demasiadas transiciones de estado
- arranque de video sensible a heurísticas de calidad/fuente
- cambios de cola/metadata/related demasiado cercanos al inicio de reproducción

### Datos/feed
- ranking y continuidad todavía complejos para el feed y watch-next
- búsqueda y relacionados dependen de varias fuentes y siguen pagando costo de armado

### Persistencia/local
- matching de descargas ↔ historial ↔ MediaStore todavía no está completamente blindado

## Recomendación de remediación inmediata

### Slice 1
- Auditar y endurecer el arranque del watch player:
  - cero refresh lateral del watch durante los primeros segundos
  - cero mutación de relacionados que toque el player mientras entra el stream
  - separar por completo “abrir video” de “enriquecer sigue”

### Slice 2
- Medir y recortar el jank de raíz:
  - root state mínimo en `SnapMusicApp`
  - `NavHost` y bottom bar sin observar playback amplio
  - tabs superiores sin trabajo lateral al cambiar de pantalla

### Slice 3
- Hardening de biblioteca local:
  - thumbnails reales persistidas por id de descarga
  - lista local más barata en scroll largo

## Criterio de cierre de esta auditoría
- No se considera cerrada mientras:
  - `gfxinfo` siga mostrando jank dominante tan alto
  - abrir video siga tardando perceptiblemente en escenarios normales
  - el feed y watch-next sigan mezclando trabajo de playback y ranking en vivo

## Conclusión
- La app mejoró mucho respecto de slices anteriores, pero hoy no está todavía al nivel de fluidez/instantaneidad de YouTube real.
- El cuello principal ya no es “falta una feature”; es una combinación de:
  - render ancho
  - transiciones de playback
  - arranque del video
  - continuidad de feed/relacionados
- La siguiente remediación debe enfocarse primero en el **arranque real del watch player** y luego en el **jank estructural de la raíz**.

## Re-auditoría de regresión — 2026-05-21

### Culpables concretos encontrados
1. `SnapMusicApp` seguía observando en Compose tres flujos de alto nivel:
   - `youtubePictureInPictureEligibility`
   - `previewPictureInPictureEligibility`
   - `youtubePlaybackAutoPlay`
   Eso hacía que la raíz recompusiera por cambios que en realidad solo debían actualizar la `Activity`.

2. `PreviewScreen` observaba en la raíz el `PreviewDownloadsState` completo.
   - Ese estado incluye `activeItems`.
   - Cada tick de progreso de descargas podía invalidar la pantalla entera de `Reproducir`.
   - Para la raíz solo se necesitaban:
     - `hasActiveDownloads`
     - `completedCount`
     - `openRequestId`

3. `YouTubeFeedComponents` actualizaba progreso, duración y buffer dentro del mismo shell que contiene:
   - `AndroidView(PlayerView)`
   - spinner
   - overlays
   - entrada/salida de fullscreen
   Con eso el shell del video recompuso varias veces por segundo por simple progreso visual.

### Corrección aplicada en esta re-auditoría
- `SnapMusicApp` ya no usa `collectAsStateWithLifecycle()` para esos tres flujos de raíz.
  - ahora los consume en un `LaunchedEffect` con `combine(...)`
  - la `Activity` se actualiza sin volver a recomponer toda la app

- Se creó `PreviewDownloadsShellState`
  - la raíz de `PreviewScreen` dejó de observar la lista completa de descargas activas
  - ahora observa solo un shell mínimo para decidir:
    - refresco de biblioteca
    - apertura/cierre de la pantalla de descargas

- El polling visual de progreso/buffer en YouTube se movió a hosts dedicados de overlay
  - el `PlayerView` y el shell principal ya no recompone por cada tick de seek/buffer
  - fullscreen y watch overlay siguen mostrando progreso real, pero sin meter ese costo en el host completo

### Verificación local
- Validación ejecutada:
  - `.\gradlew.bat :app:compileDebugKotlin`
- Resultado:
  - compilación correcta

### Estado posterior
- Esta corrección no cierra toda la meta 60fps.
- Sí recorta tres focos reales de jank estructural introducido o reintroducido por cambios recientes:
  1. recomposición de raíz por PiP/autoplay
  2. recomposición de `PreviewScreen` por progreso de descargas
  3. recomposición excesiva del shell de video por polling visual

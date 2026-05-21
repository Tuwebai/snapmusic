# Auditoría de bugs que impedían la calidad real de video en SnapMusic

## Alcance

Auditoría enfocada solo en el playback de calidad de video de YouTube dentro de SnapMusic.

Base auditada:
- YouTube Android observado por ADB: `com.google.android.youtube`
  - `versionName=21.19.286`
- SnapTube observado por ADB: `com.snaptube.premium`
  - `versionName=7.58.1.75872701`
- Código auditado en SnapMusic:
  - `NewPipeStreamResolverRepository.kt`
  - `SnapMusicViewModel.kt`
  - `YouTubePlaybackController.kt`
  - `YouTubeFeedComponents.kt`

Referencias técnicas usadas:
- Soporte oficial de YouTube para calidad de video en Android.
- Documentación oficial de Media3 sobre selección de tracks y `TrackSelectionOverride`.

## Síntoma real

Aunque el usuario elegía 1080p o 720p:
- la app seguía viendo el stream como 360p;
- a veces el selector solo ofrecía 360p;
- la UI prometía una calidad alta que en realidad no quedaba aplicada;
- en algunos casos el audio se perdía al mezclar mal reproducción y variantes pensadas para descarga.

## Causas raíz encontradas

### 1. Detección incorrecta del playback adaptativo

SnapMusic estaba tratando muchos manifiestos válidos de YouTube como si **no** fueran adaptativos.

Error de diseño:
- se asumía que un manifest válido siempre terminaba explícitamente en:
  - `.mpd`
  - `.m3u8`

Problema real:
- muchas URLs reales de manifest de YouTube no vienen así de “limpias”;
- llegan desde `NewPipeExtractor` en otras formas válidas;
- al rechazarlas, SnapMusic caía a playback progresivo bajo, típicamente 360p.

Impacto:
- el player dejaba de usar el manifest adaptativo real;
- el selector manual nunca podía subir a tracks HD porque el playback ya había quedado clavado en una URL baja.

### 2. El sheet de calidad se construía con variantes del extractor, no con los tracks reales del player

SnapMusic armaba las opciones de calidad usando sobre todo `resolved.videoVariants`.

Problema real:
- en varios videos, especialmente streams, lives o ciertos contenidos recientes, esas variantes visibles del extractor no reflejan todas las alturas reales del manifest adaptativo;
- por eso el sheet podía mostrar solo 360p aunque el player real tuviera más tracks disponibles.

Impacto:
- la UI limitaba artificialmente las opciones;
- el usuario no podía pedir una calidad que sí existía en el manifest.

### 3. La selección manual estaba acoplada a IDs del extractor, no a las alturas reales del stream adaptativo

La lógica manual dependía de `variant.id` y de resoluciones derivadas desde `videoVariants`.

Problema real:
- en adaptive playback, lo correcto no es confiar en la lista estática del extractor para elegir la pista final;
- lo correcto es seleccionar por track real disponible en `Media3`.

Impacto:
- la app podía creer que “eligió 1080p”, pero el override no terminaba alineado con el track HD real del manifest.

### 4. La UI mostraba calidad “activa” antes de confirmarla

SnapMusic adelantaba labels como:
- `Automático · 1080P`
- o la calidad pedida por el usuario

antes de que `Media3` confirmara el track realmente seleccionado.

Impacto:
- el usuario veía una promesa falsa;
- el estado visible no representaba la calidad efectiva del stream.

### 5. El override manual no tomaba como fuente de verdad todos los grupos reales de video disponibles

Aunque ya había una mejora previa, el problema seguía incompleto mientras la UI y la capa de resolución no trabajaran con la misma fuente real:
- tracks disponibles del player;
- altura realmente seleccionada;
- manifest adaptativo real.

Impacto:
- desalineación entre lo que el sheet mostraba, lo que el ViewModel creía y lo que el player realmente estaba reproduciendo.

### 6. Muchos videos de YouTube llegaban sin `dashMpdUrl` ni `hlsUrl`, aunque sí tenían pistas HD separadas

Auditoría real hecha con `NewPipeExtractor` sobre videos públicos de YouTube:
- `dashMpdUrl = ""`
- `hlsUrl = ""`
- `videoStreams` progresivos con audio: muchas veces solo `360p`
- `videoOnlyStreams`: `720p`, `1080p` y más
- `audioStreams`: pistas separadas M4A / WebM

Problema real:
- SnapMusic asumía que, si no había manifest adaptativo, solo podía reproducir los progresivos con audio;
- entonces terminaba cayendo a 360p;
- las variantes HD quedaban disponibles solo para descarga/mux, no para playback real.

Impacto:
- no aparecían 720p/1080p reales en el watch;
- al intentar forzar una calidad alta, la app volvía a un progresivo bajo o usaba una URL incorrecta;
- en algunos casos el audio se perdía porque video y audio no se trataban como dos fuentes separadas.

## Corrección estructural aplicada

### A. Playback adaptativo tomado como válido cuando `adaptivePlaybackUrl` existe

Se dejó de depender del sufijo textual de la URL.

Nueva regla:
- si `NewPipeExtractor` entrega `adaptivePlaybackUrl`, SnapMusic la trata como fuente adaptativa válida para playback.

### B. Las opciones de calidad adaptativa ahora salen de los tracks reales del player

Se agregó sincronización desde `MediaController.currentTracks` para extraer:
- alturas disponibles reales;
- altura seleccionada real.

Nuevo estado:
- `availablePlaybackHeights`
- `actualVideoHeight`
- `actualPlaybackLabel`

### C. El sheet de calidad adaptativa ya no depende solo de `videoVariants`

Para adaptive playback:
- las opciones manuales se construyen desde `availablePlaybackHeights`;
- los IDs pasan a representar la altura real pedida, por ejemplo:
  - `adaptive-1080`
  - `adaptive-720`

### D. El override manual ahora se alinea con el objetivo real de altura

La reproducción adaptativa sigue sobre el manifest.

La selección manual:
- no cambia a una URL falsa “HD”;
- aplica override real sobre tracks de video soportados;
- conserva el audio del manifest.

### E. La UI deja de mentir sobre la calidad activa

Se corrigió el comportamiento visible:
- la opción elegida se marca como elegida;
- la calidad activa se basa en la confirmación real del player;
- si todavía no terminó de aplicar, la UI no la vende como “activa” cerrada.

### F. Playback HD real cuando YouTube solo expone video HD separado + audio separado

Se endureció la arquitectura del player:
- si existe manifest adaptativo válido, SnapMusic sigue usando playback adaptativo;
- si no existe, pero sí hay `videoOnlyStreams` HD y `audioStreams`, SnapMusic arma una reproducción fusionada real:
  - video HD como pista de video;
  - audio separado como pista de audio;
  - `Media3` los une en playback con `MergingMediaSource`.

Consecuencia:
- 720p/1080p ya no dependen de que YouTube entregue un `.mpd` o `.m3u8`;
- si el extractor trae video HD separado + audio separado, SnapMusic puede reproducirlo de verdad.

## Estado después de la corrección

Quedó corregida la parte de raíz que más forzaba el 360p:
- rechazo incorrecto del manifest adaptativo;
- sheet construido desde una fuente incorrecta;
- desacople entre selector manual y tracks reales del player.

## Validación que todavía hay que cerrar en dispositivo

Casos a validar manualmente en el teléfono:
- video normal con DASH y 1080p;
- video con 720p pero sin 1080p;
- stream/live o video donde el extractor muestre pocas variantes pero el manifest tenga más tracks;
- cambio manual entre 1080/720/480 sin perder audio;
- modo automático priorizando HD.

## Conclusión

El problema no era un bug superficial de label.

La causa raíz estaba en cuatro capas al mismo tiempo:
1. detección incorrecta del manifest adaptativo;
2. menú de calidad armado desde datos incompletos del extractor;
3. ausencia de playback fusionado cuando YouTube entregaba HD separado en video+audio;
4. UI adelantando una calidad no confirmada.

La remediación correcta fue mover la verdad de calidad hacia el player real y usar el extractor solo como apoyo, no como autoridad final de las pistas efectivamente reproducibles.

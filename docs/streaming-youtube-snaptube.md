# Referencia de streaming tipo YouTube + SnapTube para SnapMusic

Fecha: 16 de mayo de 2026

## Objetivo

Dejar documentado cómo se comportan hoy YouTube y SnapTube en flujos de reproducción móvil para usarlo como guía de producto y de implementación dentro de SnapMusic.

## Patrones clave observados

### 1. Autoplay real entre videos

- En YouTube, cuando el autoplay está activo, al terminar un video se prepara y reproduce el siguiente.
- YouTube también aclara que el siguiente no se reproduce automáticamente si el usuario se aleja del reproductor para escribir o navegar de otra forma.
- Para SnapMusic conviene implementar:
  - autoplay secuencial del siguiente item visible
  - fallback al primer item si se terminó la lista
  - flag de usuario para activar o desactivar autoplay
  - precarga liviana del siguiente stream si ya está resuelto

### 2. Miniplayer dentro de la app

- YouTube usa un miniplayer interno que aparece al tocar atrás o deslizar hacia abajo.
- Ese miniplayer sigue reproduciendo mientras el usuario navega por el feed.
- El miniplayer se puede cerrar o volver a expandir.
- Para SnapMusic conviene mantener:
  - swipe down sobre el video
  - mini reproductor persistente entre tabs
  - tap para restaurar
  - cerrar sin matar la sesión si el usuario no lo pide explícitamente

### 3. PiP fuera de la app

- YouTube usa Picture-in-Picture al salir de la app mientras un video sigue activo.
- Android recomienda `setAutoEnterEnabled` para una transición suave con navegación por gestos.
- En PiP el foco debe ser solo el video, sin tabs ni chrome de la app.
- Para SnapMusic conviene sumar después:
  - `sourceRectHint` real del contenedor del video
  - acciones custom en PiP: play/pausa, siguiente, cerrar
  - restauración exacta al volver a la app

### 4. Reproducción en segundo plano

- YouTube separa la reproducción de la UI usando controles y sesión multimedia; oficialmente el background play sigue atado a Premium para la app oficial.
- Android Media3 recomienda `MediaSessionService` para sostener reproducción y notificación fuera de la Activity.
- Para SnapMusic esto significa:
  - mantener player y sesión desacoplados de la pantalla
  - no perder audio/video al navegar
  - notificación multimedia viva con metadata correcta

### 5. Continuidad de reproducción

- SnapTube hoy promociona:
  - reproducción en segundo plano
  - modo Picture-in-Picture
  - reproducción más fluida entre canciones
  - soporte de playlists por lote
- Para SnapMusic conviene incorporar:
  - autoplay del siguiente item del feed
  - looping configurable
  - shuffle para feeds/listas
  - memoria del punto de reproducción
  - reintento automático ante fallo del siguiente stream

### 6. Feed + reproducción sin ruptura

- YouTube mantiene browsing y reproducción como dos capas del mismo flujo:
  - feed
  - watch screen
  - miniplayer
  - PiP
- SnapMusic debería sostener exactamente ese gradiente:
  - feed principal
  - pantalla completa del video
  - miniplayer interno
  - PiP del sistema
  - audio de fondo con notificación

## Qué ya deberíamos tener en SnapMusic

- feed de YouTube separado
- pantalla de reproducción
- miniplayer interno entre tabs
- PiP del sistema
- servicio multimedia en segundo plano
- notificación multimedia
- swipe down para minimizar

## Qué falta para quedar más cerca de YouTube/SnapTube

### Prioridad alta

1. autoplay sólido del siguiente video
2. restauración perfecta del player al volver al tab YouTube
3. cola de reproducción interna para feed/resultados
4. botón `Siguiente` real en media controls y notificación
5. indicador de “Reproduciendo siguiente en…” opcional

### Prioridad media

1. ocultar miniplayer al borde con handle lateral
2. resize del miniplayer
3. recordar autoplay on/off por usuario
4. reanudar desde historial reciente
5. fallback de stream si el siguiente URL falla

### Prioridad media/alta para UX premium

1. precarga del próximo stream
2. crossfade opcional en audio
3. persistencia de playlist temporal de sesión
4. acciones rápidas: siguiente, compartir, descargar desde miniplayer

## Reglas de producto recomendadas para SnapMusic

- Si el usuario está en pantalla completa y el video termina:
  - reproducir el siguiente si autoplay está activo
- Si el usuario está en miniplayer y termina:
  - seguir con el siguiente sin expandir solo
- Si el usuario está en PiP y termina:
  - seguir con el siguiente manteniendo PiP
- Si el siguiente item falla:
  - intentar uno más
  - mostrar feedback corto
  - no matar la sesión completa
- Si no hay más items:
  - detener limpio o reiniciar lista según preferencia de usuario

## Propuesta de implementación técnica en esta app

### Capa de dominio

- agregar preferencia `youtubeAutoplayEnabled`
- agregar política `PlaybackContinuationMode`
  - `STOP_AT_END`
  - `PLAY_NEXT`
  - `LOOP_FEED`

### ViewModel

- mantener índice actual dentro de `state.items`
- exponer:
  - `playNextYouTubeItem()`
  - `playPreviousYouTubeItem()`
  - `toggleYouTubeAutoplay()`

### Player / Media3

- escuchar `Player.STATE_ENDED`
- resolver el siguiente item y reemplazar `MediaItem`
- cargar metadata del siguiente antes de reproducirlo
- más adelante:
  - usar playlist real de `MediaItem` cuando tengamos URLs resueltas en cadena

### UI

- miniplayer con play/pausa y cerrar
- pantalla completa con indicador de autoplay
- feedback claro cuando arranca el siguiente

## Decisión recomendada

Para SnapMusic conviene copiar el comportamiento base de YouTube:

- swipe down => miniplayer
- salir de la app => PiP
- volver al tab YouTube => restaurar reproductor
- fin del video => siguiente video si autoplay está activo

Y sumar el enfoque SnapTube:

- descarga directa desde watch screen
- formatos rápidos
- continuidad fuerte entre reproducción y descarga

## Fuentes

- YouTube Help — Autoplay videos: https://support.google.com/youtube/answer/6327615?hl=en-EN
- YouTube Help — Watch videos on the Miniplayer (Android): https://support.google.com/youtube/answer/9162927?co=GENIE.Platform%3DAndroid&hl=en-GB
- YouTube Help — Using picture-in-picture on your mobile device: https://support.google.com/youtube/answer/7552722?hl=en-GB
- YouTube Help — Background play isn't working (Android): https://support.google.com/youtube/answer/7437614?hl=en-GB
- Android Developers — Use picture-in-picture (PiP): https://developer.android.com/develop/ui/views/picture-in-picture?hl=en
- Android Developers — Reproducción en segundo plano con `MediaSessionService`: https://developer.android.com/media/media3/session/background-playback?hl=es-419
- Snaptube — Original app features: https://www.snaptube.io/snaptube-original/

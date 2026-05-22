# Fixes V3 de rendimiento

## Fix 1 — Unificar estado de playback en Preview
**Archivo principal:** `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewPlaybackUi.kt`

- Eliminar el productor duplicado `rememberPreviewPlaybackState()`.
- Usar `rememberPlaybackOverlayState()` como única fuente de verdad local.
- En audio, el progreso queda siempre activo.
- En video, el progreso solo se sigue cuando los controles están visibles.

## Fix 2 — Frenar polling inútil del overlay
**Archivo principal:** `app/src/main/java/com/juan/snapmusic/feature/player/PlaybackOverlayState.kt`

- Agregar `trackProgress`.
- Si `trackProgress=false`, mantener listener pero cortar el loop periódico.
- Dejar el polling solo para casos donde el slider/progreso está visible.

## Fix 3 — Estabilidad Compose faltante
**Archivo principal:** `app/src/main/java/com/juan/snapmusic/core/model/Models.kt`

Agregar `@Immutable` a:
- `YouTubePlaybackSnapshot`
- `PreviewPlaybackSnapshot`
- `YouTubePlaybackRenderState`
- `PreviewPlaybackRenderState`
- `DownloadBadgeState`

## Fix 4 — Estado Home estable
**Archivo principal:** `app/src/main/java/com/juan/snapmusic/feature/home/HomeUiState.kt`

- Agregar `@Immutable` a `HomeUiState`.

## Fix 5 — Raíz de actividad más angosta
**Archivo principal:** `app/src/main/java/com/juan/snapmusic/MainActivity.kt`

- Observar solo `themeMode`.
- Dejar de colectar `UserPreferences` completo en `setContent`.

## Fix 6 — Controller de YouTube desacoplado del render state ancho
**Archivos principales:**
- `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubePlaybackController.kt`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt`
- `app/src/main/java/com/juan/snapmusic/core/model/YouTubePlayerControllerState.kt`

- Separar sesión de player (`featured`) de los comandos de seek.
- Emitir `seek` solo por `requestId` explícito, no por espejo continuo del progreso.
- Hacer que play/pause ya no reinicialice media items ni reaplique setup completo.
- Dejar el controller reaccionando a cambios de fuente, calidad, seek y autoplay en circuitos distintos.

## Checklist de cierre
- [x] `compileDebugKotlin`
- [x] `testDebugUnitTest`
- [x] `assembleBenchmark`
- [ ] smoke manual Home / YouTube / Preview
- [ ] reauditar benchmark y `gfxinfo`

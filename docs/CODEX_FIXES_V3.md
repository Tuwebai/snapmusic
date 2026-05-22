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

## Checklist de cierre
- [ ] `compileDebugKotlin`
- [ ] `testDebugUnitTest`
- [ ] `assembleBenchmark`
- [ ] smoke manual Home / YouTube / Preview
- [ ] reauditar benchmark y `gfxinfo`

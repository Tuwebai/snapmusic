# Reducción de tamaño — SnapMusic

## Estado medido

- `app-debug.apk`: ~`99.45 MB`
- `ffmpeg-kit-full-6.1.4.aar`: ~`33.32 MB`

La mochila principal del tamaño no estaba en Compose ni en assets chicos: estaba en el bundle nativo de FFmpegKit y en distribuir builds sin shrink ni separación por ABI.

## Cambios aplicados

### Build release
- `isMinifyEnabled = true`
- `isShrinkResources = true`

### Packaging por ABI
- Se habilitaron APKs separados:
  - `arm64-v8a`
  - `armeabi-v7a`
- Se deshabilitó el APK universal para evitar cargar binarios que el dispositivo actual no necesita.

### Reglas mínimas de R8
- Se agregaron `keep` y `dontwarn` mínimos para:
  - FFmpegKit
  - NewPipe Extractor
  - clases opcionales reportadas por R8 en release

## Resultado medido

- `app-arm64-v8a-release-unsigned.apk`: ~`43.66 MB`
- `app-armeabi-v7a-release-unsigned.apk`: ~`39.62 MB`

## Qué sigue para bajar más

1. Reemplazar `ffmpeg-kit-full` por un bundle mínimo vendorado.
2. Optimizar `preview_local_music_fallback.png`.
3. Revisar si alguna librería transitiva de benchmarking o tooling quedó donde no corresponde.

## Riesgo controlado

- Se mantuvo compatibilidad con `arm64-v8a` y `armeabi-v7a`.
- No se tocó la lógica funcional de MP3/M4A/MP4 para no romper descargas mientras se baja tamaño.

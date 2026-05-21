# Contexto de migración — ConvertIO → SnapMusic Android

## 1. Objetivo
Construir una app Android nativa llamada **SnapMusic** que reemplace la app de escritorio ConvertIO para uso local/propio, manteniendo la lógica funcional clave y rehaciendo la experiencia en una UI nativa inspirada en SnapTube, con branding donor de SnapMusic-Nativa.

## 2. Mapa ConvertIO → Android
| ConvertIO desktop | SnapMusic Android |
|---|---|
| Tkinter UI | Jetpack Compose |
| `yt-dlp` para resolver/descargar | `NewPipeExtractor` para resolver streams |
| `ffmpeg`/`static_ffmpeg` | `FFmpegKit` vendor local |
| Config JSON local | DataStore |
| Historial JSON | Room |
| Cola JSON | Room |
| Preview con `pygame` | Media3 / ExoPlayer |
| Notificaciones Windows | NotificationManager Android |
| Selector carpeta escritorio | Storage Access Framework |

## 3. Inventario donor de SnapMusic-Nativa
### Assets gráficos
- `app/src/main/res/drawable-nodpi/snapmusic_logo.png`
- `app/src/main/res/drawable-nodpi/snapmusic_splash.png`

### Tokens y piezas de UI reutilizables conceptualmente
- paleta oscura con acento rojo
- tarjetas redondeadas grandes
- badges de formato/estado
- headers con jerarquía visual fuerte
- thumbnails dominantes

## 4. Decisiones técnicas cerradas
- `applicationId`: `com.juan.snapmusic`
- `namespace`: `com.juan.snapmusic`
- `minSdk`: 24
- `targetSdk`: 34
- arquitectura: modular monolith interno
- features: home, analyze, downloads, queue, history, preview, settings
- data layers: extractor, download, transcode, storage, persistence
- dependencias base: Compose, Navigation, Room, DataStore, WorkManager, Media3, Coil

## 5. Riesgos y workaround
### NewPipeExtractor
- Riesgo: cambios en YouTube o necesidad de WebView integrity checks.
- Workaround: encapsular la extracción detrás de `StreamResolverRepository` para permitir reemplazo futuro sin romper UI ni casos de uso.

### FFmpegKit
- Riesgo: packaging Android inestable o assets removidos aguas arriba.
- Workaround: vendorizar el AAR dentro del repo y aislarlo detrás de `TranscodeEngine`.

### Storage Android
- Riesgo: diferencias de permisos entre Android 7 y Android 14+.
- Workaround: usar carpeta por defecto en `Downloads/SnapMusic` y fallback/control fino con SAF persistente.

## 6. Resultado esperado de la v1
- pegar URL de YouTube
- analizar metadata y variantes
- descargar audio o video
- ver progreso
- cancelar
- mantener cola e historial
- abrir preview local del último archivo
- persistir preferencias y destinos favoritos

## 7. Estado actual de implementación
- Base Android nativa ya creada y compilando.
- UI donor SnapMusic ya adaptada a una shell Compose con bottom navigation.
- `NewPipeExtractor` ya resuelve metadata y variantes directas.
- Cola, historial, preferencias y preview ya están conectados.
- La capa de transcodificación/mux todavía quedó encapsulada y pendiente de conexión con un bundle local real de FFmpegKit.

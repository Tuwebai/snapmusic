# Inventario de archivos grandes

Arquitectura objetivo: monolito modular por dominio/feature. Cada archivo debe tener una responsabilidad principal y evitar mezclar UI, orquestación, estado, descarga, navegación e infraestructura.

## Archivos de más de 500 líneas después del corte actual

| Líneas | Archivo | Corte requerido |
|---:|---|---|
| 3369 | `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt` | Queda como corte mayor pendiente: partir por coordinadores de estado de YouTube, preview, cola/descargas, búsqueda, settings y persistencia con wrappers públicos mínimos. |

## Archivos partidos en este corte

| Archivo original | Nuevos archivos de responsabilidad |
|---|---|
| `feature/youtube/YouTubeFeedComponents.kt` | `YouTubeFeedListComponents.kt`, `YouTubeMiniPlayerComponents.kt`, `YouTubeFeaturedVideoComponents.kt`, `YouTubeFeaturedVideoShell.kt`, `YouTubeTrackSelection.kt` |
| `feature/preview/PreviewPlaybackUi.kt` | `PreviewVideoPlaybackCard.kt`, `PreviewPlaybackSurfaces.kt` |
| `feature/queue/QueueScreen.kt` | `QueueCards.kt` |
| `feature/preview/PreviewScreen.kt` | `PreviewLocalMediaDialogs.kt` |
| `feature/settings/SettingsPanels.kt` | `SettingsSharedComponents.kt` |
| `feature/player/VideoOverlayUi.kt` | `VideoFullscreenDialogUi.kt` |
| `navigation/SnapMusicNavHost.kt` | `SnapMusicNavChrome.kt` |
| `data/download/DownloadWorker.kt` | `DownloadWorkerHelpers.kt`, `CombinedTransferProgress.kt` |

## Reglas de corte

- No mover lógica entre dominios.
- No cambiar comportamiento visual ni contratos públicos.
- Extraer primero funciones top-level y modelos sin tocar flujo interno.
- Para clases con mucho estado, extraer adaptadores/helpers puros antes de partir responsabilidades mutables.
- Cada corte debe compilar antes de continuar.

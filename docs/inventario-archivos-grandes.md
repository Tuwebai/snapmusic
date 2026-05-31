# Inventario de archivos grandes

Arquitectura objetivo: monolito modular por dominio/feature. Cada archivo debe tener una responsabilidad principal y evitar mezclar UI, orquestación, estado, descarga, navegación e infraestructura.

## Archivos de más de 500 líneas

| Líneas | Archivo | Corte requerido |
|---:|---|---|
| 3369 | `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt` | Separar modelos, estado de YouTube, estado de preview, cola/descargas, búsqueda y helpers de persistencia. |
| 1191 | `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubeFeedComponents.kt` | Separar filas/lista, reproductor destacado, mini player, overlays y sheets. |
| 937 | `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewPlaybackUi.kt` | Separar controles, superficie, overlays, mini player y fullscreen. |
| 785 | `app/src/main/java/com/juan/snapmusic/feature/queue/QueueScreen.kt` | Separar pantalla, secciones, cards, acciones y estados vacíos. |
| 711 | `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewScreen.kt` | Separar shell, detalle, biblioteca, descargas y efectos. |
| 693 | `app/src/main/java/com/juan/snapmusic/feature/settings/SettingsPanels.kt` | Separar panel raíz, descargas, notificaciones, tema y acerca de. |
| 594 | `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubePlaybackController.kt` | Separar controller, preparación de media, listeners, progreso y selección de tracks. |
| 593 | `app/src/main/java/com/juan/snapmusic/feature/player/VideoOverlayUi.kt` | Separar controles, timeline, gestos, estado visual y botones. |
| 592 | `app/src/main/java/com/juan/snapmusic/navigation/SnapMusicNavHost.kt` | Separar host, bottom bar, efectos de navegación, PiP y players administrados. |
| 581 | `app/src/main/java/com/juan/snapmusic/data/download/DownloadWorker.kt` | Separar worker, ejecución de descarga, transcodificación, validación y updates de progreso. |

## Reglas de corte

- No mover lógica entre dominios.
- No cambiar comportamiento visual ni contratos públicos.
- Extraer primero funciones top-level y modelos sin tocar flujo interno.
- Para clases con mucho estado, extraer adaptadores/helpers puros antes de partir responsabilidades mutables.
- Cada corte debe compilar antes de continuar.

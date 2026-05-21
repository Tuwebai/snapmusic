# Auditoría observable de SnapTube en dispositivo

## Resumen

Esta auditoría compara **solo lo observable y verificable** entre SnapTube instalado en el teléfono y SnapMusic actual.  
No incluye decompilación, reverse engineering ni inferencias sobre lógica interna no comprobable.

## Evidencia base observada

### SnapTube

- Paquete: `com.snaptube.premium`
- Versión: `7.58.1.75872701`
- `versionCode=75872701`
- `minSdk=21`
- `targetSdk=35`
- `primaryCpuAbi=arm64-v8a`
- `base.apk` observado en el dispositivo: ~`46,81 MB`
- Flags visibles:
  - `LARGE_HEAP`
  - `REQUEST_LEGACY_EXTERNAL_STORAGE`
  - `ALLOW_AUDIO_PLAYBACK_CAPTURE`
  - `HAS_DOMAIN_URLS`
- Permisos solicitados observados:
  - `POST_NOTIFICATIONS`
  - `MANAGE_EXTERNAL_STORAGE`
  - `SYSTEM_ALERT_WINDOW`
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_SPECIAL_USE`
  - `FOREGROUND_SERVICE_DATA_SYNC`
  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`
  - `ACCESS_WIFI_STATE`
  - `WAKE_LOCK`
  - `REORDER_TASKS`
  - `GET_PACKAGE_SIZE`
  - `KILL_BACKGROUND_PROCESSES`
- Intents/capacidades observables:
  - múltiples `SEND`
  - múltiples `SEND_MULTIPLE`
  - muchos `VIEW/BROWSABLE`
- `appops` relevantes observados:
  - `MANAGE_EXTERNAL_STORAGE: allow`
  - `START_FOREGROUND: allow`
  - `READ_CLIPBOARD: allow`
  - `SYSTEM_ALERT_WINDOW: ignore`

### SnapMusic

- Paquete: `com.juan.snapmusic`
- Versión instalada: `1.0.77`
- `versionCode=77`
- `minSdk=24`
- `targetSdk=34`
- APK benchmark arm64 actual del repo: ~`43,72 MB`
- Flags visibles:
  - `ALLOW_AUDIO_PLAYBACK_CAPTURE`
  - `HAS_DOMAIN_URLS`
- Permisos solicitados observados:
  - `POST_NOTIFICATIONS`
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  - `FOREGROUND_SERVICE_DATA_SYNC`
  - `READ_MEDIA_AUDIO`
  - `READ_MEDIA_VIDEO`
  - `READ_MEDIA_VISUAL_USER_SELECTED`
  - `RECEIVE_BOOT_COMPLETED`
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`
  - `WAKE_LOCK`
- Intents/capacidades observables en manifest:
  - `SEND` de texto plano
  - `VIEW/BROWSABLE` acotado a dominios de YouTube
- `appops` relevantes observados:
  - `START_FOREGROUND: allow`
  - `READ_MEDIA_AUDIO: allow`
  - `READ_MEDIA_VIDEO: allow`
  - `SYSTEM_ALERT_WINDOW: default`
  - `MANAGE_EXTERNAL_STORAGE: default`

## Comparación directa

| Área | SnapTube observado | SnapMusic actual | Lectura útil |
|---|---|---|---|
| Storage | Usa `MANAGE_EXTERNAL_STORAGE` y legado | Usa scoped storage moderno | Conviene sostener el modelo actual de SnapMusic |
| Overlay flotante | Pide `SYSTEM_ALERT_WINDOW` | No lo pide | No adoptar salvo necesidad demostrada |
| Foreground | Pide `mediaPlayback`, `dataSync` y `specialUse` | Pide `mediaPlayback` y `dataSync` | SnapMusic ya cubre el caso normal; no sumar `specialUse` sin prueba |
| Share target | Tiene `SEND` y `SEND_MULTIPLE` visibles | Solo `SEND` simple | Sí conviene ampliar routing de entrada |
| Deeplinks | Superficie `BROWSABLE` muy amplia | Superficie acotada a YouTube | Mantener enfoque acotado, ampliar solo si hay soporte real |
| Heap | `LARGE_HEAP` activo | No usa `LARGE_HEAP` | No copiar; primero optimizar memoria/render |
| Tamaño | ~46,81 MB en `base.apk` | ~43,72 MB en APK benchmark arm64 | SnapMusic no está peor en tamaño arm64 observable |

## Hallazgos por tema

| Hallazgo | Evidencia | Impacto | Conviene adoptar | Riesgo |
|---|---|---|---|---|
| SnapTube abre muchas superficies externas | `SEND`, `SEND_MULTIPLE`, `VIEW`, `BROWSABLE` observados | Mejor entrada desde otras apps | Sí, de forma medida | Medio si el routing queda ambiguo |
| SnapTube usa permisos invasivos de storage | `MANAGE_EXTERNAL_STORAGE`, legado | Facilita acceso amplio a archivos | No | Alto en seguridad y cumplimiento |
| SnapTube declara overlay flotante | `SYSTEM_ALERT_WINDOW` solicitado | Puede habilitar UX flotante | No por ahora | Alto |
| SnapTube usa `FOREGROUND_SERVICE_SPECIAL_USE` | permiso observable | Puede cubrir casos extra de background | No salvo necesidad demostrada | Medio |
| SnapMusic ya tiene permisos mínimos más sanos | manifest y `appops` actuales | Base más segura y mantenible | Sí, mantener | Bajo |
| SnapMusic todavía puede mejorar share/deeplink | manifest actual limitado a `SEND` simple | Menos continuidad entre apps y SnapMusic | Sí | Bajo |

## Conclusión

La comparación observable muestra que **SnapMusic no necesita copiar el modelo agresivo de permisos de SnapTube** para mejorar.  
Lo más valioso para importar es:

- mejor routing de entrada
- soporte de compartir más robusto
- continuidad desde notificaciones
- compatibilidad más completa con fuentes externas

Lo que **no conviene adoptar**:

- `MANAGE_EXTERNAL_STORAGE`
- `SYSTEM_ALERT_WINDOW`
- `REQUEST_LEGACY_EXTERNAL_STORAGE`
- `LARGE_HEAP`
- `FOREGROUND_SERVICE_SPECIAL_USE` sin un caso real comprobado

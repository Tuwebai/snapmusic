# Auditoría ADB de playback largo YouTube

Fecha: 2026-06-06  
APK probado: `perf`  
Dispositivo: `cff29cc6`

## Objetivo

Validar 5 videos de YouTube durante 3 minutos cada uno y registrar:

- tiempo hasta primer frame;
- cantidad de rebuffers reales;
- duración total de rebuffers reales;
- altura seleccionada;
- altura real reproducida;
- reinicios visibles o eventos críticos.

## Endurecimiento de logs

`SnapMusicPlayback` ahora registra eventos compactos:

- `event=firstFrame`: primer frame, calidad seleccionada y altura real.
- `event=quality`: cambios reales de track, altura seleccionada, altura real y alturas disponibles.
- `event=rebuffer`: solo rebuffers reales después del arranque.
- `event=startupBuffer`: buffers iniciales dentro del margen de arranque, separados para no confundirlos con freezes de reproducción.

## Resultado ADB

| Video | Primer frame | Startup buffer | Rebuffers reales | Duración rebuffer | Altura seleccionada | Altura real | Reinicios | Errores críticos |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Rick Astley - Never Gonna Give You Up | 396 ms | 539 ms | 0 | 0 ms | 720p | 720p | 0 | 0 |
| PSY - Gangnam Style | 365 ms | 493 ms | 0 | 0 ms | 720p | 720p | 0 | 0 |
| Luis Fonsi - Despacito | 359 ms | 496 ms | 0 | 0 ms | 720p | 720p | 0 | 0 |
| Ed Sheeran - Shape of You | 320 ms | 372 ms | 0 | 0 ms | 720p | 720p | 0 | 0 |
| Queen - Bohemian Rhapsody | 370 ms | 503 ms | 0 | 0 ms | 720p | 720p | 0 | 0 |

## Cierre

- Los 5 videos mantuvieron `auto` con objetivo 720p y altura real 720p.
- No hubo reinicios durante los 3 minutos por video.
- No hubo caídas bruscas visibles de calidad.
- No hubo rebuffer repetido ni rebuffer real durante playback.
- Los buffers iniciales quedaron separados como `startupBuffer` para auditar arranque sin contaminar la métrica de freezes.

Logs crudos de la corrida: `build/playback-long-youtube/`.

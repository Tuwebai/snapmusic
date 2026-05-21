# Plan de trabajo — SnapMusic Android

## Estado general
- Estado: en progreso
- Fecha de inicio: 2026-05-15
- Objetivo: crear la base nativa Android de SnapMusic en Kotlin/Compose, con arquitectura modular, branding donor de SnapMusic-Nativa y lógica adaptada desde ConvertIO.

## Fases
1. [completa] Bootstrap del proyecto Android y documentación persistente
2. [completa] Arquitectura base: modelos, navegación, diseño y persistencia
3. [en progreso] Motor funcional: extractor, cola, descargas, storage y preview
4. [completa] Pantallas principales y UX SnapMusic
5. [completa] Validaciones, pruebas mínimas y cierre
6. [pendiente] Ajustes v2 guiados por SnapTube: FFmpegKit real, share target, clipboard UX, about y pulido lógico
7. [en progreso] Auditoría enterprise de estabilidad, rendimiento, UX y remediación priorizada
8. [en progreso] Remediación 60fps y reducción de tamaño por build/perf/render
9. [completa] Auditoría observable de SnapTube en dispositivo y plan de adopción segura para SnapMusic
10. [completa] Auditoría ADB de SnapTube y YouTube para stream, fullscreen y UX comparada con SnapMusic
11. [completa] Auditoría focalizada de watch player, controles y fullscreen para clonado visual/funcional
12. [en progreso] Auditoría integral de flujos completos en dispositivo con reporte consolidado de lag, bugs y jank

## Decisiones cerradas
- App nueva en `C:\Users\juan\Documents\Proyectos\SnapMusic`
- Nombre visible: `SnapMusic`
- `applicationId` / `namespace`: `com.juan.snapmusic`
- Stack: Kotlin + Compose + Navigation + Room + DataStore + WorkManager + Media3 + Coil
- Alcance v1: YouTube, audio + video, cola, historial, preview, favoritos, cancelación y notificaciones
- Estética: oscuro cinemático con acento rojo, inspirada en SnapTube y aterrizada con el branding donor de SnapMusic-Nativa
- La estabilización release-blocker pasa a ser prioridad por encima de nuevas features de streaming.

## Riesgos activos
- `FFmpegKit` todavía no quedó vendorado ni cableado; por ahora la slice resuelve descargas directas y deja transcodificación/mux como siguiente paso.
- Android SAF + foreground work + mux/transcode requieren pruebas reales en dispositivo.
- El flujo tipo SnapTube más natural depende de intents, clipboard y pruebas en dispositivo real.
- `lintDebug` ya quedó estable, pero la deuda pendiente sigue en performance, monolitos y hardening funcional.
- Hay monolitos de UI/estado que superan ampliamente los límites de mantenimiento y elevan el riesgo de regresión.
- La carga de biblioteca local todavía mezcla acceso pesado a MediaStore con orquestación de UI sin una separación enterprise.
- El rendimiento visible depende todavía de seguir desacoplando estado amplio en reproducción, miniplayer y listas pesadas.
- La reducción fuerte del tamaño de distribución quedó encaminada con shrink/minify y APKs por ABI, pero el bundle FFmpeg sigue siendo la mayor mochila estructural.
- Los siguientes cuellos reales de 60fps ya quedaron aislados: refresco prematuro de biblioteca local, observación amplia en raíz y polling agresivo en overlays/reproductores.
- El feed YouTube y watch-next ya entraron en remediación estructural:
  - faltaba paginación real de origen
  - Home y watch-next estaban limitados por leer solo la primera página de búsquedas/trending
  - la solución elegida es continuidad por lotes con ranking previo al render, no carga masiva upfront
- La comparación con SnapTube debe mantenerse en el terreno observable/verificable; no conviene copiar permisos invasivos ni comportamiento no comprobado.
- La continuidad local entre foreground/background/notificaciones ya quedó encaminada con snapshot propio, pero todavía requiere prueba manual completa de app fría, audio focus y reentrada desde sistema.
- La comparación nueva con SnapTube y YouTube para stream/fullscreen debe quedarse en evidencia ADB y UI observable; no conviene inferir comportamientos que no quedaron expuestos en dumps o pantallas reales del dispositivo.
- Para clonar watch player, controles y fullscreen, conviene separar:
  - evidencia ADB de estructura
  - referencias visuales aprobadas en el chat para tamaño, posición e iconografía exacta
- La nueva auditoría integral de flujos confirmó que el problema más caro actual ya no es una sola pantalla:
  - `gfxinfo` del proceso vivo reporta 91.53% de jank
  - el watch player sigue siendo el cuello más crítico por arranque y buffering
  - la raíz de navegación/Home y la biblioteca local siguen siendo los siguientes dos frentes más caros
- La re-auditoría de regresión confirmó tres puntos donde cambios recientes reinyectaron jank:
  - observación de PiP/autoplay en la raíz Compose
  - observación de descargas activas completas en la raíz de `PreviewScreen`
  - polling de progreso/buffer dentro del mismo shell que hospeda el `PlayerView` de YouTube

## Errores registrados
- Falta inicial de `local.properties` para el SDK Android; resuelto apuntando al SDK local.
- Tema XML Material3 no disponible para resources linking; resuelto usando tema base de Material Components.
- Auditoría 2026-05-17: `lintDebug` falla con 33 errores y 85 warnings; quedan abiertos problemas de PiP/API level, `Base64` en snapshot, `SessionResult` inválido y manifest con warnings críticos de compatibilidad.

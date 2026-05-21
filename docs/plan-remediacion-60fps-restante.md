# Plan de remediación 60fps restante — SnapMusic

## Criterio de priorización
Ordenado por:
1. severidad visible en dispositivo
2. costo de CPU/render
3. riesgo de regresión
4. facilidad de validación real

## Prioridad alta

### A. Biblioteca local de Reproducir
- Severidad: alta
- Costo: medio
- Riesgo: medio
- Causa raíz:
  - thumbnails locales y lista larga siguen siendo el frente más costoso fuera de YouTube
- Acción:
  - seguir reduciendo costo de miniaturas locales
  - separar todavía más la fila activa del resto de la lista
  - evitar invalidación completa cuando cambian progreso, detalle o descargas activas
- Cierre:
  - scroll largo sin stutter dominante en biblioteca real

### B. Restauraciones y mini player
- Severidad: alta
- Costo: medio
- Riesgo: medio/alto
- Causa raíz:
  - minimizar/restaurar sigue rozando el árbol de navegación más de lo ideal
- Acción:
  - dejar la restauración en un host todavía más fino
  - evitar que el retorno al watch/detail toque lista/feed si no cambia contenido visible
- Cierre:
  - back/swipe down/miniplayer sin tirón apreciable en Home ni Reproducir

## Prioridad media

### C. Submit Buscar → YouTube
- Severidad: media
- Costo: bajo/medio
- Riesgo: bajo
- Causa raíz:
  - la transición aún junta overlay, cambio de tab y feed remoto
- Acción:
  - mantener submit como flujo corto
  - revisar si conviene diferir parte del refresh visual hasta después del cambio de tab
- Cierre:
  - transición sin tirón visible al enviar búsqueda

### D. Watch screen residual
- Severidad: media
- Costo: medio
- Riesgo: medio
- Causa raíz:
  - aunque el host ya está separado, todavía conviven overlays, comentario y sugerencias debajo
- Acción:
  - seguir observando si queda recomposición lateral en fullscreen / watch / mini
- Cierre:
  - abrir video y volver sin congelar Home

## Prioridad baja

### E. Ajustes, overlays secundarios y estados raros
- Severidad: baja
- Costo: bajo
- Riesgo: bajo
- Acción:
  - auditar solo si siguen apareciendo síntomas en dispositivo
- Cierre:
  - sin cuellos visibles fuera de los flujos principales

## Validación obligatoria
- `:app:assembleDebug`
- instalar APK en teléfono
- repro manual:
  - Buscar
  - submit a YouTube
  - scroll largo del feed
  - abrir/minimizar/restaurar video
  - abrir Reproducir
  - scroll largo con biblioteca real

## Regla de cierre
- El siguiente slice no debe volver a abrir branding ni features.
- Solo puede salir de una causa raíz concreta medida o visible.
# Actualizacion 2026-05-18

## Estado nuevo
- `benchmark` ya cubre mejor tabs, Buscar -> YouTube, watch/minimize y Reproducir.
- La raiz de `PreviewScreen` ya no depende del `previewState` completo.
- El mini reproductor de Preview ya no fuerza navegacion redundante al abrirse dentro de la misma ruta.
- El siguiente cuello real ya no es la navegacion completa, sino:
  1. miniaturas locales visibles en scroll largo
  2. detalle activo de Reproducir conviviendo con biblioteca extensa
  3. restauracion del mini player en listas largas de gama media/baja

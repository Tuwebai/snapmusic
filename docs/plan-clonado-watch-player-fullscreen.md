# Plan por fases — clonado de watch player, controles y fullscreen para SnapMusic

## Objetivo

Clonar de manera correcta el comportamiento y la composición del:

- watch player
- controles sobre video
- fullscreen horizontal
- continuidad watch → mini player → restore

usando:

- referencias visuales aprobadas del chat para exactitud
- auditoría ADB de YouTube para estructura real
- auditoría focalizada de SnapTube para flujo y jerarquía UX

## Regla central

No se va a “improvisar” un player parecido.  
Se va a dividir el trabajo por shells, con contratos visuales y lógicos cerrados.

### Resultado esperado

El mismo overlay visual y lógico en todos los videos internos.

## Fase 3 — Fullscreen horizontal real

**Estado:** implementada base

### Objetivo

Que fullscreen sea una experiencia separada y no una simple variante de la vertical.

### Trabajo

- host propio de fullscreen
- overlays propias
- ocultar metadata/relacionados mientras está fullscreen
- mantener progreso, calidad y back sin lag ni reinicios
- implementar controles reales tambien a fullscreen horizontal,los mismos que watch player

### Resultado esperado

Fullscreen horizontal sólido, limpio y visualmente cercano a la referencia aprobada.

## Fase 4 — Mini player y restore como familia visual

**Estado:** implementada base

### Objetivo

Hacer que:

- back
- swipe down
- restore

formen una sola familia visual con el watch y fullscreen.

### Trabajo

- reforzar mini player para que conserve video/controles correctos
- garantizar restore sin reabrir capas incorrectas
- mantener continuidad de metadata y artwork sin flicker

### Resultado esperado

Una transición natural entre watch, mini player y vuelta al watch.

## Fase 5 — Rendimiento y validación

### Objetivo

Cerrar el clonado sin perder 60fps.

### Trabajo

- validar que overlays no recompongan todo el watch
- aislar progreso del resto del panel
- probar:
  - watch vertical
  - fullscreen horizontal
  - mini player
  - restore
  - descarga desde watch

### Resultado esperado

Clonado visual/funcional sin introducir lag nuevo.

## Orden recomendado

1. Fase 1
2. Fase 2
3. Fase 3
4. Fase 4
5. Fase 5

## Qué no entra en este plan

- permisos
- intents externos
- descargas en background
- browser interno
- features nuevas fuera del player

## Criterio de cierre

El clonado queda cerrado cuando:

- los controles se ven y se sienten iguales a la referencia aprobada
- fullscreen ya no parece una adaptación improvisada
- el watch shell ya no mezcla bloques
- mini player y restore conservan continuidad
- no aparece jank nuevo en reproducción

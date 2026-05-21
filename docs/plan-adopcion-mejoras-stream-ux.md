# Plan por fases — mejoras de stream, fullscreen y UX observadas en SnapTube + YouTube

## Resumen

Este plan toma solo lo **observable y verificable** en las auditorías ADB nuevas:

- `docs/auditoria-snaptube-stream-fullscreen-adb.md`
- `docs/auditoria-youtube-watch-home-adb.md`

No repite permisos, intents o decisiones ya cerradas en auditorías anteriores.  
Se enfoca solo en:

- stream
- fullscreen
- watch shell
- análisis/descarga
- continuidad visible al usuario

## Fase 2 — Separar el watch shell como lo hace YouTube

**Estado:** implementada base

### Objetivo

Partir SnapMusic en superficies pequeñas y estables:

- player
- overlays
- barra de tiempo
- metadata
- relacionados
- fondo cinemático

### Cambios

- seguir fragmentando el watch actual en hosts finos
- evitar que `Sigue:` o relacionados dependan del progreso del stream
- mantener el fondo del watch como capa separada y barata

### Beneficio

- menos recomposición
- mejor base de 60fps
- fullscreen y restore más previsibles

## Fase 3 — Mini player, restore y fullscreen como shells propios

**Estado:** implementada base

### Objetivo

Tratar mini player y fullscreen como estados explícitos, no como parches sobre listas o feeds.

### Cambios

- endurecer el shell del mini player
- mantener fullscreen desacoplado del feed y del panel inferior
- reforzar restore para que:
  - no reabra capas equivocadas
  - no mezcle share/análisis con playback actual

### Beneficio

- menos lag al minimizar/restaurar
- menos bugs visuales
- continuidad más parecida a YouTube


## Fase 5 — Validación en dispositivo antes de mover visuales grandes

### Objetivo

No romper 60fps ni continuidad mientras se adopta UX nueva.

### Validaciones obligatorias

- share/deeplink → análisis → descarga
- share/deeplink → análisis → reproducir
- volver desde stream al contexto anterior
- abrir fullscreen
- minimizar/restaurar
- watch con scroll largo de relacionados
- Reproducir local con mini player activo

### Regla cerrada

Si un efecto visual empeora la fluidez:

- se simplifica
- se desacopla
- o se elimina

## Prioridad recomendada

1. Fase 1
2. Fase 2
3. Fase 3
4. Fase 5
5. Fase 4

## Qué no se va a copiar

- permisos invasivos
- navegador interno
- overlay flotante
- comportamiento no observable en ADB
- iconografía exacta sin evidencia confiable de funcionamiento

## Resultado esperado

Si se ejecuta bien, SnapMusic debería ganar:

- flujo de descarga más corto y claro
- watch screen más estable
- fullscreen y mini player más sólidos
- menos jank por mezcla de estados
- una UX más cercana a SnapTube y YouTube sin copiar lo que no hace falta

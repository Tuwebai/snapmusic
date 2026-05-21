# Plan de remediación enterprise — SnapMusic

## Resumen
Este roadmap ordena la corrección de SnapMusic con prioridad en **release estable primero**, luego rendimiento, luego refactor enterprise y recién después hardening funcional ampliado.  
La regla central es: **no sumar nuevas features de streaming mientras existan blockers de compatibilidad, lint o threading básico**.

## Fase 1 — Auditoría consolidada
### Objetivo
Transformar observaciones sueltas en evidencia priorizada y backlog ejecutable.

### Alcance
- consolidar `testDebugUnitTest`, `assembleDebug` y `lintDebug`
- inventariar archivos monolíticos
- confirmar hotspots de estado, compatibilidad Android y placeholders
- dejar matriz única de riesgos

### Criterio de salida
- reportes `.md` completos
- matriz priorizada por severidad
- backlog ordenado por dependencia

## Fase 2 — Estabilización release-blocker
### Objetivo
Eliminar lo que hoy impide considerar estable una build.

### Orden obligatorio
1. encapsular PiP y `RemoteAction` por API level
2. reemplazar `java.util.Base64` por alternativa compatible o desugaring explícito
3. corregir contratos de `MediaSession` y `SessionResult`
4. revisar permisos y manifest
5. cerrar warnings/errores críticos de compatibilidad

### Validaciones obligatorias
- `lintDebug` con **0 errores**
- `testDebugUnitTest` verde
- `assembleDebug` verde
- prueba real en dispositivo:
  - arranque
  - YouTube
  - preview
  - PiP
  - notificación multimedia

### Dependencias
- bloquea todo lo demás si no está cerrada

## Fase 3 — Rendimiento y threading
### Objetivo
Sacar trabajo pesado del main thread y reducir recomposición global.

### Orden obligatorio
1. mover MediaStore/storage a `Dispatchers.IO`
2. auditar `SnapMusicApp` y `SnapMusicNavHost`
3. reducir observación global por feature
4. bajar side effects desde UI a coordinadores
5. revisar loops de progreso/persistencia del player

### Validaciones obligatorias
- sin queries pesadas en main
- apertura de Preview sin congelamiento
- cambio de tabs y playback más fluido
- inspección manual de recomposición en pantallas raíz

### Dependencias
- requiere Fase 2 cerrada para evitar mezclar compatibilidad con tuning

## Fase 4 — Refactor enterprise de estructura
### Objetivo
Eliminar monolitos y fijar ownership por dominio.

### Orden obligatorio
1. partir `SnapMusicViewModel`
2. extraer:
   - `HomeViewModel`
   - `QueueViewModel`
   - `YouTubePlaybackCoordinator`
   - `PreviewViewModel`
   - `SettingsViewModel`
3. mover lógica a use cases:
   - `LoadLocalMediaUseCase`
   - `ResolveYouTubePlaybackUseCase`
   - `EnqueueDownloadUseCase`
   - `RestorePlaybackSnapshotUseCase`
4. cortar archivos >200 líneas, prioridad total a >500
5. convertir navegación y `NavHost` en orquestación fina

### Validaciones obligatorias
- archivos críticos divididos
- dependencias más claras por feature
- sin regresiones funcionales en cola, preview y YouTube

### Dependencias
- no iniciar refactor amplio antes de cerrar Fase 3

## Fase 5 — Hardening funcional
### Objetivo
Quitar comportamientos ambiguos y endurecer flujos reales.

### Alcance
- reemplazar o degradar explícitamente `NoOpTranscodeEngine`
- robustecer restore de sesión
- unificar fallback de stream y copy de error
- estabilizar preview local con permisos y biblioteca grande
- alinear feedback visual entre cola, preview y YouTube

### Validaciones obligatorias
- sin estados fantasma
- sin autoplay involuntario
- descargas + preview + cola cerrando el loop de uso

## Fase 6 — QA enterprise y gates permanentes
### Objetivo
Hacer repetible la calidad.

### Implementación obligatoria
- smoke tests instrumentados para:
  - arranque
  - permisos
  - navegación base
  - playback/PiP
  - cola/preview
- gate mínimo local:
  - `testDebugUnitTest`
  - `assembleDebug`
  - `lintDebug`
- checklist manual por dispositivo:
  - Android 7/8 class
  - Android 13/14+
  - permisos
  - descargas
  - playback
  - PiP
  - segundo plano

### Criterio de salida
- proceso repetible de validación
- backlog residual separado de release blockers

## Reglas de ejecución
- No usar lint baseline para esconder errores actuales.
- No abrir nuevas líneas de producto mientras haya blockers de Fase 2.
- Cada fase debe cerrar con pruebas y con actualización de `task_plan.md`, `findings.md` y `progress.md`.
- Cada slice de refactor debe ser local, verificable y con riesgo de regresión acotado.

# Matriz de riesgos y bugs — SnapMusic

| ID | Área | Síntoma | Causa raíz | Severidad | Esfuerzo | Riesgo de regresión | Slice recomendado |
|---|---|---|---|---|---|---|---|
| AND-001 | Compatibilidad Android | PiP puede romper compatibilidad en APIs < 26 | Uso de `PictureInPictureParams.Builder` y `RemoteAction` sin encapsulado completo por API | Crítica | Medio | Alto | Fase 2.1 |
| AND-002 | Persistencia playback | Restore de sesión no seguro para `minSdk 24` | `YouTubePlaybackSnapshotCodec` usa `java.util.Base64` sin desugaring o reemplazo | Crítica | Bajo | Medio | Fase 2.2 |
| AND-003 | Media session | Controles de sesión pueden responder mal | Uso inválido de `SessionResult.RESULT_ERROR_NOT_SUPPORTED` | Crítica | Bajo | Medio | Fase 2.3 |
| AND-004 | Manifest / deeplinks | Matching ambiguo y warnings de lint | `intent-filter` con múltiples `data` combinadas | Media | Bajo | Bajo | Fase 2.4 |
| REL-001 | Calidad release | Lint falla y no hay gate de salida | No existe política de cierre de errores antes de avanzar features | Alta | Bajo | Bajo | Fase 2.5 |
| REL-002 | Distribución | Build release poco endurecida | `isMinifyEnabled = false` y sin estrategia de hardening | Media | Medio | Medio | Fase 6 |
| PERF-001 | Preview local | Posibles congelamientos al cargar biblioteca | `listLocalMedia()` no está blindado en `Dispatchers.IO` | Alta | Bajo | Medio | Fase 3.1 |
| PERF-002 | Storage / MediaStore | Carga lenta con bibliotecas grandes | Consulta completa de audio + video y ordenado en memoria | Alta | Medio | Medio | Fase 3.1 |
| PERF-003 | Compose raíz | Recomposición amplia al cambiar playback/cola | `SnapMusicApp` y `SnapMusicNavHost` observan demasiado estado global | Alta | Medio | Alto | Fase 3.2 |
| PERF-004 | YouTube UI | Side effects difíciles de razonar | Múltiples `LaunchedEffect` orquestando lógica de producto en UI | Media | Medio | Medio | Fase 3.4 |
| PERF-005 | Preview | Refresh duplicado en algunos flujos | Biblioteca local se refresca desde init y desde pantalla | Media | Bajo | Bajo | Fase 3.1 |
| ARC-001 | Estado global | Cambios de una feature afectan otras | `SnapMusicViewModel` concentra demasiadas responsabilidades | Alta | Alto | Alto | Fase 4.1 |
| ARC-002 | UI de cola | Archivo demasiado grande | `QueueScreen.kt` mezcla layout, acciones, tabs y feedback | Alta | Medio | Medio | Fase 4.4 |
| ARC-003 | UI de ajustes | Archivo demasiado grande | `SettingsPanels.kt` concentra demasiados paneles y decisiones visuales | Alta | Medio | Medio | Fase 4.4 |
| ARC-004 | UI YouTube | Archivo demasiado grande | `YouTubeFeedComponents.kt` mezcla watch screen, mini player y feed pieces | Alta | Medio | Medio | Fase 4.4 |
| ARC-005 | Home | Archivo demasiado grande | `HomeLandingPanels.kt` mezcla tabs y secciones densas | Media | Medio | Medio | Fase 4.4 |
| ARC-006 | Preview UI | Archivo demasiado grande | `PreviewPlaybackUi.kt` concentra biblioteca, reproductor y controles | Alta | Medio | Medio | Fase 4.4 |
| UX-001 | Feedback funcional | Algunos errores pueden sentirse técnicos o inconsistentes | Falta un criterio único de copy de error y recuperación | Media | Medio | Bajo | Fase 5 |
| UX-002 | Continuidad visual | Mini player, PiP y watch screen dependen de capas distintas | Lógica de reproducción no está totalmente encapsulada | Alta | Alto | Alto | Fase 5 |
| UX-003 | Formatos avanzados | Usuario puede esperar formatos no plenamente resueltos | Sigue existiendo `NoOpTranscodeEngine` como placeholder | Alta | Alto | Medio | Fase 5 |
| QA-001 | Cobertura | No hay pruebas instrumentadas mínimas | Solo existe cobertura de unit tests puntuales | Alta | Medio | Bajo | Fase 6 |
| QA-002 | Compatibilidad futura | La app se atrasa respecto del ciclo Android | `targetSdk = 34` ya genera warning `OldTargetApi` | Media | Medio | Medio | Fase 6 |

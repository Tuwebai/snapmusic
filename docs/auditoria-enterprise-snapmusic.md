# Auditoría enterprise — SnapMusic Android

## Estado actual
- Proyecto Android nativo funcional, con reproducción YouTube, cola, preview local, notificaciones, mini reproductor y PiP.
- El estado de release todavía no es estable: `lintDebug` falla con **33 errores** y **85 warnings**.
- La app ya resolvió mucho producto visible, pero quedó con deuda técnica acumulada en compatibilidad, arquitectura, threading y calidad de release.

## Inventario de problemas confirmados

### Severidad crítica
1. **Compatibilidad Android rota en PiP**
   - Evidencia: `MainActivity.kt` usa `PictureInPictureParams.Builder`, `setAspectRatio`, `setActions`, `build()` y `RemoteAction` con `minSdk 24`.
   - Impacto usuario: riesgo de crash o comportamiento inválido en APIs por debajo de 26 si el guard no cubre toda la ruta.
   - Impacto mantenibilidad: cualquier ajuste de playback/PiP queda frágil mientras la compatibilidad no esté encapsulada.

2. **Persistencia de snapshot con API no segura para `minSdk 24`**
   - Evidencia: `YouTubePlaybackSnapshotCodec.kt` usa `java.util.Base64`.
   - Impacto usuario: restore de sesión y snapshot pueden fallar en dispositivos fuera de API 26.
   - Impacto mantenibilidad: persiste una base técnica incorrecta para una función central de continuidad.

3. **Contrato multimedia incorrecto**
   - Evidencia: `SnapMusicPlaybackService.kt` devuelve `SessionResult.RESULT_ERROR_NOT_SUPPORTED`, constante inválida para ese uso.
   - Impacto usuario: controles de sesión/notificación pueden degradarse o responder mal ante comandos no soportados.
   - Impacto mantenibilidad: rompe la seguridad contractual de `MediaSession`.

### Severidad alta
4. **Monolito de estado principal**
   - Evidencia: `SnapMusicViewModel.kt` ~1010 líneas.
   - Impacto usuario: más probabilidad de regresiones cruzadas entre Home, Queue, YouTube, Preview y Settings.
   - Impacto mantenibilidad: ownership difuso, pruebas difíciles y evolución lenta.

5. **Pantallas grandes con demasiadas responsabilidades**
   - Evidencia:
     - `QueueScreen.kt` ~710 líneas
     - `SettingsPanels.kt` ~673
     - `YouTubeFeedComponents.kt` ~579
     - `HomeLandingPanels.kt` ~573
     - `PreviewPlaybackUi.kt` ~507
   - Impacto usuario: más riesgo de glitches visuales, recomposición costosa y deuda de consistencia.
   - Impacto mantenibilidad: revisión, refactor y testeo más caros.

6. **I/O pesado no gobernado como política**
   - Evidencia: `refreshLocalPreviewLibrary()` llama `storageRepository.listLocalMedia()` sin asegurar `Dispatchers.IO`.
   - Impacto usuario: riesgo de jank o congelamientos al cargar biblioteca local.
   - Impacto mantenibilidad: el contrato entre ViewModel y repositorio no protege el hilo principal.

7. **Feature placeholder con impacto de producto**
   - Evidencia: `NoOpTranscodeEngine`.
   - Impacto usuario: formatos/transcode pueden prometer más de lo que realmente cumplen.
   - Impacto mantenibilidad: introduce deuda funcional y branches de comportamiento opacos.

### Severidad media
8. **Raíz visual observando demasiado estado global**
   - Evidencia:
     - `SnapMusicApp`
     - `SnapMusicNavHost`
     - `HomeScreen`
     - `PreviewScreen`
   - Impacto usuario: recomposiciones más amplias de lo necesario.
   - Impacto mantenibilidad: cuesta razonar qué cambia y por qué.

9. **Carga de biblioteca local con estrategia cara**
   - Evidencia: `StorageRepository.listLocalMedia()` consulta audio y video completos y ordena en memoria.
   - Impacto usuario: costo acumulado a medida que crece la biblioteca local.
   - Impacto mantenibilidad: no hay cache, paginación ni separación por fuente.

10. **Release sin hardening**
    - Evidencia:
      - `isMinifyEnabled = false`
      - sin `androidTest`
      - sin baseline ni gate formal de lint
    - Impacto usuario: bugs visuales o contractuales más probables en builds de distribución.
    - Impacto mantenibilidad: no hay barreras reales de calidad.

### Severidad baja
11. **Warnings de manifest e intención**
    - Evidencia: múltiples `data` dentro del mismo `intent-filter` con warning `IntentFilterUniqueDataAttributes`.
    - Impacto usuario: bajo a corto plazo.
    - Impacto mantenibilidad: confusión semántica y ambigüedad de matching.

12. **Target SDK atrasado respecto del ciclo actual**
    - Evidencia: `targetSdk = 34` con warning `OldTargetApi`.
    - Impacto usuario: compat modes en versiones nuevas.
    - Impacto mantenibilidad: retrasa endurecimiento de compatibilidad.

## Smells estructurales
- ViewModel único absorbiendo estado, orchestration, restore, playback, queue feedback, settings y preview.
- UI de features mezclada con composición, microinteracciones y acciones de dominio en archivos demasiado extensos.
- Lógica multimedia repartida entre `MainActivity`, service, controller, ViewModel y composables.
- Deuda de release acumulada por haber priorizado producto visible sobre estabilización.

## Conclusión ejecutiva
SnapMusic ya tiene mucho valor funcional, pero hoy está en una etapa donde **seguir agregando features sin estabilizar** aumenta fuerte el costo de mantenimiento y el riesgo de regresión.  
La prioridad correcta es:
1. cerrar errores de lint y compatibilidad,
2. mover I/O y estados sensibles a una arquitectura más segura,
3. recién después seguir con más streaming o features premium.

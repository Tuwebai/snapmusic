# Auditoría 60fps — SnapMusic

## Resumen

La auditoría encontró tres causas raíz principales del lag visible en dispositivo:

1. **Trabajo innecesario al arranque**
   - La app cargaba la biblioteca local completa aunque el usuario no abriera `Reproducir`.
   - `PreviewScreen` además duplicaba el refresh con dos efectos.

2. **Observación demasiado amplia en la raíz**
   - `SnapMusicApp` observaba más estado del necesario para PiP.
   - Eso metía recomposición en la raíz cuando solo cambiaba playback.

3. **Polling agresivo en reproducción**
   - YouTube y Preview actualizaban posición/duración con intervalos demasiado chicos para la ganancia real.
   - Ese trabajo se sentía sobre todo en watch screen, mini player y transiciones.

## Causantes confirmados

### Carga temprana de MediaStore
- `SnapMusicViewModel.init` disparaba `refreshLocalPreviewLibrary()`.
- `StorageRepository.listLocalMedia()` consulta audio + video, arma la lista y ordena todo.
- Aunque corría en `Dispatchers.IO`, seguía siendo costo temprano sobre una app que todavía no necesitaba esa pantalla.

### Refresh duplicado en Reproducir
- `PreviewScreen` tenía dos `LaunchedEffect` que refrescaban la misma biblioteca.
- El resultado era más I/O, más mapping y más presión visual sin beneficio real.

### Root recomposition innecesaria
- `SnapMusicApp` observaba `youtubePlaybackRenderState` completo solo para saber si debía marcar PiP como reproduciendo.
- Eso hacía que cambios del player empujaran recomposición en la raíz.

### Polling fino en overlays
- YouTube:
  - posición: `250ms`
  - duración: `650ms`
- Preview:
  - progreso activo: `900ms`
- No era la única causa del lag, pero sí un amplificador del jank cuando el dispositivo ya estaba cargado.

## Cambios aplicados

- Carga de biblioteca local solo bajo demanda.
- Caché de snapshot local en `StorageRepository`.
- Invalidación explícita del caché al borrar una descarga.
- `PreviewScreen` unificado a un solo refresh controlado.
- `SnapMusicApp` reducido a observar solo `youtubePlaybackAutoPlay` para PiP.
- Polling de reproducción relajado:
  - YouTube: `500ms` / `2000ms`
  - Preview: `500ms` / `1500ms`
- Macrobenchmark ampliado para medir:
  - watch + mini player
  - biblioteca local + reproducción

## Impacto esperado

- Menor costo al abrir la app.
- Menos jank al moverse entre Inicio / YouTube / Reproducir.
- Menos trabajo innecesario cuando el player está activo.
- Mejor percepción de fluidez en watch screen y preview local.

## Deuda que sigue abierta

- `SnapMusicViewModel` sigue siendo demasiado grande.
- `YouTubeFeedComponents` y `PreviewPlaybackUi` siguen pesados.
- El siguiente corte fuerte debería separar coordinadores por dominio para seguir bajando recomposición lateral.

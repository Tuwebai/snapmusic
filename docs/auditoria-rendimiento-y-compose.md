# Auditoría de rendimiento y Compose — SnapMusic

## Resumen
La app no muestra todavía un cuello de botella único, pero sí varios patrones que juntos explican riesgo de jank, recomposición amplia y degradación de fluidez en dispositivos medios o con bibliotecas grandes.

## Hotspots de recomposición

### 1. Raíz de app demasiado observadora
- `SnapMusicApp` observa varios estados globales de playback y preview a la vez.
- `SnapMusicNavHost` observa `queue`, `youtubeState`, `previewState`, `previewDetailVisible` y `previewMiniVisible`.
- Efecto: cambios pequeños de playback o cola pueden forzar recomposición de capas demasiado arriba.
- Riesgo: el árbol entero navega y decide mini players desde un punto muy ancho.

### 2. Pantallas con demasiada densidad composable
- Archivos grandes y de alta responsabilidad:
  - `QueueScreen.kt` ~710 líneas
  - `SettingsPanels.kt` ~673
  - `YouTubeFeedComponents.kt` ~579
  - `HomeLandingPanels.kt` ~573
  - `PreviewPlaybackUi.kt` ~507
- Efecto: más difícil aislar state holders y memorizar subárboles costosos.
- Riesgo: recomposición accidental en tarjetas, listas y controles multimedia.

### 3. ViewModel global demasiado ancho
- `SnapMusicViewModel.kt` concentra demasiadas fuentes de verdad.
- Efecto: la UI se vuelve más dependiente de un único estado mutable de alto fan-out.
- Riesgo: tocar una feature puede generar invalidaciones laterales en otras.

## I/O y trabajo pesado

### 4. Biblioteca local sin garantía de `Dispatchers.IO`
- `refreshLocalPreviewLibrary()` llama `storageRepository.listLocalMedia()` desde un `viewModelScope.launch` genérico.
- `StorageRepository.listLocalMedia()`:
  - consulta audio
  - consulta video
  - arma listas completas
  - ordena en memoria
- Efecto: acceso a MediaStore + sorting puede caer en main si el caller no fuerza contexto.
- Riesgo: congelamiento visible al entrar a Preview o al refrescar biblioteca.

### 5. Estrategia de carga completa
- La biblioteca local no está paginada ni cacheada.
- Efecto: el costo crece linealmente con la cantidad de medios del dispositivo.
- Riesgo: dispositivos con mucha música/video local van a sentir latencia y más GC.

## Loops, `LaunchedEffect` y playback

### 6. Loop de progreso del player YouTube
- `YouTubePlaybackController.kt` mantiene un `while (isActive)` con `delay(5_000)` para persistir progreso.
- Efecto: no es grave por sí mismo, pero suma complejidad en sincronización de estado.
- Riesgo: si se duplica la estrategia en varias capas o cambia el scope, puede terminar emitiendo más de lo necesario.

### 7. Pantalla YouTube con múltiples `LaunchedEffect`
- `YouTubeTabContent.kt` tiene varios efectos para:
  - carga inicial
  - apertura de modal por descarga pendiente
  - apertura de sheet por flag global
- Efecto: la lógica de side effects está repartida en UI.
- Riesgo: condiciones de carrera y flows difíciles de seguir al crecer el feature.

### 8. Preview local refrescando más de una vez
- El refresh se dispara desde init del ViewModel y también desde `PreviewScreen` al detectar permiso.
- Efecto: posible trabajo duplicado en algunos flujos de entrada.
- Riesgo: costo innecesario justo al abrir una pantalla pesada.

## Player, session y background

### 9. Sesión multimedia con demasiadas fronteras
- Intervienen:
  - `MainActivity`
  - `SnapMusicPlaybackService`
  - `YouTubePlaybackController`
  - `SnapMusicViewModel`
  - composables de YouTube
- Efecto: la continuidad visual y la continuidad de sesión no están encapsuladas en una única capa.
- Riesgo: regresiones al tocar PiP, notificación, mini reproductor o autoplay.

### 10. Precarga y continuidad ya mejoradas, pero no cerradas
- La precarga del siguiente stream reduce cortes.
- Aun así, el estado sigue dependiendo de varias capas que mezclan UI y lógica de sesión.
- Riesgo: el rendimiento percibido mejora, pero la complejidad operativa queda alta.

## Deuda de rendering y jank potencial

### 11. Listas y pantallas densas
- Cola, feed YouTube, preview local y settings muestran bloques largos con visuales complejos.
- Sin segmentación más fina, cualquier cambio de estado puede reimpactar una superficie grande.

### 12. Diseño premium sin capa de performance dedicada
- La app ya tiene overlays, mini players, PiP, thumbnails, badges y cards complejas.
- Eso exige separar bien:
  - estado efímero
  - estado persistente
  - acciones de dominio
  - rendering puro

## Recomendaciones obligatorias
1. Forzar I/O real en repositorios de storage y consultas MediaStore.
2. Partir `SnapMusicViewModel` por dominios antes de seguir expandiendo features.
3. Bajar observación global desde raíz y mover estado a feature scopes más finos.
4. Reducir efectos en UI y centralizar side effects multimedia.
5. Replantear carga de biblioteca local con cache liviano y refresh explícito.
6. Cortar archivos grandes para poder aplicar memorization, previews y pruebas más puntuales.

# Research — SnapTube como referencia y ajustes v2 para SnapMusic

## Objetivo
Definir qué ajustes son normales y esperables para una app como SnapMusic, tomando como referencia pública a SnapTube, pero sin meter funciones raras ni desviar el foco de la app.

## Qué muestran las páginas públicas de SnapTube
### Patrones repetidos
- descarga por **URL pegada o compartida**
- elección clara de **formato** y **calidad**
- foco fuerte en **MP3, M4A y MP4**
- **descarga en segundo plano**
- manejo de **múltiples resoluciones**
- reproducción offline de lo descargado

### Señales específicas vistas
- `snaptube.io` destaca:
  - compatibilidad multi-plataforma
  - detección automática de recursos descargables
  - resoluciones desde 360p hasta 4K
  - descargas en segundo plano
  - playlists personalizadas
- `snaptube.io/snaptube-original` destaca:
  - reconocimiento rápido de links copiados
  - MP3 y M4A
  - descargas por lotes
  - reproductor en segundo plano
  - PiP y letras como extras
- `origin.snaptube.support` destaca:
  - botón de descarga desde compartir
  - detección de URL copiada
  - MP3/M4A/MP4
  - historial/gestión local de descargas

## Qué sí conviene copiar como patrón de producto
### Núcleo UX
- pegar link y analizar en un paso
- si el usuario viene desde “Compartir”, abrir SnapMusic ya con la URL lista
- si el usuario copió una URL válida, sugerirla sin fricción
- mostrar primero las opciones más usadas:
  - MP3 320
  - M4A
  - MP4 720p
  - MP4 1080p cuando exista

### Núcleo funcional
- transcodificación real a MP3
- mux real de audio + video cuando el stream venga separado
- progreso confiable
- cancelación real
- reanudación de cola tras reinicio de app
- manejo prolijo de nombres de archivo y conflictos

### Núcleo de biblioteca local
- historial real de descargas
- preview del último archivo
- acceso rápido a la carpeta destino
- favoritos de carpetas

## Qué no conviene meter ahora
- navegador interno completo
- home feed tipo red social
- soporte multi-sitio más allá de YouTube
- bóveda privada, status saver, suscripciones internas
- letras sincronizadas, PiP y extras pesados

Eso existe en el universo SnapTube, pero para SnapMusic hoy sería ruido y deuda.

## Ajustes v2 recomendados para SnapMusic
1. **FFmpegKit real**
   - conectar bundle local
   - habilitar MP3 128/192/256/320
   - habilitar mux MP4 cuando haga falta
2. **Entrada más natural**
   - `ACTION_SEND` / share target
   - detector de URL copiada al entrar
   - botón “Pegar y analizar”
3. **Flujo de descarga más sólido**
   - evitar duplicados exactos en cola
   - estado visual por item
   - errores accionables y humanos
4. **Biblioteca local**
   - abrir archivo
   - abrir carpeta
   - refrescar preview con metadata final
5. **Ajustes y confianza**
   - pantalla “Acerca de”
   - versión instalada
   - autor visible

## Copy obligatorio para “Acerca de”
- texto base: **Hecho por Juanchi López**

## Decisión de producto
SnapMusic debe parecer una app madura de descarga multimedia, no una app experimental:
- menos features exóticas
- más flujo directo
- más estabilidad
- más claridad en formato/calidad/destino

## Fuentes usadas
- https://www.snaptube.io/snaptube-download/
- https://www.snaptube.io/snaptube-original/
- https://origin.snaptube.support/

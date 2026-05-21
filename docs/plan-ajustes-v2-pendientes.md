# Plan de ajustes v2 pendientes — esperar confirmación antes de implementar

## Meta de esta siguiente etapa
Llevar SnapMusic desde una base funcional a una app más realista para uso diario, manteniendo el alcance normal de una app estilo SnapTube pero centrada en YouTube y biblioteca local.

## Bloque 1 — Motor real
### 1. Vendorizar FFmpegKit
- ✅ copiar AAR estable dentro del repo
- ✅ declararlo como dependencia local
- ✅ encapsularlo detrás de `TranscodeEngine`

### 2. Audio real
- ✅ convertir a MP3 real en 128/192/256/320
- ✅ conservar M4A directo cuando convenga
- ⏳ escribir metadata final y carátula cuando sea viable

### 3. Video real
- ✅ detectar streams separados
- ✅ descargar video + audio cuando haga falta
- ✅ muxear a MP4 final
- ✅ mantener naming consistente

## Bloque 2 — Flujo tipo SnapTube
### 4. Entrada por compartir
- ✅ registrar intent filter para recibir URLs desde YouTube y otras apps
- ✅ abrir SnapMusic con la URL precargada
- ✅ permitir descargar desde ese flujo sin pasos extra

### 5. Detección de clipboard
- ✅ al entrar a Home, inspeccionar clipboard
- ✅ si hay URL válida de YouTube, mostrar CTA:
  - ✅ “Usar link copiado”

### 6. Atajos normales
- ✅ botón “Pegar”
- ✅ botón “Pegar y analizar”
- ✅ presets rápidos:
  - ✅ MP3 320
  - ✅ M4A
  - ✅ MP4 720p

## Bloque 3 — Gestión local
### 7. Cola más robusta
- ✅ deduplicación por URL + formato + calidad + destino
- ✅ reanudación visual tras reinicio
- ✅ estados vacíos claros

### 8. Historial útil
- ✅ abrir archivo
- ✅ abrir carpeta
- ✅ reintentar descarga desde historial

### 9. Preview más serio
- ✅ mostrar duración
- ✅ controles básicos reales
- ✅ artwork final si existe

## Bloque 4 — Ajustes y confianza
### 10. Pantalla “Acerca de”
- ✅ nombre de app
- ✅ versión
- ✅ texto fijo:
  - ✅ **Hecho por Juanchi López**
- ✅ nota breve de uso local/personal si querés mantenerla

### 11. Mensajes y errores
- ✅ reemplazar errores técnicos crudos por textos claros
- ✅ distinguir:
  - ✅ error de red
  - ✅ error de extracción
  - ✅ error de transcodificación
  - ✅ error de permiso/carpeta

## Orden recomendado de implementación
1. FFmpegKit real
2. MP3 real
3. mux MP4 real
4. share target
5. clipboard UX
6. about screen
7. mejoras de cola/historial/preview

## Criterio de aceptación de esta etapa
- MP3 funciona de punta a punta
- MP4 con streams separados funciona
- compartir desde otra app abre SnapMusic con la URL lista
- la pantalla “Acerca de” muestra `Hecho por Juanchi López`
- el flujo principal queda natural, corto y confiable

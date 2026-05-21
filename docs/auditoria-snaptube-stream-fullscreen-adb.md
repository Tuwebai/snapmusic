# Auditoría ADB de SnapTube — stream, fullscreen y UX observable

## Alcance

Esta auditoría usa solo evidencia **observable por ADB** en el teléfono conectado.  
No se decompiló SnapTube ni se infirió lógica cerrada no expuesta.

## Dispositivo y paquete auditado

- Dispositivo: `23129RA5FL`
- Resolución física: `1080x2400`
- Densidad física: `440`
- Densidad override activa: `272`
- Paquete: `com.snaptube.premium`
- `versionName`: `7.58.1.75872701`
- `minSdk`: `21`
- `targetSdk`: `35`

## Evidencia usada

- `adb shell dumpsys package com.snaptube.premium`
- `adb shell uiautomator dump /sdcard/snaptube-home.xml`
- `adb pull /sdcard/snaptube-home.xml docs/snaptube-home.xml`
- `adb shell uiautomator dump /sdcard/snaptube-clean.xml`
- `adb pull /sdcard/snaptube-clean.xml docs/snaptube-clean.xml`

## Lo nuevo que sí quedó visible y útil

### 1. Landing centrada en búsqueda

En `snaptube-home.xml` se ve una estructura de entrada muy clara:

- `iv_bg`
- `iv_logo`
- `search_view`
- `search_box_edit`
- `iv_search`

Lectura UX:

- el hero no intenta resolver demasiadas tareas al mismo tiempo
- la acción principal visible es **buscar/pegar**
- el input completo funciona como puerta de entrada, no solo el icono de lupa

### 2. Tabs superiores de dominio

ADB expuso un `HorizontalScrollView` `tabs` con:

- `Buscar`
- `YouTube`
- `Música`
- `Más`

Lectura UX:

- SnapTube separa **intención** antes de abrir listas largas
- el cambio de contexto se hace arriba, cerca del search flow
- esto reduce ambigüedad entre explorar, buscar y bajar

### 3. Bottom nav corta y muy estable

En el dump aparecen solo tres destinos:

- `Descargar`
- `Reproducir`
- `Configuración`

Lectura UX:

- la barra inferior no compite con los tabs superiores
- cada destino tiene responsabilidad muy clara
- evita mezclar feed, stream y biblioteca en demasiados puntos de entrada

### 4. Sheet de descarga con disclosure progresivo

En el estado visible por ADB aparecen:

- `tv_download_as_label`
- `format_listview`
- `tv_more_formats`
- `download_button`

Y opciones resumidas tipo:

- `Rápido`
- `MP3 clásico`
- `Rápido (360p)`
- `Calidad alta (720p)`
- `Más formatos`

Lectura UX:

- SnapTube **no abre de entrada una lista gigantesca**
- primero ofrece presets cortos y entendibles
- deja el resto detrás de `Más formatos`
- el CTA `Descargar` queda fijo abajo y no se mueve con el scroll

### 5. Contexto del item siempre visible dentro del sheet

ADB mostró en la cabecera del sheet:

- cover (`iv_cover`)
- título (`tv_link`)
- host (`tv_host`)

Lectura UX:

- el usuario no pierde el contexto del video al elegir formato
- la selección de calidad no rompe la continuidad mental con el ítem elegido

## Stream y fullscreen: límite real de esta pasada

En esta auditoría ADB **sí** quedó visible el shell de búsqueda y el sheet de formatos, pero **no** quedó expuesto un estado confiable de fullscreen/stream de SnapTube para documentar controles exactos sin inventar.

Lo observable con seguridad en esta pasada es:

- SnapTube prioriza análisis/descarga antes de empujar al player
- el flujo de share/deeplink puede aterrizar directo en el sheet
- la UX de formatos está muy pulida y comprimida

Lo que **no** se documenta como hecho, porque no quedó probado en dumps/pantallas de esta pasada:

- layout exacto del fullscreen horizontal
- controles exactos del player de stream
- comportamiento interno del miniplayer

## Comparación puntual con SnapMusic

### Ya resuelto o equivalente

- SnapMusic ya tiene:
  - branding propio
  - tabs superiores tipo Buscar/YouTube/Convertir
  - sheet real de formatos
  - continuidad desde share/deeplink

### Diferencias relevantes todavía útiles

1. **SnapMusic sigue mostrando demasiada decisión demasiado temprano** en algunos flujos.  
   SnapTube resume mejor con presets cortos + `Más formatos`.

2. **La continuidad visual entre análisis y descarga** en SnapTube es más compacta.  
   El cover, host y CTA quedan resueltos en el mismo bloque.

3. **La superficie inferior de CTA** en SnapTube es más estable.  
   Conviene seguir endureciendo CTAs fijos y layouts con menos salto.

## Mejoras concretas recomendadas para SnapMusic

1. Mantener el input completo como punto de entrada total en todos los entry points externos.
2. Consolidar el sheet de formatos con:
   - presets cortos arriba
   - `Más formatos` como expansión manual
   - CTA fijo abajo
3. Mantener cover + título + host visibles mientras el usuario elige formato.
4. Evitar abrir player o navegación extra cuando el usuario todavía está en modo análisis/descarga.

## Impacto esperado si se adopta bien

- menos fricción entre compartir y descargar
- menos carga visual al elegir formato
- sensación más inmediata y controlada tipo SnapTube
- cero necesidad de copiar permisos o servicios extra

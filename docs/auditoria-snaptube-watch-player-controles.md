# Auditoría focalizada de SnapTube — watch player, controles y fullscreen

## Alcance real de esta auditoría

Esta auditoría se centra **solo** en:

- watch player
- controles sobre el video
- fullscreen horizontal
- continuidad visual entre watch, mini player y descarga

No repite permisos, intents ni hallazgos de storage ya documentados en otras auditorías.

## Fuentes usadas

### Evidencia visual aprobada en este chat

Se usaron como referencia directa las capturas ya aportadas en la conversación para:

- controles del player vertical
- fullscreen horizontal
- mini player / PiP visual

Estas referencias son las únicas que permiten fijar:

- tamaño exacto de iconos
- jerarquía visual
- posición relativa
- espaciados

### Evidencia ADB verificable

- `docs/snaptube-home.xml`
- `docs/snaptube-clean.xml`
- `dumpsys package com.snaptube.premium`

Con ADB sí quedó confirmada la estructura general de búsqueda, tabs, CTA de descarga y sheet de formatos.  
Para el player exacto, la evidencia más confiable sigue siendo la captura visual aprobada por el usuario.

## Hallazgos útiles para clonar el watch player

### 1. SnapTube usa un watch muy directo y con poco ruido

Patrón visible en referencias:

- video arriba ocupando protagonismo casi total
- controles mínimos sobreimpresos
- título y metadata fuera del área de video
- CTA de descarga fuerte debajo
- relacionados inmediatamente después

Qué significa para SnapMusic:

- el video no debe compartir aire con tabs, títulos flotando sobre el frame ni wrappers pesados
- la información del item va **debajo**, no montada de forma invasiva sobre el contenido

### 2. Los controles son pocos y perfectamente centrados

Patrón visible en referencias:

- atrás arriba a la izquierda
- acciones livianas arriba a la derecha
- anterior, play/pause y siguiente centrados
- barra de progreso abajo
- fullscreen abajo a la derecha cuando corresponde

Qué significa para SnapMusic:

- no conviene sumar botones extra “por si acaso”
- cada control debe tener:
  - mismo tamaño visual
  - misma distancia entre sí
  - misma opacidad/jerarquía

### 3. Fullscreen horizontal es otro shell, no un parche

Patrón visible:

- cambia la composición completa
- desaparece el bloque inferior de metadata/relacionados
- quedan solo video + overlays + barra + acciones mínimas

Qué significa para SnapMusic:

- fullscreen no debe sentirse como “la misma pantalla estirada”
- necesita host propio, con overlays propias y layout distinto

### 4. SnapTube prioriza una salida limpia del flujo de reproducción

En las referencias de mini player y sheet:

- el contexto de reproducción sigue claro
- la descarga no destruye el stream
- el mini player sigue mostrando continuidad visual

Qué significa para SnapMusic:

- abrir sheet de descarga desde watch o relacionados nunca debe vaciar ni romper el player
- el mini player debe seguir siendo una continuación natural del watch

## Hallazgos ADB que sí aportan al clonado

Aunque ADB no dejó fijado el layout final del player exacto, sí confirmó patrones de UX valiosos:

### Home y entrada

En `snaptube-home.xml` se ve:

- `search_view`
- tabs superiores
- bottom nav corta

Eso confirma que SnapTube empuja al usuario a:

- analizar
- elegir
- descargar o reproducir

sin mezclar todo en una misma superficie.

### Sheet de formatos

ADB dejó visibles:

- `format_listview`
- `tv_more_formats`
- `download_button`

Eso confirma un patrón de flujo muy útil para SnapMusic:

- presets resumidos primero
- expansión manual después
- CTA fijo abajo

## Comparación puntual con SnapMusic

### Lo que hoy SnapMusic ya tiene cerca

- overlay propia de video
- fullscreen horizontal
- mini player y PiP
- sheet de formatos real

### Lo que todavía falta para clonar de verdad

1. **Controles aún demasiado variables** según estado o pantalla.  
   Deben volverse un único contrato visual.

2. **Watch shell todavía mezcla cosas** que SnapTube separa mejor:
   - video
   - metadata
   - CTA
   - relacionados

3. **Fullscreen debe endurecerse como modo propio**, no solo como una bandera.

4. **La transición watch → mini player → restore** debe verse como una familia visual única.

## Decisiones cerradas para el clonado

1. Tomar la captura aprobada del chat como referencia primaria para:
   - iconos
   - tamaños
   - posiciones
   - espaciado

2. Usar la evidencia ADB solo para:
   - confirmar estructura de flujo
   - evitar suposiciones sobre navegación y continuidad

3. No agregar extras no visibles en la referencia:
   - botones sobrantes
   - títulos sobre el video
   - overlays recargados

## Qué conviene implementar después de esta auditoría

1. un `watch shell` más limpio
2. un contrato único de `player controls`
3. un `fullscreen host` propio
4. transición visual coherente entre watch, mini player y restore

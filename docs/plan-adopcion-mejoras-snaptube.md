# Plan de adopción segura de mejoras observables de SnapTube

## Resumen

Este plan toma solo mejoras **observables, útiles y seguras** de SnapTube para llevarlas a SnapMusic sin copiar permisos invasivos ni comportamiento no comprobado.

## Fase A — Seguridad y permisos

- Mantener scoped storage actual de SnapMusic como decisión cerrada.
- No adoptar:
  - `MANAGE_EXTERNAL_STORAGE`
  - `SYSTEM_ALERT_WINDOW`
  - `REQUEST_LEGACY_EXTERNAL_STORAGE`
  - `LARGE_HEAP`
  - `FOREGROUND_SERVICE_SPECIAL_USE`
- Auditar si `READ_MEDIA_VISUAL_USER_SELECTED` participa de un flujo real; si no, preparar su remoción.
- Evaluar subida a `targetSdk=35` solo después de validar:
  - playback
  - descargas
  - PiP
  - notificaciones

## Fase B — Inbound, share y deeplink

- Extender SnapMusic para soportar de forma robusta:
  - `SEND` de texto/link
  - `SEND_MULTIPLE` cuando el contenido sea compatible
  - `VIEW/BROWSABLE` solo para orígenes soportados de verdad
- Definir routing exacto:
  - link compartido → Buscar/Convertir con análisis directo
  - deeplink YouTube → apertura controlada sin romper sesión vigente
  - múltiples shares → cola de entradas o rechazo explícito si el tipo no se soporta
- Mantener la superficie externa acotada; no abrir handlers genéricos sin soporte real.

## Fase C — Background, reproducción y notificaciones

- Verificar paridad funcional contra lo observable de SnapTube en:
  - foreground de descargas
  - media playback service
  - reentrada desde notificación
- Mantener solo tipos explícitos mínimos:
  - `mediaPlayback`
  - `dataSync`
- Revisar que las notificaciones de SnapMusic reabran exactamente:
  - stream activo
  - pantalla de descargas activas
  - biblioteca/reproducción local cuando corresponda

## Fase D — UX útil a importar

- Mejorar continuidad entre:
  - buscar
  - abrir stream
  - descargar
  - reentrar desde share o notificación
- Extender compatibilidad con fuentes de compartir sin romper la sesión actual.
- No implementar:
  - navegador interno
  - overlays flotantes
  - vault
  - herramientas extra fuera del dominio de SnapMusic
- Estado:
  - implementada con `IncomingSharePayload`
  - soporte visible para `SEND`, `SEND_MULTIPLE` y `VIEW` compatibles
  - selección explícita cuando llegan varios links válidos
  - preservación del playback actual mientras se analiza el contenido compartido

## Cambios concretos esperados en SnapMusic

- Manifest:
  - posible alta de `SEND_MULTIPLE`
  - posible ajuste medido de `VIEW/BROWSABLE`
  - sin permisos invasivos nuevos
- Routing interno:
  - contrato claro para intents externos
  - separación entre:
    - share/link
    - deeplink
    - notificación de playback
    - notificación de descargas
- Seguridad:
  - matriz documentada de permisos aceptados vs rechazados

## Validación

- Compartir 1 link de YouTube a SnapMusic
- Compartir múltiples entradas si se implementa `SEND_MULTIPLE`
- Abrir links por `VIEW/BROWSABLE`
- Reabrir desde notificación de descarga
- Reabrir desde notificación de reproducción
- Confirmar que todo siga funcionando sin:
  - `MANAGE_EXTERNAL_STORAGE`
  - `SYSTEM_ALERT_WINDOW`
  - `LARGE_HEAP`

## Criterio de cierre

La implementación queda bien cerrada cuando:

- SnapMusic mejora el ingreso externo y la continuidad de uso
- no suma permisos invasivos
- no depende de comportamiento legado de storage
- las decisiones quedan documentadas para que no se “copien por reflejo” patrones más agresivos de SnapTube

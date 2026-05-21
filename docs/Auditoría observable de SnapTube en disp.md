Auditoría observable de SnapTube en dispositivo + plan de adopción segura para SnapMusic
Resumen
Se va a hacer una auditoría solo observable y verificable de SnapTube en el teléfono, sin decompilar ni inferir lógica interna no comprobable.
Base ya confirmada en el dispositivo:

SnapTube: com.snaptube.premium, versionName=7.58.1.75872701, minSdk=21, targetSdk=35
Permisos solicitados por SnapTube observados:
POST_NOTIFICATIONS
MANAGE_EXTERNAL_STORAGE
SYSTEM_ALERT_WINDOW
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE
FOREGROUND_SERVICE_DATA_SYNC
FOREGROUND_SERVICE_MEDIA_PLAYBACK
red / wifi / wake lock / reorder tasks
Flags visibles:
LARGE_HEAP
REQUEST_LEGACY_EXTERNAL_STORAGE
múltiples SEND / SEND_MULTIPLE
muchos BROWSABLE
SnapMusic hoy:
versionName=1.0.77, minSdk=24, targetSdk=34
usa permisos de medios específicos (READ_MEDIA_AUDIO, READ_MEDIA_VIDEO)
no usa MANAGE_EXTERNAL_STORAGE
no usa SYSTEM_ALERT_WINDOW
no usa LARGE_HEAP
expone SEND simple de texto y deeplinks de YouTube
Decisión cerrada:

no se va a copiar ciegamente el modelo de permisos de SnapTube
se van a adoptar solo mejoras útiles, seguras y justificadas
se va a documentar explícitamente qué sí conviene importar y qué no
Cambios y entregables
1) Documentación a crear/actualizar
Actualizar:

task_plan.md
findings.md
progress.md
Crear:

docs/auditoria-snaptube-observable.md
docs/plan-adopcion-mejoras-snaptube.md
2) Contenido exacto de la auditoría
docs/auditoria-snaptube-observable.md debe incluir, con evidencia del dispositivo:

paquete, versión, minSdk, targetSdk
permisos solicitados y runtime/appops relevantes
intents visibles:
SEND
SEND_MULTIPLE
VIEW
BROWSABLE
servicios foreground observables y tipos
flags relevantes:
LARGE_HEAP
REQUEST_LEGACY_EXTERNAL_STORAGE
audio playback capture
comparación directa con SnapMusic:
permisos
intents
storage model
foreground/background model
tamaño/apk
tabla por hallazgo:
hallazgo
evidencia
impacto
conviene adoptar
riesgo
3) Plan de adopción en SnapMusic
docs/plan-adopcion-mejoras-snaptube.md debe quedar por fases, con decisiones cerradas:

Fase A — Seguridad y permisos
Mantener scoped storage actual de SnapMusic.
No adoptar:
MANAGE_EXTERNAL_STORAGE
SYSTEM_ALERT_WINDOW
REQUEST_LEGACY_EXTERNAL_STORAGE
LARGE_HEAP
Auditar si READ_MEDIA_VISUAL_USER_SELECTED sigue teniendo sentido o si sobra en el flujo real.
Evaluar subida de targetSdk a 35 solo si no rompe playback/download/PiP.
Fase B — Inbound/share/deeplink
Extender SnapMusic para soportar de forma robusta:
SEND de texto
SEND_MULTIPLE cuando tenga sentido real
VIEW para links compatibles
Mantener deeplink estricto a orígenes soportados; no abrir superficie genérica innecesaria.
Definir routing exacto:
link compartido → pantalla Buscar/Convertir con análisis directo
video compartido mientras ya hay playback → no romper sesión actual sin confirmación o cola explícita
Fase C — Background/foreground
Revisar paridad funcional con SnapTube en:
foreground service de descarga
media playback service
notificaciones
reentrada desde notificación
Mantener tipos explícitos y mínimos:
mediaPlayback
dataSync
No sumar FOREGROUND_SERVICE_SPECIAL_USE salvo necesidad real demostrada.
Fase D — UX/flujo observable útil
Mapear mejoras observables de SnapTube que sí tienen valor para SnapMusic:
share target más completo
reentrada más natural desde notificaciones
continuidad entre buscar → reproducir → descargar
compatibilidad mejor con múltiples fuentes de “compartir”
No planear navegador interno, overlay flotante ni extras fuera del alcance actual.
Cambios importantes de interfaces / contratos
Manifest de SnapMusic:
posible incorporación de SEND_MULTIPLE
posible ampliación medida de VIEW/BROWSABLE si la auditoría confirma utilidad concreta
sin permisos invasivos nuevos por default
Ruteo interno:
nuevo contrato claro para entrada externa (IntentRouter o equivalente actual)
separación entre:
compartir texto/link
abrir deeplink
retomar playback/notificación
Capa de seguridad:
tabla de permisos aceptados vs rechazados, documentada como decisión del producto
Plan de pruebas
Casos obligatorios
compartir 1 link de YouTube a SnapMusic
compartir múltiples elementos si se decide soportarlo
abrir links VIEW/BROWSABLE
descargar con app en foreground y background
reabrir desde notificación de descarga
reabrir desde notificación de reproducción
reproducir y descargar sin pedir permisos invasivos
validar que SnapMusic siga funcionando sin:
MANAGE_EXTERNAL_STORAGE
SYSTEM_ALERT_WINDOW
LARGE_HEAP
Criterios de aceptación
la auditoría deja evidencia verificable, no opiniones
el plan distingue claramente:
mejoras a adoptar
mejoras a rechazar
mejoras a evaluar después
ningún cambio propuesto requiere decompilación ni supuestos sobre lógica cerrada de SnapTube
el implementador puede ejecutar el plan sin decidir por su cuenta qué permisos copiar o evitar
Supuestos y defaults
El alcance queda limitado a auditoría observable del paquete instalado y del comportamiento visible.
No se va a reverse-engineerear SnapTube.
“Mejoras” significa adoptar solo lo que mejore SnapMusic sin degradar seguridad, permisos ni mantenimiento.
Si SnapTube usa una capacidad riesgosa pero SnapMusic no la necesita, la decisión por default es no adoptarla.
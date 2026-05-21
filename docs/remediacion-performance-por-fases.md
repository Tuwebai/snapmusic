# Remediación de performance por fases — SnapMusic

## Fase aplicada ahora

### Fase 1 — aliviar arranque, raíz y polling
- sacar carga temprana de biblioteca local
- cachear snapshot local
- recortar observación amplia en `SnapMusicApp`
- reducir polling agresivo de YouTube/Preview
- ampliar benchmarks mínimos

## Fase siguiente recomendada

### Fase 2 — cortar monolitos que siguen arrastrando jank
- separar `SnapMusicViewModel` por dominio:
  - Home
  - YouTube playback
  - Preview library
  - Preview playback
  - Downloads
- mover derivaciones pesadas fuera de Compose
- evitar que el feed dependa de chrome/player state lateral

## Fase 3 — virtualización y render fino

- revisar filas pesadas de `YouTubeFeedComponents`
- revisar lista integrada de `Reproducir`
- usar estados más estables por tarjeta/item
- conservar miniaturas memoizadas sin recreación accidental

## Fase 4 — bundle nativo mínimo

- vendorizar FFmpegKit recortado a:
  - MP3
  - M4A
  - mux MP4 actual
- medir tamaño final por ABI
- validar descargas reales en dispositivo

## Criterios de salida

- app sin tirones visibles al abrir
- feed YouTube scrollable con mejor estabilidad
- Reproducir sin refrescos redundantes
- mini player/watch con menos jank
- release arm64 significativamente más liviano que debug

# MedidorBLE – App Android per mesurar espais amb làser BLE

## Descripció
App Android en Kotlin per mesurar habitacions paret a paret connectant-se al làser
Popoman LMBT60 (o qualsevol làser BLE genèric) i exportant el plànol a DXF i les
superfícies a Excel.

## Funcionalitats
- Connexió BLE automàtica (UUID FFE0/FFE1 i Nordic UART)
- Parser multi-format: ASCII metres/cm/mm + binary 6B, 4B, 2B
- Entrada manual com a fallback
- Plànol 2D en temps real (Canvas personalitzat)
- Múltiples habitacions per projecte
- Angle de gir configurable (defecte 90°, slider 0°–270°)
- Desfer última paret
- Export **DXF R12** (AutoCAD, LibreCAD, FreeCAD)
- Export **XLSX** (Excel, LibreOffice, Google Sheets) sense dependències externes
- Compartir via Android Sharesheet

## Requisits
- Android Studio Hedgehog (2023.1.1) o superior
- Android 6.0+ (API 23+)
- Gradle 8.2+

## Com obrir el projecte
1. Descomprimeix `MedidorBLE.zip`
2. Obre Android Studio → Open → selecciona la carpeta `MedidorBLE/`
3. Deixa que Gradle sincronitzi
4. Compila i instal·la (Run ▶)

## Flux de treball
1. Pantalla principal → `+` → Nom del projecte
2. Toca el projecte → Editor d'habitacions
3. [Opcional] **Cercar Làser BLE** → selecciona el Popoman LMBT60
4. **+ Habitació** → posa nom
5. Apunta el làser a la primera paret → mesura rebuda automàticament (o **Manual**)
6. Ajusta l'angle de gir si cal (defecte 90°)
7. **+ Paret** per afegir
8. Repeteix fins que tanqui l'habitació
9. **✓ Tancar** per confirmar superfície
10. **↑ DXF** o **↑ Excel** per exportar

## Protocol BLE – Popoman LMBT60
El làser usa xip HM-10 (o compatible):
- **Servei**: `0000FFE0-0000-1000-8000-00805F9B34FB`
- **Característica notificació**: `0000FFE1-0000-1000-8000-00805F9B34FB`
- **Dades**: típicament 6 bytes, mm en bytes [4][5] big-endian
- El parser prova múltiples formats per màxima compatibilitat

## Format DXF generat
DXF R12 ASCII (AC1009) amb capes:
- `WALLS` – polilínies de les habitacions
- `DIMS`  – cotes de cada paret
- `TEXT`  – nom i superfície de cada habitació
- Unitats: metres

## Format Excel generat
XLSX (Office Open XML) manual, sense dependències:
- Columnes: Habitació / Paret / Longitud (m) / Superfície (m²) / Perímetre (m)
- Files resum per habitació amb color blau
- Fila de totals si hi ha més d'una habitació
- Filtre automàtic a la capçalera

## Extensions possibles
- Persistència local (Room Database / JSON)
- Detecció automàtica de tancament (ja implementada parcialment)
- Mode millimètric / centimètric
- Export PDF (PrintManager o iText)
- Zoom i pan al canvas
- Etiquetes editables per a cada paret
- Suport DWG via API Autodesk Forge

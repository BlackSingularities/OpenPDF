# OpenPDF

**Otwarty, pełnoprawny edytor PDF dla Androida i iOS** – czytanie w trybie książki, adnotacje zapisywane w standardzie PDF, podpisy, formularze i pełna praca na stronach.

[![Build & Release](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

## Pobieranie

Najnowsze pliki są zawsze w **[Releases → Latest](../../releases/latest)**. Każdy merge do `main` buduje i publikuje nową wersję na obie platformy automatycznie, a aplikacja sama proponuje aktualizację.

**Android**
1. Pobierz `OpenPDF-x.y.z.apk` na telefon.
2. Otwórz plik i zezwól na instalację z tego źródła.
3. Wymagany Android 10 (API 29) lub nowszy.

**iPhone i iPad**
1. Pobierz `OpenPDF-x.y.z.ipa`.
2. Zainstaluj go przez [AltStore](https://altstore.io) albo [Sideloadly](https://sideloadly.io) – plik nie jest podpisany, narzędzie podpisuje go Twoim Apple ID (przy darmowym koncie podpis trzeba odświeżać co 7 dni).
3. Wymagany iOS / iPadOS 16.4 lub nowszy.

## Funkcje

### Tryb książki
| Funkcja | Opis |
|---|---|
| **Dwie strony obok siebie** | Automatycznie w poziomie i na rozłożonym ekranie folda (wykrywanie zawiasu), albo zawsze / nigdy. |
| **Okładka osobno** | `1 | 2-3 | 4-5…` jak w drukowanej książce albo `1-2 | 3-4…` – parzystość rozkładówek jednym dotknięciem. |
| **Jeden obraz** | Rozkładówka powiększa się i przesuwa jak jedna ilustracja; przy zbliżeniu widoczny fragment renderuje się ostro. |
| **Przewijanie** | Ciągłe w pionie (z szybkim suwakiem) albo przewracanie stron w poziomie, strefy dotyku i klawisze głośności. |
| **Od prawej do lewej** | Dla mangi i języków RTL. Cień grzbietu, papier zwykły / nocny / sepia. |

### Edycja
- Pióro, zakreślacz, gumka, tekst, notatki, prostokąty, elipsy, linie i strzałki.
- Zakreślanie, podkreślanie i przekreślanie tekstu (przytrzymaj słowo i przeciągnij).
- Podpisy odręczne (zapisywane do ponownego użycia) i obrazy – z przesuwaniem i zmianą rozmiaru.
- Wszystko zapisywane jako **standardowe adnotacje PDF z wyglądem** – widoczne i edytowalne w Acrobacie i przeglądarkach.
- Usuwanie istniejących adnotacji gumką lub z listy, tryb „tylko rysik”.

### Strony i dokument
- Obracanie, usuwanie, powielanie, zmiana kolejności, wstawianie pustych stron, innych PDF-ów i obrazów, wyodrębnianie do nowego pliku.
- Nowy dokument, PDF z obrazów, scalanie wielu plików.
- Formularze (pola tekstowe, wyboru, pola zaznaczenia), spłaszczanie formularza.
- Wyszukiwanie (bez względu na polskie znaki), spis treści, zakładki, kopiowanie tekstu.
- Hasło AES-256, właściwości dokumentu, kompresja obrazów, eksport stron do PNG/JPG, wyodrębnianie tekstu, druk, udostępnianie.
- Cofanie i ponawianie wszystkich zmian – także operacji na stronach.
- Interfejs po polsku i angielsku – automatycznie według języka systemu albo wybrany w ustawieniach.

## Architektura

```
shared/src/commonMain/   # wspólny kod: interfejs (Compose Multiplatform), PdfViewModel, PageImages, TextIndex,
                         # Geometry, ustawienia, biblioteka; interfejsy Platform i PdfEngine
shared/src/iosMain/      # iOS: silnik PDF na Apple PDFKit, rysowanie przez CoreGraphics, okna systemu
shared/src/androidMain/  # drobne funkcje zależne od Androida
app/                     # aplikacja Android: PdfOps (PdfBox), AnnotationWriter, RenderSession (PdfRenderer),
                         # dostęp do plików, wybór plików, druk, aktualizacje
iosApp/                  # powłoka aplikacji iOS (SwiftUI + XcodeGen)
```

- **Kotlin Multiplatform · Compose Multiplatform · Material 3**
- Android: **PdfBox-Android · systemowy PdfRenderer**. Ponieważ pdfium nie rysuje adnotacji, aplikacja tworzy kopię do wyświetlania z wtopionymi adnotacjami i wypieczonym obrotem stron – plik użytkownika zostaje nienaruszony.
- iOS: **Apple PDFKit** (odczyt, zmiany, adnotacje) i **CoreGraphics** (rysowanie stron).

## Budowanie lokalnie

Android – wymagany JDK 17 i Android SDK (API 36):

```bash
gradle :app:assembleDebug
```

iOS – wymagany macOS z Xcode i [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
gradle :shared:linkReleaseFrameworkIosArm64
cd iosApp && xcodegen generate && open OpenPDF.xcodeproj
```

Wydania podpisuje CI kluczem z sekretów repozytorium (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`).

## Licencja

MIT. Wersja na Androida wykorzystuje [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0).

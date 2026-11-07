## 2.2.1

- Zgłaszanie błędów i sugestii: nowa pozycja „Zgłoś błąd lub sugestię” – opis trafia prosto do autora przez redzikowski.com, e-mail jest opcjonalny.
- Po nieoczekiwanym zamknięciu aplikacja przy następnym uruchomieniu proponuje wysłanie raportu o błędzie – tylko za zgodą użytkownika.

## 2.1.2

- Bez żadnych wzmianek o GitHubie w aplikacji: nowy adres serwera aktualizacji, komunikaty błędów bez nazw zewnętrznych serwerów.

## 2.1.1

- Komunikat o nowej wersji bez listy zmian, linków i informacji, skąd pobierana jest aktualizacja.

## 2.1.0

- Windows: przewracanie kartek jak w książce w widoku „strona po stronie” (dwie strony i książka) – ta sama animacja co na Androidzie; włączane w Ustawieniach → Czytanie → „Przewracanie kartek”.
- Windows: podpis „Pomysł i wykonanie: Adam Rędzikowski · redzikowski.com” z adresem kontaktowym na ekranie startowym i w ustawieniach (z przyciskiem „Postaw kawę”).

## 2.0.1

- Windows: przebudowany górny pasek – po lewej nawigacja (strony, powiększenie), po prawej wszystkie narzędzia; menu i przycisk panelu bocznego przed kartami; usunięty zdublowany przycisk widoku stron (porządkowanie zostaje w menu „Dokument” i pod Ctrl+Shift+O).
- Windows: poprawka zapisu hasła dokumentu.

## 2.0.0

- **Windows – nowy interfejs i edycja dokumentów** (Android i iOS bez zmian).
  - Kompaktowy pasek narzędzi w jednym wierszu: rozwijane menu Narzędzie, Styl, Widok i Dokument zamiast rzędów przycisków; na wąskim oknie zostają same ikony.
  - Przeprojektowane motywy AMOLED, Grafit i Jasny z podglądem w ustawieniach, wybór koloru akcentu, szybka zmiana motywu z paska tytułu.
  - Adnotacje: wyróżnianie tekstu (zakreślenie, podkreślenie, przekreślenie), pióro, zakreślacz, kształty, pola tekstowe, notatki i gumka; pasek nad zaznaczonym tekstem.
  - Zapis i „Zapisz jako”, cofanie i ponawianie (Ctrl+Z / Ctrl+Y), kropka na karcie przy niezapisanych zmianach, pytanie przy zamykaniu.
  - Porządkowanie stron: siatka miniatur, zaznaczanie wielu stron, obracanie, usuwanie, powielanie, przenoszenie, wstawianie pustych stron, innych PDF i obrazów, wyodrębnianie do nowego pliku.
  - Panele boczne: zakładki użytkownika (Ctrl+D), adnotacje (niezapisane i w pliku), formularz z wypełnianiem i spłaszczaniem.
  - Narzędzia: hasło i ochrona (AES-256), zmniejszanie rozmiaru, eksport stron do PNG/JPG, wyodrębnianie tekstu, edycja właściwości, druk z adnotacjami.
  - Nowe dokumenty: pusty dokument, PDF z obrazów, scalanie plików PDF.
  - Ekran startowy z szybkimi akcjami i ulubionymi plikami.

## 1.9.1

- **Wersja dla Windows** – rozbudowana przeglądarka (bez edycji), jeden instalator `OpenDocument-x.y.z-setup-x64.exe`.
  - Karty dokumentów w pasku tytułu, w jednej linii z przyciskami minimalizacji, maksymalizacji i zamknięcia.
  - Wiele dokumentów w kartach: otwieranie wielu plików naraz, przeciąganie z Eksploratora, przywracanie kart po uruchomieniu, jedno okno programu (kolejne pliki trafiają do nowych kart).
  - Tryb książki: jedna strona, dwie strony, książka z okładką osobno; strony rozkładówki mają wspólną wysokość i stykają się grzbietem, od prawej do lewej; przewijanie ciągłe albo strona po stronie.
  - Miniatury, spis treści, wyszukiwanie bez względu na polskie znaki, zaznaczanie i kopiowanie tekstu, odnośniki.
  - Powiększanie (Ctrl+kółko), dopasowanie do szerokości i strony, obrót widoku, papier zwykły / sepia / nocny, pełny ekran i prezentacja, druk.
  - Wszystkie formaty wersji mobilnej (DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML, obrazy).
  - Systemowe okno otwierania plików z filtrami; przeciąganie okna z przyciąganiem do krawędzi ekranu.
  - Motywy ciemny, szary i jasny w stylu OpenPano, polski i angielski, automatyczne aktualizacje.

## 1.8.0

- Biblioteka: wyszukiwanie po nazwie, sortowanie (ostatnio otwarte, nazwa, postęp czytania), ulubione z gwiazdką zawsze na początku listy, etykieta formatu na okładce.
- Książki Kindle bez DRM: MOBI, AZW i AZW3 – tekst, tytuł i ilustracje.
- Komiksy CBZ: każdy obraz z archiwum jako osobna strona, kolejność według numerów w nazwach.
- CBR (RAR) i CB7 (7z): czytelny komunikat z prośbą o zapis jako CBZ.

## 1.7.5

- Otwieranie dokumentów z linków (np. faktura PDF z maila): aplikacja pojawia się na liście „Otwórz za pomocą”, pobiera plik i od razu go otwiera.

## 1.7.2

## 1.7.4

- Dopasowane kolorystycznie tła ikon aplikacji i spójne zasoby graficzne.

- Przewracanie kartek: cień przy grzbiecie i pod kartką narasta i gaśnie płynnie – nie pojawia się nagle przy chwyceniu, puszczeniu ani na końcu animacji.
- Szybkie kartkowanie: przesunięcie palcem w trakcie animacji od razu kończy poprzednią kartkę i przewraca następną (zamiast cofać poprzednią).
- Dopracowany wygląd książki: miękki cień pod otwartą książką, krawędzie bloku kartek widoczne także podczas przewracania.

## 1.7.1

- Nowa strona aplikacji z opisem funkcji.
- Jednolita informacja o autorze (Adam Rędzikowski · redzikowski.com) i przycisk „Postaw kawę”.
- Usunięte odnośniki do repozytorium z interfejsu.

## 1.7.0 — OpenDocument

- Nowa nazwa: **OpenDocument** (pliki wydań `OpenDocument-x.y.z.apk` / `.ipa`).
- Podgląd DjVu: własny dekoder w czystym Kotlinie (Android i iOS) – maska tekstu JB2 ze słownikami Djbz/INCL, tło i kolor IW44, paleta FGbz, obrót stron, pakiety wielostronicowe. Duże skany odtwarzane od razu w zmniejszonej skali, bez zapychania pamięci; postęp „strona X z Y”.
- Otwieranie książek i dokumentów: EPUB, FB2 (także w ZIP), DOCX, ODT, RTF, Markdown, HTML, TXT, CSV – składane w strony PDF ze spisem treści.
- Czytelne komunikaty dla nieobsługiwanych formatów (DOC, MOBI/AZW, arkusze, prezentacje).

## 1.6.2 — Transparent application icon

- Use the transparent application mark in launcher, splash and existing branding surfaces.
- Preserve alpha in legacy and adaptive Android icons; refresh themed icon resources.

# Zmiany

## 1.6.1
- Precyzyjne przycinanie marginesów: suwaki na całą szerokość panelu, przełączany zakres 0–10% (dokładny) lub 0–30%, przyciski −/+ co 0,1% z powtarzaniem po przytrzymaniu.
- Nowy suwak „Wszystkie” przesuwa wszystkie cztery marginesy naraz, zachowując różnice między nimi.
- W trakcie przeciągania suwaka panel ustawień znika, a strony od razu pokazują przycięcie – nad nimi tylko dymek z wartością.
- Usunięty zbędny przełącznik „Rozkładówka jako całość” – zakładka „Rozkładówka” zawsze przycina rozkładówkę jako całość.
- Czytelniejsze nazwy w ustawieniach: „Wibracja przycisków” (zamiast „Wibracje”) i „Strony głośnością” (zamiast „Głośność”).

## 1.6.0
- Wersja na iPhone'a i iPada: ten sam interfejs i funkcje (Compose Multiplatform), silnik PDF na Apple PDFKit. Każde wydanie zawiera teraz `OpenPDF-x.y.z.apk` (Android) i `OpenPDF-x.y.z.ipa` (iOS 16.4+, instalacja przez AltStore lub Sideloadly).
- Na iOS aktualizacja otwiera stronę wydania z nowym plikiem IPA.
- Przycinanie marginesów w dwóch trybach: pojedyncze strony (jak dotąd) i rozkładówka książki jako całość – lewy margines przycina lewą stronę, prawy prawą, a miejsce styku przy grzbiecie zostaje nietknięte. Przełącznik „Rozkładówka jako całość” pozwala wrócić do przycinania każdej strony osobno.
- Dokładniejsze ustawianie marginesów: przyciski −/+ co 0,5%, wartości z dokładnością do 0,1%, nowy szybki wybór 2%.

## 1.5.0
- Menu z dwiema zakładkami: Dokument i Ustawienia; ustawienia na jednej liście (Książka, Wyświetlanie, Edycja, Ogólne).
- Na tablecie dotknięcie obok menu je zamyka.

## 1.4.0
- Górny pasek przytwierdzony do krawędzi ekranu (w poziomie – kolumna przy lewej krawędzi), bez zaokrąglonej kapsuły.
- Przyciski paska: dotknięcie pokazuje wszystkie opcje, przytrzymanie – szybka akcja.
- Przycinanie marginesów stron osobno z lewej, góry, prawej i dołu – w trybie jedno- i dwustronnym.
- Kompaktowe ustawienia: kafelki zamiast długich wierszy, zakładka „Ogólne” na początku.

## 1.3.0
- Automatyczny układ (domyślnie): telefon w pionie – jedna strona i przewijanie w dół; poziomo, na tablecie i rozłożonym foldzie – dwie strony jak w książce z przewracaniem kartek. Można go wyłączyć w ustawieniach.
- Czarne tło pod AMOLED, czarne paski tylko w swoim obrębie, pasek edycji przy dolnej krawędzi.
- Szybsze pojawianie się stron: najpierw widoczne strony, podgląd z miniatur dla sąsiednich.
- Usunięto opcję przypiętego paska narzędzi.

## 1.2.0
- Ustawienie OpenPDF jako domyślnej aplikacji do plików PDF (Menu → Ogólne → Aplikacja domyślna).

## 1.1.0
- Jedno menu: dokument i ustawienia w jednym panelu (na tablecie – boczna kolumna obok dokumentu).
- Tryb ciemny: biały tekst na czarnym tle (pełna inwersja), szybki przełącznik papieru na pasku.
- Strony od krawędzi do krawędzi – bez marginesów.
- Przypięty pasek narzędzi (dokument pod nim, chowa się po dotknięciu strony) i opcja ukrycia paska stanu.
- Pełna angielska wersja interfejsu: język automatycznie według systemu (polski albo angielski) lub wybrany w Ustawienia → Ogólne → Język.
- Tryb książki: przewracanie kartek z animacją – kartka obraca się wokół grzbietu i podąża za palcem (tryb „Przewracanie stron”).
- Aktualizacje jednym dotknięciem: baner nowej wersji, pobieranie i instalacja bez przeglądarki.

## 1.0.0
- Pierwsze wydanie OpenPDF.
- Tryb książki: dwie strony obok siebie (automatycznie w poziomie i na rozłożonym foldzie), okładka osobno albo rozkładówki od pierwszej strony, kolejność od prawej do lewej, cień grzbietu.
- Powiększanie i przesuwanie całej rozkładówki jak jednego obrazu, ostre renderowanie przy zbliżeniu, przewijanie ciągłe albo przewracanie stron.
- Adnotacje zapisywane jako standardowe adnotacje PDF: pióro, zakreślacz, gumka, tekst, notatki, kształty, zakreślanie/podkreślanie/przekreślanie tekstu, podpisy, obrazy.
- Strony: obracanie, usuwanie, powielanie, przenoszenie, wstawianie pustych stron, innych PDF-ów i obrazów, wyodrębnianie.
- Formularze, wyszukiwanie, spis treści, zakładki, zaznaczanie i kopiowanie tekstu.
- Hasło (AES-256), kompresja obrazów, eksport stron do PNG/JPG, wyodrębnianie tekstu, druk i udostępnianie.
- Cofanie i ponawianie wszystkich zmian.

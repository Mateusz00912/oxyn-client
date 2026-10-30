# Oxyn Client (Fabric 1.21.1)

Mod kliencki Fabric dla Minecraft 1.21.1. Działa w Modrinth App (profil Fabric 1.21.1 + Fabric API).

## Budowanie .jar

**Lokalnie (Java 21):**

    ./gradlew build        (Windows: gradlew.bat build)

Gotowy plik: `build/libs/oxyn-client-1.0.0.jar`

**Bez instalacji:** wrzuć folder na GitHub - workflow `.github/workflows/build.yml`
zbuduje .jar (zakładka Actions → artifact `oxyn-client`).

## Instalacja

1. Modrinth App → nowy profil: Minecraft 1.21.1, Fabric.
2. Dodaj mod **Fabric API**.
3. Wrzuć `oxyn-client-1.0.0.jar` do folderu `mods` profilu.

## Sterowanie

- Prawy Shift - menu modułów (suwaki, przełączniki, zapis do `config/oxyn.json`)
- C (przytrzymaj) - zoom
- Klawisze można zmienić w Opcje → Sterowanie → Oxyn Client

## Spotify

Windows: tytuł okna Spotify.exe (otwórz aplikację desktopową). Linux: `playerctl`.
Tekst z lrclib.net; pozycja jest szacowana, więc po przewinięciu utworu możesz
poprawić suwakiem "Przesunięcie tekstu".

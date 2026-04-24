# SRT Camera

Prosta aplikacja Android do streamowania obrazu z kamery przez protokół SRT (Secure Reliable Transport) w czasie rzeczywistym.

## Wymagania

- Android 7.0 (API 24) lub nowszy
- Android Studio (Hedgehog lub nowsze)
- JDK 17
- Na komputerze odbierającym: `ffplay` z pakietu FFmpeg z obsługą SRT

## Uruchomienie aplikacji

### 1. Zbuduj i zainstaluj APK

Otwórz projekt w Android Studio, a następnie:

```
Run → Run 'app'
```

lub przez terminal w katalogu projektu:

```bash
./gradlew installDebug
```

### 2. Nadaj uprawnienia

Przy pierwszym uruchomieniu aplikacja poprosi o dostęp do **kamery** i **mikrofonu** — zatwierdź oba.

### 3. Skonfiguruj strumień

W górnym panelu aplikacji:

| Pole | Opis | Domyślnie |
|------|------|-----------|
| **IP serwera** | Adres IP komputera odbierającego strumień | `192.168.1.100` |
| **Port** | Port UDP, na którym nasłuchuje odbiorca | `9999` |

Upewnij się, że telefon i komputer są w tej samej sieci (np. Wi-Fi).

### 4. Rozpocznij streaming

Naciśnij przycisk **▶ START**. Status zmieni się na `🔴 LIVE → srt://...`.

Aby zatrzymać, naciśnij **⏹ STOP**.

Przycisk **🔄** w prawym górnym rogu przełącza między kamerą tylną a przednią.

---

## Odbieranie sygnału w ffplay

Uruchom na komputerze poniższą komendę **przed** naciśnięciem START w aplikacji — ffplay nasłuchuje w trybie `listener`, więc musi być gotowy jako pierwszy.

```bash
ffplay -fflags nobuffer -flags low_delay -analyzeduration 1000000 "srt://0.0.0.0:9999?mode=listener&latency=120"
```

### Opis parametrów

| Parametr | Znaczenie |
|----------|-----------|
| `srt://0.0.0.0:9999` | Nasłuchuj na wszystkich interfejsach, port 9999 |
| `mode=listener` | ffplay czeka na połączenie przychodzące |
| `latency=120` | Bufor SRT w ms — musi zgadzać się z ustawieniem w aplikacji |
| `-fflags nobuffer` | Wyłącza buforowanie wejścia (zmniejsza opóźnienie) |
| `-flags low_delay` | Tryb niskiego opóźnienia dekodera |
| `-analyzeduration 1000000` | Skraca czas analizy strumienia do 1 s |

### Zmiana portu

Jeśli używasz innego portu, zmień go zarówno w aplikacji, jak i w komendzie ffplay:

```bash
ffplay -fflags nobuffer -flags low_delay "srt://0.0.0.0:5000?mode=listener&latency=120"
```

### Sprawdzenie, czy FFmpeg obsługuje SRT

```bash
ffmpeg -protocols | grep srt
```

Jeśli `srt` nie pojawia się na liście, zainstaluj FFmpeg z obsługą SRT:

```bash
# Ubuntu / Debian
sudo apt install ffmpeg libsrt-dev

# macOS (Homebrew)
brew install ffmpeg
```

---

## Schemat połączenia

```
[Telefon Android]  ──── Wi-Fi / LAN ────▶  [Komputer]
  SRT Camera app                            ffplay listener
  srt://192.168.1.X:9999    ◀── IP komputera
```

## Biblioteki

- [StreamPack](https://github.com/thibaultbee/StreamPack) v3.1.2 — silnik streamowania SRT/RTMP na Androida

# Aplikacja Todo

Prosta aplikacja do zarządzania zadaniami, napisana w języku Clojure. Umożliwia tworzenie projektów i dodawanie do nich zadań.

## Funkcje

*   Logowanie użytkownika
*   Tworzenie projektów
*   Dodawanie zadań do projektów
*   Oznaczanie zadań jako ukończone
*   Usuwanie zadań
*   Usuwanie projektów
*   Trwałość danych (dane są zapisywane w pliku `data.edn`)

## Uruchamianie

1.  **Zainstaluj Leiningen:** Upewnij się, że masz zainstalowane [Leiningen](https://leiningen.org/).
2.  **Uruchom aplikację:** W głównym katalogu projektu uruchom polecenie:
    ```bash
    lein run
    ```
3.  **Otwórz w przeglądarce:** Aplikacja będzie dostępna pod adresem `http://localhost:3000`.

## Dane logowania

*   **Nazwa użytkownika:** `admin`
*   **Hasło:** `password123`

## Technologie

*   **Język:** Clojure
*   **Framework webowy:** Ring
*   **Serwer:** Jetty
*   **Biblioteka UI:** Hiccup

## Licencja

Ten projekt jest udostępniany na licencji GNU General Public License v3.0. Szczegóły znajdują się w pliku `LICENSE`.

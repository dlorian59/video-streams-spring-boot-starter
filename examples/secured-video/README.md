# Secured video consumer / Защищённый видео-потребитель

[Repository README](../../README.md) · [English guide](../../docs/0.1.0/en.md) · [Русское руководство](../../docs/0.1.0/ru.md) · [Run and verify / Запуск и проверка](../../CONTRIBUTING.md)

EN: Copy VideoController, VideoService and VideoErrors into an existing WebFlux application under its scan package. Keep your authentication and replace the account namespace rule with the required ACL. DemoSecurity, SessionController and VideoApplication only make this example standalone. Maven and Gradle builds use source artifacts prepared in sibling local repositories. verify.py checks a disposable TLS/S3 environment using synthetic media; it never configures production credentials.

RU: Скопируйте VideoController, VideoService и VideoErrors под пакет сканирования существующего WebFlux-приложения. Сохраните свою аутентификацию и замените namespace-правило требуемым ACL. DemoSecurity, SessionController и VideoApplication нужны только для самостоятельного примера. Maven/Gradle используют артефакты из соседних локальных репозиториев. verify.py проверяет отдельное TLS/S3-окружение на синтетическом медиа и не настраивает рабочие credentials.

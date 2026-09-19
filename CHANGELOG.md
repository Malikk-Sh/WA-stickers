# Changelog

## 1.6.0 — 2026-09-19

### Новый интерфейс

- Новый task-focused flow с постоянными вкладками **Создать / Медиа / Сборка / Наборы**.
- Новый экран создания с режимами Фото/Анимация, названием, компактным выбором файлов и быстрыми превью.
- Полноценная вкладка Медиа: адаптивная сетка (3 колонки на телефоне, 4 при достаточной ширине/compact), reorder, выбор обложки, удаление и детали файла.
- Для reorder добавлены accessibility-действия **«Переместить влево / вправо»**, а основные интерактивные цели приведены к минимуму 48dp.
- Отдельный экран выбора фрагмента длинного видео с поддержкой выбранной темы приложения.
- Новый Build UI с общим и пофайловым прогрессом, retry/cancel, partial finalize и явными error states.
- Новый экран сохранённых наборов с фильтрами, поиском, переименованием, дублированием, удалением, деталями, очисткой недоступных наборов и WhatsApp action.
- Настройки внешнего вида, компактного режима, качества анимации, черновиков и временного кэша.
- Контекстные overflow-меню для Create / Media / Build / Packs, включая диагностику сборки и действия с сохранёнными наборами.
- Восстановленный draft явно обозначается на Create.
- Semantic color tokens используются для foreground/overlay состояний, включая dark mode.

### Обработка и состояние

- Фото и анимация хранят раздельные черновики.
- Trim state и editor draft переживают recreation и новый запуск приложения.
- Уже готовые элементы не пересобираются при повторе неудачных файлов.
- При наличии минимум трёх успешных элементов набор можно завершить из готовых.
- Сохранённые наборы остаются владельцем `PackStore`; conversion pipeline сохранён без полной переписи.
- Неошибочные кратковременные сообщения переведены на snackbar-подобный in-app feedback, а системные ошибки остаются Toast/dialog там, где это уместно.

### Cleanup и QA

- Production launcher больше не строит старый long-scroll UI перед новым shell.
- Старые самостоятельные launcher/home/saved-packs routes выведены из production navigation.
- Legacy long-scroll presentation оставлен только за debug-only test host для независимых regression-тестов conversion pipeline и недоступен из production navigation.
- Active editor selection/mode/cover и раздельные photo/animation drafts вынесены в `EditorRuntimeState`; состояние текущего финализированного набора вынесено в `PackRuntimeState`.
- Переходный `MainActivityRuntimeAccess` полностью удалён; production reflection отсутствует, а CI запрещает его глобально.
- CI дополнительно запрещает hardcoded screen colors в ключевых redesigned UI-классах.
- Расширены JVM и instrumentation regression tests, включая handoff-compliance и release-contract проверки launcher/provider/offline-инвариантов.
- CI проверяет lint, unit tests, debug APK, сборку release-варианта и Android API 35 instrumentation suite.
- GitHub Actions переведены на актуальные major-версии `checkout`, `setup-java`, `setup-gradle` и `upload-artifact`.
- Финальные post-merge прогоны `main` после UI/UX compliance и архитектурной очистки полностью зелёные.

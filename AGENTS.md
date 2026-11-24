# Apocalipse BR Launcher

This is a Windows JavaFX application built with Maven. World of Warcraft 3.3.5a is the current game integration. Start with the [documentation index](.docs/AGENTS.md) and read the category relevant to the task.

## Architecture

- Keep application assembly in `LauncherMain` and `LauncherApp` under `cloud.apocalipse.launcher`.
- Keep JavaFX controls, navigation, UI state, and localized messages in `ui`. Run blocking game operations on background tasks and apply results on the JavaFX application thread.
- Keep per-game behavior under `games.<game>`. Put contracts in `core` when they serve multiple features, and wire concrete infrastructure adapters in the application bootstrap.
- Check the WoW integration documentation before changing settings, patch destinations, launch arguments, or installation behavior. Existing installations depend on those paths and settings.

## Validation and documentation

- Use `mvn clean package` for a clean compile and JAR. When changing entry points or packaging, validate the Windows installer with the workflow in the [build and release guide](.docs/build/windows.md).
- Update the relevant document in `.docs/` when changing a documented flow. Add new documents to [the index](.docs/AGENTS.md).
- Keep `README.md` oriented toward players, with Brazilian Portuguese first and English second.

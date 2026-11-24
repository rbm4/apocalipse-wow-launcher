# Packages and data flow

Java sources live under [`cloud.apocalipse.launcher`](../../src/main/java/cloud/apocalipse/launcher). The Maven and native launcher entry point is `LauncherMain`. `LauncherApp` configures JavaFX startup, creates the WoW services and torrent adapter, and opens the current view.

| Package | Responsibility |
| --- | --- |
| `ui.shell` | Reusable JavaFX window chrome |
| `ui.wow` | WoW tabs, setup and options pane, actions, task bindings, and localized messages |
| `games.wow` | JavaFX-free game operations exposed by `WowLauncher` |
| `games.wow.client`, `games.wow.patch`, `games.wow.launch`, `games.wow.community`, `games.wow.config` | WoW client installation, patch verification, process launch, portal data, and settings |
| `core` and `core.torrent` | Operation progress callbacks and the download contract, events, and types |
| `infrastructure` | jlibtorrent adapter, persistent web cookies, and startup diagnostics |

## Data flow

1. `WowLauncherView` receives user actions and reads or updates `LauncherConfig`. It selects folders and manages tabs, button states, and status labels.
2. Long operations such as patch synchronization and client download run in JavaFX background tasks. Tasks call `WowLauncher` with plain Java inputs such as paths and callbacks. The game service owns bundled endpoints, disk-space checks, and the delegates that access the filesystem, website API, or game process.
3. Game operations return values, throw failures, or report progress through `OperationProgress`. The view adapts these to JavaFX task properties, translates messages, and applies completion handlers on the JavaFX thread. Game exit notifications are marshalled to the same thread.
4. `ClientInstallManager` downloads through the `TorrentDownloadService` interface. `LauncherApp` supplies the jlibtorrent implementation. The game service and shared contracts have no JavaFX dependency.

The current WoW view still owns the order of setup, update, and play. `WowOptionsPane` renders setup and settings controls and emits actions through callbacks. It leaves persistence and game actions to the view and services.

## Adding a game

- Put its operations and settings under `games.<game>` and its JavaFX screens under `ui.<game>`. Keep WoW paths, URLs, patch formats, and launch switches in the WoW integration.
- Wire each view and service from the application entry point as navigation is introduced. Keep game selection and transitions in the JavaFX layer.
- Reuse applicable shared contracts and adapters. Extract more shared code when a second integration needs the same behavior.

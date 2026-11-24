# Build and release on Windows

Use JDK 25 with `jpackage`, Maven 3.9+, and Windows for native packaging. The EXE and MSI profiles also require WiX on `PATH` (`wix.exe` or WiX v3 `candle.exe` and `light.exe`). The app-image profile runs without WiX.

## Develop

```powershell
mvn javafx:run
mvn clean package
```

`package` compiles the launcher and writes its JAR and runtime dependencies to `target/launcher-input`. VS Code's launch configuration builds those inputs before opening the JavaFX app.

## Build native packages

Choose the profile for the required artifact. Pass an existing Windows `.ico` file to override the default PNG, which Windows jpackage rejects.

```powershell
$icon = (Resolve-Path -LiteralPath '.local-release/resources/ApocalipseWoW.ico').Path
mvn clean verify -Pjpackage-app-image "-Djpackage.icon=$icon"
mvn clean verify -Pjpackage-exe "-Djpackage.icon=$icon"
mvn clean verify -Pjpackage-msi "-Djpackage.icon=$icon"
```

The native packaging step runs at `verify` and writes under `target/jpackage`. The local MSI script generates the ICO at the example path from the bundled PNG and supplies it to Maven.

## Build and publish an MSI

```powershell
.\build-local-msi.ps1
```

The script checks local tooling, builds the installer, and stores the MSI and log under `.local-release/`. It prompts before publishing. Use `-Release` to enter release mode directly, where the script can also prompt for a version bump. Publication uses GitHub CLI to create or update the `launcher-v<project.version>` release and upload the MSI.

Windows SmartScreen may warn about an unsigned installer. If the installed shortcut opens no window, check `%LOCALAPPDATA%\ApocalipseWoWLauncher\launcher.log`.

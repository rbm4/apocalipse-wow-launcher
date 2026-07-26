# Apocalipse WoW Launcher

JavaFX launcher for synchronizing Apocalipse WoW client MPQ patches and starting the 3.3.5a client.

## Requirements

- JDK 25+ with `jpackage` available on `PATH` or under `JAVA_HOME/bin`.
- Maven 3.9+.
- Windows is required for building Windows `.exe` or `.msi` installers with `jpackage`.
- WiX Toolset is required by `jpackage` for Windows `.exe` and `.msi` installers.

## Development

Run the launcher:

```powershell
mvn javafx:run
```

Build the application jar and dependency input directory:

```powershell
mvn clean package
```

The packaging input is written to:

```text
target/launcher-input
```

## Native Packages

Build a portable Windows application image:

```powershell
mvn clean verify -Pjpackage-app-image
```

Build a Windows `.exe` installer:

```powershell
mvn clean verify -Pjpackage-exe
```

Build a Windows `.msi` installer:

```powershell
mvn clean verify -Pjpackage-msi
```

Outputs are written under:

```text
target/jpackage
```

Unsigned installers work, but Windows SmartScreen may warn users that the publisher is unknown.

If installer builds fail with `Can not find WiX tools`, install WiX Toolset and make sure `wix.exe`
or WiX v3 `candle.exe`/`light.exe` are on `PATH`. The `jpackage-app-image` profile does not need WiX.

## Release Workflow

Every push to `master` runs `.github/workflows/release-msi.yml` on a Windows GitHub Actions runner.
The workflow installs JDK 25 and WiX Toolset, builds the installer with:

```powershell
mvn --batch-mode --no-transfer-progress clean verify -Pjpackage-msi
```

It then creates a fresh release tag in the format `launcher-v<project.version>-<run_number>` and
publishes the generated `.msi` as the repository's latest GitHub release.

## Current Launcher Flow

1. Select the WoW 3.3.5a folder.
2. The launcher writes `set realmlist wow.apocalipse.cloud`.
3. Fetch the fixed backend manifest.
4. Compare `Data/patch-A.MPQ` by SHA-256.
5. Download the backend MPQ artifact if missing or outdated.
6. Verify the download before installing it as `Data/patch-A.MPQ`.
7. Remove older launcher-managed `patch-apocalipse-*.mpq` files.
8. Unlock `Launch WoW` after verification succeeds.
9. Launch `Wow.exe`.

On first launch, the user is prompted to choose the WoW folder. Later changes are available through
the launcher `Options` button.

Default manifest:

```text
https://wow.apocalipse.cloud/patches/manifest.json
```

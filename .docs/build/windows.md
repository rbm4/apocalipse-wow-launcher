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

The MSI includes English and Brazilian Portuguese WiX UI cultures. `src/jpackage/resources/MsiInstallerStrings_pt-BR.wxl` supplies the Portuguese installer messages; keep its string IDs aligned with `MsiInstallerStrings_en.wxl`. The localization validator runs in the local MSI script and GitHub release workflow, so a missing culture or untranslated jpackage string blocks packaging before publication. Windows Installer selects a UI language available on the user's Windows installation.

## Publish with GitHub Actions

Run [Release Windows launcher](../../.github/workflows/launcher-release.yml) from the repository's Actions tab on the default branch. Choose a patch, minor, or major bump. Patch is the default. The workflow uses the highest valid launcher version among existing tags, GitHub releases (including drafts), and the local default in the POM.

The Windows runner passes the result to Maven as `revision` and runs `clean verify`, including tests and MSI packaging. Maven Central is listed ahead of FrostWire so general Java dependencies resolve from their publishing repository. FrostWire remains available for the jlibtorrent artifacts. After a successful build the workflow creates `launcher-v<version>` at the tested commit, uploads `ApocalipseWoW-<version>.msi` to a draft with generated release notes, then publishes that draft as Latest. The same version appears in the runtime JAR and MSI. A failed publication after tag creation reserves that version. A new run advances from it.

For signed releases, configure the repository Actions secrets `WINDOWS_SIGNING_PFX_BASE64` (base64 of a password-protected certificate export) and `WINDOWS_SIGNING_PFX_PASSWORD`. The runner imports the certificate temporarily and uses the release script's Authenticode signing and verification. With neither secret set, the workflow publishes an unsigned installer for manual installation. With only one secret set or if signing fails, publication fails.

To reproduce the build version from a release tag without publishing, run the local script with `-BuildOnly -Version <version>`. The version override applies to the build without editing the POM.

## Build and publish an MSI locally

```powershell
.\build-local-msi.ps1
```

The script checks local tooling, builds the installer, and stores the MSI and log under `.local-release/`. It prompts before publishing. Use `-Release` to enter release mode directly. Use `-Version` to select a new three-part MSI version. Publication uses GitHub CLI to upload `ApocalipseWoW-<version>.msi` to the `launcher-v<version>` draft, then publishes it as Latest. A published release needs a new version for corrections.

Windows SmartScreen may warn about an unsigned installer. If the installed shortcut opens no window, check `%LOCALAPPDATA%\ApocalipseWoWLauncher\launcher.log`.

## Launcher updates

The Windows launcher checks the repository's GitHub Latest release after its window opens. It compares the bundled Maven version against the numeric version in the release tag and looks for the matching MSI and GitHub SHA-256 digest. If the update can be installed in-app, the launcher downloads and verifies it under `%LOCALAPPDATA%\ApocalipseWoWLauncher\updates`. Unsigned builds and development app images can open the release page when a newer package is available. Network and metadata failures are logged in `launcher.log`.

For in-app installation, build and sign with a Windows-trusted code-signing certificate with an accessible private key in `Cert:\CurrentUser\My`. Put the Windows SDK's SignTool on `PATH`. Pass the certificate store's SHA-1 thumbprint to the script. It embeds the certificate's SHA-256 fingerprint in the launcher, signs the MSI with SHA-256, timestamps it, and verifies the signature before offering to publish. The default timestamp service is `http://timestamp.digicert.com`, configurable with `-TimestampUrl`.

```powershell
.\build-local-msi.ps1 -Version 0.1.8 -SigningCertificateThumbprint '<certificate-store-thumbprint>'
```

For release pipelines that sign separately, pass `-Dlauncher.updatePublisherSha256=<64-hex-certificate-fingerprint>` to Maven and sign the final MSI with the corresponding certificate before uploading. Installed launchers verify both the Windows signature and the embedded fingerprint. Certificate renewal changes that fingerprint. If the old signer is still usable, ship a transition release signed by the old certificate with the new certificate fingerprint embedded. Otherwise, distribute a manual installer from the release page.

**Atualizar e reiniciar** waits until launcher-managed game operations finish. The Windows helper takes an update lock and verifies the staged MSI's hash, publisher, product version, upgrade code and per-machine scope before it asks the launcher to exit. After exit, the helper starts Windows Installer with limited UI and automatic reboot disabled. Windows may request administrator approval. The helper reopens the launcher after a successful upgrade. Failures and reboot requirements are reported by the helper. Check `helper.log` and, if Windows Installer started, `installer.log` under the corresponding `updates\install-*` directory.

The native installer profiles pin the upgrade code already used by the public 0.1.7 MSI. Existing players install the first signed updater-enabled MSI manually. Validate rollout in an isolated Windows VM by upgrading from public 0.1.7, then exercising an in-app upgrade to the next signed build. Confirm a single installed product, working shortcuts, retained settings, relaunch, and handling of cancelled elevation. For the download and version checks, run `mvn test`.

# jpackage WiX Resource Overrides

Place Windows installer customization files in this directory.

`jpackage --resource-dir` can override WiX resources such as:

- `MsiInstallerStrings_en.wxl` for installer wizard labels and messages. The current build uses this
  file for PT-BR text because `jpackage` links the MSI with `-cultures:en-us`.
- `main.wxs`, `ui.wxf`, and `overrides.wxi` for deeper WiX installer layout/behavior.
- Bitmap and icon resources referenced by custom WiX templates.

Keep overrides small and deliberate. Full WiX template overrides can break across JDK versions.

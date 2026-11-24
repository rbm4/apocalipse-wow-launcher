# Inspecting a release

## Windows installer

Start with the MSI from the [official GitHub releases](https://github.com/rbm4/apocalipse-wow-launcher/releases). To compute its SHA-256 in PowerShell, replace the sample filename with the name of your download:

```powershell
Get-FileHash -LiteralPath '.\ApocalipseWoW-0.1.7.msi' -Algorithm SHA256
```

Search for that hash to read an existing file report when one is available. Detection results are signals to review and can include false positives. [VirusTotal search guide](https://docs.virustotal.com/docs/searching)

## Source review

Open the tag on the release page to review the source associated with that version. The [architecture map](../architecture/overview.md) and [WoW integration guide](../games/world-of-warcraft.md) lead to the code that downloads files, alters the client directory, and starts the game. Review the Maven dependencies and installer inputs alongside the Java source.

CodeQL supports Java and can help reviewers identify possible security issues. It is a tool that reviewers can run on the source. [GitHub CodeQL documentation](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-code-scanning)

Source review describes what the checked-out code does. File hash comparison identifies a matching binary. Establishing that an installer came from a specific revision requires independent build provenance or a reproducible build. The [Windows build guide](../build/windows.md) describes how to build a local installer from the tagged source.

# Project Zomboid integration

In **Opções**, use **Detectar pasta (recomendado)** to find an existing Steam installation. The launcher checks the Windows uninstall entries for Steam app 108600 first. If the registered folder is missing or invalid, it reads the registered Steam installation paths, then uses `steamapps/libraryfolders.vdf` and `appmanifest_108600.acf` to locate the game in the default library or on another drive. Auto-detection accepts a folder containing the Project Zomboid Windows executable.

Steam manages library locations under **Settings → Storage**. [Steam Support](https://help.steampowered.com/en/faqs/view/4bd4-4528-6b2e-8327)

Detection runs in the background and saves the result in `project-zomboid.properties` in the launcher's application data directory. If detection finds no installation or fails, the saved folder is retained and **Escolher pasta** remains available for manual selection.

## Play and server status

The home tab shows the current season (Season 5) and points players to **Opções** to enable the optional Apocalipse BR features. Its status widget uses the same 320 × 200 viewport as the WoW realm status.

**Jogar** becomes available after saving an installation folder containing `ProjectZomboid64.exe` or `ProjectZomboid32.exe`. With all optimizations off, it starts the executable from that folder, preferring the 64-bit version when both are present. The action stays disabled until the game exits.

Use **Ver detalhes** on the home tab to see the timestamped launch log. Launch failures open the log automatically. **Status do Servidor** embeds the TrackyServer widget for **APOCALIPSE [BR]**.

The status WebView uses a stylesheet to display the bundled Zomboid PNG in the widget header because JavaFX fails to render TrackyServer's WebP logo. The stylesheet is scoped to that logo and the widget still fetches live status from TrackyServer.

## Game memory

**Opções → RAM máxima (Java)** reads the current `-Xmx` value from the `vmArgs` array in the selected installation's `ProjectZomboid64.json`. Enter a size such as `8G` or `4096M` and select **Aplicar** to save it directly to that file. The launcher preserves the other JVM arguments and settings and checks that the new maximum is at least the configured `-Xms`. Both launch modes use the updated value the next time the game starts.

## ApocBR optimizations

**Opções → Otimizações ApocBR** highlights the most effective client optimizations before grouping the other switches by engine subsystem. Hover over a checkbox to see which engine behavior it changes. The launcher saves client selections in `project-zomboid.properties`. Dedicated server switches are shown for reference.

Under **Otimizações de maior impacto**, **Reduzir a área carregada do mapa** is off by default and takes effect the next time the game starts. It passes `-Dapocbr.optimization.clientChunkMap.enabled=true` to a compatible agent. At 1920×1080 and above, the cap reduces the client's map window from 19×19 to 13×13 chunks (361 to 169 slots per player, about 53% fewer). Smaller windows keep their original size. The cap reduces the amount of world data the client can keep loaded and can shrink visible map coverage when zoomed out.

When at least one client optimization is on, **Jogar** requests the latest stable release from [ApocBR Zomboid Patches](https://github.com/rbm4/apocalipsebr-zomboid-patches/releases/latest). The release must include the shaded `apocbr-agent.jar` asset with GitHub's SHA-256 digest. The launcher hashes the cached JAR and downloads the asset only if the verified copy is missing or corrupt. It checks the downloaded size, checksum, and agent entry point before storing the file under `zomboid-agent` in the launcher data directory. A failed check opens the launch log with the error.

The optimized launch uses the game's bundled 64-bit Java runtime and the original `projectzomboid.jar` on the classpath. Each checkbox maps to an explicit JVM property, including `false` for unchecked switches whose agent defaults are enabled. The agent targets the game build used by its release. The launch log shows the location of the agent and game process output.

# Project Zomboid integration

In **Opções**, use **Detectar pasta (recomendado)** to find an existing Steam installation. The launcher checks the Windows uninstall entries for Steam app 108600 first. If the registered folder is missing or invalid, it reads the registered Steam installation paths, then uses `steamapps/libraryfolders.vdf` and `appmanifest_108600.acf` to locate the game in the default library or on another drive. Auto-detection accepts a folder containing the Project Zomboid Windows executable.

Steam manages library locations under **Settings → Storage**. [Steam Support](https://help.steampowered.com/en/faqs/view/4bd4-4528-6b2e-8327)

Detection runs in the background and saves the result in `project-zomboid.properties` in the launcher's application data directory. If detection finds no installation or fails, the saved folder is retained and **Escolher pasta** remains available for manual selection.

## Play and server status

The home tab shows the current season (Season 5) and points players to **Opções** to enable the optional Apocalipse BR features. Its status widget uses the same 320 × 200 viewport as the WoW realm status.

**Atualizar e Jogar** becomes available after saving an installation folder containing `ProjectZomboid64.exe` or `ProjectZomboid32.exe`. The adjacent launch selector offers **Padrão** and **Viewpoint**. Each launcher session starts in **Padrão**. In **Padrão** with all client optimizations off, the button starts the executable from that folder, preferring the 64-bit version when both are present. The launcher sets `-Dzomboid.steam=1` for both the native and Java launch paths. For the native executable, it passes the property before the `--` separator so the executable applies it as a JVM option after loading the installation settings. The action stays disabled until the game exits.

Use **Ver detalhes** on the home tab to see the timestamped launch log. Launch failures open the log automatically. **Status do Servidor** embeds the TrackyServer widget for **APOCALIPSE [BR]**.

The status WebView uses a stylesheet to display the bundled Zomboid PNG in the widget header because JavaFX fails to render TrackyServer's WebP logo. The stylesheet is scoped to that logo and the widget still fetches live status from TrackyServer.

## Game memory

**Opções → RAM máxima (Java)** reads the current `-Xmx` value from the `vmArgs` array in the selected installation's `ProjectZomboid64.json`. Enter a size such as `8G` or `4096M` and select **Aplicar** to save it directly to that file. The launcher preserves the other JVM arguments and settings and checks that the new maximum is at least the configured `-Xms`. Both launch modes use the updated value the next time the game starts.

## ApocBR optimizations

**Opções → Otimizações ApocBR** highlights the most effective client optimizations before grouping the other switches by engine subsystem. Hover over a checkbox to see which engine behavior it changes. Select the **i** beside an option to open its guide or section in the default browser on GitHub. The links also work for the dedicated server switches shown for reference. The launcher saves client selections in `project-zomboid.properties`. Link targets are maintained in `ZomboidOptimization`; building reconciliation links to the published algorithm section while the standalone agent guide is awaiting publication.

Under **Otimizações de maior impacto**, **Reduzir a área carregada do mapa** is off by default and takes effect the next time the game starts. It passes `-Dapocbr.optimization.clientChunkMap.enabled=true` to a compatible agent. At 1920×1080 and above, the cap reduces the client's map window from 19×19 to 13×13 chunks (361 to 169 slots per player, about 53% fewer). Smaller windows keep their original size. The cap reduces the amount of world data the client can keep loaded and can shrink visible map coverage when zoomed out.

**Reconciliação de construções** is also off by default and can be enabled independently of the chunk-map cap. It passes `-Dapocbr.optimization.clientBuildingReconciliation.enabled=true` to a compatible agent on the next launch. The agent reduces work when updating buildings and rooms after map regions change by tracking visited regions and chunks that need refreshing more efficiently.

**Viewpoint** unchecks and disables the character stats and Lua dispatch options. Returning to **Padrão** restores the saved selections. **Viewpoint** forces these JVM arguments:

- `-Dapocbr.optimization.characterStats.enabled=false`
- `-Dapocbr.optimization.luaDispatch.enabled=false`
- `-Dapocbr.telemetry.lua.enabled=false`
- `-Dapocbr.lua.enabled=false`

With **Viewpoint** selected or at least one client optimization on, **Atualizar e Jogar** checks for the Steam client before downloading agents or starting the game. If Steam is closed, the launcher asks the player to open Steam, sign in, and try again. Steam must be running for the game to initialize Steamworks. [Steamworks API reference](https://partner.steamgames.com/doc/api/steam_api#SteamAPI_Init)

With Steam running, **Atualizar e Jogar** requests the latest stable release. The release must include the shaded `apocbr-agent.jar` asset with GitHub's SHA-256 digest. The launcher hashes the cached JAR and downloads the asset only if the verified copy is missing or corrupt. It checks the downloaded size, checksum, and agent entry point before storing the file under `zomboid-agent` in the launcher data directory. A failed check opens the launch log with the error. [ApocBR Zomboid Patches](https://github.com/rbm4/apocalipsebr-zomboid-patches/releases/latest)

The optimized launch uses the game's bundled 64-bit Java runtime and the original `projectzomboid.jar` on the classpath. The agent and game run in the same JVM, which receives the Steam property and explicit properties for each checkbox. This includes `false` for unchecked switches whose agent defaults are enabled. The optimized launch reads its heap settings from `ProjectZomboid64.json`. The agent targets the game build used by its release. The launch log shows the location of the agent and game process output.

## Viewpoint template

**Atualizar e Jogar** in **Viewpoint** mode checks the latest stable releases of both agents, including when every optimization is off. The **Apocalipse [BR] ZombieBuddy** release must contain a shaded `ZombieBuddy.jar` with GitHub's SHA-256 digest, the launcher template hook, and support for launcher-managed mod approval. The launcher verifies the size, checksum, and agent entry point and reuses matching cached copies under `zombiebuddy-agent`. It starts both agents in the same game JVM, with ApocBR first so its transformers are registered before ZombieBuddy preloads game classes. [ApocalipseBr ZombieBuddy releases](https://github.com/ApocalipseBr/ZombieBuddy/releases)

The launcher provisions Valve's Windows SteamCMD under its application data directory. Each Viewpoint launch issues anonymous Workshop downloads with `validate` for app `108600` and confirms success for every item. The template uses this load order.

| Mod ID | Workshop ID |
| --- | --- |
| `ZombieBuddy` | `3619862853` |
| `Viewpoint` | `3809306528` |
| `PZVoxelStudioViewpoint` | `3810302175` |

The home status and **Ver detalhes** log show when SteamCMD starts checking each mod and when SteamCMD verifies it, including its position in the template. The launcher shows a separate installation message when the staged files need replacing. SteamCMD stores downloads and `workshop-download.log` in the launcher's `steamcmd` directory. The launcher validates the expected mod IDs and Viewpoint Java mod JAR, then stages all three packages as a unit in `%USERPROFILE%/Zomboid/Workshop/Apocalipse Launcher - Viewpoint/Contents/mods`. It reuses a complete stage with matching Steam manifest IDs. A failed download or validation retains the prior stage and opens the details log. The managed stage stores each Workshop ID for ZombieBuddy's mod identity and approval checks.

The fork applies the template to the game's local active list in memory for the Viewpoint session. Personal mod lists stay on disk as saved, and multiplayer continues to use the server's mod list. The launcher compares the staged Viewpoint JAR with the SteamCMD-verified package and passes the SHA-256 digests to the fork for this session. The fork skips the approval dialog for a matching JAR inside the managed Viewpoint folder with the expected Workshop ID. A different path, ID, or digest blocks the managed Java mod. Existing signature and Steam ban checks still apply, and other Java mods follow their ordinary approval policy. The downloaded model pack declares Build 42.22.2 as its minimum. The game checks each mod's availability before applying the template. **Padrão** starts without the managed template or its ZombieBuddy agent.

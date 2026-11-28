# Project Zomboid integration

In **Opções**, use **Detectar pasta (recomendado)** to find an existing Steam installation. The launcher checks the Windows uninstall entries for Steam app 108600 first. If the registered folder is missing or invalid, it reads the registered Steam installation paths, then uses `steamapps/libraryfolders.vdf` and `appmanifest_108600.acf` to locate the game in the default library or on another drive. Auto-detection accepts a folder containing the Project Zomboid Windows executable.

Steam manages library locations under **Settings → Storage**. [Steam Support](https://help.steampowered.com/en/faqs/view/4bd4-4528-6b2e-8327)

Detection runs in the background and saves the result in `project-zomboid.properties` in the launcher's application data directory. If detection finds no installation or fails, the saved folder is retained and **Escolher pasta** remains available for manual selection.

## Play and server status

The home tab shows the current season (Season 5) and points players to **Opções** to enable the optional Apocalipse BR features. Its status widget uses the same 320 × 200 viewport as the WoW realm status.

**Atualizar e Jogar** becomes available after saving a Project Zomboid installation folder. The launch selector offers **Padr?o**, **Viewpoint**, **Viewpoint Visible Loot**, **Viewpoint Visible Loot + Right Click Fix**, **Viewpoint + Tree Flicker Patch**, and **Viewpoint + Immersive Driving**. Each launcher session starts in **Padr?o**. All modes use the Build 42 game JAR and bundled 64-bit Java runtime with Steam enabled. The action stays disabled until the game exits.

Use **Ver detalhes** on the home tab to see the timestamped launch log. Launch failures open the log automatically. **Status do Servidor** embeds the TrackyServer widget for **APOCALIPSE [BR]**.

The status WebView uses a stylesheet to display the bundled Zomboid PNG in the widget header because JavaFX fails to render TrackyServer's WebP logo. The stylesheet is scoped to that logo and the widget still fetches live status from TrackyServer.

## Game memory

**Opções → RAM máxima (Java)** reads the current `-Xmx` value from the `vmArgs` array in the selected installation's `ProjectZomboid64.json`. Enter a size such as `8G` or `4096M` and select **Aplicar** to save it directly to that file. The launcher preserves the other JVM arguments and settings and checks that the new maximum is at least the configured `-Xms`. Both launch modes use the updated value the next time the game starts.

## ApocBR optimizations

**Opções → Otimizações ApocBR** highlights the most effective client optimizations before grouping the other switches by engine subsystem. Hover over a checkbox to see which engine behavior it changes. Select the **i** beside an option to open its guide or section in the default browser on GitHub. The links also work for the dedicated server switches shown for reference. The launcher saves client selections in `project-zomboid.properties`. Link targets are maintained in `ZomboidOptimization`; building reconciliation links to the published algorithm section while the standalone agent guide is awaiting publication.

Under **Otimizações de maior impacto**, **Reduzir a área carregada do mapa** is off by default and takes effect the next time the game starts. It passes `-Dapocbr.optimization.clientChunkMap.enabled=true` to a compatible agent. At 1920×1080 and above, the cap reduces the client's map window from 19×19 to 13×13 chunks (361 to 169 slots per player, about 53% fewer). Smaller windows keep their original size. The cap reduces the amount of world data the client can keep loaded and can shrink visible map coverage when zoomed out.

**Reconciliação de construções** is also off by default and can be enabled independently of the chunk-map cap. It passes `-Dapocbr.optimization.clientBuildingReconciliation.enabled=true` to a compatible agent on the next launch. The agent reduces work when updating buildings and rooms after map regions change by tracking visited regions and chunks that need refreshing more efficiently.

The Viewpoint modes uncheck and disables the character stats and Lua dispatch options. Returning to **Padrão** restores the saved selections. These modes force these JVM arguments:

- `-Dapocbr.optimization.characterStats.enabled=false`
- `-Dapocbr.optimization.luaDispatch.enabled=false`
- `-Dapocbr.telemetry.lua.enabled=false`
- `-Dapocbr.lua.enabled=false`

In every mode, **Atualizar e Jogar** checks for the Steam client before downloading agents or starting the game. If Steam is closed, the launcher asks the player to open Steam, sign in, and try again. Steam must be running for the game to initialize Steamworks. [Steamworks API reference](https://partner.steamgames.com/doc/api/steam_api#SteamAPI_Init)

With Steam running, **Atualizar e Jogar** requests the latest stable release. The release must include the shaded `apocbr-agent.jar` asset with GitHub's SHA-256 digest. The launcher hashes the cached JAR and downloads the asset only if the verified copy is missing or corrupt. It checks the downloaded size, checksum, and agent entry point before storing the file under `zomboid-agent` in the launcher data directory. A failed check opens the launch log with the error. [ApocBR Zomboid Patches](https://github.com/rbm4/apocalipsebr-zomboid-patches/releases/latest)

The optimized launch uses the game's bundled 64-bit Java runtime and the original `projectzomboid.jar` on the classpath. The agent and game run in the same JVM, which receives the Steam property and explicit properties for each checkbox. This includes `false` for unchecked switches whose agent defaults are enabled. The optimized launch reads its heap settings from `ProjectZomboid64.json`. The agent targets the game build used by its release. The launch log shows the location of the agent and game process output.

## Launch profiles and Workshop mods

Every mode checks the latest stable releases of both agents. The **Apocalipse [BR] ZombieBuddy** JAR must support the launcher mod list and Java approvals. The launcher verifies the size, SHA-256 digest, and agent entry point and reuses matching cached copies. It starts ApocBR first so its transformers are registered before ZombieBuddy preloads game classes.

The selected mode defines which Workshop items SteamCMD downloads anonymously and verifies for app `108600`.

| Mode | Workshop items |
| --- | --- |
| Padr?o | ZombieBuddy (`3619862853`) |
| Viewpoint | ZombieBuddy (`3619862853`), Viewpoint (`3809306528`), PZVoxelStudioViewpoint (`3810302175`) |
| Viewpoint Visible Loot | ZombieBuddy (`3619862853`), Viewpoint (`3809306528`), ViewpointVisibleLoot (`3818189653`), PZVoxelStudioViewpoint (`3810302175`) |
| Viewpoint Visible Loot + Right Click Fix | All Viewpoint Visible Loot items plus ViewpointRightClick (`3819018807`) |
| Viewpoint + Tree Flicker Patch | All items from the Right Click Fix profile plus ViewpointDisableIsometricUpkeep (`3842290180`, Java) |
| Viewpoint + Immersive Driving | All items from the Tree Flicker Patch profile plus ImmersiveDriving (`3817413313`, Java) |

The Tree Flicker Patch requires Project Zomboid 42.22. Its published metadata pins ZombieBuddy to 2.3.4, but the updated ZombieBuddy fork accepts the launcher-verified copy through its 2.x compatibility layer. Player-installed copies still follow the published version range.

The launcher checks the mod metadata and Java JARs, then stages the selected items together in `%USERPROFILE%/Zomboid/Workshop/Apocalipse Launcher - Viewpoint/Contents/mods`. It reuses a complete stage when its Steam manifest IDs and registered Java digests match. A failed download or validation retains the prior stage and opens the details log.

The launcher passes an ordered list of mod IDs in `apocbr.launcher.mods`. ZombieBuddy merges it into the local game's mod list in memory. The separately installed `APOCALISEBR_PACK` is included when available, including in multiplayer. Other profile mods apply only to local sessions. ZombieBuddy still accepts `apocbr.launcher.template=viewpoint` and its existing hash property from older launchers.

The launcher finds Java JAR declarations in the verified Workshop mod metadata for the selected profile. It passes the resulting approvals in `apocbr.launcher.java.approvals` as comma-separated `modId:workshopId:sha256` entries. ZombieBuddy checks the mod ID, Workshop ID, staged path, and digest before approving a managed JAR for that session. Existing signature and Steam ban checks still apply.

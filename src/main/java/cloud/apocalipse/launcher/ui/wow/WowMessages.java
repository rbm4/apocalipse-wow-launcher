package cloud.apocalipse.launcher.ui.wow;

import java.util.function.Function;

final class WowMessages {
    private final Function<String, String> text;

    WowMessages(Function<String, String> text) {
        this.text = text;
    }

    private String t(String key) {
        return text.apply(key);
    }

    String translatePatchMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.equals("Fetching patch manifest...")) {
            return t("patch.fetchingManifest");
        }
        if (message.startsWith("Manifest version ") && message.endsWith(" has no files.")) {
            return t("patch.emptyManifest").replace("{version}", message.substring("Manifest version ".length(), message.length() - " has no files.".length()));
        }
        if (message.startsWith("Patch verification complete. Version ") && message.endsWith(" is installed.")) {
            return t("patch.complete").replace("{version}", message.substring("Patch verification complete. Version ".length(), message.length() - " is installed.".length()));
        }
        if (message.startsWith("Already current: ")) {
            return t("patch.current").replace("{file}", message.substring("Already current: ".length()));
        }
        if (message.equals("Downloading patch...")) {
            return t("patch.downloading");
        }
        if (message.startsWith("Installed ")) {
            return t("patch.installed").replace("{file}", message.substring("Installed ".length()));
        }
        return message;
    }

    String translateClientInstallMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.startsWith("Client already installed: ")) {
            return t("client.alreadyInstalled").replace("{folder}", message.substring("Client already installed: ".length()));
        }
        if (message.equals("Preparing client download...")) {
            return t("client.preparingDownload");
        }
        if (message.equals("Starting client download...")) {
            return t("client.startingDownload");
        }
        if (message.equals("Download initializing...")) {
            return t("client.downloadInitializing");
        }
        if (message.startsWith("Receiving client data... ") && message.contains(" at ")) {
            String details = message.substring("Receiving client data... ".length());
            int separator = details.lastIndexOf(" at ");
            if (separator > 0) {
                return t("client.receivingClientData")
                        .replace("{downloaded}", details.substring(0, separator))
                        .replace("{speed}", details.substring(separator + " at ".length()));
            }
        }
        if (message.startsWith("Downloading client... ") && message.contains(" at ")) {
            String details = message.substring("Downloading client... ".length());
            int separator = details.lastIndexOf(" at ");
            if (separator > 0) {
                return t("client.downloading")
                        .replace("{percent}", details.substring(0, separator))
                        .replace("{speed}", details.substring(separator + " at ".length()));
            }
        }
        if (message.equals("Verifying client download...") || message.equals("Verifying client files...")) {
            return t("client.verifying");
        }
        if (message.equals("Cleaning cancelled client download...")) {
            return t("client.cleaningCancelled");
        }
        if (message.equals("Client torrent download complete.")) {
            return t("client.downloadComplete");
        }
        if (message.startsWith("Client download complete: ")) {
            return t("client.complete").replace("{folder}", message.substring("Client download complete: ".length()));
        }
        if (message.startsWith("Using ") && message.endsWith(" fixed seed peer endpoint(s).")) {
            String count = message.substring("Using ".length(), message.indexOf(" fixed seed"));
            return t("client.fixedSeedPeers").replace("{count}", count);
        }
        if (message.equals("Fixed seed peer was ignored because its address is invalid.")) {
            return t("client.fixedSeedInvalid");
        }
        if (message.startsWith("Fixed seed peer could not be resolved: ")) {
            return t("client.fixedSeedResolveFailed")
                    .replace("{peer}", message.substring("Fixed seed peer could not be resolved: ".length()));
        }
        if (message.equals("Loaded saved peer discovery state.")) {
            return t("client.discoveryStateLoaded");
        }
        if (message.equals("Saved peer discovery state.")) {
            return t("client.discoveryStateSaved");
        }
        if (message.equals("Saved peer discovery state could not be loaded.")) {
            return t("client.discoveryStateLoadFailed");
        }
        if (message.equals("Peer discovery state could not be saved.")) {
            return t("client.discoveryStateSaveFailed");
        }
        if (message.equals("Client download registered.")) {
            return t("client.networkRegistered");
        }
        if (message.equals("Network listener ready.")) {
            return t("client.networkListening");
        }
        if (message.equals("Peer discovery network ready.")) {
            return t("client.dhtReady");
        }
        if (message.startsWith("Tracker found ") && (message.endsWith(" peer.") || message.endsWith(" peers."))) {
            String count = message.substring("Tracker found ".length(), message.indexOf(" peer"));
            return t("client.trackerPeers").replace("{count}", count);
        }
        if (message.equals("Tracker answered. Waiting for available peers...")) {
            return t("client.trackerWaiting");
        }
        if (message.equals("Some trackers are unavailable. Trying alternate sources...")) {
            return t("client.trackerAlternates");
        }
        if (message.equals("A tracker was unavailable. Trying other trackers and peer discovery...")) {
            return t("client.trackerFirstUnavailable");
        }
        if (message.equals("Connected to a download peer.")) {
            return t("client.peerConnected");
        }
        if (message.equals("Found peers. Waiting for downloadable pieces...")) {
            return t("client.peersWaitingPieces");
        }
        if (message.equals("Receiving data. Progress will move after pieces verify.")) {
            return t("client.receivingBeforeProgress");
        }
        if (message.startsWith("Received ") && message.contains(" before verified progress, ")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("Received (.+) before verified progress, (.+), (\\d+) seeds?, (\\d+) connections?\\.")
                    .matcher(message);
            if (matcher.matches()) {
                return t("client.receivedBeforeProgress")
                        .replace("{downloaded}", matcher.group(1))
                        .replace("{speed}", matcher.group(2))
                        .replace("{seeds}", matcher.group(3))
                        .replace("{connections}", matcher.group(4));
            }
        }
        if (message.startsWith("Downloaded ") && message.endsWith(" before verified piece progress.")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("Downloaded (\\d+) blocks? before verified piece progress\\.")
                    .matcher(message);
            if (matcher.matches()) {
                return t("client.blocksBeforeProgress").replace("{blocks}", matcher.group(1));
            }
        }
        if (message.equals("Preparing disk allocation for client files...")) {
            return t("client.diskPreparing");
        }
        if (message.startsWith("Download engine state: ")) {
            return t("client.engineState")
                    .replace("{state}", translateTorrentEngineState(message.substring("Download engine state: ".length())));
        }
        if (message.startsWith("Peer discovery nodes: ")) {
            return t("client.discoveryNodes").replace("{count}", message.substring("Peer discovery nodes: ".length()));
        }
        if (message.startsWith("Listening for peer connections on ")) {
            return t("client.listeningEndpoint").replace("{endpoint}", message.substring("Listening for peer connections on ".length()));
        }
        if (message.startsWith("Download health: ")) {
            String details = message.substring("Download health: ".length());
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d+) peers?, (\\d+) seeds?, (\\d+) connections?, (\\d+) discovery nodes, (.+), (.+) received")
                    .matcher(details);
            if (matcher.matches()) {
                return t("client.healthCheck")
                        .replace("{peers}", matcher.group(1))
                        .replace("{seeds}", matcher.group(2))
                        .replace("{connections}", matcher.group(3))
                        .replace("{nodes}", matcher.group(4))
                        .replace("{speed}", matcher.group(5))
                        .replace("{downloaded}", matcher.group(6));
            }
            return details;
        }
        if (message.equals("Peer discovery is online, but no downloadable sources have connected yet.")) {
            return t("client.noPeersYet");
        }
        if (message.startsWith("Tracker health: ")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d+) replies, (\\d+) reported sources, (\\d+) unavailable trackers\\.")
                    .matcher(message.substring("Tracker health: ".length()));
            if (matcher.matches()) {
                return t("client.trackerHealth")
                        .replace("{replies}", matcher.group(1))
                        .replace("{sources}", matcher.group(2))
                        .replace("{errors}", matcher.group(3));
            }
        }
        if (message.startsWith("Loaded ") && message.endsWith(" tracker entries from torrent.")) {
            String count = message.substring("Loaded ".length(), message.indexOf(" tracker entries"));
            return t("client.trackersLoaded").replace("{count}", count);
        }
        if (message.equals("Requested immediate announce to all trackers and peer discovery.")
                || message.equals("Requested immediate tracker and peer discovery announce.")) {
            return t("client.forceAnnounce");
        }
        if (message.equals("Requested immediate peer discovery announce.")) {
            return t("client.forcePeerDiscovery");
        }
        if (message.equals("Could not request immediate peer discovery announce.")) {
            return t("client.forceAnnounceFailed");
        }
        return message;
    }

    private String translateTorrentEngineState(String state) {
        return switch (state) {
            case "CHECKING_FILES" -> t("client.stateCheckingFiles");
            case "DOWNLOADING_METADATA" -> t("client.stateDownloadingMetadata");
            case "DOWNLOADING" -> t("client.stateDownloading");
            case "FINISHED" -> t("client.stateFinished");
            case "SEEDING" -> t("client.stateSeeding");
            case "CHECKING_RESUME_DATA" -> t("client.stateCheckingResume");
            default -> state;
        };
    }

    String translateErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return t("error.unknown");
        }
        if (message.equals("Select a valid World of Warcraft folder.")) {
            return t("error.validWowFolder");
        }
        if (message.startsWith("Wow.exe was not found in ")) {
            return t("error.wowExeMissing").replace("{folder}", message.substring("Wow.exe was not found in ".length()));
        }
        if (message.equals("Wow.exe is already running.")) {
            return t("error.wowAlreadyRunning");
        }
        if (message.startsWith("Data folder was not found in ")) {
            return t("error.dataFolderMissing").replace("{folder}", message.substring("Data folder was not found in ".length()));
        }
        return message;
    }
}

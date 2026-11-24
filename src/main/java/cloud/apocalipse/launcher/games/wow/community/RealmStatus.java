package cloud.apocalipse.launcher.games.wow.community;

public record RealmStatus(
        String status,
        boolean online,
        String realmName,
        Integer playersOnline,
        Integer totalAccounts,
        String error
) {
    public boolean hasError() {
        return error != null && !error.isBlank();
    }

    public String effectiveStatus() {
        if (hasError()) {
            return "error";
        }
        if (status != null && !status.isBlank()) {
            return status;
        }
        return online ? "online" : "offline";
    }
}

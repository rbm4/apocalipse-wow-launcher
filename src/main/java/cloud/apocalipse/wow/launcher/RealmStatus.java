package cloud.apocalipse.wow.launcher;

record RealmStatus(
        String status,
        boolean online,
        String realmName,
        Integer playersOnline,
        Integer totalAccounts,
        String error
) {
    boolean hasError() {
        return error != null && !error.isBlank();
    }

    String effectiveStatus() {
        if (hasError()) {
            return "error";
        }
        if (status != null && !status.isBlank()) {
            return status;
        }
        return online ? "online" : "offline";
    }
}

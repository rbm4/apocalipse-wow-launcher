package cloud.apocalipse.wow.launcher;

interface PatchProgress {
    void message(String message);

    void progress(double value);
}

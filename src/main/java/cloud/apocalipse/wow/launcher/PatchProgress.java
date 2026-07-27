package cloud.apocalipse.wow.launcher;

public interface PatchProgress {
    void message(String message);

    void progress(double value);

    default void detail(String message) {
    }
}

package cloud.apocalipse.launcher.core;

public interface OperationProgress {
    void message(String message);

    void progress(double value);

    default void detail(String message) {
    }
}

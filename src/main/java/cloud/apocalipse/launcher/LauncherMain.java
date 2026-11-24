package cloud.apocalipse.launcher;

import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;
import javafx.application.Application;

public final class LauncherMain {
    private LauncherMain() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                StartupLog.write("Uncaught exception on thread " + thread.getName() + ".", throwable));
        try {
            Application.launch(LauncherApp.class, args);
        } catch (RuntimeException exc) {
            StartupLog.write("Launcher failed before the JavaFX window opened.", exc);
            throw exc;
        }
    }
}

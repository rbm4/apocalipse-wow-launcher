package cloud.apocalipse.launcher.ui.zomboid;

import cloud.apocalipse.launcher.games.zomboid.config.ZomboidJvmSettings;
import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ResourceBundle;

final class ZomboidMemoryOptions extends VBox {
    private final ResourceBundle messages;
    private final TextField maximum = new TextField();
    private final Button apply = new Button();
    private final Label status = new Label();
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper();
    private Path directory;
    private Task<String> pending;
    private String savedValue;

    ZomboidMemoryOptions(ResourceBundle messages) {
        super(6);
        this.messages = messages;
        getStyleClass().addAll("glass-panel", "zomboid-memory-options");

        Label title = new Label(messages.getString("ram.title"));
        title.getStyleClass().add("section-label");
        title.setLabelFor(maximum);
        maximum.setPromptText(messages.getString("ram.example"));
        maximum.setPrefColumnCount(9);
        maximum.setOnAction(event -> apply.fire());
        maximum.textProperty().addListener((observable, before, after) -> {
            if (savedValue != null && pending == null) {
                status.setText(messages.getString("ram.hint"));
            }
            refreshControls();
        });

        apply.setText(messages.getString("ram.apply"));
        apply.getStyleClass().add("secondary-button");
        apply.setOnAction(event -> updateHeap(maximum.getText().strip()));
        status.getStyleClass().add("muted");
        status.setWrapText(true);
        HBox row = new HBox(12, title, maximum, apply, status);
        row.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(row);
    }

    void setInstallation(Path directory) {
        this.directory = directory;
        pending = null;
        saving.set(false);
        savedValue = null;
        maximum.clear();
        if (directory == null) {
            status.setText(messages.getString("ram.noFolder"));
            refreshControls();
        } else {
            updateHeap(null);
        }
    }

    ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    private void updateHeap(String requestedValue) {
        boolean write = requestedValue != null;
        var settings = new ZomboidJvmSettings(directory);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws IOException {
                if (write) {
                    settings.setMaximumHeap(requestedValue);
                    return requestedValue;
                }
                return settings.maximumHeap().orElse("");
            }
        };
        pending = task;
        saving.set(write);
        status.setText(messages.getString(write ? "ram.saving" : "ram.loading"));
        refreshControls();
        task.setOnSucceeded(event -> {
            if (pending != task) {
                return;
            }
            pending = null;
            saving.set(false);
            savedValue = task.getValue();
            maximum.setText(savedValue);
            status.setText(messages.getString(write ? "ram.saved" : "ram.hint"));
            refreshControls();
        });
        task.setOnFailed(event -> {
            if (pending != task) {
                return;
            }
            pending = null;
            saving.set(false);
            Throwable error = task.getException();
            String key;
            if (error instanceof NoSuchFileException) {
                savedValue = null;
                key = "ram.missing";
            } else if (error instanceof IllegalArgumentException) {
                key = "ram.belowInitial";
            } else {
                key = write ? "ram.saveFailed" : "ram.readFailed";
                StartupLog.write("Could not access ProjectZomboid64.json.", error);
            }
            status.setText(messages.getString(key));
            refreshControls();
        });
        Thread worker = new Thread(task, "zomboid-memory-settings");
        worker.setDaemon(true);
        worker.start();
    }

    private void refreshControls() {
        String value = maximum.getText().strip();
        boolean unavailable = savedValue == null || pending != null;
        maximum.setDisable(unavailable);
        apply.setDisable(unavailable || value.equals(savedValue) || !ZomboidJvmSettings.isHeapSize(value));
    }
}

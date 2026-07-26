package cloud.apocalipse.wow.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class LauncherApp extends Application {
    private final LauncherDefaults defaults = LauncherDefaults.load();
    private final LauncherConfig config = new LauncherConfig(defaults);
    private final PatchManager patchManager = new PatchManager();
    private final GameLauncher gameLauncher = new GameLauncher(defaults.wowExecutable());

    private Label wowPathLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea logArea;
    private Hyperlink detailsLink;
    private Button patchButton;
    private Button launchButton;
    private Button optionsButton;
    private boolean patchVerified;

    @Override
    public void start(Stage stage) {
        wowPathLabel = new Label();
        refreshWowPathLabel();

        optionsButton = new Button("Options");
        optionsButton.getStyleClass().add("secondary-button");
        optionsButton.setOnAction(event -> chooseWowFolder(stage));

        patchButton = new Button("Check and Patch");
        patchButton.setDefaultButton(true);
        patchButton.setOnAction(event -> synchronize());

        launchButton = new Button("Launch WoW");
        launchButton.getStyleClass().add("primary-button");
        launchButton.setDisable(true);
        launchButton.setOnAction(event -> launchGame());

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Ready");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(12);
        logArea.setVisible(false);
        logArea.setManaged(false);

        detailsLink = new Hyperlink("See details");
        detailsLink.setPadding(Insets.EMPTY);
        detailsLink.setOnAction(event -> toggleDetails());

        VBox root = new VBox(16);
        root.getStyleClass().add("root-shell");
        root.getChildren().addAll(
                heroBlock(),
                folderStatusRow(),
                actions(),
                progressBlock(),
                logArea
        );
        VBox.setVgrow(logArea, Priority.ALWAYS);

        BorderPane shell = new BorderPane(root);
        Scene scene = new Scene(shell, 820, 560);
        scene.getStylesheets().add(getClass().getResource("/launcher.css").toExternalForm());
        stage.setTitle("Apocalipse WoW Launcher");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/Apocalipse_wow_icon.png")));
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(500);
        stage.show();

        log("Launcher ready.");
        if (config.wowDirectory().isEmpty()) {
            Platform.runLater(() -> chooseWowFolder(stage));
        }
    }

    private VBox heroBlock() {
        ImageView banner = new ImageView(new Image(getClass().getResourceAsStream("/Banner.png")));
        banner.setPreserveRatio(true);
        banner.setFitHeight(230);
        banner.setSmooth(true);
        banner.getStyleClass().add("banner");

        Label title = new Label("Apocalipse WoW");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Keep your 3.3.5a client ready for Azeroth.");
        subtitle.getStyleClass().add("subtitle");
        VBox box = new VBox(10, banner, title, subtitle);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox folderStatusRow() {
        Label label = new Label("Game folder");
        label.getStyleClass().add("section-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, label, wowPathLabel, spacer, optionsButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("panel");
        return row;
    }

    private HBox actions() {
        HBox row = new HBox(10, patchButton, launchButton);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private VBox progressBlock() {
        VBox box = new VBox(8, statusLabel, progressBar, detailsLink);
        box.getStyleClass().add("panel");
        VBox.setVgrow(progressBar, Priority.NEVER);
        return box;
    }

    private void chooseWowFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select World of Warcraft Folder");
        config.wowDirectory()
                .filter(path -> path.toFile().isDirectory())
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);

        File selected = chooser.showDialog(stage);
        if (selected == null) {
            return;
        }

        Path wowDirectory = selected.toPath().toAbsolutePath().normalize();
        config.setWowDirectory(wowDirectory);
        refreshWowPathLabel();
        patchVerified = false;
        launchButton.setDisable(true);
        saveConfig();
        log("Selected WoW folder: " + wowDirectory);
    }

    private void synchronize() {
        Path wowDirectory = selectedWowDirectory();
        if (wowDirectory == null) {
            log("Select your WoW folder first.");
            return;
        }

        patchVerified = false;
        launchButton.setDisable(true);
        setWorking(true);
        Task<PatchManifest> task = new Task<>() {
            @Override
            protected PatchManifest call() throws Exception {
                return patchManager.synchronize(wowDirectory, defaults.manifestUrl(), new PatchProgress() {
                    @Override
                    public void message(String message) {
                        updateMessage(message);
                        Platform.runLater(() -> log(message));
                    }

                    @Override
                    public void progress(double value) {
                        updateProgress(value, 1);
                    }
                });
            }
        };
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            statusLabel.setText("Ready to play");
            progressBar.setProgress(1);
            patchVerified = true;
            setWorking(false);
        });
        task.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            statusLabel.setText("Patch failed");
            progressBar.setProgress(0);
            setWorking(false);
            Throwable error = task.getException();
            log("Patch failed: " + (error == null ? "unknown error" : error.getMessage()));
        });

        Thread thread = new Thread(task, "patch-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private void launchGame() {
        Path wowDirectory = selectedWowDirectory();
        if (wowDirectory == null) {
            log("Select your WoW folder first.");
            return;
        }
        try {
            gameLauncher.launch(wowDirectory);
            log("Launching Wow.exe");
        } catch (Exception exc) {
            log("Launch failed: " + exc.getMessage());
        }
    }

    private Path selectedWowDirectory() {
        return config.wowDirectory().orElse(null);
    }

    private void saveConfig() {
        try {
            config.save();
        } catch (Exception exc) {
            log("Could not save launcher settings: " + exc.getMessage());
        }
    }

    private void setWorking(boolean working) {
        patchButton.setDisable(working);
        launchButton.setDisable(working || !patchVerified);
        optionsButton.setDisable(working);
    }

    private void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + time + "] " + message + System.lineSeparator());
    }

    private void toggleDetails() {
        boolean show = !logArea.isVisible();
        logArea.setVisible(show);
        logArea.setManaged(show);
        detailsLink.setText(show ? "Hide details" : "See details");
    }

    private void refreshWowPathLabel() {
        wowPathLabel.setText(config.wowDirectory()
                .map(Path::toString)
                .orElse("Not configured"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}

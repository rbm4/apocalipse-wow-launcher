package cloud.apocalipse.launcher.ui;

import cloud.apocalipse.launcher.LauncherGame;
import cloud.apocalipse.launcher.LauncherPreferences;
import cloud.apocalipse.launcher.games.wow.WowLauncher;
import cloud.apocalipse.launcher.games.wow.config.LauncherConfig;
import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher;
import cloud.apocalipse.launcher.games.zomboid.SteamInstallationDetector;
import cloud.apocalipse.launcher.games.zomboid.config.ProjectZomboidConfig;
import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;
import cloud.apocalipse.launcher.infrastructure.update.LauncherReleases;
import cloud.apocalipse.launcher.infrastructure.update.WindowsLauncherInstaller;
import cloud.apocalipse.launcher.ui.shell.WindowTitleBar;
import cloud.apocalipse.launcher.ui.wow.AppLanguage;
import cloud.apocalipse.launcher.ui.wow.WowLauncherView;
import cloud.apocalipse.launcher.ui.zomboid.ProjectZomboidLauncherView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class LauncherView {
    private final LauncherConfig config;
    private final WowLauncher wow;
    private final LauncherPreferences preferences;
    private final ProjectZomboidConfig zomboidConfig;
    private final ProjectZomboidLauncher zomboid;
    private final LauncherReleases releases;
    private final Consumer<String> openInBrowser;

    private Stage stage;
    private BorderPane root;
    private WindowTitleBar titleBar;
    private Node wowContent;
    private Node zomboidContent;
    private WowLauncherView wowView;
    private ProjectZomboidLauncherView zomboidView;
    private String updateLabel;
    private Runnable updateAction;
    private LauncherGame activeGame;

    public LauncherView(LauncherConfig config, WowLauncher wow, ProjectZomboidLauncher zomboid,
                        LauncherReleases releases, Consumer<String> openInBrowser) {
        this.config = config;
        this.wow = wow;
        this.zomboid = zomboid;
        this.releases = releases;
        this.openInBrowser = openInBrowser;
        preferences = new LauncherPreferences(config.appDataDirectory());
        zomboidConfig = new ProjectZomboidConfig(config.appDataDirectory());
    }

    public void show(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);
        root = new BorderPane();
        root.getStyleClass().add("root-shell");
        titleBar = new WindowTitleBar(stage, brandTitle(), chooseGameLabel(), this::showGameSelection,
                otherGameLabel(), this::switchToOtherGame);
        root.setTop(titleBar);

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(getClass().getResource("/launcher.css").toExternalForm());
        stage.setTitle(brandTitle());
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/Apocalipse_wow_icon.png")));
        stage.setScene(scene);
        stage.setMinWidth(1080);
        stage.setMinHeight(680);
        stage.centerOnScreen();
        stage.setOnShown(event -> {
            StartupLog.write("Launcher window shown.", null);
            stage.toFront();
            stage.requestFocus();
        });
        stage.setOnCloseRequest(event -> {
            if (root.isDisable()) {
                event.consume();
            }
        });

        preferences.selectedGame().ifPresentOrElse(this::showGame, this::showGameSelection);
        stage.show();
        checkForLauncherUpdate();
    }

    private void checkForLauncherUpdate() {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return;
        }
        Thread.ofVirtual().name("launcher-update-check").start(() -> {
            try {
                var available = releases.check(getClass().getPackage().getImplementationVersion());
                if (available.isEmpty()) {
                    return;
                }
                var update = available.get();
                Platform.runLater(() -> showLauncherUpdate("launcherUpdate.available",
                        () -> openInBrowser.accept(update.page().toString())));
                var installer = WindowsLauncherInstaller.current();
                if (installer.isPresent()) {
                    Path msi = releases.download(update);
                    Platform.runLater(() -> showLauncherUpdate("launcherUpdate.restart",
                            () -> installLauncherUpdate(installer.get(), update, msi)));
                }
            } catch (IOException | RuntimeException ex) {
                StartupLog.write("Launcher update check or download failed.", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void showLauncherUpdate(String label, Runnable action) {
        updateLabel = label;
        updateAction = action;
        titleBar.showUpdate(currentLanguage().text(label), action);
    }

    private void installLauncherUpdate(WindowsLauncherInstaller installer, LauncherReleases.Update update, Path msi) {
        var language = currentLanguage();
        if ((wowView != null && wowView.isUpdateBlocked()) || (zomboidView != null && zomboidView.isUpdateBlocked())) {
            showUpdateMessage(Alert.AlertType.INFORMATION, language.text("launcherUpdate.busy"));
            return;
        }
        root.setDisable(true);
        Task<Void> preparation = new Task<>() {
            @Override
            protected Void call() throws IOException, InterruptedException {
                installer.prepare(msi, update, language.text("launcherUpdate.failed"), language.text("launcherUpdate.reboot"),
                        language.text("launcherUpdate.relaunchFailed"));
                return null;
            }
        };
        preparation.setOnSucceeded(event -> Platform.exit());
        preparation.setOnFailed(event -> {
            StartupLog.write("Launcher installer did not start.", preparation.getException());
            root.setDisable(false);
            var manual = new javafx.scene.control.ButtonType(language.text("launcherUpdate.available"));
            var alert = new Alert(Alert.AlertType.ERROR, language.text("launcherUpdate.failed"),
                    manual, javafx.scene.control.ButtonType.CLOSE);
            alert.initOwner(stage);
            alert.setHeaderText(null);
            if (alert.showAndWait().filter(manual::equals).isPresent()) {
                openInBrowser.accept(update.page().toString());
            }
        });
        Thread.ofVirtual().name("launcher-update-prepare").start(preparation);
    }

    private void showUpdateMessage(Alert.AlertType type, String message) {
        var alert = new Alert(type, message);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showGameSelection() {
        AppLanguage language = currentLanguage();
        titleBar.setActionText(language.text("button.chooseGame"));
        titleBar.setActionVisible(false);
        titleBar.setOtherGameVisible(false);
        root.setCenter(new GameSelectionView(language, this::selectGame).createContent());
    }

    private void selectGame(LauncherGame game) {
        try {
            preferences.setSelectedGame(game);
        } catch (IOException exc) {
            StartupLog.write("Could not save selected game.", exc);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle(brandTitle());
            alert.setHeaderText(currentLanguage().text("selection.saveFailedTitle"));
            alert.setContentText(currentLanguage().text("selection.saveFailedMessage"));
            alert.showAndWait();
            return;
        }
        showGame(game);
    }

    private void showGame(LauncherGame game) {
        activeGame = game;
        titleBar.setActionText(chooseGameLabel());
        titleBar.setActionVisible(true);
        titleBar.setOtherGameText(otherGameLabel());
        titleBar.setOtherGameVisible(true);
        switch (game) {
            case WORLD_OF_WARCRAFT -> {
                if (wowContent == null) {
                    wowView = new WowLauncherView(config, wow, this::refreshNavigationTexts);
                    wowContent = wowView.createContent(stage);
                }
                root.setCenter(wowContent);
            }
            case PROJECT_ZOMBOID -> {
                if (zomboidContent == null) {
                    zomboidView = new ProjectZomboidLauncherView(
                            currentLanguage(), zomboidConfig, new SteamInstallationDetector(), zomboid, openInBrowser);
                    zomboidContent = zomboidView.createContent(stage);
                }
                root.setCenter(zomboidContent);
            }
        }
    }

    private void refreshNavigationTexts() {
        titleBar.setActionText(chooseGameLabel());
        titleBar.setOtherGameText(otherGameLabel());
        if (updateAction != null) {
            titleBar.showUpdate(currentLanguage().text(updateLabel), updateAction);
        }
    }

    private void switchToOtherGame() {
        LauncherGame target = activeGame == LauncherGame.WORLD_OF_WARCRAFT
                ? LauncherGame.PROJECT_ZOMBOID
                : LauncherGame.WORLD_OF_WARCRAFT;
        selectGame(target);
    }

    private String chooseGameLabel() {
        return currentLanguage().text("button.chooseGame");
    }

    private String otherGameLabel() {
        String key = activeGame == LauncherGame.WORLD_OF_WARCRAFT
                ? "button.switchToZomboid"
                : "button.switchToWow";
        return currentLanguage().text(key);
    }

    private AppLanguage currentLanguage() {
        return AppLanguage.fromCode(config.language());
    }

    private String brandTitle() {
        return currentLanguage().text("window.title");
    }
}

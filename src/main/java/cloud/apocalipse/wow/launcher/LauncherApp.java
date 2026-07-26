package cloud.apocalipse.wow.launcher;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LauncherApp extends Application {
    private final LauncherDefaults defaults = LauncherDefaults.load();
    private final LauncherConfig config = new LauncherConfig(defaults);
    private final PatchManager patchManager = new PatchManager();
    private final GameLauncher gameLauncher = new GameLauncher(defaults.wowExecutable());
    private final RealmStatusClient realmStatusClient = new RealmStatusClient();

    private Label wowPathLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Label realmNameLabel;
    private Label realmStatusLabel;
    private Label playersOnlineLabel;
    private Label totalAccountsLabel;
    private Label realmUpdatedLabel;
    private Circle realmStatusDot;
    private TextArea logArea;
    private Hyperlink detailsLink;
    private Button updateAndPlayButton;
    private MenuButton launchOptionsButton;
    private Button browseButton;
    private ComboBox<LaunchProfile> launchProfileCombo;
    private TabPane tabs;
    private Timeline realmStatusTimeline;
    private double dragOffsetX;
    private double dragOffsetY;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
    private boolean patchVerified;
    private boolean launchAfterSync;
    private boolean manuallyMaximized;

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
        wowPathLabel = new Label();
        refreshWowPathLabel();

        browseButton = new Button("Choose folder");
        browseButton.getStyleClass().add("secondary-button");
        browseButton.setOnAction(event -> chooseWowFolder(stage));

        updateAndPlayButton = new Button("Update & Play");
        updateAndPlayButton.setDefaultButton(true);
        updateAndPlayButton.getStyleClass().add("update-button");
        updateAndPlayButton.setOnAction(event -> synchronize(true));

        launchOptionsButton = new MenuButton("Launch options");
        launchOptionsButton.getStyleClass().add("launch-options-button");
        configureLaunchOptionsMenu();

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Ready");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(5);
        logArea.setMinHeight(88);
        logArea.setPrefHeight(88);
        logArea.setMaxHeight(88);
        logArea.setVisible(false);
        logArea.setManaged(false);
        logArea.getStyleClass().add("details-log");

        detailsLink = new Hyperlink("See details");
        detailsLink.setPadding(Insets.EMPTY);
        detailsLink.setOnAction(event -> toggleDetails());

        launchProfileCombo = new ComboBox<>();
        launchProfileCombo.getItems().addAll(LaunchProfile.values());
        launchProfileCombo.setValue(LaunchProfile.fromId(config.launchProfile()));
        launchProfileCombo.setOnAction(event -> {
            config.setLaunchProfile(launchProfileCombo.getValue().id());
            saveConfig();
            configureLaunchOptionsMenu();
        });

        tabs = new TabPane();
        tabs.getStyleClass().add("main-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Apocalipse WoW", homeContent()));
        tabs.getTabs().add(new Tab("Options", optionsContent(stage)));

        StackPane tabSurface = tabSurface();
        BorderPane root = new BorderPane();
        root.setTop(titleBar(stage));
        root.setCenter(tabSurface);
        root.getStyleClass().add("root-shell");

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(getClass().getResource("/launcher.css").toExternalForm());
        stage.setTitle("Apocalipse [BR] Launcher");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/Apocalipse_wow_icon.png")));
        stage.setScene(scene);
        stage.setMinWidth(1080);
        stage.setMinHeight(680);
        stage.show();

        log("Launcher ready.");
        startRealmStatusRefresh();
        if (config.wowDirectory().isEmpty()) {
            Platform.runLater(() -> {
                tabs.getSelectionModel().select(1);
                chooseWowFolder(stage);
            });
        }
    }

    private HBox titleBar(Stage stage) {
        Label title = new Label("Apocalipse WoW Launcher");
        title.getStyleClass().add("window-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimize = windowButton(minimizeIcon());
        minimize.setOnAction(event -> stage.setIconified(true));
        Button maximize = windowButton(maximizeIcon());
        maximize.setOnAction(event -> toggleMaximize(stage));
        Button close = windowButton(closeIcon());
        close.getStyleClass().add("close-button");
        close.setOnAction(event -> stage.close());

        HBox bar = new HBox(8, title, spacer, minimize, maximize, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("title-bar");
        bar.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        bar.setOnMouseDragged(event -> {
            if (!manuallyMaximized) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
        bar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximize(stage);
            }
        });
        return bar;
    }

    private void toggleMaximize(Stage stage) {
        if (manuallyMaximized) {
            stage.setX(restoreX);
            stage.setY(restoreY);
            stage.setWidth(restoreWidth);
            stage.setHeight(restoreHeight);
            manuallyMaximized = false;
            return;
        }

        restoreX = stage.getX();
        restoreY = stage.getY();
        restoreWidth = stage.getWidth();
        restoreHeight = stage.getHeight();

        Rectangle2D bounds = currentScreenBounds(stage);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        manuallyMaximized = true;
    }

    private Rectangle2D currentScreenBounds(Stage stage) {
        double centerX = stage.getX() + stage.getWidth() / 2;
        double centerY = stage.getY() + stage.getHeight() / 2;
        return Screen.getScreensForRectangle(centerX, centerY, 1, 1).stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private StackPane tabSurface() {
        Image backgroundImage = new Image(getClass().getResourceAsStream("/Background.png"));
        ImageView background = new ImageView(backgroundImage);
        background.setManaged(false);
        background.setMouseTransparent(true);
        background.setPreserveRatio(false);
        background.setSmooth(true);
        background.setOpacity(0.50);
        background.getStyleClass().add("banner");

        StackPane surface = new StackPane(background, tabs);
        background.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> coverWidth(surface.getWidth(), surface.getHeight(), backgroundImage),
                surface.widthProperty(), surface.heightProperty(), backgroundImage.widthProperty(), backgroundImage.heightProperty()));
        background.fitHeightProperty().bind(Bindings.createDoubleBinding(
                () -> coverHeight(surface.getWidth(), surface.getHeight(), backgroundImage),
                surface.widthProperty(), surface.heightProperty(), backgroundImage.widthProperty(), backgroundImage.heightProperty()));

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(surface.widthProperty());
        clip.heightProperty().bind(surface.heightProperty());
        surface.setClip(clip);
        return surface;
    }

    private Button windowButton(Region icon) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("window-button");
        button.setFocusTraversable(false);
        return button;
    }

    private Region minimizeIcon() {
        Region icon = new Region();
        icon.getStyleClass().addAll("window-icon", "minimize-icon");
        return icon;
    }

    private Region maximizeIcon() {
        Region icon = new Region();
        icon.getStyleClass().addAll("window-icon", "maximize-icon");
        return icon;
    }

    private Region closeIcon() {
        Line first = new Line(0, 0, 11, 11);
        Line second = new Line(11, 0, 0, 11);
        first.getStyleClass().add("close-icon-line");
        second.getStyleClass().add("close-icon-line");
        StackPane icon = new StackPane(first, second);
        icon.getStyleClass().add("window-icon");
        return icon;
    }

    private AnchorPane homeContent() {
        Label title = new Label("Apocalipse WoW");
        title.getStyleClass().add("hero-title");
        Label subtitle = new Label("Patch, verify, and enter Azeroth through the Apocalipse realm.");
        subtitle.getStyleClass().add("hero-subtitle");

        VBox heroText = new VBox(8, title, subtitle, actionRow(), releaseBadge());
        heroText.setAlignment(Pos.CENTER_LEFT);
        HBox hero = new HBox(24, heroText, sidePanel());
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);
        hero.getStyleClass().add("hero-panel");

        VBox bottomContent = new VBox(12, newsSection(), progressBlock());
        bottomContent.getStyleClass().add("bottom-content");
        bottomContent.setMaxWidth(Double.MAX_VALUE);

        AnchorPane page = new AnchorPane(hero, bottomContent);
        AnchorPane.setTopAnchor(hero, 22.0);
        AnchorPane.setLeftAnchor(hero, 22.0);
        AnchorPane.setRightAnchor(hero, 22.0);
        AnchorPane.setLeftAnchor(bottomContent, 22.0);
        AnchorPane.setRightAnchor(bottomContent, 22.0);
        AnchorPane.setBottomAnchor(bottomContent, 22.0);
        page.getStyleClass().add("home-page");
        return page;
    }

    private double coverWidth(double containerWidth, double containerHeight, Image image) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return containerWidth;
        }
        double scale = Math.max(containerWidth / imageWidth, containerHeight / imageHeight);
        return imageWidth * scale;
    }

    private double coverHeight(double containerWidth, double containerHeight, Image image) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return containerHeight;
        }
        double scale = Math.max(containerWidth / imageWidth, containerHeight / imageHeight);
        return imageHeight * scale;
    }

    private HBox actionRow() {
        HBox row = new HBox(10, updateAndPlayButton, launchOptionsButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label releaseBadge() {
        Label badge = new Label("Season client patch managed by launcher");
        badge.getStyleClass().add("release-badge");
        return badge;
    }

    private VBox sidePanel() {
        Label headline = new Label("Realm Status");
        headline.getStyleClass().add("panel-title");

        realmStatusDot = new Circle(5);
        realmStatusDot.getStyleClass().addAll("realm-status-dot", "status-unknown");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, headline, spacer, realmStatusDot);
        header.setAlignment(Pos.CENTER_LEFT);

        realmNameLabel = new Label("Apocalipse");
        realmNameLabel.getStyleClass().add("realm-name");
        realmStatusLabel = new Label("Checking...");
        realmStatusLabel.getStyleClass().addAll("realm-status-value", "status-unknown");
        playersOnlineLabel = new Label("-");
        playersOnlineLabel.getStyleClass().add("realm-stat-value");
        totalAccountsLabel = new Label("-");
        totalAccountsLabel.getStyleClass().add("realm-stat-value");
        realmUpdatedLabel = new Label("Awaiting status");
        realmUpdatedLabel.getStyleClass().add("muted");

        VBox box = new VBox(10,
                header,
                realmNameLabel,
                realmStatusRow("Status", realmStatusLabel),
                realmStatusRow("Players online", playersOnlineLabel),
                realmStatusRow("Registered accounts", totalAccountsLabel),
                realmUpdatedLabel);
        box.setMinWidth(270);
        box.getStyleClass().add("glass-panel");
        return box;
    }

    private HBox realmStatusRow(String label, Label value) {
        Label title = new Label(label);
        title.getStyleClass().add("realm-stat-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, title, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("realm-status-row");
        return row;
    }

    private VBox progressBlock() {
        VBox box = new VBox(8, statusLabel, progressBar, detailsLink, logArea);
        box.getStyleClass().add("glass-panel");
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(progressBar, Priority.NEVER);
        return box;
    }

    private VBox newsSection() {
        Label header = new Label("Latest News");
        header.getStyleClass().add("section-title");
        VBox firstCard = newsCard("Client Patching Online", "Launcher-managed MPQ updates are now being validated.");
        VBox secondCard = newsCard("Random Battlegrounds", "Client UI patching moved to the bundled ApocalipsePVP AddOn.");
        VBox thirdCard = newsCard("Coming Next", "Website news integration will feed this panel soon.");
        HBox cards = new HBox(14,
                firstCard,
                secondCard,
                thirdCard
        );
        cards.setFillHeight(false);
        cards.setAlignment(Pos.CENTER_LEFT);
        cards.setMaxWidth(Double.MAX_VALUE);
        firstCard.prefWidthProperty().bind(cards.widthProperty().subtract(28).divide(3));
        secondCard.prefWidthProperty().bind(cards.widthProperty().subtract(28).divide(3));
        thirdCard.prefWidthProperty().bind(cards.widthProperty().subtract(28).divide(3));
        VBox section = new VBox(10, header, cards);
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private VBox newsCard(String title, String body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("news-title");
        Label bodyLabel = new Label(body);
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("news-body");
        VBox card = new VBox(8, titleLabel, bodyLabel);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        card.setMinHeight(86);
        card.setPrefHeight(86);
        card.setMaxHeight(86);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getStyleClass().add("news-card");
        return card;
    }

    private VBox optionsContent(Stage stage) {
        Label title = new Label("Options");
        title.getStyleClass().add("hero-title");
        Label description = new Label("Launcher configuration used before patching and starting the client.");
        description.getStyleClass().add("hero-subtitle");

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(14);
        form.getStyleClass().add("glass-panel");

        Label folderLabel = new Label("Game directory");
        folderLabel.getStyleClass().add("section-label");
        Label launchLabel = new Label("Launch profile");
        launchLabel.getStyleClass().add("section-label");

        form.add(folderLabel, 0, 0);
        form.add(wowPathLabel, 1, 0);
        form.add(browseButton, 2, 0);
        form.add(launchLabel, 0, 1);
        form.add(launchProfileCombo, 1, 1);
        GridPane.setHgrow(wowPathLabel, Priority.ALWAYS);
        GridPane.setHgrow(launchProfileCombo, Priority.ALWAYS);

        VBox content = new VBox(18, title, description, form);
        content.setPadding(new Insets(22));
        return content;
    }

    private void configureLaunchOptionsMenu() {
        if (launchOptionsButton == null) {
            return;
        }
        LaunchProfile selected = LaunchProfile.fromId(config.launchProfile());
        launchOptionsButton.setText(selected.label());
        launchOptionsButton.getItems().clear();
        for (LaunchProfile profile : LaunchProfile.values()) {
            MenuItem item = new MenuItem(profile.label());
            item.setOnAction(event -> {
                config.setLaunchProfile(profile.id());
                if (launchProfileCombo != null) {
                    launchProfileCombo.setValue(profile);
                }
                saveConfig();
                launchOptionsButton.setText(profile.label());
                log("Launch profile selected: " + profile.label());
            });
            launchOptionsButton.getItems().add(item);
        }
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
        updateAndPlayButton.setText("Update & Play");
        saveConfig();
        log("Selected WoW folder: " + wowDirectory);
    }

    private void synchronize(boolean launchWhenDone) {
        Path wowDirectory = selectedWowDirectory();
        if (wowDirectory == null) {
            tabs.getSelectionModel().select(1);
            log("Select your WoW folder first.");
            return;
        }

        launchAfterSync = launchWhenDone;
        patchVerified = false;
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
            if (launchAfterSync) {
                launchGame();
            }
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
            tabs.getSelectionModel().select(1);
            log("Select your WoW folder first.");
            return;
        }
        try {
            LaunchProfile profile = LaunchProfile.fromId(config.launchProfile());
            gameLauncher.launch(wowDirectory, profile.arguments());
            log("Launching Wow.exe with profile: " + profile.label());
        } catch (Exception exc) {
            log("Launch failed: " + exc.getMessage());
        }
    }

    private Path selectedWowDirectory() {
        return config.wowDirectory().orElse(null);
    }

    private void startRealmStatusRefresh() {
        refreshRealmStatus();
        realmStatusTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(30), event -> refreshRealmStatus()));
        realmStatusTimeline.setCycleCount(Timeline.INDEFINITE);
        realmStatusTimeline.play();
    }

    private void refreshRealmStatus() {
        if (realmStatusLabel == null) {
            return;
        }
        Task<RealmStatus> task = new Task<>() {
            @Override
            protected RealmStatus call() throws Exception {
                return realmStatusClient.fetch(defaults.realmStatusUrl());
            }
        };
        task.setOnSucceeded(event -> applyRealmStatus(task.getValue()));
        task.setOnFailed(event -> {
            RealmStatus fallback = new RealmStatus(
                    "error",
                    false,
                    "Apocalipse",
                    null,
                    null,
                    task.getException() == null ? "unknown error" : task.getException().getMessage()
            );
            applyRealmStatus(fallback);
            log("Realm status unavailable: " + fallback.error());
        });

        Thread thread = new Thread(task, "realm-status-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyRealmStatus(RealmStatus status) {
        realmNameLabel.setText(blankFallback(status.realmName(), "Apocalipse"));
        playersOnlineLabel.setText(numberFallback(status.playersOnline()));
        totalAccountsLabel.setText(numberFallback(status.totalAccounts()));

        String effectiveStatus = status.effectiveStatus();
        setStatusClass(realmStatusDot, effectiveStatus);
        setStatusClass(realmStatusLabel, effectiveStatus);

        realmStatusLabel.setText(switch (effectiveStatus) {
            case "online" -> "Online";
            case "starting" -> "Starting";
            case "offline" -> "Offline";
            default -> "Unavailable";
        });
        realmUpdatedLabel.setText("Last check: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private void setStatusClass(javafx.scene.Node node, String status) {
        node.getStyleClass().removeAll("status-online", "status-starting", "status-offline", "status-error", "status-unknown");
        node.getStyleClass().add(switch (status) {
            case "online" -> "status-online";
            case "starting" -> "status-starting";
            case "offline" -> "status-offline";
            case "error" -> "status-error";
            default -> "status-unknown";
        });
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String numberFallback(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private void saveConfig() {
        try {
            config.save();
        } catch (Exception exc) {
            log("Could not save launcher settings: " + exc.getMessage());
        }
    }

    private void setWorking(boolean working) {
        updateAndPlayButton.setDisable(working);
        updateAndPlayButton.setText(working ? "Updating..." : "Update & Play");
        launchOptionsButton.setDisable(working);
        browseButton.setDisable(working);
        if (launchProfileCombo != null) {
            launchProfileCombo.setDisable(working);
        }
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

    private enum LaunchProfile {
        DEFAULT("default", "Default", List.of()),
        WINDOWED("windowed", "Windowed", List.of("-windowed")),
        FULLSCREEN("fullscreen", "Fullscreen", List.of("-fullscreen")),
        NO_SOUND("nosound", "No sound", List.of("-nosound")),
        CONSOLE("console", "Console enabled", List.of("-console")),
        WINDOWED_CONSOLE("windowed-console", "Windowed + console", List.of("-windowed", "-console"));

        private final String id;
        private final String label;
        private final List<String> arguments;

        LaunchProfile(String id, String label, List<String> arguments) {
            this.id = id;
            this.label = label;
            this.arguments = arguments;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        List<String> arguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return label;
        }

        static LaunchProfile fromId(String id) {
            for (LaunchProfile profile : values()) {
                if (profile.id.equals(id)) {
                    return profile;
                }
            }
            return DEFAULT;
        }
    }
}

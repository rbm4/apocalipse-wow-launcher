package cloud.apocalipse.launcher.ui.zomboid;

import cloud.apocalipse.launcher.games.zomboid.AgentReleaseCache;
import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher;
import cloud.apocalipse.launcher.games.zomboid.SteamInstallationDetector;
import cloud.apocalipse.launcher.games.zomboid.config.ProjectZomboidConfig;
import cloud.apocalipse.launcher.games.zomboid.config.ZomboidOptimization;
import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;
import cloud.apocalipse.launcher.ui.HomeHero;
import cloud.apocalipse.launcher.ui.wow.AppLanguage;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;

public final class ProjectZomboidLauncherView {
    private static final String WEBSITE_URL = "https://plataforma.apocalipse.cloud/";
    private static final String TITLE_ICON = "/zombified.png";
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppLanguage language;
    private final ResourceBundle agentMessages;
    private final ProjectZomboidConfig config;
    private final SteamInstallationDetector installationDetector;
    private final ProjectZomboidLauncher launcher;
    private final Consumer<String> openInBrowser;

    private Label gameDirectoryLabel;
    private ZomboidMemoryOptions memoryOptions;
    private Button playButton;
    private Label launchStatus;
    private TextArea logArea;
    private Hyperlink detailsLink;
    private boolean launchingOrPlaying;
    private boolean startingGame;

    public boolean isUpdateBlocked() {
        return startingGame || savingMemorySettings();
    }
    private TabPane tabs;

    public ProjectZomboidLauncherView(AppLanguage language, ProjectZomboidConfig config,
                                    SteamInstallationDetector installationDetector,
                                    ProjectZomboidLauncher launcher, Consumer<String> openInBrowser) {
        this.language = language;
        this.agentMessages = ResourceBundle.getBundle("zomboid-agent", Locale.forLanguageTag(language.code()));
        this.config = config;
        this.installationDetector = installationDetector;
        this.launcher = launcher;
        this.openInBrowser = openInBrowser;
    }

    public Node createContent(Stage stage) {
        validateSavedDirectory();
        tabs = new TabPane();
        tabs.getStyleClass().add("main-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab(language.text("tab.zomboid.home"), homeContent()),
                new Tab(language.text("tab.options"), optionsContent(stage)),
                new Tab(language.text("tab.website"), websiteContent()));

        Image backgroundImage = new Image(getClass().getResourceAsStream("/loginhospital.png"));
        ImageView background = new ImageView(backgroundImage);
        background.setManaged(false);
        background.setMouseTransparent(true);
        background.setPreserveRatio(false);
        background.setSmooth(true);
        background.setOpacity(0.55);
        background.getStyleClass().add("banner");

        StackPane surface = new StackPane(background, tabs);
        background.fitWidthProperty().bind(surface.widthProperty());
        background.fitHeightProperty().bind(surface.heightProperty());
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(surface.widthProperty());
        clip.heightProperty().bind(surface.heightProperty());
        surface.setClip(clip);

        if (configuredInstallation().isEmpty()) {
            Platform.runLater(() -> tabs.getSelectionModel().select(1));
        }
        return surface;
    }

    private Node homeContent() {
        HBox titleRow = HomeHero.titleRow(language.text("tab.zomboid.home"), TITLE_ICON);
        Label subtitle = new Label(language.text("home.zomboidSubtitle"));
        subtitle.getStyleClass().add("hero-subtitle");
        subtitle.setWrapText(true);

        playButton = new Button(language.text("button.zomboidPlay"));
        playButton.getStyleClass().add("update-button");
        playButton.setOnAction(event -> launchGame());
        HBox actions = new HBox(playButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label seasonBadge = new Label(language.text("home.zomboidBadge"));
        seasonBadge.getStyleClass().add("release-badge");

        VBox heroCopy = new VBox(titleRow, subtitle, actions, seasonBadge);
        heroCopy.getStyleClass().add("hero-copy");
        heroCopy.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroCopy, Priority.ALWAYS);
        HBox hero = new HBox(24, heroCopy, serverStatusPanel());
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("hero-panel");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox content = new VBox(hero, spacer, launchDetails());
        content.getStyleClass().addAll("launcher-page", "home-page");
        refreshLaunchAvailability();
        log(language.text("log.launcherReady"));
        return content;
    }

    private Node serverStatusPanel() {
        Label title = new Label(language.text("server.zomboidStatus"));
        title.getStyleClass().add("panel-title");
        WebView widget = new WebView();
        widget.setContextMenuEnabled(false);
        WebEngine engine = widget.getEngine();
        applyWidgetIcon(engine);
        engine.load(getClass().getResource("/zomboid-server-status.html").toExternalForm());

        return HomeHero.statusPanel(title, widget);
    }

    private void applyWidgetIcon(WebEngine engine) {
        // The widget's WebP logo does not render in JavaFX WebView. A user stylesheet also applies inside the iframe.
        try (var icon = getClass().getResourceAsStream(TITLE_ICON)) {
            String image = Base64.getEncoder().encodeToString(icon.readAllBytes());
            String css = """
                    #header-orange img[src="https://trackyserver.com/pages/logos/project-zomboid-server-small.webp"] {
                        content: url("data:image/png;base64,%s");
                    }
                    """.formatted(image);
            String encodedCss = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
            engine.setUserStyleSheetLocation("data:text/css;charset=utf-8;base64," + encodedCss);
        } catch (IOException failure) {
            StartupLog.write("Could not load the Project Zomboid widget icon.", failure);
        }
    }

    private Node launchDetails() {
        launchStatus = new Label();
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("details-log");

        detailsLink = new Hyperlink();
        detailsLink.setPadding(Insets.EMPTY);
        detailsLink.setOnAction(event -> setDetailsVisible(!logArea.isVisible()));
        setDetailsVisible(false);

        VBox panel = new VBox(8, launchStatus, detailsLink, logArea);
        panel.getStyleClass().add("glass-panel");
        return panel;
    }

    private void setDetailsVisible(boolean visible) {
        logArea.setVisible(visible);
        logArea.setManaged(visible);
        detailsLink.setText(language.text(visible ? "details.hide" : "details.show"));
    }

    private Optional<Path> configuredInstallation() {
        return config.gameDirectory()
                .filter(directory -> ProjectZomboidLauncher.findExecutable(directory).isPresent());
    }

    private void refreshLaunchAvailability() {
        boolean configured = configuredInstallation().isPresent();
        playButton.setDisable(launchingOrPlaying || !configured || savingMemorySettings());
        if (!launchingOrPlaying) {
            playButton.setText(language.text("button.zomboidPlay"));
            launchStatus.setText(language.text(configured ? "status.readyToPlay" : "status.zomboidSelectFolder"));
        }
    }

    private void launchGame() {
        Optional<Path> installation = config.gameDirectory();
        if (installation.isEmpty() || launchingOrPlaying || savingMemorySettings()) {
            return;
        }
        Path directory = installation.get();
        Set<ZomboidOptimization> optimizations = config.optimizations();
        launchingOrPlaying = true;
        startingGame = true;
        playButton.setDisable(true);
        launchStatus.setText(language.text("status.zomboidStarting"));
        log(language.text("status.zomboidStarting") + " " + directory);

        Task<ProjectZomboidLauncher.Launch> launch = new Task<>() {
            @Override
            protected ProjectZomboidLauncher.Launch call() throws IOException, InterruptedException {
                return launcher.launch(directory, optimizations,
                        status -> Platform.runLater(() -> showAgentStatus(status)));
            }
        };
        launch.setOnSucceeded(event -> {
            startingGame = false;
            playButton.setText(language.text("button.playing"));
            launchStatus.setText(language.text("status.zomboidRunning"));
            log(launchStatus.getText());
            var game = launch.getValue();
            game.consoleLog().ifPresent(path -> log(agentMessages.getString("status.console") + " " + path));
            game.process().onExit().thenAccept(process ->
                    Platform.runLater(() -> gameExited(process.exitValue())));
        });
        launch.setOnFailed(event -> {
            startingGame = false;
            launchingOrPlaying = false;
            refreshLaunchAvailability();
            Throwable failure = launch.getException();
            StartupLog.write("Could not start Project Zomboid.", failure);
            if (failure instanceof ProjectZomboidLauncher.SteamNotRunningException) {
                launchStatus.setText(agentMessages.getString("status.steamRequired"));
                log(launchStatus.getText());
            } else {
                launchStatus.setText(language.text("log.launchFailed"));
                log(launchStatus.getText() + ": " + failure.getMessage());
            }
            setDetailsVisible(true);
        });
        Thread worker = new Thread(launch, "zomboid-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean savingMemorySettings() {
        return memoryOptions != null && memoryOptions.savingProperty().get();
    }

    private void showAgentStatus(AgentReleaseCache.Status status) {
        String key = switch (status) {
            case CHECKING -> "status.checking";
            case DOWNLOADING -> "status.downloading";
            case VERIFIED -> "status.verified";
        };
        String message = agentMessages.getString(key);
        launchStatus.setText(message);
        log(message);
    }

    private void gameExited(int exitCode) {
        launchingOrPlaying = false;
        refreshLaunchAvailability();
        if (exitCode == 0) {
            log(language.text("log.zomboidClosed"));
        } else {
            launchStatus.setText(language.text("status.zomboidExitError").replace("{code}", Integer.toString(exitCode)));
            log(launchStatus.getText());
            setDetailsVisible(true);
        }
    }

    private void log(String message) {
        logArea.appendText("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + message + System.lineSeparator());
    }

    private Node optionsContent(Stage stage) {
        Label title = new Label(language.text("options.title"));
        title.getStyleClass().add("hero-title");
        Label description = new Label(language.text("options.zomboidDescription"));
        description.getStyleClass().add("hero-subtitle");
        description.setWrapText(true);

        Label folderTitle = new Label(language.text("options.gameDirectory"));
        folderTitle.getStyleClass().add("section-label");
        folderTitle.setMinWidth(Region.USE_PREF_SIZE);
        gameDirectoryLabel = new Label();
        gameDirectoryLabel.setWrapText(true);
        gameDirectoryLabel.setMinWidth(0);
        refreshGameDirectoryLabel();
        Button browseButton = new Button(language.text("button.chooseFolder"));
        browseButton.getStyleClass().add("secondary-button");
        browseButton.setMinWidth(Region.USE_PREF_SIZE);
        browseButton.setOnAction(event -> chooseGameDirectory(stage));
        Button detectButton = new Button(language.text("button.detectZomboidFolder"));
        detectButton.getStyleClass().add("secondary-button");
        detectButton.setMinWidth(Region.USE_PREF_SIZE);
        detectButton.setOnAction(event -> detectGameDirectory(stage, browseButton, detectButton));

        GridPane form = new GridPane();
        form.getStyleClass().addAll("glass-panel", "options-form");
        form.add(folderTitle, 0, 0);
        form.add(gameDirectoryLabel, 1, 0);
        form.add(browseButton, 2, 0);
        form.add(detectButton, 3, 0);
        GridPane.setHgrow(gameDirectoryLabel, Priority.ALWAYS);

        memoryOptions = new ZomboidMemoryOptions(agentMessages);
        memoryOptions.setInstallation(config.gameDirectory().orElse(null));
        memoryOptions.savingProperty().addListener((observable, before, after) -> refreshLaunchAvailability());
        VBox content = new VBox(title, description, form, memoryOptions, optimizationOptions(stage));
        content.getStyleClass().add("launcher-page");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("zomboid-options-scroll");
        return scroll;
    }

    private Node optimizationOptions(Stage stage) {
        Label heading = new Label(agentMessages.getString("options.title"));
        heading.getStyleClass().add("section-title");
        Label description = new Label(agentMessages.getString("options.description"));
        description.getStyleClass().add("muted");
        description.setWrapText(true);

        Set<ZomboidOptimization> selected = config.optimizations();
        GridPane groups = twoColumnGrid();
        VBox left = new VBox(12,
                optimizationGroup(stage, ZomboidOptimization.Group.SIMULATION, selected),
                optimizationGroup(stage, ZomboidOptimization.Group.SCRIPTING, selected));
        VBox right = new VBox(12,
                optimizationGroup(stage, ZomboidOptimization.Group.WORLD, selected),
                optimizationGroup(stage, ZomboidOptimization.Group.NETWORK, selected));
        groups.addRow(0, left, right);
        return new VBox(10, heading, description,
                optimizationGroup(stage, ZomboidOptimization.Group.HIGH_IMPACT, selected), groups,
                optimizationGroup(stage, ZomboidOptimization.Group.SERVER, selected));
    }

    private Node optimizationGroup(Stage stage, ZomboidOptimization.Group group,
                                   Set<ZomboidOptimization> selected) {
        Label title = new Label(agentMessages.getString("group." + group.name()));
        title.getStyleClass().add("panel-title");
        int columns = group == ZomboidOptimization.Group.SERVER ? 2 : 1;
        GridPane options = columns == 2 ? twoColumnGrid() : new GridPane();
        options.setVgap(8);
        int index = 0;
        for (ZomboidOptimization optimization : ZomboidOptimization.values()) {
            if (optimization.group() == group) {
                options.add(optimizationRow(stage, optimization, selected.contains(optimization)),
                        index % columns, index / columns);
                index++;
            }
        }
        VBox panel = new VBox(10, title);
        if (group == ZomboidOptimization.Group.HIGH_IMPACT) {
            Label description = new Label(agentMessages.getString("group.HIGH_IMPACT.description"));
            description.getStyleClass().add("muted");
            description.setWrapText(true);
            panel.getChildren().add(description);
        }
        panel.getChildren().add(options);
        panel.getStyleClass().add("glass-panel");
        return panel;
    }

    private Node optimizationRow(Stage stage, ZomboidOptimization optimization, boolean selected) {
        String key = optimization.name();
        CheckBox checkbox = new CheckBox(agentMessages.getString(key + ".label"));
        checkbox.getStyleClass().add("zomboid-optimization");
        checkbox.setSelected(selected);
        checkbox.setDisable(!optimization.availableOnClient());
        checkbox.setWrapText(true);
        checkbox.setMinWidth(0);
        checkbox.setOnAction(event -> {
            try {
                config.saveOptimization(optimization, checkbox.isSelected());
            } catch (IOException ex) {
                checkbox.setSelected(!checkbox.isSelected());
                StartupLog.write("Could not save Project Zomboid optimization settings.", ex);
                showSaveError(stage);
            }
        });
        Tooltip tooltip = new Tooltip(agentMessages.getString(key + ".tooltip"));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(340);
        tooltip.setShowDelay(Duration.millis(300));
        StackPane item = new StackPane(checkbox);
        item.setAlignment(Pos.CENTER_LEFT);
        if (optimization.availableOnClient()) {
            checkbox.setTooltip(tooltip);
        } else {
            checkbox.setMouseTransparent(true);
            Tooltip.install(item, tooltip);
        }

        String linkDescription = agentMessages.getString("options.documentation").formatted(checkbox.getText());
        Hyperlink documentation = new Hyperlink("i");
        documentation.getStyleClass().add("zomboid-optimization-info");
        documentation.setAccessibleText(linkDescription);
        documentation.setTooltip(new Tooltip(linkDescription));
        documentation.setOnAction(event -> openInBrowser.accept(optimization.documentationUrl()));

        HBox row = new HBox(8, item, documentation);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private GridPane twoColumnGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        grid.getColumnConstraints().addAll(left, right);
        return grid;
    }

    private WebView websiteContent() {
        WebView webView = new WebView();
        webView.getEngine().load(WEBSITE_URL);
        webView.setContextMenuEnabled(false);
        return webView;
    }

    private void chooseGameDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(language.text("dialog.selectZomboidFolder"));
        config.gameDirectory()
                .filter(Files::isDirectory)
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);

        File selected = chooser.showDialog(stage);
        if (selected != null) {
            saveGameDirectory(stage, selected.toPath());
        }
    }

    private void detectGameDirectory(Stage stage, Button browseButton, Button detectButton) {
        Task<Optional<Path>> detection = new Task<>() {
            @Override
            protected Optional<Path> call() throws IOException, InterruptedException {
                return installationDetector.findInstallation();
            }
        };
        var pending = detection.stateProperty().map(state -> switch (state) {
            case READY, SCHEDULED, RUNNING -> true;
            default -> false;
        });
        browseButton.disableProperty().bind(pending);
        detectButton.disableProperty().bind(pending);
        detection.setOnSucceeded(event -> detection.getValue().ifPresentOrElse(
                directory -> saveGameDirectory(stage, directory),
                () -> showDetectionMessage(stage, Alert.AlertType.INFORMATION, "options.zomboidDetectionNotFound")));
        detection.setOnFailed(event -> {
            StartupLog.write("Could not detect a Project Zomboid Steam installation.", detection.getException());
            showDetectionMessage(stage, Alert.AlertType.WARNING, "options.zomboidDetectionFailed");
        });
        Thread worker = new Thread(detection, "zomboid-steam-detection");
        worker.setDaemon(true);
        worker.start();
    }

    private void saveGameDirectory(Stage stage, Path directory) {
        if (ProjectZomboidLauncher.findExecutable(directory).isEmpty()) {
            showDetectionMessage(stage, Alert.AlertType.WARNING, "status.zomboidSelectFolder");
            return;
        }
        Path previousDirectory = config.gameDirectory().orElse(null);
        config.setGameDirectory(directory);
        try {
            config.save();
            refreshGameDirectoryLabel();
            memoryOptions.setInstallation(config.gameDirectory().orElse(null));
            refreshLaunchAvailability();
        } catch (IOException exc) {
            if (previousDirectory == null) {
                config.clearGameDirectory();
            } else {
                config.setGameDirectory(previousDirectory);
            }
            StartupLog.write("Could not save Project Zomboid directory.", exc);
            showSaveError(stage);
        }
    }

    private void showDetectionMessage(Stage stage, Alert.AlertType type, String messageKey) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(language.text("tab.zomboid.home"));
        alert.setHeaderText(null);
        alert.setContentText(language.text(messageKey));
        alert.showAndWait();
    }

    private void validateSavedDirectory() {
        config.gameDirectory().ifPresent(path -> {
            if (!Files.isDirectory(path)) {
                config.clearGameDirectory();
                try {
                    config.save();
                } catch (IOException exc) {
                    StartupLog.write("Could not clear the missing Project Zomboid directory.", exc);
                }
            }
        });
    }

    private void refreshGameDirectoryLabel() {
        gameDirectoryLabel.setText(config.gameDirectory()
                .map(Path::toString)
                .orElse(language.text("options.notConfigured")));
    }

    private void showSaveError(Stage stage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(language.text("window.title"));
        alert.setHeaderText(language.text("selection.saveFailedTitle"));
        alert.setContentText(language.text("selection.saveFailedMessage"));
        alert.showAndWait();
    }
}

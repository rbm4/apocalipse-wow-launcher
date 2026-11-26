package cloud.apocalipse.launcher.ui.wow;

import cloud.apocalipse.launcher.core.OperationProgress;
import cloud.apocalipse.launcher.games.wow.WowLauncher;
import cloud.apocalipse.launcher.games.wow.community.NewsPost;
import cloud.apocalipse.launcher.games.wow.community.RealmStatus;
import cloud.apocalipse.launcher.games.wow.config.LauncherConfig;
import cloud.apocalipse.launcher.games.wow.patch.PatchManifest;
import cloud.apocalipse.launcher.infrastructure.diagnostics.StartupLog;
import cloud.apocalipse.launcher.ui.HomeHero;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class WowLauncherView {
    private static final String WEBSITE_URL = "https://wow.apocalipse.cloud/";
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LauncherConfig config;
    private final WowLauncher wow;
    private final Runnable languageChanged;
    private final WowMessages messages = new WowMessages(this::t);
    private final List<NewsCardSlot> newsCards = new ArrayList<>();

    private Label wowPathLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Label heroSubtitleLabel;
    private Label releaseBadgeLabel;
    private Label realmHeadlineLabel;
    private Label realmNameLabel;
    private Label realmStatusLabel;
    private Label realmStatusTitleLabel;
    private Label playersOnlineLabel;
    private Label playersOnlineTitleLabel;
    private Label totalAccountsLabel;
    private Label totalAccountsTitleLabel;
    private Label realmUpdatedLabel;
    private Label newsHeaderLabel;
    private Label newsStatusLabel;
    private Label newsClientTitleLabel;
    private Label newsClientBodyLabel;
    private Label newsBattlegroundTitleLabel;
    private Label newsBattlegroundBodyLabel;
    private Label newsComingTitleLabel;
    private Label newsComingBodyLabel;
    private Circle realmStatusDot;
    private TextArea logArea;
    private Hyperlink detailsLink;
    private Button updateAndPlayButton;
    private Button installClientButton;
    private Button cancelClientDownloadButton;
    private MenuButton launchOptionsButton;
    private Button browseButton;
    private ComboBox<LaunchProfile> launchProfileCombo;
    private ComboBox<AppLanguage> languageCombo;
    private TabPane tabs;
    private Tab homeTab;
    private WowOptionsPane optionsPane;
    private Tab optionsTab;
    private Tab websiteTab;
    private WebView websiteWebView;
    private Timeline realmStatusTimeline;
    private Task<Path> clientInstallTask;
    private AppLanguage language;
    private boolean patchVerified;
    private boolean launchAfterSync;
    private boolean working;
    private boolean gameRunning;
    private boolean clientInstallInProgress;

    public boolean isUpdateBlocked() {
        return working || clientInstallInProgress;
    }

    public WowLauncherView(LauncherConfig config, WowLauncher wow) {
        this(config, wow, () -> {
        });
    }

    public WowLauncherView(LauncherConfig config, WowLauncher wow, Runnable languageChanged) {
        this.config = config;
        this.wow = wow;
        this.languageChanged = languageChanged;
    }

    public Node createContent(Stage stage) {
        language = AppLanguage.fromCode(config.language());
        wowPathLabel = new Label();
        refreshWowPathLabel();

        browseButton = new Button(t("button.chooseFolder"));
        browseButton.getStyleClass().add("secondary-button");
        browseButton.setOnAction(event -> chooseWowFolder(stage));

        updateAndPlayButton = new Button(t("button.updatePlay"));
        updateAndPlayButton.setDefaultButton(true);
        updateAndPlayButton.getStyleClass().add("update-button");
        updateAndPlayButton.setOnAction(event -> synchronize(true));

        installClientButton = new Button(t("button.downloadClient"));
        installClientButton.getStyleClass().add("secondary-button");
        installClientButton.setOnAction(event -> installClientAndPlay(stage));

        launchOptionsButton = new MenuButton(t("button.launchOptions"));
        launchOptionsButton.getStyleClass().add("launch-options-button");
        configureLaunchOptionsMenu();

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label(t("status.ready"));

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

        detailsLink = new Hyperlink(t("details.show"));
        detailsLink.setPadding(Insets.EMPTY);
        detailsLink.setOnAction(event -> toggleDetails());

        cancelClientDownloadButton = new Button(t("button.cancelDownload"));
        cancelClientDownloadButton.getStyleClass().addAll("secondary-button", "cancel-button");
        cancelClientDownloadButton.setVisible(false);
        cancelClientDownloadButton.setManaged(false);
        cancelClientDownloadButton.setOnAction(event -> cancelClientDownload());

        launchProfileCombo = new ComboBox<>();
        launchProfileCombo.getStyleClass().add("game-launch-profile");
        launchProfileCombo.getItems().addAll(LaunchProfile.values());
        launchProfileCombo.setValue(LaunchProfile.fromId(config.launchProfile()));
        configureLaunchProfileComboDisplay();
        launchProfileCombo.setOnAction(event -> {
            config.setLaunchProfile(launchProfileCombo.getValue().id());
            saveConfig();
            configureLaunchOptionsMenu();
        });

        languageCombo = new ComboBox<>();
        languageCombo.getItems().addAll(AppLanguage.values());
        languageCombo.setValue(language);
        languageCombo.setOnAction(event -> {
            language = languageCombo.getValue();
            config.setLanguage(language.code());
            saveConfig();
            refreshTexts();
            languageChanged.run();
        });

        tabs = new TabPane();
        tabs.getStyleClass().add("main-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        homeTab = new Tab(t("tab.home"), homeContent());
        optionsPane = new WowOptionsPane(this::t, wowPathLabel, browseButton, launchProfileCombo, languageCombo,
                () -> installClientAndPlay(stage), () -> chooseWowFolder(stage));
        refreshSetupPanelVisibility();
        optionsTab = new Tab(t("tab.options"), optionsPane);
        websiteTab = new Tab(t("tab.website"), websiteContent());
        tabs.getTabs().addAll(homeTab, optionsTab, websiteTab);

        StackPane tabSurface = tabSurface();
        log(t("log.launcherReady"));
        StartupLog.write("Launcher UI initialized.", null);
        validateConfiguredWowDirectory();
        startRealmStatusRefresh();
        refreshNews();
        if (config.wowDirectory().isEmpty()) {
            Platform.runLater(() -> {
                try {
                    StartupLog.write("Showing client setup choices.", null);
                    tabs.getSelectionModel().select(1);
                    stage.toFront();
                } catch (RuntimeException exc) {
                    StartupLog.write("Client setup prompt failed.", exc);
                    throw exc;
                }
            });
        }
        return tabSurface;
    }

    private void validateConfiguredWowDirectory() {
        config.wowDirectory().ifPresent(wowDirectory -> {
            if (!wow.hasClientExecutable(wowDirectory)) {
                config.clearWowDirectory();
                patchVerified = false;
                refreshWowPathLabel();
                refreshSetupPanelVisibility();
                saveConfig();
                log(t("log.savedWowFolderMissing"));
            }
        });
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

    private WebView websiteContent() {
        websiteWebView = new WebView();
        websiteWebView.getEngine().load(WEBSITE_URL);
        websiteWebView.setContextMenuEnabled(false);
        return websiteWebView;
    }

    private AnchorPane homeContent() {
        HBox titleRow = HomeHero.titleRow("Apocalipse WoW", "/Apocalipse_wow_icon.png");

        heroSubtitleLabel = new Label(t("home.subtitle"));
        heroSubtitleLabel.getStyleClass().add("hero-subtitle");

        VBox heroText = new VBox(titleRow, heroSubtitleLabel, actionRow(), releaseBadge());
        heroText.setAlignment(Pos.CENTER_LEFT);
        heroText.getStyleClass().add("hero-copy");
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
        HBox row = new HBox(10, updateAndPlayButton, launchOptionsButton, installClientButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label releaseBadge() {
        releaseBadgeLabel = new Label(t("home.badge"));
        releaseBadgeLabel.getStyleClass().add("release-badge");
        return releaseBadgeLabel;
    }

    private VBox sidePanel() {
        realmHeadlineLabel = new Label(t("realm.title"));
        realmHeadlineLabel.getStyleClass().add("panel-title");

        realmStatusDot = new Circle(5);
        realmStatusDot.getStyleClass().addAll("realm-status-dot", "status-unknown");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, realmHeadlineLabel, spacer, realmStatusDot);
        header.setAlignment(Pos.CENTER_LEFT);

        realmNameLabel = new Label("Apocalipse");
        realmNameLabel.getStyleClass().add("realm-name");
        realmStatusLabel = new Label(t("realm.checking"));
        realmStatusLabel.getStyleClass().addAll("realm-status-value", "status-unknown");
        playersOnlineLabel = new Label("-");
        playersOnlineLabel.getStyleClass().add("realm-stat-value");
        totalAccountsLabel = new Label("-");
        totalAccountsLabel.getStyleClass().add("realm-stat-value");
        realmUpdatedLabel = new Label(t("realm.awaiting"));
        realmUpdatedLabel.getStyleClass().add("muted");

        VBox status = new VBox(10,
                realmNameLabel,
                realmStatusRow("realm.status", realmStatusLabel),
                realmStatusRow("realm.players", playersOnlineLabel),
                realmStatusRow("realm.accounts", totalAccountsLabel),
                realmUpdatedLabel);
        return HomeHero.statusPanel(header, status);
    }

    private HBox realmStatusRow(String key, Label value) {
        Label title = new Label(t(key));
        title.getStyleClass().add("realm-stat-label");
        if ("realm.status".equals(key)) {
            realmStatusTitleLabel = title;
        } else if ("realm.players".equals(key)) {
            playersOnlineTitleLabel = title;
        } else if ("realm.accounts".equals(key)) {
            totalAccountsTitleLabel = title;
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, title, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("realm-status-row");
        return row;
    }

    private VBox progressBlock() {
        HBox detailsRow = new HBox(12, detailsLink, cancelClientDownloadButton);
        detailsRow.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, statusLabel, progressBar, detailsRow, logArea);
        box.getStyleClass().add("glass-panel");
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(progressBar, Priority.NEVER);
        return box;
    }

    private VBox newsSection() {
        newsHeaderLabel = new Label(t("news.title"));
        newsHeaderLabel.getStyleClass().add("section-title");
        newsStatusLabel = new Label(t("news.loading"));
        newsStatusLabel.getStyleClass().add("news-status");
        VBox firstCard = newsCard();
        VBox secondCard = newsCard();
        VBox thirdCard = newsCard();
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
        VBox section = new VBox(10, newsHeaderLabel, newsStatusLabel, cards);
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private VBox newsCard() {
        Label titleLabel = new Label();
        titleLabel.getStyleClass().add("news-title");
        Label bodyLabel = new Label();
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("news-body");
        if (newsClientTitleLabel == null) {
            newsClientTitleLabel = titleLabel;
            newsClientBodyLabel = bodyLabel;
        } else if (newsBattlegroundTitleLabel == null) {
            newsBattlegroundTitleLabel = titleLabel;
            newsBattlegroundBodyLabel = bodyLabel;
        } else if (newsComingTitleLabel == null) {
            newsComingTitleLabel = titleLabel;
            newsComingBodyLabel = bodyLabel;
        }
        ImageView imageView = new ImageView();
        imageView.setFitWidth(92);
        imageView.setFitHeight(62);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setVisible(false);
        imageView.setManaged(false);
        imageView.getStyleClass().add("news-image");

        VBox text = new VBox(6, titleLabel, bodyLabel);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox content = new HBox(10, imageView, text);
        content.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(content);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        card.setMinHeight(92);
        card.setPrefHeight(92);
        card.setMaxHeight(92);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getStyleClass().add("news-card");
        card.setVisible(false);
        card.setManaged(false);
        newsCards.add(new NewsCardSlot(card, titleLabel, bodyLabel, imageView));
        return card;
    }
    
    private void configureLaunchOptionsMenu() {
        if (launchOptionsButton == null) {
            return;
        }
        LaunchProfile selected = LaunchProfile.fromId(config.launchProfile());
        launchOptionsButton.setText(selected.label(language));
        launchOptionsButton.getItems().clear();
        for (LaunchProfile profile : LaunchProfile.values()) {
            MenuItem item = new MenuItem(profile.label(language));
            item.setOnAction(event -> {
                config.setLaunchProfile(profile.id());
                if (launchProfileCombo != null) {
                    launchProfileCombo.setValue(profile);
                }
                saveConfig();
                launchOptionsButton.setText(profile.label(language));
                log(t("log.launchProfileSelected") + ": " + profile.label(language));
            });
            launchOptionsButton.getItems().add(item);
        }
    }

    private void chooseWowFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("dialog.selectWowFolder"));
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
        refreshSetupPanelVisibility();
        patchVerified = false;
        updateAndPlayButton.setText(t("button.updatePlay"));
        saveConfig();
        log(t("log.selectedWowFolder") + ": " + wowDirectory);
    }

    private void synchronize(boolean launchWhenDone) {
        if (launchWhenDone && wow.isGameRunning()) {
            setGameRunning(true);
            log(t("log.gameAlreadyRunning"));
            return;
        }
        Path wowDirectory = selectedWowDirectory();
        if (wowDirectory == null) {
            tabs.getSelectionModel().select(1);
            log(t("log.selectWowFolderFirst"));
            return;
        }

        launchAfterSync = launchWhenDone;
        patchVerified = false;
        setWorking(true);
        Task<PatchManifest> task = new Task<>() {
            @Override
            protected PatchManifest call() throws Exception {
                return wow.synchronize(wowDirectory, new OperationProgress() {
                    @Override
                    public void message(String message) {
                        String translatedMessage = messages.translatePatchMessage(message);
                        updateMessage(translatedMessage);
                        Platform.runLater(() -> log(translatedMessage));
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
            statusLabel.setText(t("status.readyToPlay"));
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
            statusLabel.setText(t("status.patchFailed"));
            progressBar.setProgress(0);
            setWorking(false);
            Throwable error = task.getException();
            log(t("log.patchFailed") + ": " + (error == null ? t("error.unknown") : messages.translateErrorMessage(error.getMessage())));
        });

        Thread thread = new Thread(task, "patch-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private void installClientAndPlay(Stage stage) {
        if (wow.isGameRunning()) {
            setGameRunning(true);
            log(t("log.gameAlreadyRunning"));
            return;
        }

        Path installDirectory = chooseClientInstallFolder(stage);
        if (installDirectory == null) {
            return;
        }

        tabs.getSelectionModel().select(homeTab);
        patchVerified = false;
        setClientInstallInProgress(true);
        setWorking(true);
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return wow.installClient(installDirectory, new OperationProgress() {
                    @Override
                    public void message(String message) {
                        String translatedMessage = messages.translateClientInstallMessage(message);
                        updateMessage(translatedMessage);
                        Platform.runLater(() -> log(translatedMessage));
                    }

                    @Override
                    public void progress(double value) {
                        updateProgress(value, 1);
                    }

                    @Override
                    public void detail(String message) {
                        String translatedMessage = messages.translateClientInstallMessage(message);
                        Platform.runLater(() -> log(translatedMessage));
                    }
                });
            }
        };
        clientInstallTask = task;
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            Path wowDirectory = task.getValue();
            config.setWowDirectory(wowDirectory);
            refreshWowPathLabel();
            refreshSetupPanelVisibility();
            saveConfig();
            clientInstallTask = null;
            setClientInstallInProgress(false);
            setWorking(false);
            log(t("log.clientInstalled") + ": " + wowDirectory);
            synchronize(true);
        });
        task.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            boolean cancelled = wow.isClientInstallCancelled(task.getException());
            statusLabel.setText(cancelled ? t("status.clientDownloadCancelled") : t("status.clientInstallFailed"));
            clientInstallTask = null;
            setClientInstallInProgress(false);
            setWorking(false);
            Throwable error = task.getException();
            log(cancelled
                    ? t("log.clientDownloadCancelled")
                    : t("log.clientInstallFailed") + ": " + (error == null ? t("error.unknown") : error.getMessage()));
        });

        Thread thread = new Thread(task, "client-install");
        thread.setDaemon(true);
        thread.start();
    }

    private Path chooseClientInstallFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("dialog.selectClientInstallFolder"));
        Path initialDirectory = wow.defaultInstallDirectory();
        if (!Files.isDirectory(initialDirectory)) {
            initialDirectory = initialDirectory.getParent();
        }
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        File selected = chooser.showDialog(stage);
        if (selected == null) {
            return null;
        }
        Path selectedPath = selected.toPath().toAbsolutePath().normalize();
        try {
            WowLauncher.InstallSpace space = wow.checkInstallSpace(selectedPath);
            if (!space.isSufficient()) {
                String message = t("error.notEnoughDiskSpace")
                        .replace("{required}", formatDiskSize(space.requiredBytes()))
                        .replace("{available}", formatDiskSize(space.availableBytes()));
                statusLabel.setText(message);
                log(message);
                return null;
            }
        } catch (Exception exc) {
            log(t("log.clientInstallFailed") + ": " + exc.getMessage());
            return null;
        }
        return selectedPath;
    }

    private void cancelClientDownload() {
        if (!clientInstallInProgress) {
            return;
        }
        cancelClientDownloadButton.setDisable(true);
        wow.cancelClientInstall();
        log(t("log.clientDownloadCancelRequested"));
    }

    private void launchGame() {
        if (wow.isGameRunning()) {
            setGameRunning(true);
            log(t("log.gameAlreadyRunning"));
            return;
        }
        Path wowDirectory = selectedWowDirectory();
        if (wowDirectory == null) {
            tabs.getSelectionModel().select(1);
            log(t("log.selectWowFolderFirst"));
            return;
        }
        try {
            LaunchProfile profile = LaunchProfile.fromId(config.launchProfile());
            wow.launch(wowDirectory, profile.arguments(), () -> Platform.runLater(this::handleGameExited));
            setGameRunning(true);
            log(t("log.launchingProfile") + ": " + profile.label(language));
        } catch (Exception exc) {
            log(t("log.launchFailed") + ": " + messages.translateErrorMessage(exc.getMessage()));
        }
    }

    private void handleGameExited() {
        setGameRunning(false);
        statusLabel.setText(patchVerified ? t("status.readyToPlay") : t("status.ready"));
        log(t("log.gameClosed"));
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
                return wow.fetchRealmStatus();
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
                    task.getException() == null ? t("error.unknown") : task.getException().getMessage()
            );
            applyRealmStatus(fallback);
            log(t("log.realmUnavailable") + ": " + fallback.error());
        });

        Thread thread = new Thread(task, "realm-status-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshNews() {
        if (newsCards.isEmpty()) {
            return;
        }
        showNewsStatus(t("news.loading"));
        Task<List<NewsPost>> task = new Task<>() {
            @Override
            protected List<NewsPost> call() throws Exception {
                return wow.fetchNews(3);
            }
        };
        task.setOnSucceeded(event -> applyNews(task.getValue()));
        task.setOnFailed(event -> {
            showNewsStatus(t("news.unavailable"));
            hideNewsCards();
            log(t("log.newsUnavailable") + ": "
                    + (task.getException() == null ? t("error.unknown") : task.getException().getMessage()));
        });

        Thread thread = new Thread(task, "news-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyNews(List<NewsPost> posts) {
        if (posts == null || posts.isEmpty()) {
            hideNewsCards();
            showNewsStatus(t("news.empty"));
            return;
        }
        showNewsStatus("");
        for (int i = 0; i < newsCards.size(); i++) {
            NewsCardSlot slot = newsCards.get(i);
            if (i >= posts.size()) {
                slot.card().setVisible(false);
                slot.card().setManaged(false);
                continue;
            }
            NewsPost post = posts.get(i);
            slot.card().setVisible(true);
            slot.card().setManaged(true);
            slot.titleLabel().setText(post.title());
            slot.bodyLabel().setText(blankFallback(post.subtitle(), post.publishedAt()));
            applyNewsImage(slot.imageView(), post.imageUrl());
            slot.card().setOnMouseClicked(event -> openNewsPost(post.url()));
        }
    }

    private void hideNewsCards() {
        for (NewsCardSlot slot : newsCards) {
            slot.card().setVisible(false);
            slot.card().setManaged(false);
            slot.card().setOnMouseClicked(null);
            slot.titleLabel().setText("");
            slot.bodyLabel().setText("");
            applyNewsImage(slot.imageView(), "");
        }
    }

    private void showNewsStatus(String message) {
        if (newsStatusLabel == null) {
            return;
        }
        boolean visible = message != null && !message.isBlank();
        newsStatusLabel.setText(visible ? message : "");
        newsStatusLabel.setVisible(visible);
        newsStatusLabel.setManaged(visible);
    }

    private void applyNewsImage(ImageView imageView, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            imageView.setImage(null);
            imageView.setVisible(false);
            imageView.setManaged(false);
            return;
        }
        imageView.setImage(new Image(imageUrl, true));
        imageView.setVisible(true);
        imageView.setManaged(true);
    }

    private void openNewsPost(String url) {
        if (url == null || url.isBlank() || websiteWebView == null || websiteTab == null) {
            return;
        }
        websiteWebView.getEngine().load(url);
        tabs.getSelectionModel().select(websiteTab);
    }

    private void applyRealmStatus(RealmStatus status) {
        realmNameLabel.setText(blankFallback(status.realmName(), "Apocalipse"));
        playersOnlineLabel.setText(numberFallback(status.playersOnline()));
        totalAccountsLabel.setText(numberFallback(status.totalAccounts()));

        String effectiveStatus = status.effectiveStatus();
        setStatusClass(realmStatusDot, effectiveStatus);
        setStatusClass(realmStatusLabel, effectiveStatus);

        realmStatusLabel.setText(switch (effectiveStatus) {
            case "online" -> t("realm.online");
            case "starting" -> t("realm.starting");
            case "offline" -> t("realm.offline");
            default -> t("realm.unavailable");
        });
        realmUpdatedLabel.setText(t("realm.lastCheck") + ": " + LocalTime.now().format(LOG_TIME_FORMAT));
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
            log(t("log.saveFailed") + ": " + exc.getMessage());
        }
    }

    private void setWorking(boolean working) {
        this.working = working;
        updateActionControls();
    }

    private void setClientInstallInProgress(boolean inProgress) {
        clientInstallInProgress = inProgress;
        if (cancelClientDownloadButton != null) {
            cancelClientDownloadButton.setVisible(inProgress);
            cancelClientDownloadButton.setManaged(inProgress);
            cancelClientDownloadButton.setDisable(!inProgress);
        }
        updateActionControls();
    }

    private void setGameRunning(boolean running) {
        gameRunning = running && wow.isGameRunning();
        updateActionControls();
        if (gameRunning) {
            statusLabel.setText(t("status.gameRunning"));
        }
    }

    private void updateActionControls() {
        boolean clientRunning = gameRunning || wow.isGameRunning();
        updateAndPlayButton.setDisable(working || clientRunning);
        updateAndPlayButton.setText(working
                ? t("button.updating")
                : clientRunning ? t("button.playing") : t("button.updatePlay"));
        installClientButton.setDisable(working || clientRunning);
        if (optionsPane != null) {
            optionsPane.setSetupActionsDisabled(working || clientRunning);
        }
        if (cancelClientDownloadButton != null) {
            cancelClientDownloadButton.setDisable(!clientInstallInProgress);
        }
        launchOptionsButton.setDisable(working);
        browseButton.setDisable(working);
        if (launchProfileCombo != null) {
            launchProfileCombo.setDisable(working);
        }
    }

    private void log(String message) {
        String time = LocalTime.now().format(LOG_TIME_FORMAT);
        logArea.appendText("[" + time + "] " + message + System.lineSeparator());
    }

    private void toggleDetails() {
        boolean show = !logArea.isVisible();
        logArea.setVisible(show);
        logArea.setManaged(show);
        detailsLink.setText(show ? t("details.hide") : t("details.show"));
    }

    private void refreshWowPathLabel() {
        wowPathLabel.setText(config.wowDirectory()
                .map(Path::toString)
                .orElse(t("options.notConfigured")));
    }

    private void refreshTexts() {
        if (homeTab != null) {
            homeTab.setText(t("tab.home"));
        }
        if (optionsTab != null) {
            optionsTab.setText(t("tab.options"));
        }
        if (websiteTab != null) {
            websiteTab.setText(t("tab.website"));
        }
        browseButton.setText(t("button.chooseFolder"));
        installClientButton.setText(t("button.downloadClient"));
        cancelClientDownloadButton.setText(t("button.cancelDownload"));
        updateActionControls();
        if (gameRunning) {
            statusLabel.setText(t("status.gameRunning"));
        } else if (!working) {
            statusLabel.setText(patchVerified ? t("status.readyToPlay") : t("status.ready"));
        }
        configureLaunchOptionsMenu();
        configureLaunchProfileComboDisplay();
        heroSubtitleLabel.setText(t("home.subtitle"));
        releaseBadgeLabel.setText(t("home.badge"));
        realmHeadlineLabel.setText(t("realm.title"));
        realmStatusTitleLabel.setText(t("realm.status"));
        playersOnlineTitleLabel.setText(t("realm.players"));
        totalAccountsTitleLabel.setText(t("realm.accounts"));
        newsHeaderLabel.setText(t("news.title"));
        if (newsStatusLabel.isVisible()) {
            newsStatusLabel.setText(t("news.loading"));
        }
        optionsPane.refreshText();
        detailsLink.setText(logArea.isVisible() ? t("details.hide") : t("details.show"));
        refreshWowPathLabel();
        refreshSetupPanelVisibility();
        refreshRealmStatus();
        refreshNews();
    }

    private void refreshSetupPanelVisibility() {
        if (optionsPane != null) {
            optionsPane.setSetupVisible(config.wowDirectory().isEmpty());
        }
    }

    private void configureLaunchProfileComboDisplay() {
        if (launchProfileCombo == null) {
            return;
        }
        launchProfileCombo.setButtonCell(launchProfileCell());
        launchProfileCombo.setCellFactory(list -> launchProfileCell());
    }

    private ListCell<LaunchProfile> launchProfileCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(LaunchProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label(language));
            }
        };
    }

    private String t(String key) {
        return language.text(key);
    }

    private static String formatDiskSize(long bytes) {
        double gib = bytes / 1024.0 / 1024.0 / 1024.0;
        return String.format("%.1f GB", gib);
    }

    private record NewsCardSlot(VBox card, Label titleLabel, Label bodyLabel, ImageView imageView) {
    }
}

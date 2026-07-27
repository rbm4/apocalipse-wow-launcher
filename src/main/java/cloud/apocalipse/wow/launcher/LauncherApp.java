package cloud.apocalipse.wow.launcher;

import cloud.apocalipse.wow.launcher.client.ClientInstallManager;
import cloud.apocalipse.wow.launcher.client.ClientInstallPaths;
import cloud.apocalipse.wow.launcher.torrent.jlibtorrent.JlibtorrentDownloadService;
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
import javafx.scene.control.ListCell;
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
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class LauncherApp extends Application {
    private static final String WEBSITE_URL = "https://wow.apocalipse.cloud/";
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LauncherDefaults defaults = LauncherDefaults.load();
    private final LauncherConfig config = new LauncherConfig(defaults);
    private final PatchManager patchManager = new PatchManager();
    private final GameLauncher gameLauncher = new GameLauncher(defaults.wowExecutable());
    private final ClientInstallManager clientInstallManager = new ClientInstallManager(
            new ClientInstallPaths(),
            new JlibtorrentDownloadService()
    );
    private final RealmStatusClient realmStatusClient = new RealmStatusClient();
    private final NewsClient newsClient = new NewsClient();
    private final List<NewsCardSlot> newsCards = new ArrayList<>();

    private Label wowPathLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Label windowTitleLabel;
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
    private Label optionsTitleLabel;
    private Label optionsDescriptionLabel;
    private Label setupTitleLabel;
    private Label setupDescriptionLabel;
    private Label setupExistingNoticeLabel;
    private Label folderLabel;
    private Label launchLabel;
    private Label languageLabel;
    private Circle realmStatusDot;
    private TextArea logArea;
    private Hyperlink detailsLink;
    private VBox setupPanel;
    private Button updateAndPlayButton;
    private Button installClientButton;
    private Button cancelClientDownloadButton;
    private Button setupDownloadButton;
    private Button setupExistingButton;
    private MenuButton launchOptionsButton;
    private Button browseButton;
    private ComboBox<LaunchProfile> launchProfileCombo;
    private ComboBox<AppLanguage> languageCombo;
    private TabPane tabs;
    private Tab homeTab;
    private Tab optionsTab;
    private Tab websiteTab;
    private WebView websiteWebView;
    private Timeline realmStatusTimeline;
    private Task<Path> clientInstallTask;
    private AppLanguage language;
    private double dragOffsetX;
    private double dragOffsetY;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
    private boolean patchVerified;
    private boolean launchAfterSync;
    private boolean working;
    private boolean gameRunning;
    private boolean clientInstallInProgress;
    private boolean manuallyMaximized;

    @Override
    public void start(Stage stage) {
        try {
            writeStartupLog("Starting launcher.", null);
            stage.initStyle(StageStyle.UNDECORATED);
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
            installClientButton.setOnAction(event -> installClientAndPlay());

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
            });

            tabs = new TabPane();
            tabs.getStyleClass().add("main-tabs");
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            homeTab = new Tab(t("tab.home"), homeContent());
            optionsTab = new Tab(t("tab.options"), optionsContent(stage));
            websiteTab = new Tab(t("tab.website"), websiteContent());
            tabs.getTabs().addAll(homeTab, optionsTab, websiteTab);

            StackPane tabSurface = tabSurface();
            BorderPane root = new BorderPane();
            root.setTop(titleBar(stage));
            root.setCenter(tabSurface);
            root.getStyleClass().add("root-shell");

            Scene scene = new Scene(root, 1200, 760);
            scene.getStylesheets().add(getClass().getResource("/launcher.css").toExternalForm());
            stage.setTitle(t("window.title"));
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/Apocalipse_wow_icon.png")));
            stage.setScene(scene);
            stage.setMinWidth(1080);
            stage.setMinHeight(680);
            stage.centerOnScreen();
            stage.setOnShown(event -> {
                writeStartupLog("Launcher window shown.", null);
                stage.toFront();
                stage.requestFocus();
            });
            stage.show();

            log(t("log.launcherReady"));
            writeStartupLog("Launcher UI initialized.", null);
            startRealmStatusRefresh();
            refreshNews();
            if (config.wowDirectory().isEmpty()) {
                Platform.runLater(() -> {
                    try {
                        writeStartupLog("Showing client setup choices.", null);
                        tabs.getSelectionModel().select(1);
                        stage.toFront();
                    } catch (RuntimeException exc) {
                        writeStartupLog("Client setup prompt failed.", exc);
                        throw exc;
                    }
                });
            }
        } catch (RuntimeException exc) {
            writeStartupLog("Launcher failed during JavaFX start().", exc);
            throw exc;
        }
    }

    private HBox titleBar(Stage stage) {
        windowTitleLabel = new Label(t("window.title"));
        windowTitleLabel.getStyleClass().add("window-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimize = windowButton(minimizeIcon());
        minimize.setOnAction(event -> stage.setIconified(true));
        Button maximize = windowButton(maximizeIcon());
        maximize.setOnAction(event -> toggleMaximize(stage));
        Button close = windowButton(closeIcon());
        close.getStyleClass().add("close-button");
        close.setOnAction(event -> stage.close());

        HBox bar = new HBox(8, windowTitleLabel, spacer, minimize, maximize, close);
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

    private WebView websiteContent() {
        websiteWebView = new WebView();
        websiteWebView.getEngine().load(WEBSITE_URL);
        websiteWebView.setContextMenuEnabled(false);
        return websiteWebView;
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
        ImageView titleIcon = new ImageView(new Image(getClass().getResourceAsStream("/Apocalipse_wow_icon.png")));
        titleIcon.setFitWidth(42);
        titleIcon.setFitHeight(42);
        titleIcon.setPreserveRatio(true);
        titleIcon.setSmooth(true);
        HBox titleRow = new HBox(10, titleIcon, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        heroSubtitleLabel = new Label(t("home.subtitle"));
        heroSubtitleLabel.getStyleClass().add("hero-subtitle");

        VBox heroText = new VBox(8, titleRow, heroSubtitleLabel, actionRow(), releaseBadge());
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

        VBox box = new VBox(10,
                header,
                realmNameLabel,
                realmStatusRow("realm.status", realmStatusLabel),
                realmStatusRow("realm.players", playersOnlineLabel),
                realmStatusRow("realm.accounts", totalAccountsLabel),
                realmUpdatedLabel);
        box.setMinWidth(270);
        box.getStyleClass().add("glass-panel");
        return box;
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
    
    private VBox optionsContent(Stage stage) {
        optionsTitleLabel = new Label(t("options.title"));
        optionsTitleLabel.getStyleClass().add("hero-title");
        optionsDescriptionLabel = new Label(t("options.description"));
        optionsDescriptionLabel.getStyleClass().add("hero-subtitle");
        setupPanel = setupPanel(stage);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(14);
        form.getStyleClass().add("glass-panel");

        folderLabel = new Label(t("options.gameDirectory"));
        folderLabel.getStyleClass().add("section-label");
        launchLabel = new Label(t("options.launchProfile"));
        launchLabel.getStyleClass().add("section-label");
        languageLabel = new Label(t("options.language"));
        languageLabel.getStyleClass().add("section-label");

        form.add(folderLabel, 0, 0);
        form.add(wowPathLabel, 1, 0);
        form.add(browseButton, 2, 0);
        form.add(launchLabel, 0, 1);
        form.add(launchProfileCombo, 1, 1);
        form.add(languageLabel, 0, 2);
        form.add(languageCombo, 1, 2);
        GridPane.setHgrow(wowPathLabel, Priority.ALWAYS);
        GridPane.setHgrow(launchProfileCombo, Priority.ALWAYS);
        GridPane.setHgrow(languageCombo, Priority.ALWAYS);

        VBox content = new VBox(18, optionsTitleLabel, optionsDescriptionLabel, setupPanel, form);
        content.setPadding(new Insets(22));
        refreshSetupPanelVisibility();
        return content;
    }

    private VBox setupPanel(Stage stage) {
        setupTitleLabel = new Label(t("setup.title"));
        setupTitleLabel.getStyleClass().add("setup-title");
        setupDescriptionLabel = new Label(t("setup.description"));
        setupDescriptionLabel.setWrapText(true);
        setupDescriptionLabel.getStyleClass().add("setup-body");

        setupDownloadButton = new Button(t("button.downloadClient"));
        setupDownloadButton.getStyleClass().add("update-button");
        setupDownloadButton.setOnAction(event -> installClientAndPlay());

        setupExistingButton = new Button(t("button.useExistingClient"));
        setupExistingButton.getStyleClass().add("secondary-button");
        setupExistingButton.setOnAction(event -> chooseWowFolder(stage));

        setupExistingNoticeLabel = new Label(t("setup.existingNotice"));
        setupExistingNoticeLabel.setWrapText(true);
        setupExistingNoticeLabel.getStyleClass().add("setup-warning");

        HBox actions = new HBox(12, setupDownloadButton, setupExistingButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(12, setupTitleLabel, setupDescriptionLabel, actions, setupExistingNoticeLabel);
        panel.getStyleClass().add("setup-panel");
        panel.setMaxWidth(Double.MAX_VALUE);
        return panel;
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
        if (launchWhenDone && gameLauncher.isRunning()) {
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
                return patchManager.synchronize(wowDirectory, defaults.manifestUrl(), new PatchProgress() {
                    @Override
                    public void message(String message) {
                        String translatedMessage = translatePatchMessage(message);
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
            log(t("log.patchFailed") + ": " + (error == null ? t("error.unknown") : translateErrorMessage(error.getMessage())));
        });

        Thread thread = new Thread(task, "patch-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private void installClientAndPlay() {
        if (gameLauncher.isRunning()) {
            setGameRunning(true);
            log(t("log.gameAlreadyRunning"));
            return;
        }

        patchVerified = false;
        setClientInstallInProgress(true);
        setWorking(true);
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return clientInstallManager.installFromBundledTorrent(new PatchProgress() {
                    @Override
                    public void message(String message) {
                        String translatedMessage = translateClientInstallMessage(message);
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
            boolean cancelled = isClientDownloadCancelled(task.getException());
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

    private void cancelClientDownload() {
        if (!clientInstallInProgress) {
            return;
        }
        cancelClientDownloadButton.setDisable(true);
        clientInstallManager.cancelInstall();
        log(t("log.clientDownloadCancelRequested"));
    }

    private void launchGame() {
        if (gameLauncher.isRunning()) {
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
            gameLauncher.launch(wowDirectory, profile.arguments(), () -> Platform.runLater(this::handleGameExited));
            setGameRunning(true);
            log(t("log.launchingProfile") + ": " + profile.label(language));
        } catch (Exception exc) {
            log(t("log.launchFailed") + ": " + translateErrorMessage(exc.getMessage()));
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
                return newsClient.fetchLatest(defaults.newsUrl(), 3);
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
        gameRunning = running && gameLauncher.isRunning();
        updateActionControls();
        if (gameRunning) {
            statusLabel.setText(t("status.gameRunning"));
        }
    }

    private void updateActionControls() {
        boolean clientRunning = gameRunning || gameLauncher.isRunning();
        updateAndPlayButton.setDisable(working || clientRunning);
        updateAndPlayButton.setText(working
                ? t("button.updating")
                : clientRunning ? t("button.playing") : t("button.updatePlay"));
        installClientButton.setDisable(working || clientRunning);
        if (setupDownloadButton != null) {
            setupDownloadButton.setDisable(working || clientRunning);
        }
        if (setupExistingButton != null) {
            setupExistingButton.setDisable(working || clientRunning);
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
        if (windowTitleLabel != null) {
            windowTitleLabel.setText(t("window.title"));
        }
        browseButton.setText(t("button.chooseFolder"));
        installClientButton.setText(t("button.downloadClient"));
        cancelClientDownloadButton.setText(t("button.cancelDownload"));
        if (setupDownloadButton != null) {
            setupDownloadButton.setText(t("button.downloadClient"));
        }
        if (setupExistingButton != null) {
            setupExistingButton.setText(t("button.useExistingClient"));
        }
        if (setupTitleLabel != null) {
            setupTitleLabel.setText(t("setup.title"));
        }
        if (setupDescriptionLabel != null) {
            setupDescriptionLabel.setText(t("setup.description"));
        }
        if (setupExistingNoticeLabel != null) {
            setupExistingNoticeLabel.setText(t("setup.existingNotice"));
        }
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
        optionsTitleLabel.setText(t("options.title"));
        optionsDescriptionLabel.setText(t("options.description"));
        folderLabel.setText(t("options.gameDirectory"));
        launchLabel.setText(t("options.launchProfile"));
        languageLabel.setText(t("options.language"));
        detailsLink.setText(logArea.isVisible() ? t("details.hide") : t("details.show"));
        refreshWowPathLabel();
        refreshSetupPanelVisibility();
        refreshRealmStatus();
        refreshNews();
    }

    private boolean isClientDownloadCancelled(Throwable error) {
        while (error != null) {
            if ("Client download cancelled.".equals(error.getMessage())) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }

    private void refreshSetupPanelVisibility() {
        if (setupPanel == null) {
            return;
        }
        boolean visible = config.wowDirectory().isEmpty();
        setupPanel.setVisible(visible);
        setupPanel.setManaged(visible);
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

    private String translatePatchMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.equals("Fetching patch manifest...")) {
            return t("patch.fetchingManifest");
        }
        if (message.startsWith("Manifest version ") && message.endsWith(" has no files.")) {
            return t("patch.emptyManifest").replace("{version}", message.substring("Manifest version ".length(), message.length() - " has no files.".length()));
        }
        if (message.startsWith("Patch verification complete. Version ") && message.endsWith(" is installed.")) {
            return t("patch.complete").replace("{version}", message.substring("Patch verification complete. Version ".length(), message.length() - " is installed.".length()));
        }
        if (message.startsWith("Already current: ")) {
            return t("patch.current").replace("{file}", message.substring("Already current: ".length()));
        }
        if (message.equals("Downloading patch...")) {
            return t("patch.downloading");
        }
        if (message.startsWith("Installed ")) {
            return t("patch.installed").replace("{file}", message.substring("Installed ".length()));
        }
        return message;
    }

    private String translateClientInstallMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.startsWith("Client already installed: ")) {
            return t("client.alreadyInstalled").replace("{folder}", message.substring("Client already installed: ".length()));
        }
        if (message.equals("Preparing client download...")) {
            return t("client.preparingDownload");
        }
        if (message.equals("Starting client download...")) {
            return t("client.startingDownload");
        }
        if (message.equals("Download initializing...")) {
            return t("client.downloadInitializing");
        }
        if (message.startsWith("Downloading client... ") && message.contains(" at ")) {
            String details = message.substring("Downloading client... ".length());
            int separator = details.lastIndexOf(" at ");
            if (separator > 0) {
                return t("client.downloading")
                        .replace("{percent}", details.substring(0, separator))
                        .replace("{speed}", details.substring(separator + " at ".length()));
            }
        }
        if (message.equals("Verifying client download...") || message.equals("Verifying client files...")) {
            return t("client.verifying");
        }
        if (message.equals("Cleaning cancelled client download...")) {
            return t("client.cleaningCancelled");
        }
        if (message.equals("Client torrent download complete.")) {
            return t("client.downloadComplete");
        }
        if (message.startsWith("Client download complete: ")) {
            return t("client.complete").replace("{folder}", message.substring("Client download complete: ".length()));
        }
        return message;
    }

    private String translateErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return t("error.unknown");
        }
        if (message.equals("Select a valid World of Warcraft folder.")) {
            return t("error.validWowFolder");
        }
        if (message.startsWith("Wow.exe was not found in ")) {
            return t("error.wowExeMissing").replace("{folder}", message.substring("Wow.exe was not found in ".length()));
        }
        if (message.equals("Wow.exe is already running.")) {
            return t("error.wowAlreadyRunning");
        }
        if (message.startsWith("Data folder was not found in ")) {
            return t("error.dataFolderMissing").replace("{folder}", message.substring("Data folder was not found in ".length()));
        }
        return message;
    }

    static void writeStartupLog(String message, Throwable throwable) {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path directory = localAppData == null || localAppData.isBlank()
                    ? Path.of(System.getProperty("user.home"), ".apocalipse-wow-launcher")
                    : Path.of(localAppData, "ApocalipseWoWLauncher");
            Files.createDirectories(directory);
            StringBuilder entry = new StringBuilder()
                    .append(LocalTime.now().format(LOG_TIME_FORMAT))
                    .append(" ")
                    .append(message)
                    .append(System.lineSeparator());
            if (throwable != null) {
                StringWriter stackTrace = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stackTrace));
                entry.append(stackTrace);
            }
            Files.writeString(
                    directory.resolve("launcher.log"),
                    entry.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Last-resort diagnostics should never prevent the launcher from opening.
        }
    }

    private record NewsCardSlot(VBox card, Label titleLabel, Label bodyLabel, ImageView imageView) {
    }

    private enum AppLanguage {
        PORTUGUESE("pt", "Português"),
        ENGLISH("en", "English"),
        SPANISH("es", "Español");

        private final String code;
        private final String displayName;

        AppLanguage(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        String code() {
            return code;
        }

        String text(String key) {
            return switch (this) {
                case ENGLISH -> english(key);
                case SPANISH -> spanish(key);
                case PORTUGUESE -> portuguese(key);
            };
        }

        @Override
        public String toString() {
            return displayName;
        }

        static AppLanguage fromCode(String code) {
            for (AppLanguage language : values()) {
                if (language.code.equalsIgnoreCase(code)) {
                    return language;
                }
            }
            return PORTUGUESE;
        }

        private static String portuguese(String key) {
            return switch (key) {
                case "window.title" -> "Apocalipse WoW Launcher";
                case "tab.home" -> "Apocalipse WoW";
                case "tab.options" -> "Opções";
                case "tab.website" -> "Site";
                case "button.chooseFolder" -> "Escolher pasta";
                case "button.updatePlay" -> "Atualizar e Jogar";
                case "button.downloadClient" -> "Baixar cliente";
                case "button.cancelDownload" -> "Cancelar download";
                case "button.useExistingClient" -> "Usar instalação existente";
                case "button.launchOptions" -> "Opções de execução";
                case "button.updating" -> "Atualizando...";
                case "button.playing" -> "Em jogo";
                case "status.ready" -> "Pronto";
                case "status.readyToPlay" -> "Pronto para jogar";
                case "status.gameRunning" -> "WoW está aberto";
                case "status.clientInstallFailed" -> "Falha ao baixar cliente";
                case "status.clientDownloadCancelled" -> "Download cancelado";
                case "status.patchFailed" -> "Falha na atualização";
                case "details.show" -> "Ver detalhes";
                case "details.hide" -> "Ocultar detalhes";
                case "setup.title" -> "Preparar cliente";
                case "setup.description" -> "Baixe um cliente pronto para o Apocalipse ou selecione uma instalação existente do WoW 3.3.5a.";
                case "setup.existingNotice" -> "Ao usar uma instalação existente, o launcher irá ajustar o realmlist e gerenciar arquivos de patch dentro dessa pasta.";
                case "home.subtitle" -> "Atualize, verifique e entre em Azeroth pelo reino Apocalipse.";
                case "home.badge" -> "Patch de temporada gerenciado pelo launcher";
                case "realm.title" -> "Status do Reino";
                case "realm.checking" -> "Verificando...";
                case "realm.awaiting" -> "Aguardando status";
                case "realm.status" -> "Status";
                case "realm.players" -> "Jogadores online";
                case "realm.accounts" -> "Contas registradas";
                case "realm.online" -> "Online";
                case "realm.starting" -> "Iniciando";
                case "realm.offline" -> "Offline";
                case "realm.unavailable" -> "Indisponível";
                case "realm.lastCheck" -> "Última verificação";
                case "news.title" -> "Últimas Notícias";
                case "news.loading" -> "Carregando noticias...";
                case "news.empty" -> "Nenhuma noticia publicada.";
                case "news.unavailable" -> "Noticias indisponiveis no momento.";
                case "news.client.title" -> "Patch do Cliente Online";
                case "news.client.body" -> "Atualizações MPQ gerenciadas pelo launcher estão sendo validadas.";
                case "news.battlegrounds.title" -> "Campos de Batalha Aleatórios";
                case "news.battlegrounds.body" -> "A UI do cliente passou para o AddOn ApocalipsePVP integrado.";
                case "news.coming.title" -> "Em Breve";
                case "news.coming.body" -> "A integração de notícias do site alimentará este painel em breve.";
                case "options.title" -> "Opções";
                case "options.description" -> "Configuração usada antes de atualizar e iniciar o cliente.";
                case "options.gameDirectory" -> "Diretório do jogo";
                case "options.launchProfile" -> "Perfil de execução";
                case "options.language" -> "Idioma";
                case "options.notConfigured" -> "Não configurado";
                case "profile.default" -> "Padrão";
                case "profile.windowed" -> "Janela";
                case "profile.fullscreen" -> "Tela cheia";
                case "profile.noSound" -> "Sem som";
                case "profile.console" -> "Console ativado";
                case "profile.windowedConsole" -> "Janela + console";
                case "dialog.selectWowFolder" -> "Selecionar pasta do World of Warcraft";
                case "log.launchProfileSelected" -> "Perfil de execução selecionado";
                case "log.selectedWowFolder" -> "Pasta do WoW selecionada";
                case "log.selectWowFolderFirst" -> "Selecione sua pasta do WoW primeiro.";
                case "log.patchFailed" -> "Falha na atualização";
                case "log.launchingProfile" -> "Iniciando Wow.exe com perfil";
                case "log.launchFailed" -> "Falha ao iniciar";
                case "log.gameAlreadyRunning" -> "Wow.exe já está aberto.";
                case "log.gameClosed" -> "Wow.exe foi fechado.";
                case "log.clientInstalled" -> "Cliente instalado";
                case "log.clientInstallFailed" -> "Falha ao baixar cliente";
                case "log.clientDownloadCancelRequested" -> "Cancelando download do cliente...";
                case "log.clientDownloadCancelled" -> "Download do cliente cancelado. Arquivos parciais removidos.";
                case "log.realmUnavailable" -> "Status do reino indisponível";
                case "log.newsUnavailable" -> "Noticias indisponiveis";
                case "log.saveFailed" -> "Não foi possível salvar as configurações";
                case "log.launcherReady" -> "Launcher pronto.";
                case "patch.fetchingManifest" -> "Buscando manifesto do patch...";
                case "patch.emptyManifest" -> "A versão {version} do manifesto não possui arquivos.";
                case "patch.complete" -> "Verificação concluída. A versão {version} está instalada.";
                case "patch.current" -> "Já atualizado: {file}";
                case "patch.downloading" -> "Baixando patch...";
                case "patch.installed" -> "Instalado {file}";
                case "client.alreadyInstalled" -> "Cliente já instalado: {folder}";
                case "client.preparingDownload" -> "Preparando download do cliente...";
                case "client.startingDownload" -> "Iniciando download do cliente...";
                case "client.downloadInitializing" -> "Inicializando download...";
                case "client.downloading" -> "Baixando cliente... {percent} a {speed}";
                case "client.verifying" -> "Verificando arquivos do cliente...";
                case "client.cleaningCancelled" -> "Removendo arquivos parciais...";
                case "client.downloadComplete" -> "Download do cliente concluído.";
                case "client.complete" -> "Cliente pronto: {folder}";
                case "error.validWowFolder" -> "Selecione uma pasta válida do World of Warcraft.";
                case "error.wowExeMissing" -> "Wow.exe não foi encontrado em {folder}";
                case "error.wowAlreadyRunning" -> "Wow.exe já está aberto.";
                case "error.dataFolderMissing" -> "A pasta Data não foi encontrada em {folder}";
                case "error.unknown" -> "erro desconhecido";
                default -> key;
            };
        }

        private static String english(String key) {
            return switch (key) {
                case "window.title" -> "Apocalipse WoW Launcher";
                case "tab.home" -> "Apocalipse WoW";
                case "tab.options" -> "Options";
                case "tab.website" -> "Website";
                case "button.chooseFolder" -> "Choose folder";
                case "button.updatePlay" -> "Update & Play";
                case "button.downloadClient" -> "Download Client";
                case "button.cancelDownload" -> "Cancel Download";
                case "button.useExistingClient" -> "Use Existing Install";
                case "button.launchOptions" -> "Launch options";
                case "button.updating" -> "Updating...";
                case "button.playing" -> "Playing";
                case "status.ready" -> "Ready";
                case "status.readyToPlay" -> "Ready to play";
                case "status.gameRunning" -> "WoW is running";
                case "status.clientInstallFailed" -> "Client download failed";
                case "status.clientDownloadCancelled" -> "Download cancelled";
                case "status.patchFailed" -> "Patch failed";
                case "details.show" -> "See details";
                case "details.hide" -> "Hide details";
                case "setup.title" -> "Prepare your client";
                case "setup.description" -> "Download a client ready for Apocalipse or select an existing WoW 3.3.5a installation.";
                case "setup.existingNotice" -> "When using an existing installation, the launcher will update the realmlist and manage patch files inside that folder.";
                case "home.subtitle" -> "Patch, verify, and enter Azeroth through the Apocalipse realm.";
                case "home.badge" -> "Season client patch managed by launcher";
                case "realm.title" -> "Realm Status";
                case "realm.checking" -> "Checking...";
                case "realm.awaiting" -> "Awaiting status";
                case "realm.status" -> "Status";
                case "realm.players" -> "Players online";
                case "realm.accounts" -> "Registered accounts";
                case "realm.online" -> "Online";
                case "realm.starting" -> "Starting";
                case "realm.offline" -> "Offline";
                case "realm.unavailable" -> "Unavailable";
                case "realm.lastCheck" -> "Last check";
                case "news.title" -> "Latest News";
                case "news.loading" -> "Loading news...";
                case "news.empty" -> "No published news.";
                case "news.unavailable" -> "News unavailable right now.";
                case "news.client.title" -> "Client Patching Online";
                case "news.client.body" -> "Launcher-managed MPQ updates are now being validated.";
                case "news.battlegrounds.title" -> "Random Battlegrounds";
                case "news.battlegrounds.body" -> "Client UI patching moved to the bundled ApocalipsePVP AddOn.";
                case "news.coming.title" -> "Coming Next";
                case "news.coming.body" -> "Website news integration will feed this panel soon.";
                case "options.title" -> "Options";
                case "options.description" -> "Launcher configuration used before patching and starting the client.";
                case "options.gameDirectory" -> "Game directory";
                case "options.launchProfile" -> "Launch profile";
                case "options.language" -> "Language";
                case "options.notConfigured" -> "Not configured";
                case "profile.default" -> "Default";
                case "profile.windowed" -> "Windowed";
                case "profile.fullscreen" -> "Fullscreen";
                case "profile.noSound" -> "No sound";
                case "profile.console" -> "Console enabled";
                case "profile.windowedConsole" -> "Windowed + console";
                case "dialog.selectWowFolder" -> "Select World of Warcraft Folder";
                case "log.launchProfileSelected" -> "Launch profile selected";
                case "log.selectedWowFolder" -> "Selected WoW folder";
                case "log.selectWowFolderFirst" -> "Select your WoW folder first.";
                case "log.patchFailed" -> "Patch failed";
                case "log.launchingProfile" -> "Launching Wow.exe with profile";
                case "log.launchFailed" -> "Launch failed";
                case "log.gameAlreadyRunning" -> "Wow.exe is already running.";
                case "log.gameClosed" -> "Wow.exe closed.";
                case "log.clientInstalled" -> "Client installed";
                case "log.clientInstallFailed" -> "Client download failed";
                case "log.clientDownloadCancelRequested" -> "Cancelling client download...";
                case "log.clientDownloadCancelled" -> "Client download cancelled. Partial files removed.";
                case "log.realmUnavailable" -> "Realm status unavailable";
                case "log.newsUnavailable" -> "News unavailable";
                case "log.saveFailed" -> "Could not save launcher settings";
                case "log.launcherReady" -> "Launcher ready.";
                case "patch.fetchingManifest" -> "Fetching patch manifest...";
                case "patch.emptyManifest" -> "Manifest version {version} has no files.";
                case "patch.complete" -> "Patch verification complete. Version {version} is installed.";
                case "patch.current" -> "Already current: {file}";
                case "patch.downloading" -> "Downloading patch...";
                case "patch.installed" -> "Installed {file}";
                case "client.alreadyInstalled" -> "Client already installed: {folder}";
                case "client.preparingDownload" -> "Preparing client download...";
                case "client.startingDownload" -> "Starting client download...";
                case "client.downloadInitializing" -> "Download initializing...";
                case "client.downloading" -> "Downloading client... {percent} at {speed}";
                case "client.verifying" -> "Verifying client files...";
                case "client.cleaningCancelled" -> "Removing partial files...";
                case "client.downloadComplete" -> "Client download complete.";
                case "client.complete" -> "Client ready: {folder}";
                case "error.validWowFolder" -> "Select a valid World of Warcraft folder.";
                case "error.wowExeMissing" -> "Wow.exe was not found in {folder}";
                case "error.wowAlreadyRunning" -> "Wow.exe is already running.";
                case "error.dataFolderMissing" -> "Data folder was not found in {folder}";
                case "error.unknown" -> "unknown error";
                default -> key;
            };
        }

        private static String spanish(String key) {
            return switch (key) {
                case "window.title" -> "Apocalipse WoW Launcher";
                case "tab.home" -> "Apocalipse WoW";
                case "tab.options" -> "Opciones";
                case "tab.website" -> "Sitio";
                case "button.chooseFolder" -> "Elegir carpeta";
                case "button.updatePlay" -> "Actualizar y Jugar";
                case "button.downloadClient" -> "Descargar cliente";
                case "button.cancelDownload" -> "Cancelar descarga";
                case "button.useExistingClient" -> "Usar instalación existente";
                case "button.launchOptions" -> "Opciones de inicio";
                case "button.updating" -> "Actualizando...";
                case "button.playing" -> "En juego";
                case "status.ready" -> "Listo";
                case "status.readyToPlay" -> "Listo para jugar";
                case "status.gameRunning" -> "WoW está abierto";
                case "status.clientInstallFailed" -> "Error al descargar cliente";
                case "status.clientDownloadCancelled" -> "Descarga cancelada";
                case "status.patchFailed" -> "Error de actualización";
                case "details.show" -> "Ver detalles";
                case "details.hide" -> "Ocultar detalles";
                case "setup.title" -> "Preparar cliente";
                case "setup.description" -> "Descarga un cliente listo para Apocalipse o selecciona una instalación existente de WoW 3.3.5a.";
                case "setup.existingNotice" -> "Al usar una instalación existente, el launcher ajustará el realmlist y gestionará archivos de parche dentro de esa carpeta.";
                case "home.subtitle" -> "Actualiza, verifica y entra en Azeroth por el reino Apocalipse.";
                case "home.badge" -> "Parche de temporada gestionado por el launcher";
                case "realm.title" -> "Estado del Reino";
                case "realm.checking" -> "Verificando...";
                case "realm.awaiting" -> "Esperando estado";
                case "realm.status" -> "Estado";
                case "realm.players" -> "Jugadores online";
                case "realm.accounts" -> "Cuentas registradas";
                case "realm.online" -> "Online";
                case "realm.starting" -> "Iniciando";
                case "realm.offline" -> "Offline";
                case "realm.unavailable" -> "No disponible";
                case "realm.lastCheck" -> "Última verificación";
                case "news.title" -> "Últimas Noticias";
                case "news.loading" -> "Cargando noticias...";
                case "news.empty" -> "No hay noticias publicadas.";
                case "news.unavailable" -> "Noticias no disponibles ahora.";
                case "news.client.title" -> "Parche del Cliente Online";
                case "news.client.body" -> "Las actualizaciones MPQ gestionadas por el launcher están siendo validadas.";
                case "news.battlegrounds.title" -> "Campos de Batalla Aleatorios";
                case "news.battlegrounds.body" -> "La UI del cliente pasó al AddOn ApocalipsePVP integrado.";
                case "news.coming.title" -> "Próximamente";
                case "news.coming.body" -> "La integración de noticias del sitio alimentará este panel pronto.";
                case "options.title" -> "Opciones";
                case "options.description" -> "Configuración usada antes de actualizar e iniciar el cliente.";
                case "options.gameDirectory" -> "Directorio del juego";
                case "options.launchProfile" -> "Perfil de inicio";
                case "options.language" -> "Idioma";
                case "options.notConfigured" -> "No configurado";
                case "profile.default" -> "Predeterminado";
                case "profile.windowed" -> "Ventana";
                case "profile.fullscreen" -> "Pantalla completa";
                case "profile.noSound" -> "Sin sonido";
                case "profile.console" -> "Consola activada";
                case "profile.windowedConsole" -> "Ventana + consola";
                case "dialog.selectWowFolder" -> "Seleccionar carpeta de World of Warcraft";
                case "log.launchProfileSelected" -> "Perfil de inicio seleccionado";
                case "log.selectedWowFolder" -> "Carpeta de WoW seleccionada";
                case "log.selectWowFolderFirst" -> "Selecciona primero tu carpeta de WoW.";
                case "log.patchFailed" -> "Error de actualización";
                case "log.launchingProfile" -> "Iniciando Wow.exe con perfil";
                case "log.launchFailed" -> "Error al iniciar";
                case "log.gameAlreadyRunning" -> "Wow.exe ya está abierto.";
                case "log.gameClosed" -> "Wow.exe fue cerrado.";
                case "log.clientInstalled" -> "Cliente instalado";
                case "log.clientInstallFailed" -> "Error al descargar cliente";
                case "log.clientDownloadCancelRequested" -> "Cancelando descarga del cliente...";
                case "log.clientDownloadCancelled" -> "Descarga del cliente cancelada. Archivos parciales eliminados.";
                case "log.realmUnavailable" -> "Estado del reino no disponible";
                case "log.newsUnavailable" -> "Noticias no disponibles";
                case "log.saveFailed" -> "No se pudieron guardar las configuraciones";
                case "log.launcherReady" -> "Launcher listo.";
                case "patch.fetchingManifest" -> "Buscando manifiesto del parche...";
                case "patch.emptyManifest" -> "La versión {version} del manifiesto no tiene archivos.";
                case "patch.complete" -> "Verificación completa. La versión {version} está instalada.";
                case "patch.current" -> "Ya actualizado: {file}";
                case "patch.downloading" -> "Descargando parche...";
                case "patch.installed" -> "Instalado {file}";
                case "client.alreadyInstalled" -> "Cliente ya instalado: {folder}";
                case "client.preparingDownload" -> "Preparando descarga del cliente...";
                case "client.startingDownload" -> "Iniciando descarga del cliente...";
                case "client.downloadInitializing" -> "Inicializando descarga...";
                case "client.downloading" -> "Descargando cliente... {percent} a {speed}";
                case "client.verifying" -> "Verificando archivos del cliente...";
                case "client.cleaningCancelled" -> "Eliminando archivos parciales...";
                case "client.downloadComplete" -> "Descarga del cliente completada.";
                case "client.complete" -> "Cliente listo: {folder}";
                case "error.validWowFolder" -> "Selecciona una carpeta válida de World of Warcraft.";
                case "error.wowExeMissing" -> "Wow.exe no fue encontrado en {folder}";
                case "error.wowAlreadyRunning" -> "Wow.exe ya está abierto.";
                case "error.dataFolderMissing" -> "La carpeta Data no fue encontrada en {folder}";
                case "error.unknown" -> "error desconocido";
                default -> key;
            };
        }
    }

    private enum LaunchProfile {
        DEFAULT("default", "profile.default", List.of()),
        WINDOWED("windowed", "profile.windowed", List.of("-windowed")),
        FULLSCREEN("fullscreen", "profile.fullscreen", List.of("-fullscreen")),
        NO_SOUND("nosound", "profile.noSound", List.of("-nosound")),
        CONSOLE("console", "profile.console", List.of("-console")),
        WINDOWED_CONSOLE("windowed-console", "profile.windowedConsole", List.of("-windowed", "-console"));

        private final String id;
        private final String labelKey;
        private final List<String> arguments;

        LaunchProfile(String id, String labelKey, List<String> arguments) {
            this.id = id;
            this.labelKey = labelKey;
            this.arguments = arguments;
        }

        String id() {
            return id;
        }

        String label(AppLanguage language) {
            return language.text(labelKey);
        }

        List<String> arguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return AppLanguage.PORTUGUESE.text(labelKey);
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

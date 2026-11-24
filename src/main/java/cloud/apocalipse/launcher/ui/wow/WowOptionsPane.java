package cloud.apocalipse.launcher.ui.wow;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Function;

final class WowOptionsPane extends VBox {
    private final Function<String, String> text;
    private final Label optionsTitleLabel;
    private final Label optionsDescriptionLabel;
    private final Label folderLabel;
    private final Label launchLabel;
    private final Label languageLabel;
    private final VBox setupPanel;
    private final Label setupTitleLabel = new Label();
    private final Label setupDescriptionLabel = new Label();
    private final Label setupExistingNoticeLabel = new Label();
    private final Button setupDownloadButton = new Button();
    private final Button setupExistingButton = new Button();

    WowOptionsPane(
            Function<String, String> text,
            Label wowPathLabel,
            Button browseButton,
            ComboBox<LaunchProfile> launchProfileCombo,
            ComboBox<AppLanguage> languageCombo,
            Runnable downloadClient,
            Runnable chooseExistingClient
    ) {
        super(18);
        this.text = text;
        optionsTitleLabel = new Label(t("options.title"));
        optionsTitleLabel.getStyleClass().add("hero-title");
        optionsDescriptionLabel = new Label(t("options.description"));
        optionsDescriptionLabel.getStyleClass().add("hero-subtitle");
        setupPanel = setupPanel(downloadClient, chooseExistingClient);

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

        getChildren().addAll(optionsTitleLabel, optionsDescriptionLabel, setupPanel, form);
        setPadding(new Insets(22));
    }

    private VBox setupPanel(Runnable downloadClient, Runnable chooseExistingClient) {
        setupTitleLabel.setText(t("setup.title"));
        setupTitleLabel.getStyleClass().add("setup-title");
        setupDescriptionLabel.setText(t("setup.description"));
        setupDescriptionLabel.setWrapText(true);
        setupDescriptionLabel.getStyleClass().add("setup-body");

        setupDownloadButton.setText(t("button.downloadClient"));
        setupDownloadButton.getStyleClass().add("update-button");
        setupDownloadButton.setOnAction(event -> downloadClient.run());

        setupExistingButton.setText(t("button.useExistingClient"));
        setupExistingButton.getStyleClass().add("secondary-button");
        setupExistingButton.setOnAction(event -> chooseExistingClient.run());

        setupExistingNoticeLabel.setText(t("setup.existingNotice"));
        setupExistingNoticeLabel.setWrapText(true);
        setupExistingNoticeLabel.getStyleClass().add("setup-warning");

        HBox actions = new HBox(12, setupDownloadButton, setupExistingButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(12, setupTitleLabel, setupDescriptionLabel, actions, setupExistingNoticeLabel);
        panel.getStyleClass().add("setup-panel");
        panel.setMaxWidth(Double.MAX_VALUE);
        return panel;
    }

    void refreshText() {
        optionsTitleLabel.setText(t("options.title"));
        optionsDescriptionLabel.setText(t("options.description"));
        folderLabel.setText(t("options.gameDirectory"));
        launchLabel.setText(t("options.launchProfile"));
        languageLabel.setText(t("options.language"));
        setupTitleLabel.setText(t("setup.title"));
        setupDescriptionLabel.setText(t("setup.description"));
        setupExistingNoticeLabel.setText(t("setup.existingNotice"));
        setupDownloadButton.setText(t("button.downloadClient"));
        setupExistingButton.setText(t("button.useExistingClient"));
    }

    void setSetupVisible(boolean visible) {
        setupPanel.setVisible(visible);
        setupPanel.setManaged(visible);
    }

    void setSetupActionsDisabled(boolean disabled) {
        setupDownloadButton.setDisable(disabled);
        setupExistingButton.setDisable(disabled);
    }

    private String t(String key) {
        return text.apply(key);
    }
}

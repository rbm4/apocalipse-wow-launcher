package cloud.apocalipse.launcher.ui;

import cloud.apocalipse.launcher.LauncherGame;
import cloud.apocalipse.launcher.ui.wow.AppLanguage;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class GameSelectionView {
    private final Consumer<LauncherGame> onGameSelected;
    private final AppLanguage language;

    public GameSelectionView(AppLanguage language, Consumer<LauncherGame> onGameSelected) {
        this.language = language;
        this.onGameSelected = onGameSelected;
    }

    public Node createContent() {
        Label title = new Label(language.text("selection.title"));
        title.getStyleClass().add("hero-title");
        Label subtitle = new Label(language.text("selection.subtitle"));
        subtitle.getStyleClass().add("hero-subtitle");

        GridPane gameCards = new GridPane();
        gameCards.setAlignment(Pos.CENTER);
        gameCards.getStyleClass().add("game-cards");
        ColumnConstraints wowColumn = new ColumnConstraints();
        wowColumn.setPercentWidth(45);
        ColumnConstraints zomboidColumn = new ColumnConstraints();
        zomboidColumn.setPercentWidth(45);
        gameCards.getColumnConstraints().addAll(wowColumn, zomboidColumn);
        gameCards.addRow(0,
                gameCard(LauncherGame.WORLD_OF_WARCRAFT, "wow-artwork",
                        "game.wow.title", "game.wow.description", "game.available"),
                gameCard(LauncherGame.PROJECT_ZOMBOID, "zomboid-artwork",
                        "game.zomboid.title", "game.zomboid.description", "game.available"));

        VBox heading = new VBox(title, subtitle);
        heading.setAlignment(Pos.CENTER);
        heading.getStyleClass().add("hero-copy");
        VBox content = new VBox(heading, gameCards);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().addAll("launcher-page", "home-page");
        return content;
    }

    private Button gameCard(LauncherGame game, String artworkClass, String titleKey,
                            String descriptionKey, String statusKey) {
        Region artwork = new Region();
        artwork.getStyleClass().addAll("game-card-artwork", artworkClass);
        artwork.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        artwork.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label status = new Label(language.text(statusKey));
        status.getStyleClass().add("section-label");
        Label title = new Label(language.text(titleKey));
        title.getStyleClass().add("game-card-title");
        title.setWrapText(true);
        Label description = new Label(language.text(descriptionKey));
        description.getStyleClass().add("hero-subtitle");
        description.setWrapText(true);

        VBox copy = new VBox(status, title, description);
        copy.setAlignment(Pos.CENTER_LEFT);
        copy.getStyleClass().add("hero-copy");
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox card = new HBox(artwork, copy);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("glass-panel", "game-card-content");

        Button button = new Button();
        button.setGraphic(card);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(title.getText());
        button.setMinWidth(0);
        button.setMaxWidth(Double.MAX_VALUE);
        card.prefWidthProperty().bind(button.widthProperty());
        button.getStyleClass().add("game-card");
        button.setOnAction(event -> onGameSelected.accept(game));
        return button;
    }
}

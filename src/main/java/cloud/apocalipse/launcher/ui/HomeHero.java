package cloud.apocalipse.launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class HomeHero {
    private HomeHero() {
    }

    public static HBox titleRow(String text, String iconResource) {
        Label title = new Label(text);
        title.getStyleClass().add("hero-title");
        ImageView icon = new ImageView(new Image(HomeHero.class.getResourceAsStream(iconResource)));
        icon.setFitWidth(42);
        icon.setFitHeight(42);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);

        HBox row = new HBox(10, icon, title);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public static VBox statusPanel(Node heading, Node content) {
        StackPane viewport = new StackPane(content);
        viewport.setPrefSize(320, 200);
        viewport.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        viewport.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        VBox panel = new VBox(10, heading, viewport);
        panel.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        panel.getStyleClass().add("glass-panel");
        return panel;
    }
}

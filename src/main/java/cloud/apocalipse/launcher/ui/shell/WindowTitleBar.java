package cloud.apocalipse.launcher.ui.shell;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class WindowTitleBar extends HBox {
    private final Label windowTitleLabel;
    private double dragOffsetX;
    private double dragOffsetY;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
    private boolean manuallyMaximized;
    private final Button gameSwitchButton;
    private final Button otherGameButton;

    public WindowTitleBar(Stage stage, String title, String actionText, Runnable onAction,
                          String otherGameText, Runnable onOtherGame) {
        setSpacing(8);
        windowTitleLabel = new Label(title);
        windowTitleLabel.getStyleClass().add("window-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        gameSwitchButton = new Button(actionText);
        gameSwitchButton.getStyleClass().add("window-switch-button");
        gameSwitchButton.setOnAction(event -> onAction.run());
        otherGameButton = new Button(otherGameText);
        otherGameButton.getStyleClass().add("window-switch-button");
        otherGameButton.setOnAction(event -> onOtherGame.run());
        Button minimize = windowButton(minimizeIcon());
        minimize.setOnAction(event -> stage.setIconified(true));
        Button maximize = windowButton(maximizeIcon());
        maximize.setOnAction(event -> toggleMaximize(stage));
        Button close = windowButton(closeIcon());
        close.getStyleClass().add("close-button");
        close.setOnAction(event -> stage.close());

        getChildren().addAll(windowTitleLabel, spacer, gameSwitchButton, otherGameButton, minimize, maximize, close);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("title-bar");
        setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        setOnMouseDragged(event -> {
            if (!manuallyMaximized) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximize(stage);
            }
        });
    }

    public void setTitle(String title) {
        windowTitleLabel.setText(title);
    }

    public void setActionText(String text) {
        gameSwitchButton.setText(text);
    }

    public void setActionVisible(boolean visible) {
        gameSwitchButton.setVisible(visible);
        gameSwitchButton.setManaged(visible);
    }

    public void setOtherGameText(String text) {
        otherGameButton.setText(text);
    }

    public void setOtherGameVisible(boolean visible) {
        otherGameButton.setVisible(visible);
        otherGameButton.setManaged(visible);
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
}

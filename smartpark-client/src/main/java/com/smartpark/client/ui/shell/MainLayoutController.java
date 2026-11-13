package com.smartpark.client.ui.shell;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.session.UserSession;
import com.smartpark.client.ui.component.ConnectionIndicator;
import com.smartpark.client.ui.component.Icons;
import com.smartpark.client.ui.navigation.View;
import com.smartpark.client.util.Formats;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The application frame shown after login:
 * <pre>
 * ┌──────────┬───────────────────────────────────────────────┐
 * │ SmartPark│ Page title                    ● Live  [user ▾]│
 * │ ▣ Over.. ├───────────────────────────────────────────────┤
 * │ ➜ Entry  │                                               │
 * │ ⇦ Exit   │            content (the current View)         │
 * │ ▭ Term.. │                                               │
 * │          ├───────────────────────────────────────────────┤
 * │ user/out │ server · terminal · last update        clock  │
 * └──────────┴───────────────────────────────────────────────┘
 * </pre>
 * The sidebar contains only the views allowed for the signed-in role.
 */
public class MainLayoutController {

    private final AppContext context;
    private final Map<View, ToggleButton> navButtons = new EnumMap<>(View.class);
    private final ToggleGroup navGroup = new ToggleGroup();
    private Timeline clock;

    @FXML private StackPane brandIcon;
    @FXML private VBox navItems;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private HBox statusHolder;
    @FXML private StackPane userAvatar;
    @FXML private Label userName;
    @FXML private Label userRole;
    @FXML private Label roleBadge;
    @FXML private Button signOutButton;
    @FXML private ScrollPane contentScroll;
    @FXML private StackPane content;
    @FXML private Label serverInfo;
    @FXML private Label terminalInfo;
    @FXML private Label lastUpdate;
    @FXML private Label clockLabel;

    public MainLayoutController(AppContext context) {
        this.context = context;
    }

    public void init(UserSession session, List<View> views, Consumer<View> onNavigate) {
        brandIcon.getChildren().add(Icons.of(Icons.CAR, 26, "brand-icon"));

        for (View view : views) {
            ToggleButton button = new ToggleButton(view.title(), Icons.of(view.icon(), 18, "nav-icon"));
            button.getStyleClass().add("nav-button");
            button.setToggleGroup(navGroup);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setGraphicTextGap(12);
            button.setOnAction(e -> onNavigate.accept(view));
            navButtons.put(view, button);
            navItems.getChildren().add(button);
        }
        // A selected toggle must stay selected when clicked again.
        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });

        userAvatar.getChildren().add(Icons.of(Icons.USER, 20, "avatar-icon"));
        userName.setText(session.username());
        userRole.setText(session.role().displayName());
        roleBadge.setText(session.role().name().replace('_', ' '));
        roleBadge.getStyleClass().add("role-" + session.role().name().toLowerCase(Locale.ROOT).replace('_', '-'));

        statusHolder.getChildren().add(new ConnectionIndicator(
                context.realtime().stateProperty(), context.realtime().detailProperty()));

        serverInfo.setText("Server " + context.config().serverHost() + ":" + context.config().serverPort());
        terminalInfo.setText("Terminal " + session.terminal());
        lastUpdate.textProperty().bind(context.realtime().lastEventAtProperty().map(
                (Instant t) -> "Last update " + Formats.time(t)).orElse("No updates yet"));

        clockLabel.setText(Formats.time(Instant.now()));
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> clockLabel.setText(Formats.time(Instant.now()))));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        contentScroll.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                clock.stop(); // shell removed (logout)
            }
        });
    }

    /** Called by the Navigator to display a screen. */
    public void showContent(View view, Node node) {
        pageTitle.setText(view.title());
        pageSubtitle.setText(view.subtitle());
        content.getChildren().setAll(node);
        contentScroll.setVvalue(0);
        ToggleButton button = navButtons.get(view);
        if (button != null) {
            button.setSelected(true);
        } else if (navGroup.getSelectedToggle() != null) {
            navGroup.getSelectedToggle().setSelected(false);
        }
    }

    @FXML
    private void onSignOut() {
        signOutButton.setDisable(true);
        context.session().logout(); // the session listener returns to the login screen
    }
}

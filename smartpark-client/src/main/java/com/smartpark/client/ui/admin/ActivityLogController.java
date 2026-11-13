package com.smartpark.client.ui.admin;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.dto.ApiDtos.ActivityEntry;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.session.UserRole;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.Tables;
import com.smartpark.client.util.Formats;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Operator activity monitor (ADMIN): who did what and when.
 * <ul>
 *     <li>Filters: role (entry operators, exit operators, admins), action, username.
 *         Filtering happens on the server ({@code GET /api/activity}).</li>
 *     <li>Live: new entries arrive as {@code ACTIVITY_RECORDED} WebSocket messages, which the
 *         server sends to admin terminals only, and are added to the top if they match the filters.</li>
 *     <li>Chart: vehicle entries and exits per operator in the records shown.</li>
 * </ul>
 */
public class ActivityLogController extends LiveView {

    private static final int LIMIT = 300;
    private static final List<String> ACTIONS = List.of("All actions", "VEHICLE_ENTRY", "VEHICLE_EXIT",
            "ALLOCATION_REJECTED", "LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT", "SLOT_CREATED", "SLOT_UPDATED",
            "SLOT_DISABLED", "SLOT_ENABLED", "CATEGORY_CREATED", "CATEGORY_UPDATED", "USER_CREATED",
            "USER_STATUS_CHANGED", "USER_ROLE_CHANGED", "SLOT_RECONCILED", "DATA_SEEDED");

    @FXML private Label errorLabel;
    @FXML private ComboBox<String> roleBox;
    @FXML private ComboBox<String> actionBox;
    @FXML private TextField userField;
    @FXML private Label countLabel;
    @FXML private TableView<ActivityEntry> table;
    @FXML private BarChart<String, Number> operatorChart;

    private final ObservableList<ActivityEntry> entries = FXCollections.observableArrayList();
    private final Map<String, UserRole> roleByUser = new HashMap<>();

    public ActivityLogController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        hide(errorLabel);
        roleBox.setItems(FXCollections.observableArrayList("All roles", "Entry operators", "Exit operators", "Administrators"));
        roleBox.setValue("All roles");
        actionBox.setItems(FXCollections.observableArrayList(ACTIONS));
        actionBox.setValue("All actions");
        roleBox.valueProperty().addListener((o, a, b) -> refresh());
        actionBox.valueProperty().addListener((o, a, b) -> refresh());

        table.setItems(entries);
        table.getColumns().setAll(List.of(
                Tables.<ActivityEntry>column("Time", a -> Formats.dateTimeSeconds(a.timestamp()), 130),
                Tables.<ActivityEntry>column("User", ActivityEntry::username, 100),
                Tables.<ActivityEntry>column("Role", a -> roleOf(a.username()), 110),
                Tables.<ActivityEntry>badge("Action", ActivityEntry::action, ActivityLogController::actionStyle, 170),
                Tables.<ActivityEntry>column("Details", ActivityEntry::description, 320)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No activity matches the filters"));
        operatorChart.setAnimated(false);
    }

    @Override
    protected void refresh() {
        load(context.admin().users(), users -> {
            roleByUser.clear();
            users.forEach(u -> roleByUser.put(u.username(), u.role()));
            table.refresh();
        }, errorLabel);
        load(context.admin().activity(selectedRole(), selectedAction(), userField.getText(), LIMIT), list -> {
            entries.setAll(list);
            updateSummary();
        }, errorLabel);
    }

    /** ACTIVITY_RECORDED is pushed to admins only; add it if it matches the current filters. */
    @Override
    protected void onEvent(RealtimeEvent e) {
        if (!"ACTIVITY_RECORDED".equals(e.type())) {
            return;
        }
        ActivityEntry a = new ActivityEntry(e.payloadText("id"), e.payloadText("username"), e.payloadText("action"),
                e.payloadText("description"), Instant.parse(e.payloadText("timestamp")));
        if (matches(a)) {
            entries.addFirst(a);
            if (entries.size() > LIMIT) {
                entries.removeLast();
            }
            updateSummary();
        }
        if (!roleByUser.containsKey(a.username())) {
            refreshSoon(); // a user we do not know yet (just created)
        }
    }

    @FXML
    private void onSearch() {
        refresh();
    }

    @FXML
    private void onReset() {
        userField.clear();
        roleBox.setValue("All roles");
        actionBox.setValue("All actions");
        refresh();
    }

    // --------------------------------------------------------------- helpers

    private boolean matches(ActivityEntry a) {
        UserRole role = selectedRole();
        String action = selectedAction();
        String user = userField.getText() == null ? "" : userField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        return (role == null || role == roleByUser.get(a.username()))
                && (action == null || action.equals(a.action()))
                && (user.isEmpty() || user.equals(a.username()));
    }

    private void updateSummary() {
        countLabel.setText(entries.size() + " records" + (entries.size() >= LIMIT ? " (latest " + LIMIT + ")" : ""));
        Map<String, int[]> perOperator = new TreeMap<>();
        for (ActivityEntry a : entries) {
            int index = "VEHICLE_ENTRY".equals(a.action()) ? 0 : "VEHICLE_EXIT".equals(a.action()) ? 1 : -1;
            if (index >= 0) {
                perOperator.computeIfAbsent(a.username(), k -> new int[2])[index]++;
            }
        }
        XYChart.Series<String, Number> in = new XYChart.Series<>();
        in.setName("Vehicle entries");
        XYChart.Series<String, Number> out = new XYChart.Series<>();
        out.setName("Vehicle exits");
        perOperator.forEach((user, c) -> {
            in.getData().add(new XYChart.Data<>(user, c[0]));
            out.getData().add(new XYChart.Data<>(user, c[1]));
        });
        operatorChart.setData(FXCollections.observableArrayList(List.of(in, out)));
    }

    private UserRole selectedRole() {
        return switch (roleBox.getValue()) {
            case "Entry operators" -> UserRole.ENTRY_OPERATOR;
            case "Exit operators" -> UserRole.EXIT_OPERATOR;
            case "Administrators" -> UserRole.ADMIN;
            default -> null;
        };
    }

    private String selectedAction() {
        return "All actions".equals(actionBox.getValue()) ? null : actionBox.getValue();
    }

    private String roleOf(String username) {
        UserRole r = roleByUser.get(username);
        return r == null ? ("SYSTEM".equals(username) ? "System" : "–") : r.displayName();
    }

    static String actionStyle(String action) {
        return switch (action) {
            case "VEHICLE_ENTRY" -> "badge-blue";
            case "VEHICLE_EXIT" -> "badge-green";
            case "ALLOCATION_REJECTED", "LOGIN_FAILED" -> "badge-red";
            case "USER_CREATED", "USER_STATUS_CHANGED", "USER_ROLE_CHANGED" -> "badge-purple";
            default -> "badge-grey";
        };
    }
}

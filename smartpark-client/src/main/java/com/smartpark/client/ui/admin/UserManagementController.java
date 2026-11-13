package com.smartpark.client.ui.admin;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.CreateUserRequest;
import com.smartpark.client.net.dto.ApiDtos.UserInfo;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.session.UserRole;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ConfirmDialog;
import com.smartpark.client.ui.component.FormDialog;
import com.smartpark.client.ui.component.Tables;
import com.smartpark.client.util.Formats;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Operator accounts (ADMIN): create accounts, view them, assign roles, activate / deactivate.
 * <p>
 * The server enforces the rules: ADMIN role required for every call, unique usernames,
 * password length, no deactivating or demoting yourself, and at least one active admin.
 * A role change or deactivation signs the user out on every terminal immediately.
 */
public class UserManagementController extends LiveView {

    @FXML private Label errorLabel;
    @FXML private Label actionMessage;
    @FXML private Label summaryLabel;
    @FXML private TableView<UserInfo> table;
    @FXML private Button roleButton;
    @FXML private Button activeButton;

    private final ObservableList<UserInfo> users = FXCollections.observableArrayList();

    public UserManagementController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        hide(errorLabel);
        hide(actionMessage);
        table.setItems(users);
        table.getColumns().setAll(List.of(
                Tables.<UserInfo>column("Username", u -> u.username() + (isMe(u) ? "  (you)" : ""), 160),
                Tables.<UserInfo>badge("Role", u -> u.role().name().replace('_', ' '), UserManagementController::roleStyle, 150),
                Tables.<UserInfo>badge("Status", u -> u.active() ? "ACTIVE" : "INACTIVE",
                        v -> "ACTIVE".equals(v) ? "badge-green" : "badge-grey", 110),
                Tables.<UserInfo>column("Created", u -> Formats.dateTime(u.createdAt()), 140)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, u) -> updateButtons(u));
        updateButtons(null);
    }

    @Override
    protected void refresh() {
        load(context.admin().users(), list -> {
            String selected = table.getSelectionModel().getSelectedItem() == null ? null
                    : table.getSelectionModel().getSelectedItem().username();
            users.setAll(list);
            list.stream().filter(u -> u.username().equals(selected)).findFirst()
                    .ifPresent(u -> table.getSelectionModel().select(u));
            long active = list.stream().filter(UserInfo::active).count();
            summaryLabel.setText(list.size() + " accounts  ·  " + active + " active  ·  "
                    + count(list, UserRole.ADMIN) + " admin  ·  " + count(list, UserRole.ENTRY_OPERATOR) + " entry  ·  "
                    + count(list, UserRole.EXIT_OPERATOR) + " exit");
        }, errorLabel);
    }

    /** Account changes by another admin arrive as ACTIVITY_RECORDED (USER_*). */
    @Override
    protected void onEvent(RealtimeEvent e) {
        if ("ACTIVITY_RECORDED".equals(e.type()) && String.valueOf(e.payloadText("action")).startsWith("USER_")) {
            refreshSoon();
        }
    }

    // ---------------------------------------------------------------- create

    @FXML
    private void onCreate() {
        TextField username = new TextField();
        username.setPromptText("3-30 letters, digits, . _ -");
        PasswordField password = new PasswordField();
        password.setPromptText("at least 8 characters");
        PasswordField confirm = new PasswordField();
        ComboBox<UserRole> role = roleBox(UserRole.ENTRY_OPERATOR);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, fieldLabel("Username"), username);
        grid.addRow(1, fieldLabel("Password"), password);
        grid.addRow(2, fieldLabel("Confirm password"), confirm);
        grid.addRow(3, fieldLabel("Role"), role);
        Label hint = new Label("The operator signs in with these credentials on their terminal.");
        hint.getStyleClass().add("muted");

        FormDialog dialog = new FormDialog(table.getScene().getWindow(), "Create operator account",
                new VBox(12, grid, hint), "Create account");
        dialog.onSubmit(() -> {
            String name = username.getText() == null ? "" : username.getText().trim();
            if (!name.matches("[A-Za-z0-9._-]{3,30}")) {
                dialog.showError("Username: 3-30 letters, digits, '.', '_' or '-'.");
                return;
            }
            if (password.getText().length() < 8 || password.getText().length() > 72) {
                dialog.showError("Password must be 8-72 characters.");
                return;
            }
            if (!password.getText().equals(confirm.getText())) {
                dialog.showError("The passwords do not match.");
                return;
            }
            dialog.hideError();
            dialog.setBusy(true);
            context.admin().createUser(new CreateUserRequest(name, password.getText(), role.getValue()))
                    .whenComplete((created, error) -> Platform.runLater(() -> {
                        dialog.setBusy(false);
                        if (error == null) {
                            dialog.close();
                            message("success-banner", "Account " + created.username() + " created ("
                                    + created.role().displayName() + ").");
                            refresh();
                        } else {
                            dialog.showError(ApiClient.unwrap(error).userMessage()); // e.g. username taken
                        }
                    }));
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ role

    @FXML
    private void onChangeRole() {
        UserInfo u = table.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        ComboBox<UserRole> role = roleBox(u.role());
        Label hint = new Label(u.username() + " will be signed out on every terminal and gets the new role's "
                + "screens at the next sign-in.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");
        FormDialog dialog = new FormDialog(table.getScene().getWindow(), "Assign role to " + u.username(),
                new VBox(12, fieldLabel("Role"), role, hint), "Assign role");
        dialog.onSubmit(() -> {
            dialog.setBusy(true);
            context.admin().changeRole(u.username(), role.getValue())
                    .whenComplete((updated, error) -> Platform.runLater(() -> {
                        dialog.setBusy(false);
                        if (error == null) {
                            dialog.close();
                            message("success-banner", updated.username() + " is now " + updated.role().displayName() + ".");
                            refresh();
                        } else {
                            dialog.showError(ApiClient.unwrap(error).userMessage()); // e.g. last admin
                        }
                    }));
        });
        dialog.show();
    }

    // ---------------------------------------------------------------- active

    @FXML
    private void onToggleActive() {
        UserInfo u = table.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        boolean activate = !u.active();
        boolean ok = ConfirmDialog.ask(table.getScene().getWindow(), (activate ? "Activate " : "Deactivate ") + u.username(),
                activate ? "The operator will be able to sign in again."
                        : "The operator is signed out on every terminal immediately and cannot sign in.",
                List.of(new String[]{"Account", u.username()}, new String[]{"Role", u.role().displayName()}),
                activate ? "Activate" : "Deactivate");
        if (!ok) {
            return;
        }
        context.admin().setActive(u.username(), activate).whenComplete((updated, error) -> Platform.runLater(() -> {
            if (error == null) {
                message("success-banner", updated.username() + " is now " + (updated.active() ? "active." : "inactive."));
                refresh();
            } else {
                ApiException ex = ApiClient.unwrap(error);
                if (!ex.isUnauthorized()) {
                    message("error-banner", ex.userMessage());
                }
            }
        }));
    }

    // --------------------------------------------------------------- helpers

    private void updateButtons(UserInfo u) {
        boolean me = u != null && isMe(u);
        roleButton.setDisable(u == null || me);          // the server refuses these too
        activeButton.setDisable(u == null || me);
        activeButton.setText(u != null && !u.active() ? "Activate" : "Deactivate");
    }

    private boolean isMe(UserInfo u) {
        return context.session().current().map(s -> s.username().equals(u.username())).orElse(false);
    }

    private ComboBox<UserRole> roleBox(UserRole selected) {
        ComboBox<UserRole> box = new ComboBox<>(FXCollections.observableArrayList(UserRole.values()));
        box.setValue(selected);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setCellFactory(v -> new RoleCell());
        box.setButtonCell(new RoleCell());
        return box;
    }

    private void message(String style, String text) {
        actionMessage.getStyleClass().setAll("label", style);
        actionMessage.setText(text);
        actionMessage.setVisible(true);
        actionMessage.setManaged(true);
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static long count(List<UserInfo> list, UserRole role) {
        return list.stream().filter(u -> u.role() == role).count();
    }

    static String roleStyle(String role) {
        return switch (role) {
            case "ADMIN" -> "badge-purple";
            case "ENTRY OPERATOR" -> "badge-blue";
            default -> "badge-green";
        };
    }

    private static final class RoleCell extends ListCell<UserRole> {
        @Override
        protected void updateItem(UserRole r, boolean empty) {
            super.updateItem(r, empty);
            setText(empty || r == null ? null : r.displayName());
        }
    }
}

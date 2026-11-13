package com.smartpark.client.ui.admin;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.Category;
import com.smartpark.client.net.dto.ApiDtos.CreateSlotRequest;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.net.dto.ApiDtos.UpdateSlotRequest;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ConfirmDialog;
import com.smartpark.client.ui.component.FormDialog;
import com.smartpark.client.ui.component.Tables;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Slot management (ADMIN): add slots, edit type / position / compatible vehicle categories,
 * disable and enable. Every change goes through the server, which checks the ADMIN role,
 * unique codes and positions, and refuses to edit or disable an OCCUPIED slot.
 */
public class SlotManagementController extends LiveView {

    private static final List<String> SLOT_TYPES = List.of("STANDARD", "LARGE", "MOTORCYCLE", "EV_CHARGING", "ACCESSIBLE");

    @FXML private Label errorLabel;
    @FXML private Label actionMessage;
    @FXML private Label summaryLabel;
    @FXML private TableView<SlotInfo> table;
    @FXML private Button editButton;
    @FXML private Button toggleButton;

    private final ObservableList<SlotInfo> slots = FXCollections.observableArrayList();
    private List<Category> categories = List.of();

    public SlotManagementController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        hide(errorLabel);
        hide(actionMessage);
        table.setItems(slots);
        table.getColumns().setAll(List.of(
                Tables.<SlotInfo>column("Slot", SlotInfo::slotCode, 70),
                Tables.<SlotInfo>column("Type", s -> s.slotType().replace('_', ' '), 110),
                Tables.<SlotInfo>column("Floor", s -> String.valueOf(s.floor()), 55),
                Tables.<SlotInfo>column("Row", SlotInfo::row, 50),
                Tables.<SlotInfo>column("Bay", s -> String.valueOf(s.column()), 50),
                Tables.<SlotInfo>badge("Status", SlotInfo::status, SlotManagementController::statusStyle, 110),
                Tables.<SlotInfo>column("Compatible vehicle categories",
                        s -> String.join(", ", s.supportedVehicleCategories()).replace('_', ' '), 240),
                Tables.<SlotInfo>column("Current ticket", s -> s.currentTicketId() == null ? "" : s.currentTicketId(), 160)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(t -> {
            TableRow<SlotInfo> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> updateButtons(s));
        updateButtons(null);
    }

    @Override
    protected void refresh() {
        load(context.parking().categories(), list -> categories = list, errorLabel);
        load(context.parking().allSlots(), list -> {
            String selected = table.getSelectionModel().getSelectedItem() == null ? null
                    : table.getSelectionModel().getSelectedItem().slotCode();
            slots.setAll(list);
            list.stream().filter(s -> s.slotCode().equals(selected)).findFirst()
                    .ifPresent(s -> table.getSelectionModel().select(s));
            summaryLabel.setText(list.size() + " slots  ·  " + count(list, "AVAILABLE") + " available  ·  "
                    + count(list, "OCCUPIED") + " occupied  ·  " + count(list, "DISABLED") + " disabled");
        }, errorLabel);
    }

    /** Any slot change from any terminal (entries, exits, other admins) refreshes the table. */
    @Override
    protected void onEvent(RealtimeEvent e) {
        if (isParkingEvent(e)) {
            refreshSoon();
        }
    }

    // --------------------------------------------------------------- actions

    @FXML
    private void onAdd() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        SlotInfo s = table.getSelectionModel().getSelectedItem();
        if (s != null) {
            openForm(s);
        }
    }

    @FXML
    private void onToggle() {
        SlotInfo s = table.getSelectionModel().getSelectedItem();
        if (s == null || "OCCUPIED".equals(s.status())) {
            return;
        }
        boolean disable = "AVAILABLE".equals(s.status());
        boolean ok = ConfirmDialog.ask(table.getScene().getWindow(), (disable ? "Disable" : "Enable") + " slot " + s.slotCode(),
                disable ? "The slot will no longer be offered to entry operators." : "The slot becomes available again.",
                List.of(new String[]{"Slot", s.slotCode()}, new String[]{"Type", s.slotType()}),
                disable ? "Disable slot" : "Enable slot");
        if (!ok) {
            return;
        }
        CompletableFuture<SlotInfo> call = disable ? context.admin().disableSlot(s.slotCode())
                : context.admin().enableSlot(s.slotCode());
        call.whenComplete((updated, error) -> Platform.runLater(() -> {
            if (error == null) {
                message("success-banner", "Slot " + updated.slotCode() + " is now " + updated.status() + ".");
                refresh();
            } else {
                ApiException ex = ApiClient.unwrap(error);
                if (!ex.isUnauthorized()) {
                    message("error-banner", ex.userMessage());
                }
            }
        }));
    }

    // ------------------------------------------------------------- the form

    /** @param existing null to add a new slot */
    private void openForm(SlotInfo existing) {
        TextField code = new TextField(existing == null ? "" : existing.slotCode());
        code.setPromptText("e.g. D-01");
        code.setDisable(existing != null); // the code identifies the slot and cannot change
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(SLOT_TYPES));
        type.setValue(existing == null ? "STANDARD" : existing.slotType());
        type.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> floor = new Spinner<>(-5, 50, existing == null ? 0 : existing.floor());
        floor.setEditable(true);
        TextField row = new TextField(existing == null ? "" : existing.row());
        row.setPromptText("e.g. D");
        Spinner<Integer> column = new Spinner<>(1, 999, existing == null ? 1 : existing.column());
        column.setEditable(true);

        CheckBox derive = new CheckBox("Use every category compatible with the slot type");
        FlowPane boxes = new FlowPane(12, 8);
        List<CheckBox> categoryBoxes = new ArrayList<>();
        for (Category c : categories) {
            CheckBox cb = new CheckBox(c.displayName());
            cb.setUserData(c.categoryName());
            cb.setSelected(existing != null && existing.supportedVehicleCategories().contains(c.categoryName()));
            categoryBoxes.add(cb);
            boxes.getChildren().add(cb);
        }
        derive.setSelected(existing == null);
        boxes.disableProperty().bind(derive.selectedProperty());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, fieldLabel("Slot code"), code);
        grid.addRow(1, fieldLabel("Slot type"), type);
        grid.addRow(2, fieldLabel("Floor"), floor);
        grid.addRow(3, fieldLabel("Row"), row);
        grid.addRow(4, fieldLabel("Bay (column)"), column);
        VBox form = new VBox(12, grid, fieldLabel("Compatible vehicle categories"), derive, boxes);

        FormDialog dialog = new FormDialog(table.getScene().getWindow(),
                existing == null ? "Add parking slot" : "Edit slot " + existing.slotCode(), form,
                existing == null ? "Add slot" : "Save changes");
        dialog.onSubmit(() -> {
            String rowText = row.getText() == null ? "" : row.getText().trim().toUpperCase(Locale.ROOT);
            String codeText = code.getText() == null ? "" : code.getText().trim().toUpperCase(Locale.ROOT);
            List<String> chosen = derive.isSelected() ? List.of() : categoryBoxes.stream()
                    .filter(CheckBox::isSelected).map(cb -> (String) cb.getUserData()).toList();
            if (existing == null && codeText.isEmpty()) {
                dialog.showError("Enter a slot code, e.g. D-01.");
                return;
            }
            if (!rowText.matches("[A-Z]{1,3}")) {
                dialog.showError("Row must be 1-3 letters, e.g. D.");
                return;
            }
            if (!derive.isSelected() && chosen.isEmpty()) {
                dialog.showError("Select at least one vehicle category, or use the slot type's defaults.");
                return;
            }
            dialog.hideError();
            dialog.setBusy(true);
            CompletableFuture<SlotInfo> call = existing == null
                    ? context.admin().createSlot(new CreateSlotRequest(codeText, type.getValue(), chosen,
                    floor.getValue(), rowText, column.getValue()))
                    : context.admin().updateSlot(existing.slotCode(), new UpdateSlotRequest(type.getValue(), chosen,
                    floor.getValue(), rowText, column.getValue()));
            call.whenComplete((saved, error) -> Platform.runLater(() -> {
                dialog.setBusy(false);
                if (error == null) {
                    dialog.close();
                    message("success-banner", "Slot " + saved.slotCode() + " saved (" + saved.slotType()
                            + ", floor " + saved.floor() + " row " + saved.row() + " bay " + saved.column() + ").");
                    refresh();
                } else {
                    ApiException ex = ApiClient.unwrap(error);
                    dialog.showError(ex.userMessage()); // e.g. duplicate code/position, slot occupied
                }
            }));
        });
        dialog.show();
    }

    // --------------------------------------------------------------- helpers

    private void updateButtons(SlotInfo s) {
        editButton.setDisable(s == null);
        toggleButton.setDisable(s == null || "OCCUPIED".equals(s.status()));
        toggleButton.setText(s != null && "DISABLED".equals(s.status()) ? "Enable slot" : "Disable slot");
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

    private static long count(List<SlotInfo> list, String status) {
        return list.stream().filter(s -> status.equals(s.status())).count();
    }

    static String statusStyle(String status) {
        return switch (status) {
            case "AVAILABLE" -> "badge-green";
            case "OCCUPIED" -> "badge-red";
            default -> "badge-grey";
        };
    }
}

package com.smartpark.client.ui.parking;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.Category;
import com.smartpark.client.net.dto.ApiDtos.EntryRequest;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.parking.ReceiptData;
import com.smartpark.client.parking.SlotMapModel;
import com.smartpark.client.realtime.ConnectionState;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.service.ParkingService;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ActivityFeed;
import com.smartpark.client.ui.component.ConfirmDialog;
import com.smartpark.client.ui.component.SlotMapView;
import com.smartpark.client.ui.component.StatCard;
import com.smartpark.client.util.VehicleNumbers;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Entry Operator dashboard.
 *
 * <h2>Workflow</h2>
 * <ol>
 *     <li>Registration form: vehicle number, category, optional notes.</li>
 *     <li>Choosing a category asks the server for the slots available to it
 *         ({@code GET /api/slots/available?category=}); those slots are highlighted on the map.</li>
 *     <li>Clicking a highlighted slot shows its code and the vehicle details.</li>
 *     <li><b>Confirm allocation</b> opens a confirmation dialog. On OK, the request goes to the
 *         server ({@code POST /api/parking/entry}).</li>
 *     <li>The server's answer is final. On 201 the ticket ID is shown, the map is updated and the
 *         receipt is prepared. On 409 (another terminal was faster) the map is reloaded.</li>
 * </ol>
 *
 * <h2>Who decides</h2>
 * The map is only a <i>view</i> of the server state. Nothing is ever marked occupied locally
 * until the server has confirmed it, and a slot that looks free is still only a request.
 *
 * <h2>Separation</h2>
 * This controller handles screen state only. REST calls go through {@link ParkingService},
 * live events arrive through the {@link LiveView} subscription, and map rules live in the
 * UI-free {@link SlotMapModel}.
 */
public class VehicleEntryController extends LiveView {

    private static final int NOTES_MAX = 200;

    // KPI strip
    @FXML private FlowPane kpis;
    // form
    @FXML private TextField vehicleField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private TextArea notesArea;
    @FXML private Label notesCounter;
    @FXML private Label selectedSlotLabel;
    @FXML private Label selectedSlotHint;
    @FXML private Label selectedVehicleLabel;
    @FXML private Label selectedCategoryLabel;
    @FXML private Button confirmButton;
    @FXML private Button clearButton;
    @FXML private Label formError;
    @FXML private Label connectionHint;
    @FXML private VBox lastTicketBox;
    @FXML private Label lastTicketLabel;
    @FXML private Label lastTicketDetail;
    // reprint
    @FXML private TextField reprintField;
    @FXML private Button reprintButton;
    @FXML private Label reprintError;
    // map + feed
    @FXML private Label errorLabel;
    @FXML private Label mapSummary;
    @FXML private VBox legendHolder;
    @FXML private StackPane mapHolder;
    @FXML private StackPane feedHolder;

    private final ParkingService parking;
    private final SlotMapModel map = new SlotMapModel();
    private final StatCard totalCard = new StatCard("Total slots", "accent-slate");
    private final StatCard availableCard = new StatCard("Available", "accent-green");
    private final StatCard occupiedCard = new StatCard("Occupied", "accent-red");
    private final StatCard vehicleCard = new StatCard("Current vehicle", "accent-blue");
    private final ActivityFeed feed = new ActivityFeed("Gate activity",
            Set.of("VEHICLE_ENTERED", "VEHICLE_EXITED", "PARKING_MAP_UPDATED"));
    private final ChangeListener<ConnectionState> connectionListener = (obs, old, s) -> updateConfirmState();
    private boolean busy;

    public VehicleEntryController(AppContext context) {
        super(context);
        this.parking = context.parking();
    }

    @FXML
    private void initialize() {
        kpis.getChildren().addAll(totalCard, availableCard, occupiedCard, vehicleCard);
        mapHolder.getChildren().add(new SlotMapView(map, true));
        legendHolder.getChildren().add(SlotMapView.legend(true));
        feedHolder.getChildren().add(feed);
        hide(errorLabel);
        hide(formError);
        hide(reprintError);
        reprintField.setTextFormatter(new TextFormatter<String>(change -> {   // ticket IDs are upper case
            change.setText(change.getText().toUpperCase(Locale.ROOT));
            return change.getControlNewText().length() <= 30 ? change : null;
        }));
        lastTicketBox.setVisible(false);
        lastTicketBox.setManaged(false);

        vehicleField.setTextFormatter(new TextFormatter<String>(change -> {   // upper-case, max 20
            change.setText(change.getText().toUpperCase(Locale.ROOT));
            return change.getControlNewText().length() <= 20 ? change : null;
        }));
        vehicleField.textProperty().addListener((obs, o, n) -> showSelection());

        notesArea.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= NOTES_MAX ? change : null));
        notesArea.textProperty().addListener((obs, o, n) ->
                notesCounter.setText((n == null ? 0 : n.length()) + " / " + NOTES_MAX));
        notesCounter.setText("0 / " + NOTES_MAX);

        categoryBox.setCellFactory(v -> new CategoryCell());
        categoryBox.setButtonCell(new CategoryCell());
        categoryBox.valueProperty().addListener((obs, o, c) -> onCategoryChanged(c));

        map.addListener(new SlotMapModel.Listener() {
            @Override
            public void onChanged() {
                showSelection();
            }

            @Override
            public void onSelectionLost(SlotInfo slot) {
                if (busy) {
                    // Our own request is in flight: the server broadcasts SLOT_OCCUPIED before the HTTP
                    // response arrives. If another terminal won instead, the request fails with 409.
                    return;
                }
                showError("Slot " + slot.slotCode() + " was just " + ("DISABLED".equals(slot.status())
                        ? "disabled" : "taken at another terminal") + ". Please choose another slot.");
            }
        });
        showSelection();
    }

    @Override
    public void onShow() {
        context.realtime().stateProperty().addListener(connectionListener);
        super.onShow();
        Platform.runLater(vehicleField::requestFocus);
    }

    @Override
    public void onHide() {
        context.realtime().stateProperty().removeListener(connectionListener);
        super.onHide();
    }

    // ================================================================ data

    /** Full load: categories, every slot, then the server's availability for the chosen category. */
    @Override
    protected void refresh() {
        load(parking.categories(), this::showCategories, errorLabel);
        load(parking.allSlots(), slots -> {
            map.load(slots);
            loadAvailability();
        }, errorLabel);
    }

    private void onCategoryChanged(Category category) {
        map.setCategory(category == null ? null : category.categoryName());
        hide(formError);
        loadAvailability();
    }

    /** Asks the server which slots are available for the selected category (requirement 2). */
    private void loadAvailability() {
        Category category = categoryBox.getValue();
        if (category == null) {
            return;
        }
        load(parking.availableSlots(category.categoryName()), available -> {
            if (!category.equals(categoryBox.getValue())) {
                return; // the operator has already picked another category
            }
            boolean stale = map.syncAvailability(category.categoryName(), available);
            if (stale) {
                load(parking.allSlots(), map::load, errorLabel); // local map was behind: redraw from server
            }
        }, errorLabel);
    }

    private void showCategories(List<Category> categories) {
        String current = categoryBox.getValue() == null ? null : categoryBox.getValue().categoryName();
        categoryBox.setItems(FXCollections.observableArrayList(categories));
        categories.stream().filter(c -> c.categoryName().equals(current)).findFirst().ifPresentOrElse(
                categoryBox::setValue,
                () -> categories.stream().filter(c -> c.categoryName().equals("CAR")).findFirst()
                        .ifPresent(categoryBox::setValue));
    }

    /** Requirement 7: live updates. A slot released at the exit gate turns green here immediately. */
    @Override
    protected void onEvent(RealtimeEvent e) {
        feed.add(e);
        switch (e.type()) {
            case "SLOT_OCCUPIED", "SLOT_RELEASED" -> parking.slotOf(e).ifPresent(map::apply);
            case "PARKING_MAP_UPDATED" -> parking.slotOf(e).ifPresentOrElse(map::apply, this::refreshSoon);
            default -> { }
        }
    }

    // ============================================================= actions

    @FXML
    private void onConfirm() {
        var problem = VehicleNumbers.validate(vehicleField.getText());
        if (problem.isPresent()) {
            showError(problem.get());
            vehicleField.requestFocus();
            return;
        }
        Category category = categoryBox.getValue();
        if (category == null) {
            showError("Choose the vehicle category.");
            return;
        }
        SlotInfo slot = map.selected().orElse(null);
        if (slot == null) {
            showError("Select a highlighted slot on the map.");
            return;
        }
        hide(formError);

        String vehicle = VehicleNumbers.normalize(vehicleField.getText());
        String notes = notesArea.getText() == null || notesArea.getText().isBlank() ? null : notesArea.getText().trim();
        List<String[]> rows = new ArrayList<>(List.of(
                new String[]{"Slot", slot.slotCode()},
                new String[]{"Location", location(slot)},
                new String[]{"Vehicle", vehicle},
                new String[]{"Category", category.displayName()},
                new String[]{"Terminal", context.config().terminalName()}));
        if (notes != null) {
            rows.add(new String[]{"Notes", notes});
        }
        boolean ok = ConfirmDialog.ask(vehicleField.getScene().getWindow(), "Confirm parking allocation",
                "The server will make the final decision. If another terminal takes this slot first, "
                        + "you will be asked to choose again.", rows, "Allocate slot " + slot.slotCode());
        if (!ok) {
            return;
        }
        // A live event may have changed the map while the dialog was open.
        if (map.selected().map(s -> !s.slotCode().equals(slot.slotCode())).orElse(true)) {
            showError("Slot " + slot.slotCode() + " is no longer available. Please choose another slot.");
            return;
        }

        setBusy(true);
        parking.registerEntry(new EntryRequest(vehicleField.getText().trim(), category.categoryName(), slot.slotCode(), notes))
                .whenComplete((session, error) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (error == null) {
                        onAllocated(session, slot);
                    } else {
                        onAllocationRejected(ApiClient.unwrap(error), slot);
                    }
                }));
    }

    /** Requirement 5: server confirmed. Show the ticket, update the map, prepare the receipt. */
    private void onAllocated(ParkingSession session, SlotInfo slot) {
        hide(formError);
        lastTicketLabel.setText(session.ticketId());
        lastTicketDetail.setText(session.vehicleNumber() + " → slot " + session.parkingSlotCode()
                + (session.notes() == null ? "" : "  ·  " + session.notes()));
        lastTicketBox.setVisible(true);
        lastTicketBox.setManaged(true);

        // Clear the selection first, so marking the slot occupied is not reported as "taken by someone else".
        map.clearSelection();
        // The server has confirmed: show the slot occupied now (the SLOT_OCCUPIED event will say the same).
        map.apply(new SlotInfo(slot.slotCode(), slot.slotType(), slot.supportedVehicleCategories(), "OCCUPIED",
                slot.floor(), slot.row(), slot.column(), session.ticketId()));

        vehicleField.clear();
        notesArea.clear();
        // Only a successful (201) allocation reaches this point, so only it produces a receipt.
        ReceiptDialog.show(vehicleField.getScene().getWindow(), context, ReceiptData.of(session, slot), 0, false,
                this::readyForNextVehicle);
    }

    /**
     * Reprint by ticket ID: fetches the ticket stored on the server ({@code GET /api/parking/receipts/{id}})
     * and opens the preview. Read-only on the server: no parking session is created.
     */
    @FXML
    private void onReprint() {
        String ticket = reprintField.getText() == null ? "" : reprintField.getText().trim();
        if (ticket.isEmpty()) {
            showReprintError("Enter the ticket ID to reprint.");
            reprintField.requestFocus();
            return;
        }
        hide(reprintError);
        reprintButton.setDisable(true);
        parking.receipt(ticket).whenComplete((receipt, error) -> Platform.runLater(() -> {
            reprintButton.setDisable(false);
            if (error == null) {
                reprintField.clear();
                ReceiptDialog.show(reprintField.getScene().getWindow(), context, ReceiptData.of(receipt),
                        receipt.printCount(), true, () -> reprintField.requestFocus());
                return;
            }
            ApiException e = ApiClient.unwrap(error);
            if (e.isUnauthorized()) {
                return; // handled globally (back to login)
            }
            showReprintError(switch (e.code()) {
                case "NOT_FOUND" -> "No ticket " + ticket + " was found. Check the ticket ID.";
                case ApiException.SERVER_UNREACHABLE -> "The server cannot be reached. Try again in a moment.";
                default -> e.userMessage();
            });
        }));
    }

    private void showReprintError(String message) {
        reprintError.setText(message);
        reprintError.setVisible(true);
        reprintError.setManaged(true);
    }

    /** After the receipt closes: scroll back to the top and put the cursor in the vehicle field. */
    private void readyForNextVehicle() {
        scrollToTop(vehicleField);
        vehicleField.requestFocus();
    }

    private void onAllocationRejected(ApiException e, SlotInfo slot) {
        switch (e.code()) {
            case "SLOT_UNAVAILABLE", "CONCURRENT_UPDATE" -> {
                showError("The server did not allocate " + slot.slotCode() + ": " + e.getMessage()
                        + ". The map has been refreshed; please choose another slot.");
                map.clearSelection();
                refresh();
            }
            case "VEHICLE_ALREADY_PARKED" -> showError(e.getMessage() + ". Check the vehicle number.");
            case "SLOT_NOT_COMPATIBLE" -> {
                showError(e.getMessage());
                refresh();
            }
            case "VALIDATION_FAILED" -> showError(e.userMessage());
            case ApiException.SERVER_UNREACHABLE -> showError("The server cannot be reached. Nothing was saved; try again.");
            default -> {
                if (!e.isUnauthorized()) { // 401 is handled globally (back to login)
                    showError(e.userMessage());
                }
            }
        }
    }

    @FXML
    private void onClear() {
        vehicleField.clear();
        notesArea.clear();
        map.clearSelection();
        hide(formError);
        vehicleField.requestFocus();
    }

    // ============================================================= display

    /** Requirement 4 + 6: selected slot, vehicle details and the KPI strip. */
    private void showSelection() {
        SlotInfo slot = map.selected().orElse(null);
        String vehicleText = vehicleField.getText() == null || vehicleField.getText().isBlank()
                ? null : VehicleNumbers.normalize(vehicleField.getText());
        Category category = categoryBox.getValue();

        selectedSlotLabel.setText(slot == null ? "–" : slot.slotCode());
        selectedSlotHint.setText(slot == null ? "Click a highlighted slot on the map" : location(slot));
        selectedVehicleLabel.setText(vehicleText == null ? "–" : vehicleText);
        selectedCategoryLabel.setText(category == null ? "–" : category.displayName());

        long total = map.count("AVAILABLE") + map.count("OCCUPIED") + map.count("DISABLED");
        totalCard.setValue(String.valueOf(total));
        totalCard.setHint(map.count("DISABLED") + " disabled");
        availableCard.setValue(String.valueOf(map.count("AVAILABLE")));
        availableCard.setHint(category == null ? "all vehicle types" : map.freeCount() + " fit " + category.displayName());
        occupiedCard.setValue(String.valueOf(map.count("OCCUPIED")));
        vehicleCard.setValue(vehicleText == null ? "–" : vehicleText);
        vehicleCard.setHint((category == null ? "no category" : category.displayName())
                + (slot == null ? "" : " → " + slot.slotCode()));

        mapSummary.setText(category == null ? map.count("AVAILABLE") + " available"
                : map.freeCount() + " available for " + category.displayName());
        updateConfirmState();
    }

    private void updateConfirmState() {
        boolean live = context.realtime().stateProperty().get() == ConnectionState.CONNECTED;
        connectionHint.setVisible(!live);
        connectionHint.setManaged(!live);
        confirmButton.setDisable(busy || !live || map.selected().isEmpty()
                || vehicleField.getText() == null || vehicleField.getText().isBlank());
    }

    private void setBusy(boolean value) {
        busy = value;
        confirmButton.setText(value ? "Waiting for server..." : "Confirm allocation");
        vehicleField.setDisable(value);
        categoryBox.setDisable(value);
        notesArea.setDisable(value);
        clearButton.setDisable(value);
        updateConfirmState();
    }

    private void showError(String message) {
        formError.setText(message);
        formError.setVisible(true);
        formError.setManaged(true);
    }

    private static String location(SlotInfo s) {
        return "Floor " + s.floor() + " · Row " + s.row() + " · Bay " + s.column() + " · " + s.slotType().replace('_', ' ');
    }

    private static final class CategoryCell extends ListCell<Category> {
        @Override
        protected void updateItem(Category c, boolean empty) {
            super.updateItem(c, empty);
            setText(empty || c == null ? null : c.displayName());
        }
    }
}

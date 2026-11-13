package com.smartpark.client.ui.parking;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.ExitLookup;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.parking.ExitMessages;
import com.smartpark.client.parking.SlotMapModel;
import com.smartpark.client.realtime.ConnectionState;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.service.ParkingService;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ActivityFeed;
import com.smartpark.client.ui.component.ConfirmDialog;
import com.smartpark.client.ui.component.SlotMapView;
import com.smartpark.client.util.Formats;
import com.smartpark.client.util.VehicleNumbers;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Exit Operator dashboard.
 *
 * <h2>Workflow</h2>
 * <ol>
 *     <li>Enter (or click in the "currently parked" list) a vehicle number and <b>Find</b>:
 *         {@code GET /api/parking/active/{vehicle}}.</li>
 *     <li>The session card shows vehicle, category, ticket, slot, entry time and the current status;
 *         the slot is highlighted on the live map.</li>
 *     <li><b>Confirm vehicle exit</b> asks for confirmation, then sends {@code POST /api/parking/exit}.
 *         The server completes the session and releases the slot as one transactional operation.</li>
 *     <li>Only after the server reports success does this screen show the slot as free. The server
 *         then broadcasts SLOT_RELEASED + VEHICLE_EXITED, so the entry terminals and the admin
 *         dashboard update at the same moment.</li>
 * </ol>
 *
 * <h2>Failure handling</h2>
 * Invalid numbers are rejected locally. "Not parked" distinguishes "never parked" from "already
 * checked out". If the connection drops (or the server returns 5xx) <i>after</i> the exit request
 * was sent, the outcome is unknown, so the screen <b>asks the server</b> for the ticket's status
 * before saying anything. The operator is never told "done" unless the server says so.
 */
public class VehicleExitController extends LiveView {

    // search
    @FXML private TextField searchField;
    @FXML private Button findButton;
    @FXML private Label searchMessage;
    // session details
    @FXML private VBox detailsCard;
    @FXML private Label slotCodeLabel;
    @FXML private Label slotLocationLabel;
    @FXML private Label statusBadge;
    @FXML private Label vehicleLabel;
    @FXML private Label categoryLabel;
    @FXML private Label ticketLabel;
    @FXML private Label enteredLabel;
    @FXML private Label durationLabel;
    @FXML private Label operatorLabel;
    @FXML private Label notesLabel;
    @FXML private Label notesCaption;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;
    @FXML private Label connectionHint;
    // success
    @FXML private VBox resultCard;
    @FXML private Label resultTitle;
    @FXML private Label resultVehicle;
    @FXML private Label resultSlot;
    @FXML private Label resultTicket;
    @FXML private Label resultEntered;
    @FXML private Label resultExited;
    @FXML private Label resultDuration;
    // lists, map, feed
    @FXML private Label errorLabel;
    @FXML private TableView<ParkingSession> parkedTable;
    @FXML private Label parkedCount;
    @FXML private VBox legendHolder;
    @FXML private StackPane mapHolder;
    @FXML private StackPane feedHolder;

    private final ParkingService parking;
    private final SlotMapModel map = new SlotMapModel();
    private final ObservableList<ParkingSession> parked = FXCollections.observableArrayList();
    private final ActivityFeed feed = new ActivityFeed("Gate activity", Set.of("VEHICLE_ENTERED", "VEHICLE_EXITED"));
    private final ChangeListener<ConnectionState> connectionListener = (obs, o, s) -> updateConfirmState();
    private ExitLookup current;
    private boolean busy;

    public VehicleExitController(AppContext context) {
        super(context);
        this.parking = context.parking();
    }

    @FXML
    private void initialize() {
        mapHolder.getChildren().add(new SlotMapView(map, false));
        legendHolder.getChildren().add(SlotMapView.legend(false));
        feedHolder.getChildren().add(feed);
        hide(errorLabel);
        hide(searchMessage);
        show(resultCard, false);
        showSession(null);

        searchField.setTextFormatter(new TextFormatter<String>(change -> {   // upper-case, max 20
            change.setText(change.getText().toUpperCase(Locale.ROOT));
            return change.getControlNewText().length() <= 20 ? change : null;
        }));
        findButton.setDefaultButton(true);

        parkedTable.setItems(parked);
        parkedTable.getColumns().setAll(List.of(
                column("Vehicle", ParkingSession::vehicleNumber, 110),
                column("Slot", ParkingSession::parkingSlotCode, 60),
                column("Category", s -> s.vehicleCategory().replace('_', ' '), 110),
                column("Entered", s -> Formats.dateTime(s.entryTime()), 110)));
        parkedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        parkedTable.setPlaceholder(new Label("No vehicles inside"));
        parkedTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null && !busy) {
                searchField.setText(s.vehicleNumber());
                onFind();
            }
        });
    }

    @Override
    public void onShow() {
        context.realtime().stateProperty().addListener(connectionListener);
        super.onShow();
        Platform.runLater(searchField::requestFocus);
    }

    @Override
    public void onHide() {
        context.realtime().stateProperty().removeListener(connectionListener);
        super.onHide();
    }

    // ================================================================ data

    @Override
    protected void refresh() {
        load(parking.allSlots(), map::load, errorLabel);
        load(parking.activeSessions(), list -> {
            parked.setAll(list);
            parkedCount.setText(list.size() + " inside");
        }, errorLabel);
    }

    /** Live updates from every terminal (including our own exits, which arrive as server events). */
    @Override
    protected void onEvent(RealtimeEvent e) {
        feed.add(e);
        switch (e.type()) {
            case "SLOT_OCCUPIED", "SLOT_RELEASED" -> parking.slotOf(e).ifPresent(map::apply);
            case "PARKING_MAP_UPDATED" -> parking.slotOf(e).ifPresentOrElse(map::apply, this::refreshSoon);
            case "VEHICLE_ENTERED" -> refreshSoon();
            case "VEHICLE_EXITED" -> {
                refreshSoon();
                // Another exit terminal checked out the vehicle shown here.
                if (current != null && !busy && current.session().ticketId().equals(e.payloadText("ticketId"))) {
                    showSession(null);
                    message(new ExitMessages.Message(ExitMessages.Kind.INFO, e.payloadText("vehicleNumber")
                            + " was just checked out at " + (e.terminal() == null ? "another terminal" : e.terminal())
                            + " by " + e.actor() + "."));
                }
            }
            default -> { }
        }
    }

    // ============================================================= search

    @FXML
    private void onFind() {
        show(resultCard, false);
        var problem = VehicleNumbers.validate(searchField.getText());           // invalid number: no request
        if (problem.isPresent()) {
            showSession(null);
            message(new ExitMessages.Message(ExitMessages.Kind.ERROR, problem.get()));
            searchField.requestFocus();
            return;
        }
        hide(searchMessage);
        String vehicle = VehicleNumbers.normalize(searchField.getText());
        findButton.setDisable(true);
        findButton.setText("Searching...");
        parking.findActiveSession(vehicle).whenComplete((lookup, error) -> Platform.runLater(() -> {
            findButton.setDisable(false);
            findButton.setText("Find");
            if (error == null) {
                showSession(lookup);
                return;
            }
            showSession(null);
            ApiException e = ApiClient.unwrap(error);
            if ("NO_ACTIVE_SESSION".equals(e.code())) {
                explainNotParked(vehicle);
            } else if (!e.isUnauthorized()) {
                message(ExitMessages.searchFailed(e));
            }
        }));
    }

    /** "Not parked": never parked here, or already checked out? Ask the server for the last completed session. */
    private void explainNotParked(String vehicle) {
        parking.lastCompletedSession(vehicle).whenComplete((last, error) -> Platform.runLater(() ->
                message(ExitMessages.notParked(vehicle, error == null ? last.orElse(null) : null, ZoneId.systemDefault()))));
    }

    // ============================================================= exit

    @FXML
    private void onConfirmExit() {
        if (current == null) {
            return;
        }
        ParkingSession s = current.session();
        boolean ok = ConfirmDialog.ask(searchField.getScene().getWindow(), "Confirm vehicle exit",
                "The session will be completed and slot " + s.parkingSlotCode() + " released for the next vehicle.",
                List.of(new String[]{"Vehicle", s.vehicleNumber()},
                        new String[]{"Slot", s.parkingSlotCode()},
                        new String[]{"Ticket", s.ticketId()},
                        new String[]{"Entered", Formats.dateTime(s.entryTime())},
                        new String[]{"Time parked", Formats.minutes((double) current.minutesParked())}),
                "Confirm exit");
        if (!ok || current == null) {
            return; // cancelled, or another terminal checked the vehicle out while the dialog was open
        }
        ExitLookup lookup = current;
        setBusy(true);
        hide(searchMessage);
        parking.confirmExit(lookup.session().vehicleNumber()).whenComplete((done, error) -> Platform.runLater(() -> {
            if (error == null) {
                setBusy(false);
                onExitCompleted(done, lookup);
                return;
            }
            ApiException e = ApiClient.unwrap(error);
            if (ExitMessages.outcomeUnknown(e)) {
                verifyOutcome(lookup);         // keep busy until we know
            } else {
                setBusy(false);
                if ("NO_ACTIVE_SESSION".equals(e.code())) {
                    showSession(null);
                }
                if (!e.isUnauthorized()) {
                    message(ExitMessages.exitRejected(e, lookup.session().vehicleNumber()));
                }
            }
        }));
    }

    /**
     * The exit request may or may not have been applied (connection lost or 5xx). Ask the server
     * for the ticket's status: COMPLETED means it worked, ACTIVE means it did not.
     */
    private void verifyOutcome(ExitLookup lookup) {
        message(ExitMessages.verifying());
        parking.sessionByTicket(lookup.session().ticketId()).whenComplete((s, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                message(ExitMessages.verificationFailed());
            } else if ("COMPLETED".equals(s.status())) {
                onExitCompleted(s, lookup);
            } else {
                message(ExitMessages.verifiedNotRecorded());
            }
        }));
    }

    /** The server has completed the exit: only now is the slot shown as free. */
    private void onExitCompleted(ParkingSession done, ExitLookup lookup) {
        SlotInfo slot = lookup.slot();
        if (slot != null) {
            map.apply(new SlotInfo(slot.slotCode(), slot.slotType(), slot.supportedVehicleCategories(), "AVAILABLE",
                    slot.floor(), slot.row(), slot.column(), null));
        }
        showSession(null);
        hide(searchMessage);
        resultTitle.setText("Exit completed - slot " + done.parkingSlotCode() + " is free");
        resultVehicle.setText(done.vehicleNumber() + "  (" + done.vehicleCategory().replace('_', ' ') + ")");
        resultSlot.setText(done.parkingSlotCode() + " released");
        resultTicket.setText(done.ticketId() + "  ·  COMPLETED");
        resultEntered.setText(Formats.dateTime(done.entryTime()));
        resultExited.setText(Formats.dateTime(done.exitTime()) + "  by " + done.exitOperator());
        resultDuration.setText(Formats.minutes(done.durationMinutes() == null ? null : done.durationMinutes().doubleValue()));
        show(resultCard, true);
        searchField.clear();
        parkedTable.getSelectionModel().clearSelection();
        refreshSoon();
        searchField.requestFocus();
        scrollToTop(searchField); // the result card sits just below the search box
    }

    @FXML
    private void onCancel() {
        showSession(null);
        hide(searchMessage);
        searchField.clear();
        parkedTable.getSelectionModel().clearSelection();
        searchField.requestFocus();
    }

    // ============================================================ display

    private void showSession(ExitLookup lookup) {
        current = lookup;
        show(detailsCard, lookup != null);
        map.highlight(lookup == null ? null : lookup.session().parkingSlotCode());
        updateConfirmState();
        if (lookup == null) {
            return;
        }
        show(resultCard, false);
        ParkingSession s = lookup.session();
        SlotInfo slot = lookup.slot();
        slotCodeLabel.setText(s.parkingSlotCode());
        slotLocationLabel.setText(slot == null ? "" : "Floor " + slot.floor() + " · Row " + slot.row() + " · Bay " + slot.column());
        statusBadge.setText(s.status() + " · parked " + Formats.minutes((double) lookup.minutesParked()));
        vehicleLabel.setText(s.vehicleNumber());
        categoryLabel.setText(s.vehicleCategory().replace('_', ' '));
        ticketLabel.setText(s.ticketId());
        enteredLabel.setText(Formats.dateTime(s.entryTime()));
        durationLabel.setText(Formats.minutes((double) lookup.minutesParked()));
        operatorLabel.setText(s.entryOperator());
        boolean hasNotes = s.notes() != null && !s.notes().isBlank();
        notesLabel.setText(hasNotes ? s.notes() : "");
        notesLabel.setVisible(hasNotes);
        notesCaption.setVisible(hasNotes);
        confirmButton.requestFocus();
        scrollToTop(searchField); // keep the whole session card, including its status, in view
    }

    private void updateConfirmState() {
        boolean live = context.realtime().stateProperty().get() == ConnectionState.CONNECTED;
        connectionHint.setVisible(current != null && !live);
        connectionHint.setManaged(current != null && !live);
        confirmButton.setDisable(busy || current == null || !live);
    }

    private void setBusy(boolean value) {
        busy = value;
        confirmButton.setText(value ? "Waiting for server..." : "Confirm vehicle exit");
        cancelButton.setDisable(value);
        findButton.setDisable(value);
        searchField.setDisable(value);
        updateConfirmState();
    }

    private void message(ExitMessages.Message m) {
        String style = switch (m.kind()) {
            case ERROR -> "error-banner";
            case WARNING -> "warning-banner";
            case INFO -> "info-banner";
        };
        searchMessage.getStyleClass().setAll("label", style);
        searchMessage.setText(m.text());
        searchMessage.setVisible(true);
        searchMessage.setManaged(true);
    }

    private static void show(VBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    private static TableColumn<ParkingSession, String> column(String title, Function<ParkingSession, String> value,
                                                              double width) {
        TableColumn<ParkingSession, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        c.setMinWidth(width);
        return c;
    }
}

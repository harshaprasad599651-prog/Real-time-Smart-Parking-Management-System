package com.smartpark.client.ui.parking;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.ExitLookup;
import com.smartpark.client.net.dto.ApiDtos.ExitRequest;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.parking.SlotMapModel;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ActivityFeed;
import com.smartpark.client.ui.component.SlotMapView;
import com.smartpark.client.util.Formats;
import com.smartpark.client.util.VehicleNumbers;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import tools.jackson.core.type.TypeReference;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Exit gate workflow:
 * <ol>
 *     <li>type the vehicle number (or click it in the "currently parked" list) and <b>Find</b>:
 *         {@code GET /api/parking/active/{vehicle}};</li>
 *     <li>review ticket, category, entry time, time parked and the slot (highlighted on the map);</li>
 *     <li><b>Confirm departure</b>: {@code POST /api/parking/exit}. The server stores the exit time,
 *         completes the session and releases the slot. Every terminal sees the slot turn free.</li>
 * </ol>
 * If another terminal checks the same vehicle out first, the live event clears the details here.
 */
public class VehicleExitController extends LiveView {

    @FXML private TextField searchField;
    @FXML private Button findButton;
    @FXML private Label searchError;
    @FXML private Label resultBanner;
    @FXML private VBox detailsCard;
    @FXML private Label slotCodeLabel;
    @FXML private Label slotLocationLabel;
    @FXML private Label vehicleLabel;
    @FXML private Label categoryLabel;
    @FXML private Label ticketLabel;
    @FXML private Label enteredLabel;
    @FXML private Label durationLabel;
    @FXML private Label operatorLabel;
    @FXML private Button confirmButton;
    @FXML private Label errorLabel;
    @FXML private TableView<ParkingSession> parkedTable;
    @FXML private Label parkedCount;
    @FXML private VBox legendHolder;
    @FXML private StackPane mapHolder;
    @FXML private StackPane feedHolder;

    private final SlotMapModel map = new SlotMapModel();
    private final ObservableList<ParkingSession> parked = FXCollections.observableArrayList();
    private final ActivityFeed feed = new ActivityFeed("Gate activity", Set.of("VEHICLE_ENTERED", "VEHICLE_EXITED"));
    private ExitLookup current;

    public VehicleExitController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        mapHolder.getChildren().add(new SlotMapView(map, false));
        legendHolder.getChildren().add(SlotMapView.legend(false));
        feedHolder.getChildren().add(feed);
        hide(errorLabel);
        hide(searchError);
        hide(resultBanner);
        showDetails(null);

        searchField.setTextFormatter(new TextFormatter<String>(change -> {
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
            if (s != null) {
                searchField.setText(s.vehicleNumber());
                onFind();
            }
        });
    }

    @Override
    public void onShow() {
        super.onShow();
        Platform.runLater(searchField::requestFocus);
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void refresh() {
        load(context.api().get("/api/slots", new TypeReference<List<SlotInfo>>() { }), map::load, errorLabel);
        load(context.api().get("/api/parking/sessions/active", new TypeReference<List<ParkingSession>>() { }),
                list -> {
                    parked.setAll(list);
                    parkedCount.setText(list.size() + " inside");
                }, errorLabel);
    }

    @Override
    protected void onEvent(RealtimeEvent e) {
        feed.add(e);
        switch (e.type()) {
            case "SLOT_OCCUPIED", "SLOT_RELEASED", "PARKING_MAP_UPDATED" -> {
                if (e.payload() != null && e.payload().hasNonNull("slot")) {
                    map.apply(context.api().json().treeToValue(e.payload().get("slot"), SlotInfo.class));
                } else {
                    refreshSoon();
                }
            }
            case "VEHICLE_ENTERED" -> refreshSoon();
            case "VEHICLE_EXITED" -> {
                refreshSoon();
                // Someone else checked out the vehicle we are looking at.
                if (current != null && current.session().ticketId().equals(e.payloadText("ticketId"))
                        && !confirmButton.isDisabled()) {
                    showDetails(null);
                    showBanner("success-banner", e.payloadText("vehicleNumber") + " was checked out at "
                            + (e.terminal() == null ? "another terminal" : e.terminal()) + " by " + e.actor() + ".");
                }
            }
            default -> { }
        }
    }

    // --------------------------------------------------------------- actions

    @FXML
    private void onFind() {
        hide(resultBanner);
        var problem = VehicleNumbers.validate(searchField.getText());
        if (problem.isPresent()) {
            showSearchError(problem.get());
            return;
        }
        hide(searchError);
        String vehicle = VehicleNumbers.normalize(searchField.getText());
        findButton.setDisable(true);
        context.api().get("/api/parking/active/" + URLEncoder.encode(vehicle, StandardCharsets.UTF_8), ExitLookup.class)
                .whenComplete((lookup, error) -> Platform.runLater(() -> {
                    findButton.setDisable(false);
                    if (error == null) {
                        showDetails(lookup);
                        return;
                    }
                    ApiException e = ApiClient.unwrap(error);
                    showDetails(null);
                    showSearchError("NO_ACTIVE_SESSION".equals(e.code())
                            ? vehicle + " is not parked here (no active session)."
                            : e.userMessage());
                }));
    }

    @FXML
    private void onConfirm() {
        if (current == null) {
            return;
        }
        ExitLookup lookup = current;
        confirmButton.setDisable(true);
        confirmButton.setText("Releasing slot...");
        context.api().post("/api/parking/exit", new ExitRequest(lookup.session().vehicleNumber()), ParkingSession.class)
                .whenComplete((done, error) -> Platform.runLater(() -> {
                    confirmButton.setText("Confirm departure");
                    if (error == null) {
                        showDetails(null);
                        searchField.clear();
                        parkedTable.getSelectionModel().clearSelection();
                        showBanner("success-banner", done.vehicleNumber() + " has left. Slot " + done.parkingSlotCode()
                                + " released  ·  parked " + Formats.minutes(done.durationMinutes() == null ? null
                                : done.durationMinutes().doubleValue()) + "  ·  ticket " + done.ticketId());
                        searchField.requestFocus();
                        return;
                    }
                    confirmButton.setDisable(false);
                    ApiException e = ApiClient.unwrap(error);
                    if ("NO_ACTIVE_SESSION".equals(e.code())) {
                        showDetails(null);
                        showBanner("warning-banner", lookup.session().vehicleNumber()
                                + " was already checked out at another terminal.");
                    } else if (!e.isUnauthorized()) {
                        showBanner("error-banner", e.userMessage());
                    }
                }));
    }

    @FXML
    private void onCancel() {
        showDetails(null);
        searchField.clear();
        parkedTable.getSelectionModel().clearSelection();
        searchField.requestFocus();
    }

    // ---------------------------------------------------------------- display

    private void showDetails(ExitLookup lookup) {
        current = lookup;
        detailsCard.setVisible(lookup != null);
        detailsCard.setManaged(lookup != null);
        confirmButton.setDisable(lookup == null);
        map.highlight(lookup == null ? null : lookup.session().parkingSlotCode());
        if (lookup == null) {
            return;
        }
        ParkingSession s = lookup.session();
        SlotInfo slot = lookup.slot();
        slotCodeLabel.setText(s.parkingSlotCode());
        slotLocationLabel.setText(slot == null ? "" : "Floor " + slot.floor() + " · Row " + slot.row() + " · Bay " + slot.column());
        vehicleLabel.setText(s.vehicleNumber());
        categoryLabel.setText(s.vehicleCategory().replace('_', ' '));
        ticketLabel.setText(s.ticketId());
        enteredLabel.setText(Formats.dateTime(s.entryTime()));
        durationLabel.setText(Formats.minutes((double) lookup.minutesParked()));
        operatorLabel.setText(s.entryOperator());
        confirmButton.requestFocus();
    }

    private void showSearchError(String message) {
        searchError.setText(message);
        searchError.setVisible(true);
        searchError.setManaged(true);
    }

    private void showBanner(String style, String message) {
        resultBanner.getStyleClass().setAll("label", style);
        resultBanner.setText(message);
        resultBanner.setVisible(true);
        resultBanner.setManaged(true);
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

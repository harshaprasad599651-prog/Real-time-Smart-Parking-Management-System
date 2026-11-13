package com.smartpark.client.ui.admin;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.Tables;
import com.smartpark.client.util.Formats;
import com.smartpark.client.util.VehicleNumbers;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * All vehicles currently inside ({@code GET /api/parking/sessions/active}), refreshed live
 * when any terminal registers an entry or exit. Double-click a row for the full session.
 */
public class ParkedVehiclesController extends LiveView {

    @FXML private Label errorLabel;
    @FXML private Label countLabel;
    @FXML private TextField filterField;
    @FXML private TableView<ParkingSession> table;

    private final ObservableList<ParkingSession> sessions = FXCollections.observableArrayList();
    private final FilteredList<ParkingSession> filtered = new FilteredList<>(sessions, s -> true);

    public ParkedVehiclesController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        hide(errorLabel);
        table.setItems(filtered);
        table.getColumns().setAll(List.of(
                Tables.<ParkingSession>column("Vehicle", ParkingSession::vehicleNumber, 120),
                Tables.<ParkingSession>column("Category", s -> s.vehicleCategory().replace('_', ' '), 120),
                Tables.<ParkingSession>column("Slot", ParkingSession::parkingSlotCode, 70),
                Tables.<ParkingSession>column("Entry time", s -> Formats.dateTime(s.entryTime()), 120),
                Tables.<ParkingSession>column("Parked for", s -> Formats.minutes(
                        (double) Duration.between(s.entryTime(), Instant.now()).toMinutes()), 100),
                Tables.<ParkingSession>column("Ticket ID", ParkingSession::ticketId, 170),
                Tables.<ParkingSession>column("Entry operator", ParkingSession::entryOperator, 120),
                Tables.<ParkingSession>column("Notes", s -> s.notes() == null ? "" : s.notes(), 160)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No vehicles inside"));
        table.setRowFactory(t -> {
            TableRow<ParkingSession> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    SessionDetailDialog.show(table.getScene().getWindow(), row.getItem());
                }
            });
            return row;
        });
        filterField.textProperty().addListener((obs, o, text) -> applyFilter(text));
    }

    @Override
    protected void refresh() {
        load(context.parking().activeSessions(), list -> {
            sessions.setAll(list);
            applyFilter(filterField.getText());
        }, errorLabel);
    }

    @Override
    protected void onEvent(RealtimeEvent e) {
        if ("VEHICLE_ENTERED".equals(e.type()) || "VEHICLE_EXITED".equals(e.type())) {
            refreshSoon();
        }
    }

    private void applyFilter(String text) {
        String needle = text == null ? "" : VehicleNumbers.normalize(text);
        String plain = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        filtered.setPredicate(s -> needle.isEmpty()
                || s.vehicleNumber().contains(needle)
                || s.parkingSlotCode().contains(plain)
                || s.ticketId().contains(plain));
        countLabel.setText(filtered.size() + " of " + sessions.size() + " vehicles");
    }
}

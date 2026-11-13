package com.smartpark.client.ui.admin;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SessionPage;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.Tables;
import com.smartpark.client.util.Formats;
import com.smartpark.client.util.VehicleNumbers;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Parking history ({@code GET /api/parking/sessions}): every entry and exit, newest first.
 * Search by vehicle number, filter by entry date range and status, page through the results,
 * and open any session. New entries and exits refresh the current page live.
 */
public class ParkingHistoryController extends LiveView {

    private static final int PAGE_SIZE = 25;

    @FXML private Label errorLabel;
    @FXML private TextField vehicleField;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private ComboBox<String> statusBox;
    @FXML private Label filterError;
    @FXML private TableView<ParkingSession> table;
    @FXML private Label pageLabel;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    @FXML private Button viewButton;

    private final ObservableList<ParkingSession> rows = FXCollections.observableArrayList();
    private int page;
    private int totalPages;

    public ParkingHistoryController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        hide(errorLabel);
        hide(filterError);
        statusBox.setItems(FXCollections.observableArrayList("All", "ACTIVE", "COMPLETED"));
        statusBox.setValue("All");
        table.setItems(rows);
        table.getColumns().setAll(List.of(
                Tables.<ParkingSession>column("Ticket ID", ParkingSession::ticketId, 165),
                Tables.<ParkingSession>column("Vehicle", ParkingSession::vehicleNumber, 105),
                Tables.<ParkingSession>column("Category", s -> s.vehicleCategory().replace('_', ' '), 100),
                Tables.<ParkingSession>column("Slot", ParkingSession::parkingSlotCode, 60),
                Tables.<ParkingSession>column("Entry", s -> Formats.dateTime(s.entryTime()), 110),
                Tables.<ParkingSession>column("Exit", s -> Formats.dateTime(s.exitTime()), 110),
                Tables.<ParkingSession>column("Duration", s -> s.durationMinutes() == null ? "–"
                        : Formats.minutes(s.durationMinutes().doubleValue()), 85),
                Tables.<ParkingSession>badge("Status", ParkingSession::status,
                        v -> "ACTIVE".equals(v) ? "badge-blue" : "badge-green", 100)));
        // Operators are shown in the session details (double-click / View session).
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No parking records match the filters"));
        table.setRowFactory(t -> {
            TableRow<ParkingSession> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    SessionDetailDialog.show(table.getScene().getWindow(), row.getItem());
                }
            });
            return row;
        });
        viewButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }

    @Override
    protected void refresh() {
        Instant from = fromDate.getValue() == null ? null : startOf(fromDate.getValue());
        Instant to = toDate.getValue() == null ? null : startOf(toDate.getValue().plusDays(1)); // "to" day inclusive
        String status = "All".equals(statusBox.getValue()) ? null : statusBox.getValue();
        String vehicle = vehicleField.getText() == null || vehicleField.getText().isBlank()
                ? null : VehicleNumbers.normalize(vehicleField.getText());
        load(context.admin().sessions(vehicle, status, from, to, page, PAGE_SIZE), this::show, errorLabel);
    }

    @Override
    protected void onEvent(RealtimeEvent e) {
        if ("VEHICLE_ENTERED".equals(e.type()) || "VEHICLE_EXITED".equals(e.type())) {
            refreshSoon(); // keep the current filters and page
        }
    }

    @FXML
    private void onSearch() {
        hide(filterError);
        if (fromDate.getValue() != null && toDate.getValue() != null && toDate.getValue().isBefore(fromDate.getValue())) {
            filterError.setText("'To' date must be on or after 'From' date.");
            filterError.setVisible(true);
            filterError.setManaged(true);
            return;
        }
        String v = vehicleField.getText();
        if (v != null && !v.isBlank() && VehicleNumbers.validate(v).isPresent()) {
            filterError.setText(VehicleNumbers.validate(v).get());
            filterError.setVisible(true);
            filterError.setManaged(true);
            return;
        }
        page = 0;
        refresh();
    }

    @FXML
    private void onReset() {
        vehicleField.clear();
        fromDate.setValue(null);
        toDate.setValue(null);
        statusBox.setValue("All");
        onSearch();
    }

    @FXML
    private void onToday() {
        fromDate.setValue(LocalDate.now());
        toDate.setValue(LocalDate.now());
        onSearch();
    }

    @FXML
    private void onPrev() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    @FXML
    private void onNext() {
        if (page + 1 < totalPages) {
            page++;
            refresh();
        }
    }

    @FXML
    private void onView() {
        ParkingSession s = table.getSelectionModel().getSelectedItem();
        if (s != null) {
            SessionDetailDialog.show(table.getScene().getWindow(), s);
        }
    }

    private void show(SessionPage p) {
        rows.setAll(p.content());
        totalPages = Math.max(1, p.totalPages());
        if (page >= totalPages) {        // e.g. the result shrank
            page = totalPages - 1;
        }
        pageLabel.setText("Page " + (page + 1) + " of " + totalPages + "  ·  " + p.totalElements() + " records");
        prevButton.setDisable(page == 0);
        nextButton.setDisable(page + 1 >= totalPages);
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}

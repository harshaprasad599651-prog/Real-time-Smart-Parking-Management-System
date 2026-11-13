package com.smartpark.client.ui.admin;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.ui.component.ConfirmDialog;
import com.smartpark.client.util.Formats;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/** Read-only view of one parking session (history and parked-vehicle tables, double-click). */
final class SessionDetailDialog {

    private SessionDetailDialog() {
    }

    static void show(Window owner, ParkingSession s) {
        List<String[]> rows = new ArrayList<>(List.of(
                new String[]{"Ticket", s.ticketId()},
                new String[]{"Vehicle", s.vehicleNumber()},
                new String[]{"Category", s.vehicleCategory().replace('_', ' ')},
                new String[]{"Slot", s.parkingSlotCode()},
                new String[]{"Status", s.status()},
                new String[]{"Entry", Formats.dateTime(s.entryTime()) + "  by " + s.entryOperator()},
                new String[]{"Exit", s.exitTime() == null ? "– (still parked)"
                        : Formats.dateTime(s.exitTime()) + "  by " + s.exitOperator()},
                new String[]{"Duration", s.durationMinutes() == null ? "–"
                        : Formats.minutes(s.durationMinutes().doubleValue())}));
        if (s.notes() != null) {
            rows.add(new String[]{"Notes", s.notes()});
        }
        ConfirmDialog.info(owner, "Parking session " + s.ticketId(), rows);
    }
}

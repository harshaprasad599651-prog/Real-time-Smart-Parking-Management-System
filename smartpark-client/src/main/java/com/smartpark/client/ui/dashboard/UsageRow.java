package com.smartpark.client.ui.dashboard;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** A labelled usage bar ("Floor 0 ... 12 free · 10 occupied", bar = how full). */
final class UsageRow {

    private UsageRow() {
    }

    static VBox of(String name, String detail, double fullness) {
        Label title = new Label(name);
        title.getStyleClass().add("usage-name");
        Label info = new Label(detail);
        info.getStyleClass().add("usage-detail");
        BorderPane header = new BorderPane();
        header.setLeft(title);
        header.setRight(info);

        ProgressBar bar = new ProgressBar(Math.max(0, Math.min(1, fullness)));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("usage-bar");
        bar.getStyleClass().add(fullness >= 0.9 ? "usage-high" : fullness >= 0.6 ? "usage-medium" : "usage-low");

        VBox row = new VBox(6, header, bar);
        row.getStyleClass().add("usage-row");
        return row;
    }
}

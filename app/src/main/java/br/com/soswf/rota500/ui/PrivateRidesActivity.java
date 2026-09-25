package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.PrivateRideRecord;

public final class PrivateRidesActivity extends Activity {
    private final String[] statuses = {"ORÇAMENTO", "CONFIRMADA", "CONCLUÍDA", "CANCELADA", "NO_SHOW"};
    private LinearLayout list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        root.addView(Ui.title(this, "Corridas particulares"));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        list.removeAllViews();
        AppDatabase database = AppDatabase.get(this);
        List<PrivateRideRecord> rides = database.recentPrivateRides();
        if (rides.isEmpty()) {
            list.addView(Ui.text(this, "Nenhum orçamento salvo.", 16));
            return;
        }
        for (PrivateRideRecord ride : rides) {
            String text = String.format(Locale.forLanguageTag("pt-BR"),
                    "%s • %s\n%s → %s\nR$ %.2f • %s\n%s",
                    ride.client == null ? "Cliente" : ride.client,
                    ride.scheduledAt == null ? "sem data" : ride.scheduledAt,
                    ride.origin == null ? "" : ride.origin,
                    ride.destination == null ? "" : ride.destination,
                    ride.price, ride.status, database.clientReliability(ride.phone));
            TextView card = Ui.text(this, text, 15);
            card.setPadding(18, 18, 18, 8);
            list.addView(card);
            Button status = Ui.button(this, "Alterar status");
            status.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Status da corrida")
                    .setItems(statuses, (dialog, which) -> {
                        database.updatePrivateRideStatus(ride.id, statuses[which]);
                        refresh();
                    }).show());
            list.addView(status);
        }
    }
}

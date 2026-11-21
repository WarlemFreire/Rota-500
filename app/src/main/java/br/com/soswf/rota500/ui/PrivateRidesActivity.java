package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
        root.setBackgroundColor(Ui.SURFACE);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        LinearLayout screen = Ui.column(this);
        screen.addView(Ui.pageHeader(this, "Corridas particulares", ""));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.install(this, screen);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    /** Q3, Q4: CONCLUÍDA lança o valor recebido como ganho; sair de CONCLUÍDA anula. */
    private void changeStatus(AppDatabase database, PrivateRideRecord ride, String status) {
        if (!"CONCLUÍDA".equals(status)) {
            database.setPrivateRideStatus(ride.id, status, 0);
            if ("CONCLUÍDA".equals(ride.status)) Toast.makeText(this, "Ganho da corrida anulado.", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        long suggested = ride.receivedCents > 0 ? ride.receivedCents : Math.round(ride.price * 100);
        NumberPad.show(this, "Valor recebido", 2, "", suggested, cents -> {
            if (cents <= 0) return;
            database.setPrivateRideStatus(ride.id, status, cents);
            Toast.makeText(this, "Corrida concluída lançada no ganho de hoje.", Toast.LENGTH_SHORT).show();
            refresh();
        });
    }

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
                    "%s • %s\n%s → %s\nR$ %.2f • %s%s\n%s",
                    ride.client == null ? "Cliente" : ride.client,
                    ride.scheduledAt == null ? "sem data" : ride.scheduledAt,
                    ride.origin == null ? "" : ride.origin,
                    ride.destination == null ? "" : ride.destination,
                    ride.price, ride.status,
                    ride.receivedCents > 0 ? String.format(Locale.forLanguageTag("pt-BR"),
                            " • R$ %.2f no ganho", ride.receivedCents / 100.0) : "",
                    database.clientReliability(ride.phone));
            TextView card = Ui.text(this, text, 15);
            LinearLayout container = Ui.card(this);
            container.addView(card);
            Button status = Ui.secondaryButton(this, "Alterar status");
            status.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Status da corrida")
                    .setItems(statuses, (dialog, which) -> changeStatus(database, ride, statuses[which])).show());
            container.addView(status);
            list.addView(container);
        }
    }
}

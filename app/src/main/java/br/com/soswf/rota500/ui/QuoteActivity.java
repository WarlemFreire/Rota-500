package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import br.com.soswf.rota500.core.QuoteCalculator;
import br.com.soswf.rota500.core.QuoteInput;
import br.com.soswf.rota500.core.QuoteResult;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.CarCosts;

public final class QuoteActivity extends Activity {
    private EditText client, phone, scheduled, origin, destination, stops, passengers, luggage, tripType, payment, notes;
    private EditText pickupKm, passengerKm, returnKm, pickupMin, passengerMin, returnMin, waitingMin;
    private EditText costKm, desiredNet, appNet, tolls, margin;
    private TextView resultView;
    private QuoteInput lastInput;
    private QuoteResult lastResult;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppPreferences preferences = new AppPreferences(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.SURFACE);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        LinearLayout screen = Ui.column(this);
        screen.addView(Ui.pageHeader(this, "Novo orçamento", ""));
        LinearLayout customerFields = Ui.disclosure(this, root, "Cliente e trajeto", "car", true);

        client = Ui.field(this, customerFields, "Cliente", "", false);
        phone = Ui.field(this, customerFields, "Telefone", "", false);
        scheduled = Ui.field(this, customerFields, "Data e hora", "", false);
        origin = Ui.field(this, customerFields, "Origem", "", false);
        destination = Ui.field(this, customerFields, "Destino", "", false);
        stops = Ui.field(this, customerFields, "Paradas", "", false);
        passengers = Ui.field(this, customerFields, "Passageiros", "1", true);
        luggage = Ui.field(this, customerFields, "Bagagem", "", false);
        tripType = Ui.field(this, customerFields, "Tipo (ida / ida e volta)", "Ida", false);
        payment = Ui.field(this, customerFields, "Pagamento", "A combinar", false);
        notes = Ui.field(this, customerFields, "Observações", "", false);

        LinearLayout tripFields = Ui.disclosure(this, root, "Distância e duração", "route", true);
        pickupKm = Ui.field(this, tripFields, "Km até buscar", "0", true);
        passengerKm = Ui.field(this, tripFields, "Km com passageiro", "0", true);
        returnKm = Ui.field(this, tripFields, "Km de retorno vazio", "0", true);
        pickupMin = Ui.field(this, tripFields, "Minutos até buscar", "0", true);
        passengerMin = Ui.field(this, tripFields, "Minutos com passageiro", "0", true);
        returnMin = Ui.field(this, tripFields, "Minutos de retorno", "0", true);
        waitingMin = Ui.field(this, tripFields, "Minutos de espera", "0", true);
        LinearLayout costFields = Ui.disclosure(this, root, "Custos e margem", "wallet", false);
        costKm = Ui.field(this, costFields, "Custo operacional R$/km (combustível + desgaste)",
                money(CarCosts.load(AppDatabase.get(this), preferences).totalPerKm()), true);
        desiredNet = Ui.field(this, costFields, "Líquido desejado R$/h", money(preferences.desiredNetPerHour()), true);
        appNet = Ui.field(this, costFields, "Alternativa líquida estimada nos apps R$/h (ajuste manualmente)",
                money(preferences.desiredNetPerHour()), true);
        tolls = Ui.field(this, costFields, "Pedágios e estacionamento", "0", true);
        margin = Ui.field(this, costFields, "Margem adicional (%)", "10", true);

        Button calculate = Ui.button(this, "Calcular orçamento");
        calculate.setOnClickListener(v -> calculate());
        Ui.buttonIcon(calculate, "chart", Ui.SURFACE);
        root.addView(calculate);
        resultView = Ui.text(this, "", 18);
        root.addView(resultView);
        Button save = Ui.secondaryButton(this, "Salvar orçamento");
        save.setOnClickListener(v -> save());
        root.addView(save);
        Button share = Ui.secondaryButton(this, "Compartilhar orçamento");
        share.setOnClickListener(v -> share());
        root.addView(share);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.install(this, screen);
    }

    private void calculate() {
        lastInput = new QuoteInput(
                Ui.number(pickupKm, 0), Ui.number(passengerKm, 0), Ui.number(returnKm, 0),
                Ui.integer(pickupMin, 0), Ui.integer(passengerMin, 0), Ui.integer(returnMin, 0), Ui.integer(waitingMin, 0),
                Ui.number(costKm, 1.50), Ui.number(desiredNet, 35), Ui.number(appNet, 35),
                Ui.number(tolls, 0), Ui.number(margin, 10));
        if (lastInput.totalKm() <= 0 || lastInput.totalMinutes() <= 0) {
            Toast.makeText(this, "Informe distância e tempo estimados.", Toast.LENGTH_LONG).show();
            lastResult = null;
            return;
        }
        lastResult = new QuoteCalculator().calculate(lastInput);
        resultView.setText(String.format(Locale.forLanguageTag("pt-BR"),
                "Preço recomendado: R$ %.2f\nCusto estimado: R$ %.2f\nPiso operacional: R$ %.2f\nPiso de oportunidade: R$ %.2f\nLíquido estimado: R$ %.2f (R$ %.2f/h)",
                lastResult.recommendedPrice, lastResult.estimatedCost, lastResult.operationalFloor,
                lastResult.opportunityFloor, lastResult.estimatedNet, lastResult.estimatedNetPerHour));
    }

    private void save() {
        calculate();
        if (lastResult == null) return;
        AppDatabase.get(this).insertQuote(text(client), text(phone), text(scheduled), text(origin), text(destination),
                text(stops), Ui.integer(passengers, 1), text(luggage), text(tripType), text(payment), text(notes),
                lastInput, lastResult);
        Toast.makeText(this, "Orçamento salvo.", Toast.LENGTH_SHORT).show();
    }

    private void share() {
        calculate();
        if (lastResult == null) return;
        String message = String.format(Locale.forLanguageTag("pt-BR"),
                "Orçamento de corrida particular\nCliente: %s\nData/hora: %s\nOrigem: %s\nDestino: %s\nParadas: %s\nValor: R$ %.2f\nPagamento: %s\nObservações: %s\n\nSujeito à confirmação de disponibilidade.",
                text(client), text(scheduled), text(origin), text(destination), text(stops),
                lastResult.recommendedPrice, text(payment), text(notes));
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(intent, "Enviar orçamento"));
    }

    private String text(EditText field) { return field.getText().toString().trim(); }
    private String money(double value) { return String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value); }
}

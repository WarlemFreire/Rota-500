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
        root.addView(Ui.title(this, "Corrida particular"));

        client = Ui.field(this, root, "Cliente", "", false);
        phone = Ui.field(this, root, "Telefone", "", false);
        scheduled = Ui.field(this, root, "Data e hora", "", false);
        origin = Ui.field(this, root, "Origem", "", false);
        destination = Ui.field(this, root, "Destino", "", false);
        stops = Ui.field(this, root, "Paradas", "", false);
        passengers = Ui.field(this, root, "Passageiros", "1", true);
        luggage = Ui.field(this, root, "Bagagem", "", false);
        tripType = Ui.field(this, root, "Tipo (ida / ida e volta)", "Ida", false);
        payment = Ui.field(this, root, "Pagamento", "A combinar", false);
        notes = Ui.field(this, root, "Observações", "", false);

        root.addView(Ui.sectionTitle(this, "Estimativa operacional"));
        pickupKm = Ui.field(this, root, "Km até buscar", "0", true);
        passengerKm = Ui.field(this, root, "Km com passageiro", "0", true);
        returnKm = Ui.field(this, root, "Km de retorno vazio", "0", true);
        pickupMin = Ui.field(this, root, "Minutos até buscar", "0", true);
        passengerMin = Ui.field(this, root, "Minutos com passageiro", "0", true);
        returnMin = Ui.field(this, root, "Minutos de retorno", "0", true);
        waitingMin = Ui.field(this, root, "Minutos de espera", "0", true);
        costKm = Ui.field(this, root, "Custo operacional R$/km", money(preferences.operatingCostPerKm()), true);
        desiredNet = Ui.field(this, root, "Líquido desejado R$/h", money(preferences.desiredNetPerHour()), true);
        appNet = Ui.field(this, root, "Alternativa líquida estimada nos apps R$/h (ajuste manualmente)",
                money(preferences.desiredNetPerHour()), true);
        tolls = Ui.field(this, root, "Pedágios e estacionamento", "0", true);
        margin = Ui.field(this, root, "Margem adicional (%)", "10", true);

        Button calculate = Ui.button(this, "Calcular orçamento");
        calculate.setOnClickListener(v -> calculate());
        root.addView(calculate);
        resultView = Ui.text(this, "", 18);
        root.addView(resultView);
        Button save = Ui.button(this, "Salvar orçamento");
        save.setOnClickListener(v -> save());
        root.addView(save);
        Button share = Ui.button(this, "Compartilhar texto do orçamento");
        share.setOnClickListener(v -> share());
        root.addView(share);
        setContentView(scroll);
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
        if (lastResult == null) calculate();
        if (lastResult == null) return;
        AppDatabase.get(this).insertQuote(text(client), text(phone), text(scheduled), text(origin), text(destination),
                text(stops), Ui.integer(passengers, 1), text(luggage), text(tripType), text(payment), text(notes),
                lastInput, lastResult);
        Toast.makeText(this, "Orçamento salvo.", Toast.LENGTH_SHORT).show();
    }

    private void share() {
        if (lastResult == null) calculate();
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

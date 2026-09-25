package br.com.soswf.rota500.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.core.StrategySnapshot;

/** Adapter for any low-cost provider exposing a chat-completions-compatible HTTPS API. */
public final class CompatibleApiStrategyProvider implements StrategyAiProvider {
    private final String endpoint;
    private final String model;
    private final String apiKey;

    public CompatibleApiStrategyProvider(String endpoint, String model, String apiKey) {
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
    }

    public boolean configured() {
        return endpoint.startsWith("https://") && !model.isBlank() && !apiKey.isBlank();
    }

    @Override public StrategyAdvice advise(StrategySnapshot snapshot) throws Exception {
        if (!configured()) throw new IllegalStateException("Provedor externo não configurado");
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", 0.15);
        payload.put("max_tokens", 220);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content",
                "Você é um estrategista de renda para motorista. Segurança é restrição absoluta. " +
                "A meta é maximizar lucro e atingir a meta bruta em até 12h, sem recomendar extensão de jornada. " +
                "Não decida corridas individuais. A leitura do dinâmico é experimental; não se baseie apenas nela. " +
                "Responda SOMENTE JSON com action,title,explanation,projected_final_gross,confidence. " +
                "action deve ser STAY, MOVE, REVIEW_RULER, END_SHIFT, LEAVE_UNSAFE_AREA ou COLLECT_MORE_DATA."));
        messages.put(new JSONObject().put("role", "user").put("content", snapshotJson(snapshot).toString()));
        payload.put("messages", messages);

        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        } finally {
            connection.disconnect();
        }
        JSONObject response = new JSONObject(body.toString());
        String content = response.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content");
        JSONObject answer = new JSONObject(stripFence(content));
        StrategyAdvice.Action action = StrategyAdvice.Action.valueOf(answer.getString("action"));
        String title = answer.getString("title");
        String explanation = answer.getString("explanation");
        if (title.length() > 100 || explanation.length() > 500) throw new IllegalArgumentException("Resposta extensa");
        return new StrategyAdvice(action, title, explanation,
                answer.optDouble("projected_final_gross", snapshot.projectedFinalGross()),
                answer.optDouble("confidence", 0.55));
    }

    @Override public String name() { return "api-compativel"; }

    private JSONObject snapshotJson(StrategySnapshot value) throws Exception {
        return new JSONObject()
                .put("daily_goal_gross", value.dailyGoalGross)
                .put("current_gross", value.currentGross)
                .put("worked_minutes", value.workedMinutes)
                .put("maximum_efficient_minutes", Math.min(720, value.plannedMaximumMinutes))
                .put("idle_minutes", value.idleMinutes)
                .put("offers_last_hour", value.offersLastHour)
                .put("viable_offers_last_hour", value.viableOffersLastHour)
                .put("robust_gross_hour", value.robustGrossPerHour)
                .put("learned_expected_gross_hour", value.historicalExpectedGrossPerHour)
                .put("empty_km_today", value.emptyKmToday)
                .put("current_zone", value.currentZone)
                .put("weather", value.weather)
                .put("dynamic_level", value.dynamicLevel)
                .put("unsafe_zone", value.inUnsafeZone);
    }

    private String stripFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && last > firstLine) return trimmed.substring(firstLine + 1, last).trim();
        }
        return trimmed;
    }
}

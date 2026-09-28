package br.com.soswf.rota500.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

import br.com.soswf.rota500.core.DecisionRules;

public final class AppPreferences {
    private static final String FILE = "rota500_preferences";
    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        // Versões antigas exibiam "CONFERIR". A partir da 0.3, leituras incompletas
        // são descartadas e o motorista só recebe ACEITAR ou RECUSAR.
        if ("REVIEW".equals(preferences.getString("last_decision_tone", ""))) {
            preferences.edit()
                    .putString("last_decision_title", "Aguardando oferta")
                    .putString("last_decision_details", "A leitura anterior incompleta foi descartada.")
                    .putString("last_decision_tone", "NEUTRAL")
                    .remove("last_decision_at")
                    .apply();
        }
    }

    public double dailyGoal() { return readDouble("daily_goal", 500.0); }
    public void setDailyGoal(double value) { writeDouble("daily_goal", value); }

    public int plannedMinutes() { return preferences.getInt("planned_minutes", 600); }
    public void setPlannedMinutes(int value) {
        preferences.edit().putInt("planned_minutes", Math.max(60, Math.min(720, value))).apply();
    }

    public double minimumGrossPerKm() { return readDouble("min_per_km", 1.20); }
    public void setMinimumGrossPerKm(double value) { writeDouble("min_per_km", value); }

    public double minimumLongRidePerHour() { return readDouble("min_long_hour", 35.0); }
    public void setMinimumLongRidePerHour(double value) { writeDouble("min_long_hour", value); }

    public int longRideMinutes() { return preferences.getInt("long_minutes", 35); }

    public double operatingCostPerKm() { return readDouble("operating_cost_km", 1.50); }
    public void setOperatingCostPerKm(double value) { writeDouble("operating_cost_km", value); }

    public double fuelCostPerKm() { return readDouble("fuel_cost_km", 0); }
    public void setFuelCostPerKm(double value) { writeDouble("fuel_cost_km", value); }

    public double wearCostPerKm() { return readDouble("wear_cost_km", 0); }
    public void setWearCostPerKm(double value) { writeDouble("wear_cost_km", value); }

    public long monthlyFinancingCents() { return preferences.getLong("monthly_financing_cents", 0); }
    public void setMonthlyFinancingCents(long value) {
        preferences.edit().putLong("monthly_financing_cents", Math.max(0, value)).apply();
    }

    public long otherMonthlyCents() { return preferences.getLong("other_monthly_cents", 0); }
    public void setOtherMonthlyCents(long value) {
        preferences.edit().putLong("other_monthly_cents", Math.max(0, value)).apply();
    }

    public int workDaysPerMonth() { return preferences.getInt("work_days_month", 22); }
    public void setWorkDaysPerMonth(int value) {
        preferences.edit().putInt("work_days_month", Math.max(1, Math.min(31, value))).apply();
    }

    public double desiredNetPerHour() { return readDouble("desired_net_hour", 35.0); }
    public void setDesiredNetPerHour(double value) { writeDouble("desired_net_hour", value); }

    public String weather() { return preferences.getString("weather", "Não informado"); }
    public void setWeather(String value) { preferences.edit().putString("weather", value).apply(); }

    public String aiEndpoint() { return preferences.getString("ai_endpoint", ""); }
    public void setAiEndpoint(String value) { preferences.edit().putString("ai_endpoint", value.trim()).apply(); }

    public String aiModel() { return preferences.getString("ai_model", ""); }
    public void setAiModel(String value) { preferences.edit().putString("ai_model", value.trim()).apply(); }

    public String aiApiKey() { return preferences.getString("ai_api_key", ""); }
    public void setAiApiKey(String value) { preferences.edit().putString("ai_api_key", value.trim()).apply(); }

    public int strategyIntervalMinutes() { return preferences.getInt("strategy_interval_minutes", 180); }
    public void setStrategyIntervalMinutes(int value) {
        preferences.edit().putInt("strategy_interval_minutes", Math.max(60, Math.min(1440, value))).apply();
    }

    public String backupTreeUri() { return preferences.getString("backup_tree_uri", ""); }
    public void setBackupTreeUri(String value) { preferences.edit().putString("backup_tree_uri", value).apply(); }
    public long lastBackupAt() { return preferences.getLong("last_backup_at", 0); }
    public void setLastBackupAt(long value) { preferences.edit().putLong("last_backup_at", value).apply(); }

    public boolean dynamicCapturePending() { return preferences.getBoolean("dynamic_capture_pending", false); }
    public void requestDynamicCapture() { preferences.edit().putBoolean("dynamic_capture_pending", true).apply(); }
    public void clearDynamicCaptureRequest() { preferences.edit().putBoolean("dynamic_capture_pending", false).apply(); }

    public boolean captureActive() { return preferences.getBoolean("capture_active", false); }
    public void setCaptureActive(boolean value) { preferences.edit().putBoolean("capture_active", value).apply(); }

    public void setLastDecision(String title, String details, String tone) {
        preferences.edit()
                .putString("last_decision_title", title)
                .putString("last_decision_details", details)
                .putString("last_decision_tone", tone)
                .putLong("last_decision_at", System.currentTimeMillis())
                .apply();
    }
    public String lastDecisionTitle() { return preferences.getString("last_decision_title", "Aguardando oferta"); }
    public String lastDecisionDetails() { return preferences.getString("last_decision_details", "Abra a Uber ou a 99 com a leitura ligada."); }
    public String lastDecisionTone() { return preferences.getString("last_decision_tone", "NEUTRAL"); }
    public long lastDecisionAt() { return preferences.getLong("last_decision_at", 0); }

    public boolean canAskForContext() {
        return System.currentTimeMillis() - preferences.getLong("last_context_prompt", 0) >= 3_600_000L;
    }
    public void markContextPrompt() {
        preferences.edit().putLong("last_context_prompt", System.currentTimeMillis()).apply();
    }

    public DecisionRules decisionRules() {
        return new DecisionRules(
                minimumGrossPerKm(),
                minimumLongRidePerHour(),
                longRideMinutes(),
                0.95,
                operatingCostPerKm());
    }

    /** Data settings only. API keys and the local backup folder stay on this device. */
    public JSONObject exportDataSettings() throws Exception {
        JSONObject data = new JSONObject();
        data.put("daily_goal", dailyGoal());
        data.put("planned_minutes", plannedMinutes());
        data.put("min_per_km", minimumGrossPerKm());
        data.put("min_long_hour", minimumLongRidePerHour());
        data.put("operating_cost_km", operatingCostPerKm());
        data.put("fuel_cost_km", fuelCostPerKm());
        data.put("wear_cost_km", wearCostPerKm());
        data.put("monthly_financing_cents", monthlyFinancingCents());
        data.put("other_monthly_cents", otherMonthlyCents());
        data.put("work_days_month", workDaysPerMonth());
        data.put("desired_net_hour", desiredNetPerHour());
        data.put("strategy_interval_minutes", strategyIntervalMinutes());
        return data;
    }

    public void restoreDataSettings(JSONObject data) {
        if (data == null) return;
        setDailyGoal(data.optDouble("daily_goal", dailyGoal()));
        setPlannedMinutes(data.optInt("planned_minutes", plannedMinutes()));
        setMinimumGrossPerKm(data.optDouble("min_per_km", minimumGrossPerKm()));
        setMinimumLongRidePerHour(data.optDouble("min_long_hour", minimumLongRidePerHour()));
        setOperatingCostPerKm(data.optDouble("operating_cost_km", operatingCostPerKm()));
        setFuelCostPerKm(data.optDouble("fuel_cost_km", fuelCostPerKm()));
        setWearCostPerKm(data.optDouble("wear_cost_km", wearCostPerKm()));
        setMonthlyFinancingCents(data.optLong("monthly_financing_cents", monthlyFinancingCents()));
        setOtherMonthlyCents(data.optLong("other_monthly_cents", otherMonthlyCents()));
        setWorkDaysPerMonth(data.optInt("work_days_month", workDaysPerMonth()));
        setDesiredNetPerHour(data.optDouble("desired_net_hour", desiredNetPerHour()));
        setStrategyIntervalMinutes(data.optInt("strategy_interval_minutes", strategyIntervalMinutes()));
    }

    private double readDouble(String key, double fallback) {
        return Double.longBitsToDouble(preferences.getLong(key, Double.doubleToLongBits(fallback)));
    }

    private void writeDouble(String key, double value) {
        preferences.edit().putLong(key, Double.doubleToRawLongBits(Math.max(0, value))).apply();
    }
}

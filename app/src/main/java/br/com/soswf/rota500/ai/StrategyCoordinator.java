package br.com.soswf.rota500.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.core.StrategySnapshot;
import br.com.soswf.rota500.core.StrategyGuard;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.DashboardStats;

public final class StrategyCoordinator {
    public interface Callback { void onAdvice(StrategyAdvice advice, String source, String warning); }

    private final AppDatabase database;
    private final AppPreferences preferences;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public StrategyCoordinator(Context context) {
        database = AppDatabase.get(context);
        preferences = new AppPreferences(context);
    }

    public StrategySnapshot snapshot() {
        DashboardStats stats = database.dashboardStats();
        return new StrategySnapshot(
                preferences.dailyGoal(), stats.grossCents / 100.0,
                stats.workedMinutes(System.currentTimeMillis()), Math.min(720, preferences.plannedMinutes()),
                stats.idleMinutes, stats.offersLastHour, stats.viableOffersLastHour,
                stats.robustGrossPerHour, stats.learnedGrossPerHour, stats.emptyKm,
                stats.currentZone, preferences.weather(), database.latestDynamicLevel(), stats.unsafeZone);
    }

    public StrategyAdvice localAdvice() {
        StrategyAdvice advice = new LocalStrategyProvider().advise(snapshot());
        database.insertStrategy(advice, "motor-local");
        return advice;
    }

    public void requestBestAdvice(Callback callback) {
        StrategySnapshot snapshot = snapshot();
        StrategyAdvice local = new LocalStrategyProvider().advise(snapshot);
        CompatibleApiStrategyProvider external = new CompatibleApiStrategyProvider(
                preferences.aiEndpoint(), preferences.aiModel(), preferences.aiApiKey());
        if (!external.configured()) {
            database.insertStrategy(local, "motor-local");
            main.post(() -> callback.onAdvice(local, "motor-local", "IA externa não configurada; estratégia local usada."));
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                StrategyAdvice advice = external.advise(snapshot);
                advice = new StrategyGuard().enforce(snapshot, advice);
                StrategyAdvice result = advice;
                database.insertStrategy(result, external.name());
                main.post(() -> callback.onAdvice(result, external.name(), ""));
            } catch (Exception error) {
                database.insertStrategy(local, "motor-local-fallback");
                main.post(() -> callback.onAdvice(local, "motor-local", "Falha da IA externa; estratégia local usada."));
            }
        });
    }
}

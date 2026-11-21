package br.com.soswf.rota500.core;

/**
 * Regras para jornada esquecida. Sem ganho por 1 h de tempo ativo (ou pausa aberta por 2 h),
 * pergunta ao motorista. Cada pergunta sem resposta conta; depois de 3 sem resposta,
 * a jornada é encerrada quando venceria a 4ª pergunta.
 */
public final class JourneyWatch {
    public static final long ACTIVE_INTERVAL_MS = 60 * 60_000L;
    public static final long PAUSE_INTERVAL_MS = 2 * 60 * 60_000L;
    public static final int MAX_UNANSWERED = 3;
    /** Sem ganho para medir: o fim presumido é o início + 1 h. */
    public static final long FALLBACK_AFTER_START_MS = 60 * 60_000L;
    /** Encerrar à mão depois disso sem ganho pergunta o horário real de término. */
    public static final long ASK_END_TIME_AFTER_MS = 60 * 60_000L;

    public enum Action { NONE, ASK_ACTIVE, ASK_PAUSE, AUTO_END }

    public record Decision(Action action, int promptNumber) { }

    private JourneyWatch() { }

    /**
     * @param elapsedMs tempo ativo sem ganho desde a última referência (ou duração da pausa
     *                  desde a última referência, quando pausado)
     * @param promptsSent perguntas já enviadas e não respondidas desde essa referência
     */
    public static Decision evaluate(boolean paused, long elapsedMs, int promptsSent) {
        long interval = paused ? PAUSE_INTERVAL_MS : ACTIVE_INTERVAL_MS;
        long due = Math.max(0, elapsedMs) / interval;
        if (due <= promptsSent) return new Decision(Action.NONE, promptsSent);
        if (promptsSent >= MAX_UNANSWERED) return new Decision(Action.AUTO_END, promptsSent);
        return new Decision(paused ? Action.ASK_PAUSE : Action.ASK_ACTIVE, promptsSent + 1);
    }

    /** Horário presumido do fim quando o motorista esqueceu a jornada ativa. */
    public static long presumedEnd(long sessionStart, long lastGainAt, long lastMovementAt, long now) {
        long end;
        if (lastGainAt > sessionStart) end = lastGainAt;
        else if (lastMovementAt > sessionStart) end = lastMovementAt;
        else end = sessionStart + FALLBACK_AFTER_START_MS;
        return Math.max(sessionStart, Math.min(now, end));
    }
}

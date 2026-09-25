package br.com.soswf.rota500.core;

public final class StrategyGuard {
    public StrategyAdvice enforce(StrategySnapshot snapshot, StrategyAdvice candidate) {
        StrategyAdvice local = new StrategyEngine().advise(snapshot);
        if (local.action == StrategyAdvice.Action.LEAVE_UNSAFE_AREA
                || local.action == StrategyAdvice.Action.END_SHIFT) return local;
        if (candidate == null || !Double.isFinite(candidate.projectedFinalGross)
                || !Double.isFinite(candidate.confidence)) return local;
        return candidate;
    }
}

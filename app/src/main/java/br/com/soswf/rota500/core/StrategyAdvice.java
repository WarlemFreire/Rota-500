package br.com.soswf.rota500.core;

public final class StrategyAdvice {
    public enum Action { STAY, MOVE, REVIEW_RULER, END_SHIFT, LEAVE_UNSAFE_AREA, COLLECT_MORE_DATA }

    public final Action action;
    public final String title;
    public final String explanation;
    public final double projectedFinalGross;
    public final double confidence;

    public StrategyAdvice(
            Action action,
            String title,
            String explanation,
            double projectedFinalGross,
            double confidence) {
        this.action = action;
        this.title = title;
        this.explanation = explanation;
        this.projectedFinalGross = projectedFinalGross;
        this.confidence = Math.max(0, Math.min(1, confidence));
    }
}

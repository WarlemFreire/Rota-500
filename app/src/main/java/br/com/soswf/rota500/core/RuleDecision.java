package br.com.soswf.rota500.core;

public final class RuleDecision {
    public enum Outcome { ACCEPTABLE, AUTO_REJECT, REVIEW }

    public final Outcome outcome;
    public final String reason;

    public RuleDecision(Outcome outcome, String reason) {
        this.outcome = outcome;
        this.reason = reason;
    }
}

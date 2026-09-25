package br.com.soswf.rota500.core;

public final class OfferRuleEngine {
    public RuleDecision evaluate(OfferData offer, DecisionRules rules, boolean unsafePickupOrDestination) {
        if (offer == null) return new RuleDecision(RuleDecision.Outcome.REVIEW, "Oferta ausente");
        if (offer.confidence < rules.minimumParserConfidence) {
            return new RuleDecision(RuleDecision.Outcome.REVIEW,
                    unsafePickupOrDestination ? "Possível área proibida: confira antes de aceitar" : "Leitura incompleta");
        }
        if (unsafePickupOrDestination) {
            return new RuleDecision(RuleDecision.Outcome.AUTO_REJECT, "Área cadastrada como proibida");
        }
        if (offer.totalKm() <= 0 || offer.totalMinutes() <= 0 || offer.fareCents <= 0) {
            return new RuleDecision(RuleDecision.Outcome.REVIEW, "Dados insuficientes para decidir");
        }
        if (offer.grossPerKm() < rules.operatingCostPerKm) {
            return new RuleDecision(RuleDecision.Outcome.AUTO_REJECT, "Abaixo do custo por quilômetro informado");
        }
        if (offer.grossPerKm() < rules.minimumGrossPerKm) {
            return new RuleDecision(RuleDecision.Outcome.AUTO_REJECT, "Abaixo da régua por quilômetro");
        }
        if (offer.totalMinutes() >= rules.longRideMinutes
                && offer.grossPerHour() < rules.minimumLongRidePerHour) {
            return new RuleDecision(RuleDecision.Outcome.AUTO_REJECT, "Corrida longa abaixo da régua por hora");
        }
        return new RuleDecision(RuleDecision.Outcome.ACCEPTABLE, "Dentro das regras atuais");
    }
}

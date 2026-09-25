package br.com.soswf.rota500.core;

import java.util.List;

public final class CoreSelfTest {
    public static void main(String[] args) {
        parsesOffer();
        rejectsAmbiguousScreen();
        appliesRules();
        respectsOperatingCost();
        calculatesPrivateQuote();
        resistsHourlyOutlier();
        prioritizesSafety();
        refusesUnsafeExternalStrategy();
        reviewsUncertainUnsafeOffer();
        stopsAtGoal();
        learnsIncrementally();
        System.out.println("CoreSelfTest: 11 testes aprovados");
    }

    private static void parsesOffer() {
        OfferData offer = new OfferTextParser().parse(
                "UBER",
                List.of("R$ 32,50", "5 min • 2,1 km", "28 min • 14,0 km", "Destino: Centro, Nova Iguaçu"));
        check(offer.fareCents == 3250, "valor da oferta");
        close(offer.pickupKm, 2.1, "quilômetros até embarque");
        close(offer.tripKm, 14.0, "quilômetros da viagem");
        check(offer.pickupMinutes == 5 && offer.tripMinutes == 28, "tempos da oferta");
        check(offer.confidence > 0.9, "confiança da leitura");
    }

    private static void appliesRules() {
        OfferData low = new OfferData("99", 1500, 3, 12, 8, 25, "", "x", 0.9);
        RuleDecision decision = new OfferRuleEngine().evaluate(low, DecisionRules.defaults(), false);
        check(decision.outcome == RuleDecision.Outcome.AUTO_REJECT, "recusa abaixo da régua");

        OfferData uncertain = new OfferData("99", 1500, 3, 12, 8, 25, "", "x", 0.3);
        decision = new OfferRuleEngine().evaluate(uncertain, DecisionRules.defaults(), false);
        check(decision.outcome == RuleDecision.Outcome.REVIEW, "não recusar leitura incerta");
    }

    private static void rejectsAmbiguousScreen() {
        OfferData offer = new OfferTextParser().parse("UBER",
                List.of("R$ 32,50", "R$ 400,00", "5 min • 2,1 km", "28 min • 14 km"));
        check(offer.confidence < 0.95, "dois preços na tela exigem conferência");
        OfferData missingPickup = new OfferTextParser().parse("99", List.of("R$ 32,50", "28 min • 14 km"));
        check(missingPickup.confidence < 0.95, "embarque ausente exige conferência");
    }

    private static void calculatesPrivateQuote() {
        QuoteInput input = new QuoteInput(
                5, 40, 35,
                15, 60, 50, 20,
                0.75, 35, 30, 18, 10);
        QuoteResult result = new QuoteCalculator().calculate(input);
        check(result.recommendedPrice >= result.operationalFloor, "preço cobre piso operacional");
        check(result.recommendedPrice % 5 == 0, "arredondamento de orçamento");
        check(result.estimatedNetPerHour >= 35, "meta líquida por hora");
    }

    private static void respectsOperatingCost() {
        OfferData offer = new OfferData("99", 1400, 2, 8, 5, 15, "Centro", "cost", 1);
        DecisionRules rules = new DecisionRules(1.20, 35, 35, 0.95, 1.50);
        RuleDecision decision = new OfferRuleEngine().evaluate(offer, rules, false);
        check(decision.outcome == RuleDecision.Outcome.AUTO_REJECT, "não recomendar abaixo do custo configurado");
    }

    private static void resistsHourlyOutlier() {
        double median = RobustStats.median(List.of(31.0, 33.0, 35.0, 100.0, 34.0));
        close(median, 34.0, "mediana robusta");
    }

    private static void prioritizesSafety() {
        StrategySnapshot snapshot = snapshot(120, 240, true);
        StrategyAdvice advice = new StrategyEngine().advise(snapshot);
        check(advice.action == StrategyAdvice.Action.LEAVE_UNSAFE_AREA, "segurança antes da meta");
    }

    private static void stopsAtGoal() {
        StrategySnapshot snapshot = snapshot(500, 540, false);
        StrategyAdvice advice = new StrategyEngine().advise(snapshot);
        check(advice.action == StrategyAdvice.Action.END_SHIFT, "encerrar ao atingir meta");
    }

    private static void refusesUnsafeExternalStrategy() {
        StrategySnapshot unsafe = snapshot(120, 240, true);
        StrategyAdvice unsafeSuggestion = new StrategyAdvice(StrategyAdvice.Action.STAY,
                "Fique", "Sugestão externa", 700, 0.9);
        StrategyAdvice result = new StrategyGuard().enforce(unsafe, unsafeSuggestion);
        check(result.action == StrategyAdvice.Action.LEAVE_UNSAFE_AREA, "IA externa não desfaz restrição de segurança");
        StrategySnapshot finished = snapshot(500, 540, false);
        result = new StrategyGuard().enforce(finished, unsafeSuggestion);
        check(result.action == StrategyAdvice.Action.END_SHIFT, "IA externa não estende jornada após meta");
    }

    private static void reviewsUncertainUnsafeOffer() {
        OfferData uncertain = new OfferData("UBER", 0, 0, 0, 0, 0, "Bacia", "unsafe", 0.2);
        RuleDecision decision = new OfferRuleEngine().evaluate(uncertain, DecisionRules.defaults(), true);
        check(decision.outcome == RuleDecision.Outcome.REVIEW, "leitura incerta não aciona recusa automática");
        check(decision.reason.contains("proibida"), "sinalizar possível risco ao motorista");
        OfferData certain = new OfferData("UBER", 2500, 2, 9, 5, 15, "Bacia", "safe", 1);
        decision = new OfferRuleEngine().evaluate(certain, DecisionRules.defaults(), true);
        check(decision.outcome == RuleDecision.Outcome.AUTO_REJECT, "área conhecida prevalece sobre lucro");
    }

    private static void learnsIncrementally() {
        OnlineLearningBucket bucket = new OnlineLearningBucket();
        bucket.update(30, 20);
        bucket.update(50, 10);
        close(bucket.expectedGrossPerHour(35, 2), 37.5, "média bayesiana");
        close(bucket.expectedIdleMinutes(15, 2), 15.0, "espera bayesiana");
    }

    private static StrategySnapshot snapshot(double currentGross, int workedMinutes, boolean unsafe) {
        return new StrategySnapshot(
                500, currentGross, workedMinutes, 720,
                8, 8, 4, 42, 38,
                10, "Centro", "Sem chuva", "Médio", unsafe);
    }

    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 0.001, label + ": esperado " + expected + ", recebido " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}

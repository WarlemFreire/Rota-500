package br.com.soswf.rota500.core;

import java.util.List;

public final class CoreSelfTest {
    public static void main(String[] args) {
        parsesOffer();
        parsesRealUberCard();
        rejectsAmbiguousScreen();
        appliesRules();
        rejectsVeryLowPassengerRating();
        buildsTrafficLightDecision();
        respectsOperatingCost();
        calculatesPrivateQuote();
        resistsHourlyOutlier();
        prioritizesSafety();
        refusesUnsafeExternalStrategy();
        reviewsUncertainUnsafeOffer();
        stopsAtGoal();
        learnsIncrementally();
        throttlesOcrWithoutMissingNewOffer();
        keepsOneTruthForMoney();
        filtersOdometerAndPauses();
        measuresFuelByFullTankCycles();
        measuresGpsDistanceWithFilters();
        watchesForgottenJourney();
        keepsEarningsWhenPlatformResetsAtMidnight();
        picksUpdateByBuildNumber();
        System.out.println("CoreSelfTest: 22 testes aprovados");
    }

    private static void picksUpdateByBuildNumber() {
        check(UpdatePicker.versionCodeFromAsset("rota500-0.9.0-teste.12-b12.apk") == 12, "número do build no nome");
        check(UpdatePicker.versionCodeFromAsset("app-release.apk") == -1, "nome fora do padrão é ignorado");
        List<UpdatePicker.Candidate> releases = List.of(
                new UpdatePicker.Candidate("0.9.0", 15, false, "u15", "a", 1),
                new UpdatePicker.Candidate("0.9.1-teste.18", 18, true, "u18", "b", 1),
                new UpdatePicker.Candidate("0.8.0", 8, false, "u8", "c", 1));
        check(UpdatePicker.best(releases, 12, false).versionCode() == 15, "canal estável ignora teste");
        check(UpdatePicker.best(releases, 12, true).versionCode() == 18, "canal de teste pega o build mais novo");
        check(UpdatePicker.best(releases, 18, true) == null, "nada mais novo que o instalado");
    }

    private static void measuresFuelByFullTankCycles() {
        check(FuelMath.realCost(List.of(new FuelMath.Refuel(1, 25000, 40, 10000, true))) == null,
                "um tanque cheio só não forma ciclo");
        // Ciclo 1: cheio em 10.000 km; parcial R$ 100 (20 l); cheio R$ 150 (30 l) em 10.500 km.
        List<FuelMath.Refuel> refuels = List.of(
                new FuelMath.Refuel(1, 25000, 40, 10000, true),
                new FuelMath.Refuel(2, 10000, 20, 10200, false),
                new FuelMath.Refuel(3, 15000, 30, 10500, true));
        FuelMath.Result one = FuelMath.realCost(refuels);
        close(one.costPerKm(), 0.50, "R$ pago no ciclo / km do ciclo");
        close(one.kmPerLiter(), 10.0, "km/l do ciclo");
        check(one.cycles() == 1, "um ciclo completo");
        List<FuelMath.Refuel> many = List.of(
                new FuelMath.Refuel(1, 1, 1, 1000, true),
                new FuelMath.Refuel(2, 99900, 1, 1100, true),   // ciclo antigo e caro, fica de fora
                new FuelMath.Refuel(3, 6000, 10, 1200, true),
                new FuelMath.Refuel(4, 6000, 10, 1300, true),
                new FuelMath.Refuel(5, 6000, 10, 1400, true));
        FuelMath.Result recent = FuelMath.realCost(many);
        check(recent.cycles() == 3, "no máximo três ciclos");
        close(recent.costPerKm(), 0.60, "média só dos três ciclos mais recentes");
        check(FuelMath.divergent(0.70, 0.50) && !FuelMath.divergent(0.55, 0.50), "alerta de divergência acima de 30%");
    }

    private static void measuresGpsDistanceWithFilters() {
        // ~1,11 km por 0,01 grau de latitude.
        List<GpsDistance.Point> points = List.of(
                new GpsDistance.Point(0, -22.70, -43.40, 10),
                new GpsDistance.Point(120_000, -22.69, -43.40, 10),
                new GpsDistance.Point(150_000, -22.50, -43.40, 10),   // salto de 21 km em 30 s
                new GpsDistance.Point(180_000, -22.68, -43.40, 400),  // impreciso
                new GpsDistance.Point(240_000, -22.68, -43.40, 10),
                new GpsDistance.Point(4_000_000, -22.60, -43.40, 10),  // dentro da pausa
                new GpsDistance.Point(7_300_000, -22.67, -43.40, 10)); // depois da pausa
        List<WorkMath.Span> pauses = List.of(new WorkMath.Span(3_600_000, 7_200_000));
        double km = GpsDistance.km(points, pauses);
        check(km > 2.1 && km < 2.35, "dois trechos válidos de ~1,11 km, recebido " + km);
        check(GpsDistance.lastMovementAt(points) == 7_300_000, "último movimento real");
    }

    private static void watchesForgottenJourney() {
        long hour = 3_600_000L;
        check(JourneyWatch.evaluate(false, 59 * 60_000L, 0).action() == JourneyWatch.Action.NONE, "antes de 1 h não pergunta");
        JourneyWatch.Decision first = JourneyWatch.evaluate(false, hour, 0);
        check(first.action() == JourneyWatch.Action.ASK_ACTIVE && first.promptNumber() == 1, "1 h sem ganho pergunta");
        check(JourneyWatch.evaluate(false, hour + 1, 1).action() == JourneyWatch.Action.NONE, "não repete a mesma pergunta");
        check(JourneyWatch.evaluate(false, 3 * hour, 2).promptNumber() == 3, "terceira pergunta às 3 h");
        check(JourneyWatch.evaluate(false, 4 * hour, 3).action() == JourneyWatch.Action.AUTO_END, "três sem resposta encerram");
        check(JourneyWatch.evaluate(true, hour, 0).action() == JourneyWatch.Action.NONE, "pausa de 1 h não pergunta");
        check(JourneyWatch.evaluate(true, 2 * hour, 0).action() == JourneyWatch.Action.ASK_PAUSE, "pausa de 2 h pergunta");
        check(JourneyWatch.presumedEnd(1000, 5000, 9000, 20_000) == 5000, "fim no último ganho");
        check(JourneyWatch.presumedEnd(1000, 0, 9000, 20_000) == 9000, "sem ganho, último movimento");
        check(JourneyWatch.presumedEnd(1000, 0, 0, 20_000_000) == 1000 + hour, "sem dados, início + 1 h");
        check(JourneyWatch.presumedEnd(1000, 0, 0, 5000) == 5000, "nunca depois de agora");
    }

    private static void keepsEarningsWhenPlatformResetsAtMidnight() {
        long baseline = MoneyLedger.baselineAfterReset(0, 40000);
        List<MoneyLedger.Entry> entries = List.of(
                new MoneyLedger.Entry(1, 1, "PLATFORM", "UBER", 40000, 0, false),
                new MoneyLedger.Entry(2, 2, "PLATFORM", "UBER", 3000, baseline, false),
                new MoneyLedger.Entry(3, 3, "PLATFORM", "UBER", 8000, baseline, false));
        check(MoneyLedger.at(entries, 2).totalCents() == 43000, "zerou: soma ao anterior");
        check(MoneyLedger.at(entries, 3).totalCents() == 48000, "leituras seguintes continuam somando");
        long[] rebased = MoneyLedger.rebasedBaselines(List.of(
                new MoneyLedger.Reading(30000, false),
                new MoneyLedger.Reading(40000, false),
                new MoneyLedger.Reading(3000, true),
                new MoneyLedger.Reading(2500, false)), 0);
        check(rebased[0] == 0 && rebased[1] == 0, "antes da meia-noite mantém a base");
        check(rebased[2] == -40000 && rebased[3] == -40000, "total menor após a virada vira zeramento");
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

    private static void parsesRealUberCard() {
        OfferData offer = new OfferTextParser().parse("UBER", List.of(
                "UberX", "R$ 7,34", "R$1,63/km aprox.", "★ 4,89 (736)",
                "5 min (1.1 km)", "R. Dr. Barros Júnior, Santa Catarina, Nova Iguaçu",
                "10 minutos (3.4 km)", "Rua Marlene de Oliveira, 18, Santa Eugênia, Nova Iguaçu",
                "Selecionar"));
        check(offer.fareCents == 734, "não confundir valor da corrida com valor por km");
        close(offer.passengerRating, 4.89, "nota do passageiro");
        close(offer.totalKm(), 4.5, "distância total da oferta real");
        check(offer.totalMinutes() == 15, "tempo total da oferta real");
        check(offer.destination.startsWith("Rua Marlene"), "destino da oferta real");
        check(offer.confidence > 0.95, "oferta real deve ter leitura completa");
    }

    private static void appliesRules() {
        OfferData low = new OfferData("99", 1500, 3, 12, 8, 25, "", "x", 0.9);
        RuleDecision decision = new OfferRuleEngine().evaluate(low, DecisionRules.defaults(), false);
        check(decision.outcome == RuleDecision.Outcome.AUTO_REJECT, "recusa abaixo da régua");

        OfferData uncertain = new OfferData("99", 1500, 3, 12, 8, 25, "", "x", 0.3);
        decision = new OfferRuleEngine().evaluate(uncertain, DecisionRules.defaults(), false);
        check(decision.outcome == RuleDecision.Outcome.REVIEW, "não recusar leitura incerta");
    }

    private static void rejectsVeryLowPassengerRating() {
        OfferData offer = new OfferData("UBER", 2400, 1, 8, 4, 18,
                4.65, "Centro", "rating", 1);
        RuleDecision decision = new OfferRuleEngine().evaluate(offer, DecisionRules.defaults(), false);
        check(decision.outcome == RuleDecision.Outcome.AUTO_REJECT, "recusa nota muito baixa");
    }

    private static void buildsTrafficLightDecision() {
        OfferData offer = new OfferData("UBER", 734, 1.1, 3.4, 5, 10,
                4.89, "Santa Eugênia", "traffic", 1);
        DecisionRules rules = DecisionRules.defaults();
        RuleDecision decision = new OfferRuleEngine().evaluate(offer, rules, false);
        DecisionPresentation display = DecisionPresentation.from(offer, decision, rules);
        check("ACEITAR".equals(display.finalAdvice), "decisão final binária");
        check(display.kmLight == DecisionPresentation.Light.GREEN, "km verde");
        check(display.hourLight == DecisionPresentation.Light.YELLOW, "hora amarela em corrida curta");
        check(display.ratingLight == DecisionPresentation.Light.GREEN, "nota verde");
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

    private static void throttlesOcrWithoutMissingNewOffer() {
        OcrCadence cadence = new OcrCadence();
        check(cadence.shouldRead(10_000, 480, false), "primeiro quadro deve ser lido");
        check(!cadence.shouldRead(10_120, 10, false), "animação não inicia novo OCR");
        check(cadence.shouldRead(10_550, 10, false), "segunda leitura durante transição");
        check(!cadence.shouldRead(10_670, 110, false), "respeitar limite mínimo mesmo com nova oferta");
        check(cadence.shouldRead(10_790, 10, false), "nova oferta deve ser lida rapidamente");
        cadence.offerSeen(10_790);
        check(cadence.shouldRead(11_350, 0, false), "confirmar oferta recente");
        check(!cadence.shouldRead(11_470, 0, false), "tela parada não roda OCR continuamente");
        check(cadence.shouldRead(16_000, 0, false), "verificação esparsa em repouso");

        OcrCadence animatedMap = new OcrCadence();
        check(animatedMap.shouldRead(20_000, 480, false), "primeira leitura do mapa");
        for (long time = 20_120; time <= 22_520; time += 120) {
            animatedMap.shouldRead(time, 150, false);
        }
        check(!animatedMap.shouldRead(22_640, 150, false),
                "movimento forte contínuo não prolonga o OCR rápido");
        check(animatedMap.shouldRead(23_000, 150, true), "análise solicitada é imediata");
    }

    private static void keepsOneTruthForMoney() {
        List<MoneyLedger.Entry> entries = List.of(
                new MoneyLedger.Entry(1, 1, "LEGACY", "ANTERIOR", 10000, 0, false),
                new MoneyLedger.Entry(2, 2, "PLATFORM", "UBER", 15000, 15000, false),
                new MoneyLedger.Entry(3, 3, "PLATFORM", "UBER", 18000, 15000, false),
                new MoneyLedger.Entry(4, 4, "PLATFORM", "99", 12000, 12000, false),
                new MoneyLedger.Entry(5, 5, "EXTRA", "PARTICULAR", 5000, 0, false),
                new MoneyLedger.Entry(6, 6, "PLATFORM", "99", 14000, 12000, false),
                new MoneyLedger.Entry(7, 7, "EXTRA", "PARTICULAR", 1000, 0, true));
        check(MoneyLedger.at(entries, 2).totalCents() == 10000, "primeira leitura após migração estabelece base");
        check(MoneyLedger.at(entries, 5).totalCents() == 18000,
                "plataforma substitui e particular acumula");
        check(MoneyLedger.at(entries, 7).totalCents() == 20000,
                "lançamento desfeito não entra no saldo");
    }

    private static void filtersOdometerAndPauses() {
        Double distance = WorkMath.odometerKm(List.of(
                new WorkMath.Odometer(0, 113000),
                new WorkMath.Odometer(3_600_000, 0),
                new WorkMath.Odometer(7_200_000, 180000),
                new WorkMath.Odometer(10_800_000, 113080)));
        close(distance, 80, "odômetro inválido é ignorado e próxima âncora corrige");
        check(WorkMath.odometerKm(List.of(new WorkMath.Odometer(0, 113000))) == null,
                "sem duas âncoras não há distância");
        long active = WorkMath.activeMillis(0, 7_200_000,
                List.of(new WorkMath.Span(0, 7_200_000)),
                List.of(new WorkMath.Span(3_600_000, 5_400_000)));
        check(active == 5_400_000, "pausa sai do tempo ativo");
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

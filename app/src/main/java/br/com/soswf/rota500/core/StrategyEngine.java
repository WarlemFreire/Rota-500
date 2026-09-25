package br.com.soswf.rota500.core;

import java.util.Locale;

public final class StrategyEngine {
    public StrategyAdvice advise(StrategySnapshot snapshot) {
        double projected = snapshot.projectedFinalGross();
        if (snapshot.inUnsafeZone) {
            return new StrategyAdvice(
                    StrategyAdvice.Action.LEAVE_UNSAFE_AREA,
                    "Saia desta área",
                    "A segurança é uma restrição fixa e vem antes da meta diária.",
                    projected,
                    0.98);
        }
        if (snapshot.currentGross >= snapshot.dailyGoalGross) {
            return new StrategyAdvice(
                    StrategyAdvice.Action.END_SHIFT,
                    "Meta atingida",
                    "A meta bruta do dia foi alcançada. Encerrar agora preserva tempo e margem.",
                    snapshot.currentGross,
                    0.98);
        }
        if (snapshot.workedMinutes >= 720) {
            return new StrategyAdvice(
                    StrategyAdvice.Action.END_SHIFT,
                    "Jornada acima de 12 horas",
                    "O faturamento pode continuar subindo, mas o dia já não conta como eficiente.",
                    projected,
                    0.96);
        }
        if (snapshot.offersLastHour < 3) {
            return new StrategyAdvice(
                    StrategyAdvice.Action.COLLECT_MORE_DATA,
                    "Poucos dados recentes",
                    "Ainda não há ofertas suficientes para alterar a estratégia com segurança.",
                    projected,
                    0.42);
        }
        double viableRate = snapshot.offersLastHour == 0
                ? 0
                : snapshot.viableOffersLastHour / (double) snapshot.offersLastHour;
        if (snapshot.idleMinutes >= 20 && viableRate < 0.25) {
            return new StrategyAdvice(
                    StrategyAdvice.Action.MOVE,
                    "Considere mudar de área",
                    String.format(
                            Locale.forLanguageTag("pt-BR"),
                            "%d minutos sem resultado e apenas %.0f%% das ofertas dentro da régua.",
                            snapshot.idleMinutes,
                            viableRate * 100),
                    projected,
                    Math.min(0.9, 0.55 + snapshot.offersLastHour * 0.03));
        }
        if (snapshot.requiredGrossPerHour() > snapshot.robustGrossPerHour * 1.25
                && snapshot.idleMinutes >= 10) {
            return new StrategyAdvice(
                    StrategyAdvice.Action.REVIEW_RULER,
                    "Reavalie a régua",
                    String.format(
                            Locale.forLanguageTag("pt-BR"),
                            "O ritmo necessário é R$ %.0f/h e o ritmo robusto atual é R$ %.0f/h.",
                            snapshot.requiredGrossPerHour(),
                            snapshot.robustGrossPerHour),
                    projected,
                    0.68);
        }
        return new StrategyAdvice(
                StrategyAdvice.Action.STAY,
                "Permaneça na estratégia atual",
                "O fluxo recente ainda sustenta a projeção. Reavalie quando houver mudança relevante.",
                projected,
                0.66);
    }
}

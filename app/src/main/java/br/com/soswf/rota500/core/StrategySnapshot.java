package br.com.soswf.rota500.core;

public final class StrategySnapshot {
    public final double dailyGoalGross;
    public final double currentGross;
    public final int workedMinutes;
    public final int plannedMaximumMinutes;
    public final int idleMinutes;
    public final int offersLastHour;
    public final int viableOffersLastHour;
    public final double robustGrossPerHour;
    public final double historicalExpectedGrossPerHour;
    public final double emptyKmToday;
    public final String currentZone;
    public final String weather;
    public final String dynamicLevel;
    public final boolean inUnsafeZone;

    public StrategySnapshot(
            double dailyGoalGross,
            double currentGross,
            int workedMinutes,
            int plannedMaximumMinutes,
            int idleMinutes,
            int offersLastHour,
            int viableOffersLastHour,
            double robustGrossPerHour,
            double historicalExpectedGrossPerHour,
            double emptyKmToday,
            String currentZone,
            String weather,
            String dynamicLevel,
            boolean inUnsafeZone) {
        this.dailyGoalGross = dailyGoalGross;
        this.currentGross = currentGross;
        this.workedMinutes = Math.max(0, workedMinutes);
        this.plannedMaximumMinutes = Math.max(1, plannedMaximumMinutes);
        this.idleMinutes = Math.max(0, idleMinutes);
        this.offersLastHour = Math.max(0, offersLastHour);
        this.viableOffersLastHour = Math.max(0, viableOffersLastHour);
        this.robustGrossPerHour = Math.max(0, robustGrossPerHour);
        this.historicalExpectedGrossPerHour = Math.max(0, historicalExpectedGrossPerHour);
        this.emptyKmToday = Math.max(0, emptyKmToday);
        this.currentZone = currentZone == null ? "Não identificada" : currentZone;
        this.weather = weather == null ? "Não informado" : weather;
        this.dynamicLevel = dynamicLevel == null ? "Não lido" : dynamicLevel;
        this.inUnsafeZone = inUnsafeZone;
    }

    public double remainingGoal() {
        return Math.max(0, dailyGoalGross - currentGross);
    }

    public int remainingMinutes() {
        return Math.max(0, plannedMaximumMinutes - workedMinutes);
    }

    public double requiredGrossPerHour() {
        if (remainingGoal() <= 0) return 0;
        if (remainingMinutes() <= 0) return Double.POSITIVE_INFINITY;
        return remainingGoal() * 60.0 / remainingMinutes();
    }

    public double projectedFinalGross() {
        double expected = robustGrossPerHour > 0
                ? robustGrossPerHour
                : historicalExpectedGrossPerHour;
        return currentGross + expected * remainingMinutes() / 60.0;
    }
}

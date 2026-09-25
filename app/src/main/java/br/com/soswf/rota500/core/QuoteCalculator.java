package br.com.soswf.rota500.core;

public final class QuoteCalculator {
    public QuoteResult calculate(QuoteInput input) {
        double hours = input.totalMinutes() / 60.0;
        double operatingCost = input.totalKm() * input.operatingCostPerKm;
        double estimatedCost = operatingCost + input.tollsAndParking;

        double operationalFloor = estimatedCost + hours * input.desiredNetPerHour;
        double opportunityFloor = estimatedCost + hours * input.expectedAppNetPerHour;
        double base = Math.max(operationalFloor, opportunityFloor);
        double withMargin = base * (1.0 + input.marginPercent / 100.0);
        double recommended = roundUpToFive(withMargin);
        double net = recommended - estimatedCost;
        double netPerHour = hours <= 0 ? 0 : net / hours;

        return new QuoteResult(
                roundMoney(estimatedCost),
                roundMoney(operationalFloor),
                roundMoney(opportunityFloor),
                recommended,
                roundMoney(net),
                roundMoney(netPerHour));
    }

    private double roundUpToFive(double value) {
        return Math.ceil(value / 5.0) * 5.0;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

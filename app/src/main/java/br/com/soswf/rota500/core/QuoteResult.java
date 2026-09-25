package br.com.soswf.rota500.core;

public final class QuoteResult {
    public final double estimatedCost;
    public final double operationalFloor;
    public final double opportunityFloor;
    public final double recommendedPrice;
    public final double estimatedNet;
    public final double estimatedNetPerHour;

    public QuoteResult(
            double estimatedCost,
            double operationalFloor,
            double opportunityFloor,
            double recommendedPrice,
            double estimatedNet,
            double estimatedNetPerHour) {
        this.estimatedCost = estimatedCost;
        this.operationalFloor = operationalFloor;
        this.opportunityFloor = opportunityFloor;
        this.recommendedPrice = recommendedPrice;
        this.estimatedNet = estimatedNet;
        this.estimatedNetPerHour = estimatedNetPerHour;
    }
}

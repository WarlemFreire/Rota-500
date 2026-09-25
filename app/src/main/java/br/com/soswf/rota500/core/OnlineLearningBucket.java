package br.com.soswf.rota500.core;

public final class OnlineLearningBucket {
    private long samples;
    private double meanGrossPerHour;
    private double meanIdleMinutes;

    public void update(double grossPerHour, double idleMinutes) {
        samples++;
        meanGrossPerHour += (grossPerHour - meanGrossPerHour) / samples;
        meanIdleMinutes += (idleMinutes - meanIdleMinutes) / samples;
    }

    public long samples() {
        return samples;
    }

    public double expectedGrossPerHour(double globalPrior, double priorWeight) {
        return (meanGrossPerHour * samples + globalPrior * priorWeight) / (samples + priorWeight);
    }

    public double expectedIdleMinutes(double globalPrior, double priorWeight) {
        return (meanIdleMinutes * samples + globalPrior * priorWeight) / (samples + priorWeight);
    }
}

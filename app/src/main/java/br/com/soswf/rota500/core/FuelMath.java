package br.com.soswf.rota500.core;

import java.util.List;

/**
 * Custo real de combustível por quilômetro, medido entre abastecimentos de tanque cheio.
 * Um ciclo começa num tanque cheio e termina no tanque cheio seguinte; o combustível
 * pago nesse intervalo (parciais do meio + o tanque cheio final) rodou os km do ciclo.
 */
public final class FuelMath {
    public static final int CYCLES = 3;
    /** Divergência a partir da qual o valor real deve ser destacado para conferência. */
    public static final double DIVERGENCE_ALERT = 0.30;

    public record Refuel(long at, long cents, double liters, double odometerKm, boolean fullTank) { }

    /** costPerKm em reais; kmPerLiter pode ser nulo se algum ciclo não tiver litros. */
    public record Result(double costPerKm, Double kmPerLiter, int cycles, double km) { }

    private FuelMath() { }

    /** Refuels must be in chronological order. Returns null while no complete cycle exists. */
    public static Result realCost(List<Refuel> refuels) {
        int[] ends = new int[CYCLES];
        int[] starts = new int[CYCLES];
        int found = 0;
        Integer end = null;
        for (int i = refuels.size() - 1; i >= 0 && found < CYCLES; i--) {
            Refuel point = refuels.get(i);
            if (!point.fullTank() || !valid(point)) continue;
            if (end != null) {
                if (refuels.get(end).odometerKm() > point.odometerKm()) {
                    starts[found] = i;
                    ends[found] = end;
                    found++;
                }
            }
            end = i;
        }
        if (found == 0) return null;
        long cents = 0;
        double km = 0, liters = 0;
        boolean litersKnown = true;
        for (int c = 0; c < found; c++) {
            km += refuels.get(ends[c]).odometerKm() - refuels.get(starts[c]).odometerKm();
            for (int i = starts[c] + 1; i <= ends[c]; i++) {
                Refuel point = refuels.get(i);
                cents += point.cents();
                if (point.liters() > 0) liters += point.liters();
                else litersKnown = false;
            }
        }
        if (km <= 0) return null;
        Double kmPerLiter = litersKnown && liters > 0 ? km / liters : null;
        return new Result(cents / 100.0 / km, kmPerLiter, found, km);
    }

    public static boolean divergent(double real, double temporary) {
        if (temporary <= 0) return real > 0;
        return Math.abs(real - temporary) / temporary > DIVERGENCE_ALERT;
    }

    private static boolean valid(Refuel point) {
        return point.cents() > 0 && Double.isFinite(point.odometerKm()) && point.odometerKm() > 0;
    }
}

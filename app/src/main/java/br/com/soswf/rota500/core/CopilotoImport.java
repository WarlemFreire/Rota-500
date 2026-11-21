package br.com.soswf.rota500.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converte o backup do app Copiloto (formato "copiloto", versão 1) num plano de importação
 * para o banco do Rota 500. Não grava nada: o banco aplica o plano numa transação.
 *
 * Regras (docs/SPEC-0.9.md, Q44-Q46):
 * - Saldos do Copiloto são o total do dia de cada plataforma; viram leituras PLATFORM.
 * - Jornadas com mais de 14 h ativas foram esquecidas abertas: fecham no último ganho.
 * - Gasto acima de R$ 1.000 num lançamento e odômetro que volta para trás ficam de fora.
 * - Jornada com o mesmo início de uma já existente não é importada de novo.
 */
public final class CopilotoImport {
    public static final double FORGOTTEN_ACTIVE_HOURS = 14;
    public static final long MAX_EXPENSE_CENTS = 100_000;
    public static final double MAX_JOURNEY_KM = 2_000;
    public static final long SPREADSHEET_GAP_MS = 2 * 3_600_000L;

    public record Session(String id, long start, long end, long originalEnd, int dayKey,
            boolean closedEarly, long grossCents) { }
    public record Money(String sessionId, long at, String kind, String source, long cents, long baselineCents,
            boolean voided) { }
    public record Pause(String sessionId, long start, long end, String reason) { }
    /** sessionId nulo: gasto fora de jornada importada. */
    public record Expense(String sessionId, long at, int dayKey, String category, long cents,
            Double quantity, Double odometerKm, String externalId) { }
    public record Odometer(String sessionId, long at, double km) { }
    public record Point(String sessionId, long at, double latitude, double longitude) { }

    public record Plan(List<Session> sessions, List<Money> money, List<Pause> pauses, List<Expense> expenses,
            List<Odometer> odometers, List<Point> points, List<String> problems, int skippedDuplicates,
            Double wearPerKm, Double fuelPerKm, String fuelSource) {
        public long grossCents() {
            long total = 0;
            for (Session session : sessions) total += session.grossCents();
            return total;
        }
        public long expenseCents() {
            long total = 0;
            for (Expense expense : expenses) total += expense.cents();
            return total;
        }
        public int closedEarly() {
            int count = 0;
            for (Session session : sessions) if (session.closedEarly()) count++;
            return count;
        }
    }

    private CopilotoImport() { }

    @SuppressWarnings("unchecked")
    public static Plan plan(String json, Set<Long> existingSessionStarts, ZoneId zone) {
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map<?, ?> root) || !"copiloto".equals(root.get("app"))) {
            throw new IllegalArgumentException("Este arquivo não é um backup do Copiloto.");
        }
        Map<String, Object> data = map(root.get("dados"));
        Map<String, Object> config = map(root.get("config"));
        List<String> problems = new ArrayList<>();

        List<Map<String, Object>> journeys = list(data.get("jornadas"));
        journeys.sort(Comparator.comparingLong(j -> number(j.get("horaInicio"))));
        Map<String, List<Map<String, Object>>> checkpoints = group(list(data.get("registros")));
        Map<String, List<Map<String, Object>>> rides = group(list(data.get("corridas")));
        Map<String, List<Map<String, Object>>> pausesBy = group(list(data.get("pausas")));
        List<Map<String, Object>> trail = list(data.get("trilha"));
        trail.sort(Comparator.comparingLong(t -> number(t.get("quando"))));

        List<Session> sessions = new ArrayList<>();
        List<Money> money = new ArrayList<>();
        List<Pause> pauses = new ArrayList<>();
        List<Odometer> odometers = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        Map<String, Session> imported = new HashMap<>();
        Set<String> platformsSeenByDay = new HashSet<>();
        int duplicates = 0;

        for (Map<String, Object> journey : journeys) {
            String id = text(journey.get("id"));
            long start = number(journey.get("horaInicio"));
            if (id.isEmpty() || start <= 0) { problems.add("Jornada sem identificação ou início ignorada."); continue; }
            if (existingSessionStarts.contains(start)) { duplicates++; continue; }
            String date = text(journey.get("data"));
            int day = dayKey(date, start, zone);
            long originalEnd = Math.max(start, number(journey.get("horaFim")));
            List<Map<String, Object>> own = sorted(checkpoints.getOrDefault(id, List.of()), "timestamp");
            List<Map<String, Object>> ownRides = sorted(rides.getOrDefault(id, List.of()), "timestamp");

            // Dinheiro primeiro: o último ganho define o fim de uma jornada esquecida.
            List<Money> sessionMoney = new ArrayList<>();
            Map<String, Object> initial = map(journey.get("saldoInicial"));
            for (Map<String, Object> checkpoint : own) {
                long at = number(checkpoint.get("timestamp"));
                boolean undone = Boolean.TRUE.equals(checkpoint.get("desfeito"));
                for (Map.Entry<String, Object> balance : map(checkpoint.get("saldos")).entrySet()) {
                    if (!(balance.getValue() instanceof Double value)) continue;
                    String source = platform(balance.getKey());
                    // Total do dia: a primeira jornada do dia sem histórico anterior usa o saldo inicial como base.
                    String dayPlatform = day + "|" + source;
                    long baseline = platformsSeenByDay.contains(dayPlatform) ? 0
                            : cents(initial.get(balance.getKey()));
                    sessionMoney.add(new Money(id, at, "PLATFORM", source, cents(value), baseline, undone));
                }
                Map<String, Object> extra = map(checkpoint.get("avulso"));
                if (!extra.isEmpty() && cents(extra.get("valor")) > 0) {
                    sessionMoney.add(new Money(id, at, "EXTRA", extraSource(text(extra.get("tipo"))),
                            cents(extra.get("valor")), 0, undone));
                }
            }
            for (Money item : sessionMoney) if ("PLATFORM".equals(item.kind())) platformsSeenByDay.add(day + "|" + item.source());
            if (own.isEmpty()) {
                // Jornadas importadas de planilha no Copiloto: o dinheiro está nas corridas.
                for (Map<String, Object> ride : ownRides) {
                    long value = cents(ride.get("valorBruto"));
                    if (value <= 0) continue;
                    sessionMoney.add(new Money(id, number(ride.get("timestamp")), "EXTRA",
                            platform(text(ride.get("plataforma"))), value, 0, false));
                }
            } else if (!ownRides.isEmpty()) {
                problems.add("Jornada de " + date + ": corridas individuais ignoradas (os totais já cobrem o dia).");
            }

            long lastGain = 0;
            for (Money item : sessionMoney) if (!item.voided()) lastGain = Math.max(lastGain, item.at());
            // Planilha: sem registro do tempo trabalhado. Intervalos longos entre corridas viram pausa.
            List<Pause> gaps = new ArrayList<>();
            boolean spreadsheet = own.isEmpty() && !ownRides.isEmpty();
            if (spreadsheet) {
                long previousEnd = 0;
                for (Map<String, Object> ride : ownRides) {
                    long rideStart = number(ride.get("timestamp"));
                    long rideEnd = rideStart + Math.round(doubleValue(ride.get("duracaoMin")) * 60_000);
                    if (previousEnd > 0 && rideStart - previousEnd > SPREADSHEET_GAP_MS) {
                        gaps.add(new Pause(id, previousEnd, rideStart, "Outro"));
                    }
                    previousEnd = Math.max(previousEnd, rideEnd);
                }
                originalEnd = Math.max(originalEnd, previousEnd);
                if (!gaps.isEmpty()) problems.add("Jornada de planilha de " + date + ": " + gaps.size()
                        + " intervalo(s) de mais de 2 h sem corrida contados como pausa.");
            }
            long pausedMs = 0;
            for (Pause gap : gaps) pausedMs += gap.end() - gap.start();
            for (Map<String, Object> pause : pausesBy.getOrDefault(id, List.of())) {
                long ps = number(pause.get("horaInicio"));
                long pe = number(pause.get("horaFim"));
                if (ps > 0 && pe > ps) pausedMs += Math.min(pe, originalEnd) - Math.max(ps, start);
            }
            double activeHours = (originalEnd - start - Math.max(0, pausedMs)) / 3_600_000.0;
            long end = originalEnd;
            boolean closedEarly = false;
            if (!spreadsheet && activeHours > FORGOTTEN_ACTIVE_HOURS) {
                long lastMove = lastPointBetween(trail, start, originalEnd);
                end = JourneyWatch.presumedEnd(start, lastGain, lastMove, originalEnd);
                closedEarly = true;
                problems.add(String.format(Locale.ROOT, "Jornada de %s estava aberta há %.0f h: encerrada no último ganho (%.1f h ativas).",
                        date, activeHours, Math.max(0, (end - start - Math.max(0, pausedMs)) / 3_600_000.0)));
            }
            final long sessionEnd = end;
            pauses.addAll(gaps);
            for (Money item : sessionMoney) {
                if (item.at() > sessionEnd) {
                    // Ganho depois do fim presumido não deveria existir; mantém no fim da jornada.
                    money.add(new Money(item.sessionId(), sessionEnd, item.kind(), item.source(), item.cents(),
                            item.baselineCents(), item.voided()));
                } else money.add(item);
            }

            for (Map<String, Object> pause : pausesBy.getOrDefault(id, List.of())) {
                long ps = Math.max(start, number(pause.get("horaInicio")));
                long pe = number(pause.get("horaFim"));
                if (pe <= 0 || pe > sessionEnd) pe = sessionEnd;
                if (ps >= pe) continue;
                pauses.add(new Pause(id, ps, pe, pauseReason(text(pause.get("motivo")))));
            }

            Double odoStart = positive(journey.get("odometroInicio"));
            Double odoEnd = positive(journey.get("odometroFim"));
            if (odoStart != null) odometers.add(new Odometer(id, start, odoStart));
            if (odoEnd != null) {
                if (odoStart != null && (odoEnd < odoStart || odoEnd - odoStart > MAX_JOURNEY_KM)) {
                    problems.add(String.format(Locale.ROOT, "Jornada de %s: odômetro final %.0f incoerente com o inicial %.0f, ignorado.",
                            date, odoEnd, odoStart));
                } else odometers.add(new Odometer(id, sessionEnd, odoEnd));
            }

            long gross = dayGross(sessionMoney, initial);
            Session session = new Session(id, start, sessionEnd, originalEnd, day, closedEarly, gross);
            sessions.add(session);
            imported.put(id, session);
        }

        // Trilha de GPS: cada ponto vai para a jornada importada que o contém.
        int cursor = 0;
        List<Session> byStart = new ArrayList<>(sessions);
        byStart.sort(Comparator.comparingLong(Session::start));
        for (Map<String, Object> point : trail) {
            long at = number(point.get("quando"));
            while (cursor < byStart.size() && byStart.get(cursor).end() < at) cursor++;
            if (cursor >= byStart.size()) break;
            Session session = byStart.get(cursor);
            if (at < session.start()) continue;
            Double lat = (Double) (point.get("lat") instanceof Double v ? v : null);
            Double lon = (Double) (point.get("lon") instanceof Double v ? v : null);
            if (lat == null || lon == null) continue;
            points.add(new Point(session.id(), at, lat, lon));
        }

        List<Expense> expenses = new ArrayList<>();
        List<Map<String, Object>> costs = sorted(list(data.get("custos")), "timestamp");
        for (Map<String, Object> cost : costs) {
            long at = number(cost.get("timestamp"));
            long value = cents(cost.get("valor"));
            String type = text(cost.get("tipo")).toLowerCase(Locale.ROOT);
            String journeyId = text(cost.get("jornadaId"));
            if (value <= 0) continue;
            if (value > MAX_EXPENSE_CENTS) {
                problems.add(String.format(Locale.ROOT, "Gasto de %s em %s de R$ %.2f ignorado (valor acima de R$ 1.000, provável erro de digitação).",
                        type, LocalDate.ofInstant(Instant.ofEpochMilli(at), zone), value / 100.0));
                continue;
            }
            Session session = imported.get(journeyId);
            if (session == null && !journeyId.isEmpty() && journeyExists(journeys, journeyId)) continue; // jornada já importada antes
            int day = session != null ? session.dayKey() : dayKey("", at, zone);
            expenses.add(new Expense(session == null ? null : session.id(), at, day, category(type), value,
                    positive(cost.get("litros")), positive(cost.get("odometro")), text(cost.get("id"))));
        }

        Double wear = positive(config.get("custoDesgasteKm"));
        String[] source = new String[1];
        Double fuel = fuelPerKm(costs, config, source);
        return new Plan(sessions, money, pauses, expenses, odometers, points, problems, duplicates, wear, fuel, source[0]);
    }

    /**
     * Combustível por km medido: valor pago entre o primeiro e o último abastecimento com odômetro,
     * dividido pelos km rodados. Sem dados suficientes, usa preços e consumo configurados no Copiloto.
     */
    static Double fuelPerKm(List<Map<String, Object>> costs, Map<String, Object> config, String[] source) {
        List<double[]> fuel = new ArrayList<>();
        for (Map<String, Object> cost : costs) {
            String type = text(cost.get("tipo")).toLowerCase(Locale.ROOT);
            if (!"COMBUSTIVEL".equals(category(type))) continue;
            long value = cents(cost.get("valor"));
            Double odometer = positive(cost.get("odometro"));
            if (value <= 0 || value > MAX_EXPENSE_CENTS || odometer == null) continue;
            fuel.add(new double[]{odometer, value / 100.0});
        }
        fuel.sort(Comparator.comparingDouble(f -> f[0]));
        if (fuel.size() >= 3) {
            double km = fuel.get(fuel.size() - 1)[0] - fuel.get(0)[0];
            double paid = 0;
            for (int i = 1; i < fuel.size(); i++) paid += fuel.get(i)[1];
            if (km >= 300) {
                source[0] = String.format(Locale.ROOT, "medido em %d abastecimentos, %.0f km", fuel.size(), km);
                return paid / km;
            }
        }
        Double mix = positive(config.get("mixGnvPct"));
        Double gnvPrice = positive(config.get("precoGnv"));
        Double kmPerM3 = positive(config.get("kmPorM3"));
        Double liquidPrice = positive(config.get("precoEtanol"));
        Double kmPerLiter = positive(config.get("kmPorLitro"));
        if (mix != null && gnvPrice != null && kmPerM3 != null && liquidPrice != null && kmPerLiter != null) {
            double share = Math.min(100, mix) / 100.0;
            source[0] = "calculado pelos preços e consumo do Copiloto";
            return share * gnvPrice / kmPerM3 + (1 - share) * liquidPrice / kmPerLiter;
        }
        source[0] = "sem dados";
        return null;
    }

    private static long dayGross(List<Money> sessionMoney, Map<String, Object> initial) {
        Map<String, Long> last = new HashMap<>();
        Map<String, Long> base = new HashMap<>();
        long extras = 0;
        for (Money item : sessionMoney) {
            if (item.voided()) continue;
            if ("PLATFORM".equals(item.kind())) {
                last.put(item.source(), item.cents());
                base.putIfAbsent(item.source(), 0L);
            } else extras += item.cents();
        }
        long total = extras;
        for (Map.Entry<String, Long> entry : last.entrySet()) {
            long start = 0;
            for (Map.Entry<String, Object> init : initial.entrySet()) {
                if (platform(init.getKey()).equals(entry.getKey())) start = cents(init.getValue());
            }
            total += Math.max(0, entry.getValue() - start);
        }
        return total;
    }

    private static boolean journeyExists(List<Map<String, Object>> journeys, String id) {
        for (Map<String, Object> journey : journeys) if (id.equals(text(journey.get("id")))) return true;
        return false;
    }

    private static long lastPointBetween(List<Map<String, Object>> trail, long from, long to) {
        long last = 0;
        for (Map<String, Object> point : trail) {
            long at = number(point.get("quando"));
            if (at >= from && at <= to) last = Math.max(last, at);
        }
        return last;
    }

    static String platform(String name) {
        String value = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "uber" -> "UBER";
            case "99", "99pop", "noventa e nove" -> "99";
            case "indrive", "in drive" -> "INDRIVE";
            default -> value.isEmpty() ? "OUTRA" : value.toUpperCase(Locale.ROOT);
        };
    }

    private static String extraSource(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "particular" -> "PARTICULAR";
            case "entrega" -> "ENTREGA";
            default -> "OUTRO";
        };
    }

    static String category(String type) {
        return switch (type) {
            case "gnv", "etanol", "gasolina", "diesel", "combustivel", "combustível" -> "COMBUSTIVEL";
            case "alimentacao", "alimentação", "comida", "almoco", "almoço" -> "ALIMENTACAO";
            case "manutencao", "manutenção", "oficina", "pneu", "oleo", "óleo", "revisao", "revisão" -> "MANUTENCAO";
            case "parcela", "financiamento", "aluguel" -> "FINANCIAMENTO";
            default -> "OUTROS";
        };
    }

    private static String pauseReason(String reason) {
        return switch (reason.toLowerCase(Locale.ROOT)) {
            case "abastecimento", "abastecer" -> "Abastecer";
            case "almoco", "almoço", "refeicao", "refeição" -> "Almoço";
            case "banheiro" -> "Banheiro";
            case "pessoal" -> "Pessoal";
            case "descanso" -> "Descanso";
            case "cochilo" -> "Cochilo";
            case "espera" -> "Espera estratégica";
            default -> "Outro";
        };
    }

    private static int dayKey(String date, long at, ZoneId zone) {
        LocalDate day;
        try { day = date.isEmpty() ? null : LocalDate.parse(date); }
        catch (RuntimeException invalid) { day = null; }
        if (day == null) day = LocalDate.ofInstant(Instant.ofEpochMilli(at), zone);
        return day.getYear() * 10_000 + day.getMonthValue() * 100 + day.getDayOfMonth();
    }

    private static Map<String, List<Map<String, Object>>> group(List<Map<String, Object>> items) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> item : items) {
            result.computeIfAbsent(text(item.get("jornadaId")), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static List<Map<String, Object>> sorted(List<Map<String, Object>> items, String field) {
        List<Map<String, Object>> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparingLong(item -> number(item.get(field))));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) if (item instanceof Map<?, ?> m) result.add((Map<String, Object>) m);
        }
        return result;
    }

    private static String text(Object value) { return value instanceof String s ? s : ""; }

    private static long number(Object value) { return value instanceof Double d ? Math.round(d) : 0; }

    private static double doubleValue(Object value) {
        return value instanceof Double d && Double.isFinite(d) && d > 0 ? d : 0;
    }

    private static long cents(Object value) {
        return value instanceof Double d && Double.isFinite(d) ? Math.round(d * 100) : 0;
    }

    private static Double positive(Object value) {
        return value instanceof Double d && Double.isFinite(d) && d > 0 ? d : null;
    }
}

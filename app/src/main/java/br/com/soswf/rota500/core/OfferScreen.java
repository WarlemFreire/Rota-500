package br.com.soswf.rota500.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reconhece se o texto lido da tela parece uma oferta, e diz o que faltou quando não parece. */
public final class OfferScreen {
    public static final String MONEY = "valor em R$";
    public static final String DISTANCE = "distância em km";
    public static final String TIME = "tempo em min";
    public static final String ACTION = "palavra de ação (aceitar, recusar, selecionar…)";

    private OfferScreen() { }

    /** Lista vazia: o texto tem tudo o que uma oferta precisa. */
    public static List<String> missing(List<String> lines) {
        String text = String.join(" ", lines).toLowerCase(Locale.forLanguageTag("pt-BR"));
        List<String> missing = new ArrayList<>();
        if (!text.contains("r$")) missing.add(MONEY);
        if (!text.contains(" km") && !text.contains("quilômetr")) missing.add(DISTANCE);
        if (!text.contains(" min") && !text.contains("minuto")) missing.add(TIME);
        boolean action = text.contains("aceitar") || text.contains("recusar")
                || text.contains("rejeitar") || text.contains("solicitação")
                || text.contains("solicitacao") || text.contains("nova corrida")
                || text.contains("nova viagem") || text.contains("selecionar");
        if (!action) missing.add(ACTION);
        return missing;
    }

    public static String platformFor(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.contains("uber")) return "UBER";
        if (value.contains("99pop") || value.contains("99 motorista") || value.contains("99 moto")) return "99";
        return "APP";
    }

    /**
     * Quadro bloqueado pela proteção de tela (FLAG_SECURE) chega preto. rgb são cores 0xRRGGBB
     * de uma grade de amostras; mais de 97% quase pretas indica bloqueio (ou tela apagada).
     */
    public static boolean mostlyBlack(int[] rgb, int count) {
        if (count <= 0) return false;
        int dark = 0;
        for (int i = 0; i < count; i++) {
            int color = rgb[i];
            int luma = (((color >> 16) & 0xff) * 299 + ((color >> 8) & 0xff) * 587 + (color & 0xff) * 114) / 1000;
            if (luma < 10) dark++;
        }
        return dark * 100 >= count * 97;
    }
}

package br.com.soswf.rota500.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Escolhe a atualização a oferecer. O versionCode é o número do build no GitHub (Q26) e vai
 * no nome do APK: rota500-0.9.0-teste.12-b12.apk. Versões de teste só com o canal ligado (Q25).
 */
public final class UpdatePicker {
    private static final Pattern BUILD = Pattern.compile("-b(\\d+)\\.apk$", Pattern.CASE_INSENSITIVE);

    public record Candidate(String versionName, long versionCode, boolean prerelease,
            String downloadUrl, String assetName, long sizeBytes) { }

    private UpdatePicker() { }

    /** versionCode lido do nome do APK, ou -1 se o nome não seguir o padrão. */
    public static long versionCodeFromAsset(String assetName) {
        if (assetName == null) return -1;
        Matcher matcher = BUILD.matcher(assetName.trim());
        if (!matcher.find()) return -1;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException error) { return -1; }
    }

    public static Candidate best(List<Candidate> candidates, long installedCode, boolean testChannel) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.versionCode() <= installedCode) continue;
            if (candidate.prerelease() && !testChannel) continue;
            if (best == null || candidate.versionCode() > best.versionCode()) best = candidate;
        }
        return best;
    }
}

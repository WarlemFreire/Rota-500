package br.com.soswf.rota500.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor JSON mínimo e sem dependências, para o núcleo poder ser testado fora do Android.
 * Objetos viram Map, listas viram List, números viram Double.
 */
public final class MiniJson {
    private final String text;
    private int position;

    private MiniJson(String text) { this.text = text; }

    public static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        parser.skipSpace();
        Object value = parser.value();
        parser.skipSpace();
        if (parser.position != text.length()) throw parser.error("conteúdo depois do fim");
        return value;
    }

    private Object value() {
        if (position >= text.length()) throw error("fim inesperado");
        char c = text.charAt(position);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        position++;
        skipSpace();
        if (peek('}')) { position++; return map; }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            map.put(key, value());
            skipSpace();
            if (peek(',')) { position++; continue; }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        position++;
        skipSpace();
        if (peek(']')) { position++; return list; }
        while (true) {
            skipSpace();
            list.add(value());
            skipSpace();
            if (peek(',')) { position++; continue; }
            expect(']');
            return list;
        }
    }

    private String string() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') return builder.toString();
            if (c != '\\') { builder.append(c); continue; }
            if (position >= text.length()) break;
            char e = text.charAt(position++);
            switch (e) {
                case '"', '\\', '/' -> builder.append(e);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) throw error("escape unicode incompleto");
                    builder.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> throw error("escape inválido");
            }
        }
        throw error("texto sem fim");
    }

    private Double number() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) position++;
        if (start == position) throw error("valor inválido");
        try { return Double.valueOf(text.substring(start, position)); }
        catch (NumberFormatException failure) { throw error("número inválido"); }
    }

    private void literal(String word) {
        if (!text.startsWith(word, position)) throw error("valor inválido");
        position += word.length();
    }

    private boolean peek(char c) { return position < text.length() && text.charAt(position) == c; }

    private void expect(char c) {
        if (!peek(c)) throw error("esperado '" + c + "'");
        position++;
    }

    private void skipSpace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON inválido na posição " + position + ": " + message);
    }
}

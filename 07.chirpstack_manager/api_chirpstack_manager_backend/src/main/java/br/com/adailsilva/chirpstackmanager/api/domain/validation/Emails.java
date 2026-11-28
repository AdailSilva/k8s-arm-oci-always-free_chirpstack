package br.com.adailsilva.chirpstackmanager.api.domain.validation;

import java.util.regex.Pattern;

/**
 * Validação de e-mail para dados que não passam por Bean Validation (corpos repassados ao ChirpStack).
 * Vazio é tratado como válido: a obrigatoriedade é verificada à parte.
 */
public final class Emails {

    public static final String MSG_INVALIDO = "E-mail inválido.";

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");

    private Emails() {
    }

    public static boolean valido(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        String s = email.trim();
        return s.length() <= 160 && EMAIL.matcher(s).matches();
    }
}

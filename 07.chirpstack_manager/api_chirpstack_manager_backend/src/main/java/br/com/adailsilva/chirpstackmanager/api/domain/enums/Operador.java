package br.com.adailsilva.chirpstackmanager.api.domain.enums;

import java.math.BigDecimal;

/** Comparação de uma regra de alerta do tipo VALOR. */
public enum Operador {
    GT(">") { public boolean avaliar(BigDecimal v, BigDecimal l) { return v.compareTo(l) > 0; } },
    GTE(">=") { public boolean avaliar(BigDecimal v, BigDecimal l) { return v.compareTo(l) >= 0; } },
    LT("<") { public boolean avaliar(BigDecimal v, BigDecimal l) { return v.compareTo(l) < 0; } },
    LTE("<=") { public boolean avaliar(BigDecimal v, BigDecimal l) { return v.compareTo(l) <= 0; } },
    EQ("=") { public boolean avaliar(BigDecimal v, BigDecimal l) { return v.compareTo(l) == 0; } },
    NE("≠") { public boolean avaliar(BigDecimal v, BigDecimal l) { return v.compareTo(l) != 0; } };

    private final String simbolo;

    Operador(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }

    /** true quando o valor satisfaz a condição (ou seja, a regra dispara). */
    public abstract boolean avaliar(BigDecimal valor, BigDecimal limite);
}

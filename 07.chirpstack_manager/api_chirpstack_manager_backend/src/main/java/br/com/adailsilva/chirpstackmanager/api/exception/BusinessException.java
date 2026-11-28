package br.com.adailsilva.chirpstackmanager.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio com mensagem pronta para o usuário.
 */
public class BusinessException extends RuntimeException {

    /** Código padrão das regras de negócio no corpo do erro ({@code erro}). */
    public static final String REGRA_NEGOCIO = "REGRA_NEGOCIO";

    private final HttpStatus status;
    private final String codigo;

    public BusinessException(HttpStatus status, String message) {
        this(status, REGRA_NEGOCIO, message);
    }

    /** Com código próprio, para o frontend reagir (ex.: EMAIL_NAO_CONFIRMADO no login). */
    public BusinessException(HttpStatus status, String codigo, String message) {
        super(message);
        this.status = status;
        this.codigo = codigo == null ? REGRA_NEGOCIO : codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

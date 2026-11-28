package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Ciclo de vida de um comando na fila. */
public enum StatusComando {
    PENDENTE, ENFILEIRADO, TRANSMITIDO, EXECUTADO, FALHOU, EXPIRADO, CANCELADO;

    /** Já está no ChirpStack ou no ar: ocupa o dispositivo. */
    public boolean emAndamento() {
        return this == ENFILEIRADO || this == TRANSMITIDO;
    }

    public boolean finalizado() {
        return this == EXECUTADO || this == FALHOU || this == EXPIRADO || this == CANCELADO;
    }
}

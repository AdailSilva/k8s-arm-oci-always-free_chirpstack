package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Como terminou um envio ao ChirpStack. */
public enum ResultadoTentativa {
    EXECUTADO, SEM_RESPOSTA, NAO_CONFIRMADO, ERRO, RECUSADO, OCUPADO, PERDIDO, EXPIRADO, CANCELADO
}

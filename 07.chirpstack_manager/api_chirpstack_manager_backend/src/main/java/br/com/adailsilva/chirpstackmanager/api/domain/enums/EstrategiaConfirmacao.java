package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Que prova encerra um comando como EXECUTADO. */
public enum EstrategiaConfirmacao {
    /** Resposta na porta 11 com o mesmo seq e opcode (protocolo ADAILSILVA_V1). */
    RESPOSTA_V1,
    /** Resposta "AT OK" na porta dos comandos AT (Radioenge). */
    RESPOSTA_AT,
    /** Resposta Modbus válida vinda pela serial transparente (porta 4). */
    RESPOSTA_MODBUS,
    /** Downlink confirmado: o ack LoRaWAN do dispositivo. */
    CONFIRMACAO_LORAWAN,
    /** Firmware que não responde: basta o gateway transmitir (txack). */
    TRANSMISSAO
}

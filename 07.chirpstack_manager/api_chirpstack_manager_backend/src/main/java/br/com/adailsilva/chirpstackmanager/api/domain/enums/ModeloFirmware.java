package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Como o servidor conversa com o firmware do equipamento. */
public enum ModeloFirmware {
    /** Protocolo binário próprio: comando na porta 10, resposta com o mesmo seq na 11, telemetria TLV na 20. */
    ADAILSILVA_V1("Protocolo ADAILSILVA_V1", "Relés e PZEM-004T com prova de execução por resposta (seq)."),
    /** Firmware Radioenge (STM32 + SX1272): comandos AT na porta 103, binários na 102 e serial transparente na 4. */
    RADIOENGE_ORIGINAL("Radioenge original (AT)", "Relés pelas GPIOs (AT+WPIN) e PZEM-004T pela serial transparente (Modbus na porta 4)."),
    /** Firmware ESP32 base: só o reset remoto na porta 101. */
    ESP32_ORIGINAL("ESP32 original", "Só o reset remoto; sem relés nem sensores.");

    private final String rotulo;
    private final String descricao;

    ModeloFirmware(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    public String rotulo() { return rotulo; }
    public String descricao() { return descricao; }
}

package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Comandos que o painel sabe pedir. Cada modelo de firmware suporta um subconjunto. */
public enum TipoComando {
    STATUS("Consultar status", null),
    INTERVALO("Intervalo de envio", null),
    ENVIAR_AGORA("Enviar leitura agora", null),
    CONFIRMADO("Uplink confirmado", null),
    CLASSE("Classe LoRaWAN", null),
    REINICIAR("Reiniciar", null),
    RELE_DEFINIR("Ligar/desligar relé", TipoPeriferico.RELE),
    RELE_PULSO("Pulso no relé", TipoPeriferico.RELE),
    RELE_MASCARA("Vários relés de uma vez", TipoPeriferico.RELE),
    RELE_LER("Ler relés", TipoPeriferico.RELE),
    PZEM_LER("Ler medidor de energia", TipoPeriferico.PZEM004T),
    PZEM_ZERAR_ENERGIA("Zerar energia acumulada", TipoPeriferico.PZEM004T),
    PZEM_LIMITE_POTENCIA("Limite de potência do medidor", TipoPeriferico.PZEM004T),
    RESET_REMOTO("Reset remoto (porta 101)", null),
    BRUTO("Downlink livre (porta e bytes)", null);

    private final String rotulo;
    private final TipoPeriferico periferico;

    TipoComando(String rotulo, TipoPeriferico periferico) {
        this.rotulo = rotulo;
        this.periferico = periferico;
    }

    public String rotulo() { return rotulo; }
    /** Periférico exigido pelo comando ({@code null} = do equipamento). */
    public TipoPeriferico periferico() { return periferico; }
}

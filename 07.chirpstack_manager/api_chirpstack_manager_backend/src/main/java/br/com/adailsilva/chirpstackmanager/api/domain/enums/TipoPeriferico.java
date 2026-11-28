package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Atuadores e sensores que um equipamento pode ter. Novos tipos entram aqui e no driver do modelo. */
public enum TipoPeriferico {
    RELE("Relé", true),
    PZEM004T("Medidor de energia PZEM-004T", false);

    private final String rotulo;
    private final boolean atuador;

    TipoPeriferico(String rotulo, boolean atuador) {
        this.rotulo = rotulo;
        this.atuador = atuador;
    }

    public String rotulo() { return rotulo; }
    public boolean atuador() { return atuador; }
}

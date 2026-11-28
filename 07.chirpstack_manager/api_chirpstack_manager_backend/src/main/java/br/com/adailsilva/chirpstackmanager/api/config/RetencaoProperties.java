package br.com.adailsilva.chirpstackmanager.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retenção das leituras (prefixo {@code app.retencao}).
 *
 * @param leiturasDias            leituras e eventos mais antigos são apagados toda madrugada
 * @param semComunicacaoMinutos   sem mensagem há mais que isso = "sem comunicação" no painel
 */
@ConfigurationProperties(prefix = "app.retencao")
public record RetencaoProperties(Integer leiturasDias, Integer semComunicacaoMinutos) {

    public RetencaoProperties {
        if (leiturasDias == null || leiturasDias < 1) {
            leiturasDias = 180;
        }
        if (semComunicacaoMinutos == null || semComunicacaoMinutos < 1) {
            semComunicacaoMinutos = 60;
        }
    }
}

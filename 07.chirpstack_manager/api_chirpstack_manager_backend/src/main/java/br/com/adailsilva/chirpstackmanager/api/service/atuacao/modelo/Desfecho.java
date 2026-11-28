package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.ResultadoTentativa;

/**
 * Resultado de uma prova recebida para o comando em andamento.
 *
 * @param tipo      EXECUTADO encerra; REPETIR conta como tentativa falha (vale a política);
 *                  FALHOU encerra sem novas tentativas (ex.: parâmetro recusado pelo firmware)
 * @param resultado como registrar a tentativa
 * @param resposta  texto/JSON guardado no comando
 * @param efeitos   estados e medições que a resposta revela
 */
public record Desfecho(Tipo tipo, ResultadoTentativa resultado, String detalhe, String resposta, Efeitos efeitos) {

    public enum Tipo { EXECUTADO, REPETIR, FALHOU }

    public static Desfecho executado(String detalhe, String resposta, Efeitos efeitos) {
        return new Desfecho(Tipo.EXECUTADO, ResultadoTentativa.EXECUTADO, detalhe, resposta, efeitos);
    }

    public static Desfecho repetir(ResultadoTentativa r, String detalhe, String resposta) {
        return new Desfecho(Tipo.REPETIR, r, detalhe, resposta, null);
    }

    public static Desfecho falhou(String detalhe, String resposta) {
        return new Desfecho(Tipo.FALHOU, ResultadoTentativa.RECUSADO, detalhe, resposta, null);
    }
}

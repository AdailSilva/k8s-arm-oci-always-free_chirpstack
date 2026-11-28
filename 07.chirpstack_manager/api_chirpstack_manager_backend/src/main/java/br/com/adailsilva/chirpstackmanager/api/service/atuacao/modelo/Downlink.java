package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao;

/** Quadro pronto para a fila do ChirpStack e a prova que o encerra. */
public record Downlink(int porta, byte[] dados, boolean confirmado, EstrategiaConfirmacao estrategia, String resumo) {
}

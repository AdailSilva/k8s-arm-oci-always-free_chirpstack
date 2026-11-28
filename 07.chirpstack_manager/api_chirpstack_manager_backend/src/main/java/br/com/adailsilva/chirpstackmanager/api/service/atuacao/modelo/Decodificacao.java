package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import tools.jackson.databind.node.ObjectNode;

/** Uplink decodificado pelo driver: objeto para leituras, séries e alertas, mais os efeitos. */
public record Decodificacao(ObjectNode objeto, Efeitos efeitos) {
}

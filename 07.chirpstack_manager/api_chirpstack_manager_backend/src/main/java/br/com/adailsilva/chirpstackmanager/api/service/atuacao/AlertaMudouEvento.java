package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

/** Um alerta abriu ({@code aberto} = true) ou foi resolvido; publicado dentro da transação. */
public record AlertaMudouEvento(Long regraId, Long dispositivoId, Long alertaId, boolean aberto) {
}

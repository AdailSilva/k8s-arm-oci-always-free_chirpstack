package br.com.adailsilva.chirpstackmanager.api.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Alerta;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.EventoDispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.RegraAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Operador;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Severidade;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoRegra;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dispositivos, leituras, eventos, regras e alertas. */
public final class MonitoramentoDtos {

    private MonitoramentoDtos() {
    }

    /** {@code situacao}: ONLINE, SEM_COMUNICACAO ou NUNCA (ainda sem mensagem). */
    public record DispositivoResponse(Long id, String devEui, String nome, String descricao, String aplicacaoId,
                                      String aplicacaoNome, String perfilNome, String tenantNome, String tags,
                                      boolean ativo, String situacao, Instant ultimoContatoEm, String ultimoEvento,
                                      Integer ultimoRssi, BigDecimal ultimoSnr, Long ultimoFCnt, String ultimoGateway,
                                      Integer ultimaBateria, Boolean alimentacaoExterna, String ultimoObjeto,
                                      long totalUplinks, Instant criadoEm) {

        public static DispositivoResponse of(Dispositivo d, Instant limiteOnline) {
            String situacao = d.getUltimoContatoEm() == null ? "NUNCA"
                    : d.getUltimoContatoEm().isAfter(limiteOnline) ? "ONLINE" : "SEM_COMUNICACAO";
            return new DispositivoResponse(d.getId(), d.getDevEui(), d.getNome(), d.getDescricao(), d.getAplicacaoId(),
                    d.getAplicacaoNome(), d.getPerfilNome(), d.getTenantNome(), d.getTags(), d.isAtivo(), situacao,
                    d.getUltimoContatoEm(), d.getUltimoEvento(), d.getUltimoRssi(), d.getUltimoSnr(), d.getUltimoFCnt(),
                    d.getUltimoGateway(), d.getUltimaBateria(), d.getAlimentacaoExterna(), d.getUltimoObjeto(),
                    d.getTotalUplinks(), d.getCriadoEm());
        }
    }

    public record DispositivoUpdateRequest(@Size(max = 300, message = "Descrição: até 300 caracteres.") String descricao,
                                           Boolean ativo) {
    }

    public record LeituraResponse(Long id, Instant recebidoEm, Long fCnt, Integer fPort, Integer dr,
                                  Integer spreadingFactor, Long frequencia, Integer rssi, BigDecimal snr,
                                  String gatewayId, Integer qtdGateways, Boolean confirmado, String dadosBase64,
                                  String objeto) {

        public static LeituraResponse of(Leitura l) {
            return new LeituraResponse(l.getId(), l.getRecebidoEm(), l.getFCnt(), l.getFPort(), l.getDr(),
                    l.getSpreadingFactor(), l.getFrequencia(), l.getRssi(), l.getSnr(), l.getGatewayId(),
                    l.getQtdGateways(), l.getConfirmado(), l.getDadosBase64(), l.getObjeto());
        }
    }

    public record EventoResponse(Long id, String tipo, Instant recebidoEm, String nivel, String descricao, String payload) {

        public static EventoResponse of(EventoDispositivo e) {
            return new EventoResponse(e.getId(), e.getTipo(), e.getRecebidoEm(), e.getNivel(), e.getDescricao(), e.getPayload());
        }
    }

    /** Pontos de uma série numérica para o gráfico. */
    public record SerieResponse(String campo, List<Ponto> pontos) {
    }

    public record Ponto(Instant t, BigDecimal v) {
    }

    public record RegraAlertaRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome: até 120 caracteres.") String nome,
            @NotNull(message = "Informe o tipo.") TipoRegra tipo,
            Long dispositivoId,
            @Size(max = 120, message = "Campo: até 120 caracteres.") String campo,
            Operador operador,
            @NotNull(message = "Informe o limite.") @DecimalMin(value = "-9999999999", message = "Limite muito baixo.")
            @DecimalMax(value = "9999999999", message = "Limite muito alto.") BigDecimal limite,
            Severidade severidade,
            boolean notificarEmail,
            @Size(max = 500, message = "Destinatários: até 500 caracteres.") String destinatarios,
            Boolean ativo) {
    }

    public record RegraAlertaResponse(Long id, String nome, TipoRegra tipo, Long dispositivoId, String dispositivoNome,
                                      String campo, Operador operador, BigDecimal limite, Severidade severidade,
                                      boolean notificarEmail, String destinatarios, boolean ativo, Instant atualizadoEm,
                                      String atualizadoPor) {

        public static RegraAlertaResponse of(RegraAlerta r, String dispositivoNome) {
            return new RegraAlertaResponse(r.getId(), r.getNome(), r.getTipo(), r.getDispositivoId(), dispositivoNome,
                    r.getCampo(), r.getOperador(), r.getLimite(), r.getSeveridade(), r.isNotificarEmail(),
                    r.getDestinatarios(), r.isAtivo(), r.getAtualizadoEm(), r.getAtualizadoPor());
        }
    }

    public record AlertaResponse(Long id, Long regraId, String regraNome, String severidade, Long dispositivoId,
                                 String dispositivoNome, String devEui, String status, BigDecimal valor, String mensagem,
                                 Instant disparadoEm, Instant reconhecidoEm, String reconhecidoPor, Instant resolvidoEm,
                                 boolean emailEnviado) {

        public static AlertaResponse of(Alerta a, RegraAlerta r, Dispositivo d) {
            return new AlertaResponse(a.getId(), a.getRegraId(), r == null ? null : r.getNome(),
                    r == null ? null : r.getSeveridade().name(), a.getDispositivoId(), d == null ? null : d.getNome(),
                    d == null ? null : d.getDevEui(), a.getStatus().name(), a.getValor(), a.getMensagem(),
                    a.getDisparadoEm(), a.getReconhecidoEm(), a.getReconhecidoPor(), a.getResolvidoEm(), a.isEmailEnviado());
        }
    }

    public record MqttStatusResponse(boolean habilitado, boolean conectado, String url, String topico,
                                     Instant conectadoEm, Instant ultimaMensagemEm, long mensagens, String ultimoErro,
                                     Instant ultimoErroEm) {
    }

    public record PainelResponse(long dispositivos, long online, long semComunicacao, long leiturasUltimas24h,
                                 long alertasAbertos, long alertasReconhecidos, MqttStatusResponse mqtt,
                                 List<UltimaLeitura> ultimasLeituras) {
    }

    public record UltimaLeitura(Long dispositivoId, String dispositivoNome, Instant recebidoEm, Integer rssi,
                                BigDecimal snr, String objeto) {
    }
}

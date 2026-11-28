package br.com.adailsilva.chirpstackmanager.api.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.AcaoRele;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AlvoAcao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.GatilhoAutomacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Recorrencia;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ResultadoTentativa;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Equipamentos, periféricos, fila de comandos, cenas, agendamentos e automações. */
public final class AtuacaoDtos {

    private AtuacaoDtos() {
    }

    // ── parâmetros de um comando ─────────────────────────────────────────────

    /**
     * Parâmetros dos comandos (cada tipo usa só os seus):
     * <ul>
     *   <li>RELE_DEFINIR: canal (nulo = todos), acao;</li>
     *   <li>RELE_PULSO: canal, estado (padrão ligado), duracaoMs;</li>
     *   <li>RELE_MASCARA: mascara e valores (bit n = relé n);</li>
     *   <li>PZEM_*: canal (medidor); PZEM_LIMITE_POTENCIA: watts;</li>
     *   <li>INTERVALO: segundos; CONFIRMADO: confirmado; CLASSE: classe ("A" ou "C"); REINICIAR: atrasoMs;</li>
     *   <li>BRUTO: porta, dadosHex e confirmado (downlink confirmado LoRaWAN).</li>
     * </ul>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParametrosComando(Integer canal, AcaoRele acao, Boolean estado, Long duracaoMs, Integer mascara,
                                    Integer valores, Long segundos, Boolean confirmado, String classe, Integer atrasoMs,
                                    Integer watts, Integer porta, String dadosHex) {

        public static ParametrosComando vazio() {
            return new ParametrosComando(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public static ParametrosComando rele(Integer canal, AcaoRele acao) {
            return new ParametrosComando(canal, acao, null, null, null, null, null, null, null, null, null, null, null);
        }

        public static ParametrosComando mascara(int mascara, int valores) {
            return new ParametrosComando(null, null, null, null, mascara, valores, null, null, null, null, null, null, null);
        }
    }

    /** Política de novas tentativas (campos nulos = padrão do servidor). */
    public record PoliticaRequest(
            @Min(value = 1, message = "Tentativas: de 1 a 50.") @Max(value = 50, message = "Tentativas: de 1 a 50.") Integer maxTentativas,
            @Min(value = 10, message = "Intervalo entre tentativas: de 10 s a 24 h.") @Max(value = 86400, message = "Intervalo entre tentativas: de 10 s a 24 h.") Integer intervaloTentativaS,
            @DecimalMin(value = "1", message = "Recuo: de 1 a 4.") @DecimalMax(value = "4", message = "Recuo: de 1 a 4.") BigDecimal fatorRecuo,
            @Min(value = 10, message = "Espera pela resposta: de 10 s a 24 h.") @Max(value = 86400, message = "Espera pela resposta: de 10 s a 24 h.") Integer aguardarRespostaS,
            @Min(value = 1, message = "Validade: de 1 min a 30 dias.") @Max(value = 43200, message = "Validade: de 1 min a 30 dias.") Integer validadeMin,
            LocalTime janelaInicio, LocalTime janelaFim, List<Integer> diasSemana) {
    }

    // ── modelos e equipamento ────────────────────────────────────────────────

    public record ComandoSuportado(TipoComando tipo, String rotulo, TipoPeriferico periferico) {
    }

    public record PerifericoSuportado(TipoPeriferico tipo, String rotulo, boolean atuador) {
    }

    public record ModeloResponse(ModeloFirmware modelo, String rotulo, String descricao, List<ComandoSuportado> comandos,
                                 List<PerifericoSuportado> perifericos, List<String> observacoes) {
    }

    public record PerifericoRequest(
            @NotNull(message = "Informe o tipo do periférico.") TipoPeriferico tipo,
            @NotNull(message = "Informe o canal.") @Min(value = 0, message = "Canal: de 0 a 15.") @Max(value = 15, message = "Canal: de 0 a 15.") Integer canal,
            @NotBlank(message = "Informe o nome do periférico.") @Size(max = 80, message = "Nome do periférico: até 80 caracteres.") String nome,
            @Min(value = 0, message = "GPIO: de 0 a 39.") @Max(value = 39, message = "GPIO: de 0 a 39.") Integer gpio,
            @Min(value = 1, message = "Endereço Modbus: de 1 a 248.") @Max(value = 248, message = "Endereço Modbus: de 1 a 248.") Integer enderecoModbus,
            boolean critico) {
    }

    public record EquipamentoRequest(
            @NotNull(message = "Informe o modelo do firmware.") ModeloFirmware modelo,
            Boolean reconciliar,
            @DecimalMin(value = "0", message = "Tarifa: não pode ser negativa.") @DecimalMax(value = "999999", message = "Tarifa muito alta.") BigDecimal tarifaKwh,
            @Valid @Size(max = 24, message = "No máximo 24 periféricos.") List<PerifericoRequest> perifericos) {
    }

    public record PerifericoResponse(Long id, TipoPeriferico tipo, String tipoRotulo, int canal, String nome, Integer gpio,
                                     Integer enderecoModbus, boolean critico, Boolean estadoDesejado,
                                     Boolean estadoReportado, Instant reportadoEm, String leitura, Instant leituraEm) {
    }

    public record EquipamentoResponse(Long dispositivoId, boolean configurado, ModeloFirmware modelo, String modeloRotulo,
                                      boolean reconciliar, BigDecimal tarifaKwh, String status, Instant statusEm,
                                      List<PerifericoResponse> perifericos, List<ComandoSuportado> comandos) {
    }

    // ── comandos ─────────────────────────────────────────────────────────────

    public record ComandoRequest(@NotNull(message = "Informe o comando.") TipoComando tipo, ParametrosComando parametros,
                                 @Valid PoliticaRequest politica, boolean confirmarCritico) {
    }

    public record ComandoResponse(Long id, Long dispositivoId, String dispositivoNome, TipoComando tipo, String tipoRotulo,
                                  String resumo, ParametrosComando parametros, int fPort, String dadosHex,
                                  boolean confirmado, Integer seq, EstrategiaConfirmacao estrategia, StatusComando status,
                                  OrigemComando origem, Long origemId, int tentativas, int maxTentativas,
                                  int intervaloTentativaS, BigDecimal fatorRecuo, int aguardarRespostaS,
                                  Instant validadeEm, LocalTime janelaInicio, LocalTime janelaFim,
                                  List<Integer> diasSemana, Instant proximaTentativaEm, Instant enfileiradoEm,
                                  Instant transmitidoEm, Instant concluidoEm, String resposta, String detalhe,
                                  String criadoPor, Instant criadoEm) {
    }

    public record TentativaResponse(int numero, String chirpstackItemId, Instant enfileiradoEm, Instant transmitidoEm,
                                    Instant concluidaEm, ResultadoTentativa resultado, String detalhe) {
    }

    public record ComandoDetalheResponse(ComandoResponse comando, List<TentativaResponse> tentativas) {
    }

    public record ResumoComandosResponse(long pendentes, long emAndamento, long executados24h, long falhas24h) {
    }

    // ── cenas ────────────────────────────────────────────────────────────────

    public record CenaItemRequest(@NotNull(message = "Informe o dispositivo do item.") Long dispositivoId,
                                  @NotNull(message = "Informe o comando do item.") TipoComando tipo,
                                  ParametrosComando parametros) {
    }

    public record CenaRequest(
            @NotBlank(message = "Informe o nome da cena.") @Size(max = 120, message = "Nome: até 120 caracteres.") String nome,
            @Size(max = 300, message = "Descrição: até 300 caracteres.") String descricao,
            @NotNull(message = "Inclua ao menos um comando.") @Size(min = 1, max = 50, message = "A cena tem de 1 a 50 comandos.") @Valid List<CenaItemRequest> itens) {
    }

    public record CenaItemResponse(Long id, int ordem, Long dispositivoId, String dispositivoNome, TipoComando tipo,
                                   String tipoRotulo, ParametrosComando parametros, String resumo) {
    }

    public record CenaResponse(Long id, String nome, String descricao, List<CenaItemResponse> itens, boolean temCritico,
                               Instant atualizadoEm, String atualizadoPor) {
    }

    public record ExecucaoRequest(boolean confirmarCritico) {
    }

    public record ExecucaoResponse(List<Long> comandos) {
    }

    // ── agendamentos ─────────────────────────────────────────────────────────

    public record AgendamentoRequest(
            @NotBlank(message = "Informe o nome do agendamento.") @Size(max = 120, message = "Nome: até 120 caracteres.") String nome,
            @NotNull(message = "Informe o que executar.") AlvoAcao alvo,
            Long dispositivoId, TipoComando tipo, ParametrosComando parametros, Long cenaId,
            @NotNull(message = "Informe a recorrência.") Recorrencia recorrencia,
            Instant executarEm, LocalTime horario, List<Integer> diasSemana,
            @Min(value = 1, message = "Validade: de 1 min a 30 dias.") @Max(value = 43200, message = "Validade: de 1 min a 30 dias.") Integer validadeMin,
            @Min(value = 1, message = "Tentativas: de 1 a 50.") @Max(value = 50, message = "Tentativas: de 1 a 50.") Integer maxTentativas,
            Boolean ativo, boolean confirmarCritico) {
    }

    public record AgendamentoResponse(Long id, String nome, AlvoAcao alvo, Long dispositivoId, String dispositivoNome,
                                      TipoComando tipo, String tipoRotulo, ParametrosComando parametros, Long cenaId,
                                      String cenaNome, String resumo, Recorrencia recorrencia, Instant executarEm,
                                      LocalTime horario, List<Integer> diasSemana, int validadeMin, int maxTentativas,
                                      boolean ativo, Instant proximaExecucaoEm, Instant ultimaExecucaoEm,
                                      Instant atualizadoEm, String atualizadoPor) {
    }

    // ── automações ───────────────────────────────────────────────────────────

    public record AutomacaoRequest(
            @NotBlank(message = "Informe o nome da automação.") @Size(max = 120, message = "Nome: até 120 caracteres.") String nome,
            @NotNull(message = "Informe a regra de alerta.") Long regraId,
            @NotNull(message = "Informe quando disparar.") GatilhoAutomacao gatilho,
            @NotNull(message = "Informe o que executar.") AlvoAcao alvo,
            boolean mesmoDispositivo, Long dispositivoId, TipoComando tipo, ParametrosComando parametros, Long cenaId,
            @Min(value = 0, message = "Intervalo mínimo: de 0 a 86400 s.") @Max(value = 86400, message = "Intervalo mínimo: de 0 a 86400 s.") Integer intervaloMinimoS,
            Boolean ativo, boolean confirmarCritico) {
    }

    public record AutomacaoResponse(Long id, String nome, Long regraId, String regraNome, GatilhoAutomacao gatilho,
                                    AlvoAcao alvo, boolean mesmoDispositivo, Long dispositivoId, String dispositivoNome,
                                    TipoComando tipo, String tipoRotulo, ParametrosComando parametros, Long cenaId,
                                    String cenaNome, String resumo, int intervaloMinimoS, boolean ativo,
                                    Instant ultimaExecucaoEm, Instant atualizadoEm, String atualizadoPor) {
    }

    // ── energia ──────────────────────────────────────────────────────────────

    public record EnergiaResponse(int canal, String nome, Instant de, Instant ate, Long energiaInicialWh,
                                  Long energiaFinalWh, BigDecimal consumoKwh, BigDecimal tarifaKwh, BigDecimal custo,
                                  BigDecimal potenciaMedia, BigDecimal potenciaMaxima, int leituras) {
    }
}

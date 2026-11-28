package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;

/** Comando (downlink) na fila, com a política de novas tentativas. */
@Entity
@Table(name = "tb_comando")
public class Comando {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoComando tipo;

    @Column(name = "parametros", columnDefinition = "TEXT")
    private String parametros;

    @Column(name = "resumo", nullable = false, length = 200)
    private String resumo;

    @Column(name = "f_port", nullable = false)
    private int fPort;

    @Column(name = "dados_base64", nullable = false, length = 512)
    private String dadosBase64;

    @Column(name = "confirmado", nullable = false)
    private boolean confirmado;

    @Column(name = "seq")
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "estrategia", nullable = false, length = 25)
    private EstrategiaConfirmacao estrategia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusComando status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 15)
    private OrigemComando origem;

    @Column(name = "origem_id")
    private Long origemId;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "max_tentativas", nullable = false)
    private int maxTentativas;

    @Column(name = "intervalo_tentativa_s", nullable = false)
    private int intervaloTentativaS;

    @Column(name = "fator_recuo", nullable = false, precision = 4, scale = 2)
    private BigDecimal fatorRecuo;

    @Column(name = "aguardar_resposta_s", nullable = false)
    private int aguardarRespostaS;

    @Column(name = "validade_em", nullable = false)
    private Instant validadeEm;

    @Column(name = "janela_inicio")
    private LocalTime janelaInicio;

    @Column(name = "janela_fim")
    private LocalTime janelaFim;

    @Column(name = "dias_semana", nullable = false)
    private int diasSemana;

    @Column(name = "proxima_tentativa_em", nullable = false)
    private Instant proximaTentativaEm;

    @Column(name = "chirpstack_item_id", length = 36)
    private String chirpstackItemId;

    @Column(name = "enfileirado_em")
    private Instant enfileiradoEm;

    @Column(name = "transmitido_em")
    private Instant transmitidoEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "resposta", columnDefinition = "TEXT")
    private String resposta;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    @Column(name = "criado_por", length = 160)
    private String criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private long versao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public TipoComando getTipo() { return tipo; }
    public void setTipo(TipoComando tipo) { this.tipo = tipo; }
    public String getParametros() { return parametros; }
    public void setParametros(String parametros) { this.parametros = parametros; }
    public String getResumo() { return resumo; }
    public void setResumo(String resumo) { this.resumo = resumo; }
    public int getFPort() { return fPort; }
    public void setFPort(int fPort) { this.fPort = fPort; }
    public String getDadosBase64() { return dadosBase64; }
    public void setDadosBase64(String dadosBase64) { this.dadosBase64 = dadosBase64; }
    public boolean isConfirmado() { return confirmado; }
    public void setConfirmado(boolean confirmado) { this.confirmado = confirmado; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public EstrategiaConfirmacao getEstrategia() { return estrategia; }
    public void setEstrategia(EstrategiaConfirmacao estrategia) { this.estrategia = estrategia; }
    public StatusComando getStatus() { return status; }
    public void setStatus(StatusComando status) { this.status = status; }
    public OrigemComando getOrigem() { return origem; }
    public void setOrigem(OrigemComando origem) { this.origem = origem; }
    public Long getOrigemId() { return origemId; }
    public void setOrigemId(Long origemId) { this.origemId = origemId; }
    public int getTentativas() { return tentativas; }
    public void setTentativas(int tentativas) { this.tentativas = tentativas; }
    public int getMaxTentativas() { return maxTentativas; }
    public void setMaxTentativas(int maxTentativas) { this.maxTentativas = maxTentativas; }
    public int getIntervaloTentativaS() { return intervaloTentativaS; }
    public void setIntervaloTentativaS(int intervaloTentativaS) { this.intervaloTentativaS = intervaloTentativaS; }
    public BigDecimal getFatorRecuo() { return fatorRecuo; }
    public void setFatorRecuo(BigDecimal fatorRecuo) { this.fatorRecuo = fatorRecuo; }
    public int getAguardarRespostaS() { return aguardarRespostaS; }
    public void setAguardarRespostaS(int aguardarRespostaS) { this.aguardarRespostaS = aguardarRespostaS; }
    public Instant getValidadeEm() { return validadeEm; }
    public void setValidadeEm(Instant validadeEm) { this.validadeEm = validadeEm; }
    public LocalTime getJanelaInicio() { return janelaInicio; }
    public void setJanelaInicio(LocalTime janelaInicio) { this.janelaInicio = janelaInicio; }
    public LocalTime getJanelaFim() { return janelaFim; }
    public void setJanelaFim(LocalTime janelaFim) { this.janelaFim = janelaFim; }
    public int getDiasSemana() { return diasSemana; }
    public void setDiasSemana(int diasSemana) { this.diasSemana = diasSemana; }
    public Instant getProximaTentativaEm() { return proximaTentativaEm; }
    public void setProximaTentativaEm(Instant proximaTentativaEm) { this.proximaTentativaEm = proximaTentativaEm; }
    public String getChirpstackItemId() { return chirpstackItemId; }
    public void setChirpstackItemId(String chirpstackItemId) { this.chirpstackItemId = chirpstackItemId; }
    public Instant getEnfileiradoEm() { return enfileiradoEm; }
    public void setEnfileiradoEm(Instant enfileiradoEm) { this.enfileiradoEm = enfileiradoEm; }
    public Instant getTransmitidoEm() { return transmitidoEm; }
    public void setTransmitidoEm(Instant transmitidoEm) { this.transmitidoEm = transmitidoEm; }
    public Instant getConcluidoEm() { return concluidoEm; }
    public void setConcluidoEm(Instant concluidoEm) { this.concluidoEm = concluidoEm; }
    public String getResposta() { return resposta; }
    public void setResposta(String resposta) { this.resposta = resposta; }
    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }
    public String getCriadoPor() { return criadoPor; }
    public void setCriadoPor(String criadoPor) { this.criadoPor = criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public long getVersao() { return versao; }
    public void setVersao(long versao) { this.versao = versao; }
}

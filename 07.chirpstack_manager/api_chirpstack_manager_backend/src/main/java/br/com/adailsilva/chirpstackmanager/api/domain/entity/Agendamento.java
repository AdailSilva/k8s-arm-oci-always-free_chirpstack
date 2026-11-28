package br.com.adailsilva.chirpstackmanager.api.domain.entity;

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
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AlvoAcao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Recorrencia;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;

/** Execução programada de um comando ou de uma cena. */
@Entity
@Table(name = "tb_agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "alvo", nullable = false, length = 10)
    private AlvoAcao alvo;

    @Column(name = "dispositivo_id")
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 30)
    private TipoComando tipo;

    @Column(name = "parametros", columnDefinition = "TEXT")
    private String parametros;

    @Column(name = "cena_id")
    private Long cenaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorrencia", nullable = false, length = 10)
    private Recorrencia recorrencia;

    @Column(name = "executar_em")
    private Instant executarEm;

    @Column(name = "horario")
    private LocalTime horario;

    @Column(name = "dias_semana", nullable = false)
    private int diasSemana;

    @Column(name = "validade_min", nullable = false)
    private int validadeMin;

    @Column(name = "max_tentativas", nullable = false)
    private int maxTentativas;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "proxima_execucao_em")
    private Instant proximaExecucaoEm;

    @Column(name = "ultima_execucao_em")
    private Instant ultimaExecucaoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", length = 160)
    private String atualizadoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public AlvoAcao getAlvo() { return alvo; }
    public void setAlvo(AlvoAcao alvo) { this.alvo = alvo; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public TipoComando getTipo() { return tipo; }
    public void setTipo(TipoComando tipo) { this.tipo = tipo; }
    public String getParametros() { return parametros; }
    public void setParametros(String parametros) { this.parametros = parametros; }
    public Long getCenaId() { return cenaId; }
    public void setCenaId(Long cenaId) { this.cenaId = cenaId; }
    public Recorrencia getRecorrencia() { return recorrencia; }
    public void setRecorrencia(Recorrencia recorrencia) { this.recorrencia = recorrencia; }
    public Instant getExecutarEm() { return executarEm; }
    public void setExecutarEm(Instant executarEm) { this.executarEm = executarEm; }
    public LocalTime getHorario() { return horario; }
    public void setHorario(LocalTime horario) { this.horario = horario; }
    public int getDiasSemana() { return diasSemana; }
    public void setDiasSemana(int diasSemana) { this.diasSemana = diasSemana; }
    public int getValidadeMin() { return validadeMin; }
    public void setValidadeMin(int validadeMin) { this.validadeMin = validadeMin; }
    public int getMaxTentativas() { return maxTentativas; }
    public void setMaxTentativas(int maxTentativas) { this.maxTentativas = maxTentativas; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getProximaExecucaoEm() { return proximaExecucaoEm; }
    public void setProximaExecucaoEm(Instant proximaExecucaoEm) { this.proximaExecucaoEm = proximaExecucaoEm; }
    public Instant getUltimaExecucaoEm() { return ultimaExecucaoEm; }
    public void setUltimaExecucaoEm(Instant ultimaExecucaoEm) { this.ultimaExecucaoEm = ultimaExecucaoEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String atualizadoPor) { this.atualizadoPor = atualizadoPor; }
}

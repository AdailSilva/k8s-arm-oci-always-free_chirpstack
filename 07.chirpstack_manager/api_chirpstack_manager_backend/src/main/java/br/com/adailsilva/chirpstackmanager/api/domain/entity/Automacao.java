package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AlvoAcao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.GatilhoAutomacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;

/** Ação (comando ou cena) disparada quando um alerta abre ou é resolvido. */
@Entity
@Table(name = "tb_automacao")
public class Automacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "regra_id", nullable = false)
    private Long regraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gatilho", nullable = false, length = 20)
    private GatilhoAutomacao gatilho;

    @Enumerated(EnumType.STRING)
    @Column(name = "alvo", nullable = false, length = 10)
    private AlvoAcao alvo;

    @Column(name = "mesmo_dispositivo", nullable = false)
    private boolean mesmoDispositivo;

    @Column(name = "dispositivo_id")
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 30)
    private TipoComando tipo;

    @Column(name = "parametros", columnDefinition = "TEXT")
    private String parametros;

    @Column(name = "cena_id")
    private Long cenaId;

    @Column(name = "intervalo_minimo_s", nullable = false)
    private int intervaloMinimoS;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

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
    public Long getRegraId() { return regraId; }
    public void setRegraId(Long regraId) { this.regraId = regraId; }
    public GatilhoAutomacao getGatilho() { return gatilho; }
    public void setGatilho(GatilhoAutomacao gatilho) { this.gatilho = gatilho; }
    public AlvoAcao getAlvo() { return alvo; }
    public void setAlvo(AlvoAcao alvo) { this.alvo = alvo; }
    public boolean isMesmoDispositivo() { return mesmoDispositivo; }
    public void setMesmoDispositivo(boolean mesmoDispositivo) { this.mesmoDispositivo = mesmoDispositivo; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public TipoComando getTipo() { return tipo; }
    public void setTipo(TipoComando tipo) { this.tipo = tipo; }
    public String getParametros() { return parametros; }
    public void setParametros(String parametros) { this.parametros = parametros; }
    public Long getCenaId() { return cenaId; }
    public void setCenaId(Long cenaId) { this.cenaId = cenaId; }
    public int getIntervaloMinimoS() { return intervaloMinimoS; }
    public void setIntervaloMinimoS(int intervaloMinimoS) { this.intervaloMinimoS = intervaloMinimoS; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getUltimaExecucaoEm() { return ultimaExecucaoEm; }
    public void setUltimaExecucaoEm(Instant ultimaExecucaoEm) { this.ultimaExecucaoEm = ultimaExecucaoEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String atualizadoPor) { this.atualizadoPor = atualizadoPor; }
}

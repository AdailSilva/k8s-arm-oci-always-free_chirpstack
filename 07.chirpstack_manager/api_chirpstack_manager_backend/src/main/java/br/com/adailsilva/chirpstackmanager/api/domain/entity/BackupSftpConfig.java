package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Destino SFTP dos backups (linha única, {@code tb_backup_sftp}): um computador, NAS ou servidor da
 * rede do cliente. Senha, chave privada e frase-senha ficam cifradas (AES-GCM, {@code CifradorSegredos}).
 * A impressão digital da chave do servidor é gravada só depois que o ADMIN a confere no teste.
 */
@Entity
@Table(name = "tb_backup_sftp")
public class BackupSftpConfig {

    public static final short ID = 1;

    @Id
    private Short id = ID;

    @Column(length = 255)
    private String host;

    @Column(nullable = false)
    private int porta = 22;

    @Column(length = 120)
    private String usuario;

    /** SENHA ou CHAVE. */
    @Column(nullable = false, length = 10)
    private String autenticacao = "SENHA";

    @Column(name = "senha_cifrada", length = 1024)
    private String senhaCifrada;

    @Column(name = "chave_privada_cifrada", columnDefinition = "TEXT")
    private String chavePrivadaCifrada;

    @Column(name = "frase_chave_cifrada", length = 1024)
    private String fraseChaveCifrada;

    @Column(name = "pasta_remota", length = 255)
    private String pastaRemota;

    /** Impressão digital confirmada da chave do servidor (SHA256:...). */
    @Column(length = 100)
    private String fingerprint;

    @Column(name = "tipo_chave", length = 40)
    private String tipoChave;

    @Column(name = "enviar_manuais", nullable = false)
    private boolean enviarManuais;

    @Column(name = "retencao_dias", nullable = false)
    private int retencaoDias = 30;

    @Column(name = "manter_minimo", nullable = false)
    private int manterMinimo = 3;

    @Column(name = "ultimo_envio_em")
    private Instant ultimoEnvioEm;

    @Column(name = "ultimo_envio_status", length = 20)
    private String ultimoEnvioStatus;

    @Column(name = "ultimo_envio_mensagem", length = 1000)
    private String ultimoEnvioMensagem;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", length = 160)
    private String atualizadoPor;

    @PrePersist
    @PreUpdate
    void carimbar() {
        atualizadoEm = Instant.now();
    }

    /** Cópia desligada do contexto JPA (para a configuração sobreviver a uma restauração do banco). */
    public BackupSftpConfig copia() {
        BackupSftpConfig c = new BackupSftpConfig();
        c.host = host; c.porta = porta; c.usuario = usuario; c.autenticacao = autenticacao;
        c.senhaCifrada = senhaCifrada; c.chavePrivadaCifrada = chavePrivadaCifrada;
        c.fraseChaveCifrada = fraseChaveCifrada; c.pastaRemota = pastaRemota; c.fingerprint = fingerprint;
        c.tipoChave = tipoChave; c.enviarManuais = enviarManuais; c.retencaoDias = retencaoDias;
        c.manterMinimo = manterMinimo; c.ultimoEnvioEm = ultimoEnvioEm; c.ultimoEnvioStatus = ultimoEnvioStatus;
        c.ultimoEnvioMensagem = ultimoEnvioMensagem; c.atualizadoPor = atualizadoPor;
        return c;
    }

    public boolean isPorChave() {
        return "CHAVE".equals(autenticacao);
    }

    /** Servidor, usuário e credencial informados (a chave do servidor pode ainda não estar confirmada). */
    public boolean isPreenchido() {
        return host != null && !host.isBlank() && usuario != null && !usuario.isBlank()
                && (isPorChave() ? chavePrivadaCifrada != null : senhaCifrada != null);
    }

    /** Pronto para receber backups: preenchido e com a chave do servidor confirmada. */
    public boolean isPronto() {
        return isPreenchido() && fingerprint != null && !fingerprint.isBlank();
    }

    public Short getId() { return id; }
    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }
    public int getPorta() { return porta; }
    public void setPorta(int v) { this.porta = v; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String v) { this.usuario = v; }
    public String getAutenticacao() { return autenticacao; }
    public void setAutenticacao(String v) { this.autenticacao = v; }
    public String getSenhaCifrada() { return senhaCifrada; }
    public void setSenhaCifrada(String v) { this.senhaCifrada = v; }
    public String getChavePrivadaCifrada() { return chavePrivadaCifrada; }
    public void setChavePrivadaCifrada(String v) { this.chavePrivadaCifrada = v; }
    public String getFraseChaveCifrada() { return fraseChaveCifrada; }
    public void setFraseChaveCifrada(String v) { this.fraseChaveCifrada = v; }
    public String getPastaRemota() { return pastaRemota; }
    public void setPastaRemota(String v) { this.pastaRemota = v; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String v) { this.fingerprint = v; }
    public String getTipoChave() { return tipoChave; }
    public void setTipoChave(String v) { this.tipoChave = v; }
    public boolean isEnviarManuais() { return enviarManuais; }
    public void setEnviarManuais(boolean v) { this.enviarManuais = v; }
    public int getRetencaoDias() { return retencaoDias; }
    public void setRetencaoDias(int v) { this.retencaoDias = v; }
    public int getManterMinimo() { return manterMinimo; }
    public void setManterMinimo(int v) { this.manterMinimo = v; }
    public Instant getUltimoEnvioEm() { return ultimoEnvioEm; }
    public void setUltimoEnvioEm(Instant v) { this.ultimoEnvioEm = v; }
    public String getUltimoEnvioStatus() { return ultimoEnvioStatus; }
    public void setUltimoEnvioStatus(String v) { this.ultimoEnvioStatus = v; }
    public String getUltimoEnvioMensagem() { return ultimoEnvioMensagem; }
    public void setUltimoEnvioMensagem(String v) {
        this.ultimoEnvioMensagem = v != null && v.length() > 1000 ? v.substring(0, 1000) : v;
    }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String v) { this.atualizadoPor = v; }
}

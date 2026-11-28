package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Perfil;

/**
 * Usuário do painel (tabela {@code tb_usuario}).
 */
@Entity
@Table(name = "tb_usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    /** VARCHAR explícito: o Hibernate 7 usaria o tipo ENUM nativo do H2 e a validação do schema falharia. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    /** Preenchido quando o bloqueio foi feito por um ADMIN (tela de Visitantes); nulo no bloqueio automático. */
    @Column(name = "motivo_bloqueio", length = 255)
    private String motivoBloqueio;

    @Column(name = "bloqueado_por", length = 160)
    private String bloqueadoPor;

    @Column(name = "ultimo_login")
    private Instant ultimoLogin;

    // ── cadastro completo (V18) ──────────────────────────────────────────────

    @Column(length = 20)
    private String telefone;

    @Column(length = 80)
    private String cargo;

    /** Miniatura JPEG em data URI (reduzida no navegador a 512 px). */
    @Column(name = "foto_perfil", columnDefinition = "TEXT")
    private String fotoPerfil;

    @Column(length = 9)
    private String cep;

    @Column(name = "endereco_tipo", length = 20)
    private String enderecoTipo;

    @Column(length = 255)
    private String endereco;

    @Column(length = 20)
    private String numero;

    @Column(length = 255)
    private String complemento;

    @Column(length = 150)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /** FALSE enquanto o e-mail não for confirmado pelo link: o login é recusado. */
    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = true;

    @Column(name = "email_verificado_em")
    private Instant emailVerificadoEm;

    /** Criado por convite e ainda sem senha definida pelo link. */
    @Column(name = "convite_pendente", nullable = false)
    private boolean convitePendente;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = Instant.now();
    }

    public boolean isBloqueado() {
        return bloqueadoAte != null && bloqueadoAte.isAfter(Instant.now());
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public int getTentativasFalhas() { return tentativasFalhas; }
    public void setTentativasFalhas(int tentativasFalhas) { this.tentativasFalhas = tentativasFalhas; }
    public Instant getBloqueadoAte() { return bloqueadoAte; }
    public void setBloqueadoAte(Instant bloqueadoAte) { this.bloqueadoAte = bloqueadoAte; }
    public String getMotivoBloqueio() { return motivoBloqueio; }
    public void setMotivoBloqueio(String motivoBloqueio) { this.motivoBloqueio = motivoBloqueio; }
    public String getBloqueadoPor() { return bloqueadoPor; }
    public void setBloqueadoPor(String bloqueadoPor) { this.bloqueadoPor = bloqueadoPor; }
    public Instant getUltimoLogin() { return ultimoLogin; }
    public void setUltimoLogin(Instant ultimoLogin) { this.ultimoLogin = ultimoLogin; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getFotoPerfil() { return fotoPerfil; }
    public void setFotoPerfil(String fotoPerfil) { this.fotoPerfil = fotoPerfil; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getEnderecoTipo() { return enderecoTipo; }
    public void setEnderecoTipo(String enderecoTipo) { this.enderecoTipo = enderecoTipo; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public boolean isEmailVerificado() { return emailVerificado; }
    public void setEmailVerificado(boolean emailVerificado) { this.emailVerificado = emailVerificado; }
    public Instant getEmailVerificadoEm() { return emailVerificadoEm; }
    public void setEmailVerificadoEm(Instant emailVerificadoEm) { this.emailVerificadoEm = emailVerificadoEm; }
    public boolean isConvitePendente() { return convitePendente; }
    public void setConvitePendente(boolean convitePendente) { this.convitePendente = convitePendente; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}

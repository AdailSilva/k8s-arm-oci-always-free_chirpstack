package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoToken;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Link de uso único enviado por e-mail: redefinição de senha ("Esqueci a senha"), convite ou
 * confirmação de e-mail ({@link TipoToken}). Só o SHA-256 do token fica no banco; vale uma vez e
 * até {@code expiraEm}.
 */
@Entity
@Table(name = "tb_token_redefinicao")
public class TokenRedefinicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** VARCHAR explícito: o Hibernate 7 usaria o tipo ENUM nativo do H2 e a validação do schema falharia. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TipoToken tipo = TipoToken.RECUPERACAO_SENHA;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "ip", length = 64)
    private String ip;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public TipoToken getTipo() { return tipo; }
    public void setTipo(TipoToken tipo) { this.tipo = tipo; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getExpiraEm() { return expiraEm; }
    public void setExpiraEm(Instant expiraEm) { this.expiraEm = expiraEm; }
    public Instant getUsadoEm() { return usadoEm; }
    public void setUsadoEm(Instant usadoEm) { this.usadoEm = usadoEm; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
}

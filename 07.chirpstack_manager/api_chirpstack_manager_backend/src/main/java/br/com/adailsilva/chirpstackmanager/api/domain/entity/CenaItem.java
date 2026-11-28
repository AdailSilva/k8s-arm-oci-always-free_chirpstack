package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;

/** Um comando de uma cena. */
@Entity
@Table(name = "tb_cena_item")
public class CenaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cena_id", nullable = false)
    private Long cenaId;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoComando tipo;

    @Column(name = "parametros", columnDefinition = "TEXT")
    private String parametros;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCenaId() { return cenaId; }
    public void setCenaId(Long cenaId) { this.cenaId = cenaId; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public TipoComando getTipo() { return tipo; }
    public void setTipo(TipoComando tipo) { this.tipo = tipo; }
    public String getParametros() { return parametros; }
    public void setParametros(String parametros) { this.parametros = parametros; }
}

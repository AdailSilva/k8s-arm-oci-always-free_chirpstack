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
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ResultadoTentativa;

/** Um envio de um comando ao ChirpStack e como terminou. */
@Entity
@Table(name = "tb_comando_tentativa")
public class ComandoTentativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comando_id", nullable = false)
    private Long comandoId;

    @Column(name = "numero", nullable = false)
    private int numero;

    @Column(name = "chirpstack_item_id", length = 36)
    private String chirpstackItemId;

    @Column(name = "enfileirado_em", nullable = false)
    private Instant enfileiradoEm;

    @Column(name = "transmitido_em")
    private Instant transmitidoEm;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", length = 20)
    private ResultadoTentativa resultado;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getComandoId() { return comandoId; }
    public void setComandoId(Long comandoId) { this.comandoId = comandoId; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getChirpstackItemId() { return chirpstackItemId; }
    public void setChirpstackItemId(String chirpstackItemId) { this.chirpstackItemId = chirpstackItemId; }
    public Instant getEnfileiradoEm() { return enfileiradoEm; }
    public void setEnfileiradoEm(Instant enfileiradoEm) { this.enfileiradoEm = enfileiradoEm; }
    public Instant getTransmitidoEm() { return transmitidoEm; }
    public void setTransmitidoEm(Instant transmitidoEm) { this.transmitidoEm = transmitidoEm; }
    public Instant getConcluidaEm() { return concluidaEm; }
    public void setConcluidaEm(Instant concluidaEm) { this.concluidaEm = concluidaEm; }
    public ResultadoTentativa getResultado() { return resultado; }
    public void setResultado(ResultadoTentativa resultado) { this.resultado = resultado; }
    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }
}

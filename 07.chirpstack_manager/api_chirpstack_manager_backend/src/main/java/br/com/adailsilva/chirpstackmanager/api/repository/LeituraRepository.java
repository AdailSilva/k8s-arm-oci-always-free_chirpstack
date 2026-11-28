package br.com.adailsilva.chirpstackmanager.api.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;

public interface LeituraRepository extends JpaRepository<Leitura, Long> {

    boolean existsByDeduplicationId(String deduplicationId);

    Page<Leitura> findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmDesc(Long dispositivoId, Instant de,
                                                                               Instant ate, Pageable pageable);

    /** Série para o gráfico: em ordem cronológica, limitada pelo Pageable. */
    List<Leitura> findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmAsc(Long dispositivoId, Instant de,
                                                                              Instant ate, Pageable pageable);

    List<Leitura> findTop20ByOrderByRecebidoEmDesc();

    long countByRecebidoEmAfter(Instant desde);

    @Modifying
    @Query("DELETE FROM Leitura l WHERE l.recebidoEm < :limite")
    int apagarAntesDe(@Param("limite") Instant limite);
}

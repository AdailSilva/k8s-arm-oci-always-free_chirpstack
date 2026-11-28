package br.com.adailsilva.chirpstackmanager.api.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;

public interface ComandoRepository extends JpaRepository<Comando, Long> {

    List<Comando> findByStatusInOrderByCriadoEmAscIdAsc(Collection<StatusComando> status);

    List<Comando> findByDispositivoIdAndStatusInOrderByCriadoEmAscIdAsc(Long dispositivoId, Collection<StatusComando> status);

    Optional<Comando> findFirstByChirpstackItemIdAndStatusIn(String itemId, Collection<StatusComando> status);

    boolean existsByDispositivoIdAndStatusIn(Long dispositivoId, Collection<StatusComando> status);

    @Query("""
            SELECT c FROM Comando c
             WHERE (:dispositivoId IS NULL OR c.dispositivoId = :dispositivoId)
               AND (:filtrarStatus = FALSE OR c.status IN :status)
             ORDER BY c.criadoEm DESC, c.id DESC""")
    Page<Comando> buscar(@Param("dispositivoId") Long dispositivoId, @Param("filtrarStatus") boolean filtrarStatus,
                         @Param("status") Collection<StatusComando> status, Pageable pageable);

    Optional<Comando> findFirstByDispositivoIdAndOrigemOrderByCriadoEmDesc(Long dispositivoId,
            br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando origem);

    long countByStatus(StatusComando status);

    long countByStatusAndConcluidoEmAfter(StatusComando status, Instant desde);

    @Modifying
    @Query("DELETE FROM Comando c WHERE c.criadoEm < :limite AND c.status IN :finais")
    int apagarAntesDe(@Param("limite") Instant limite, @Param("finais") Collection<StatusComando> finais);
}

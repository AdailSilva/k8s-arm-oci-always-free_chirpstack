package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Alerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Optional<Alerta> findFirstByRegraIdAndDispositivoIdAndStatusIn(Long regraId, Long dispositivoId,
                                                                   Collection<StatusAlerta> status);

    long countByStatus(StatusAlerta status);

    @Query("""
            SELECT a FROM Alerta a
             WHERE (:status IS NULL OR a.status = :status)
               AND (:dispositivoId IS NULL OR a.dispositivoId = :dispositivoId)
             ORDER BY a.disparadoEm DESC
            """)
    Page<Alerta> buscar(@Param("status") StatusAlerta status, @Param("dispositivoId") Long dispositivoId,
                        Pageable pageable);
}

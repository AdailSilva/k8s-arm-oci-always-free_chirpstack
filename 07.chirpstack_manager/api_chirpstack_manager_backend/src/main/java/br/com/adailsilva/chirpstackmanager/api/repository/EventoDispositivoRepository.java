package br.com.adailsilva.chirpstackmanager.api.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.EventoDispositivo;

public interface EventoDispositivoRepository extends JpaRepository<EventoDispositivo, Long> {

    Page<EventoDispositivo> findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmDesc(Long dispositivoId,
                                                                                         Instant de, Instant ate,
                                                                                         Pageable pageable);

    @Modifying
    @Query("DELETE FROM EventoDispositivo e WHERE e.recebidoEm < :limite")
    int apagarAntesDe(@Param("limite") Instant limite);
}

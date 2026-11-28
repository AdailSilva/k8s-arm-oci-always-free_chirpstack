package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.RegraAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoRegra;

public interface RegraAlertaRepository extends JpaRepository<RegraAlerta, Long> {

    List<RegraAlerta> findAllByOrderByNomeAsc();

    /** Regras ativas de um tipo que valem para o dispositivo (as dele e as de todos). */
    @Query("""
            SELECT r FROM RegraAlerta r
             WHERE r.ativo = TRUE AND r.tipo = :tipo
               AND (r.dispositivoId IS NULL OR r.dispositivoId = :dispositivoId)
            """)
    List<RegraAlerta> ativasPara(@Param("tipo") TipoRegra tipo, @Param("dispositivoId") Long dispositivoId);

    List<RegraAlerta> findByAtivoTrueAndTipo(TipoRegra tipo);

    java.util.Optional<RegraAlerta> findFirstByCampoOrderByIdAsc(String campo);
}

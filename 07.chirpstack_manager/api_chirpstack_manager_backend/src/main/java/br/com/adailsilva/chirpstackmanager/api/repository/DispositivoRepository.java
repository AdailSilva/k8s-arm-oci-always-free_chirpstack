package br.com.adailsilva.chirpstackmanager.api.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByDevEui(String devEui);

    @Query("""
            SELECT d FROM Dispositivo d
             WHERE (:inativos = TRUE OR d.ativo = TRUE)
               AND (:busca IS NULL
                    OR LOWER(d.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
                    OR LOWER(d.devEui) LIKE LOWER(CONCAT('%', :busca, '%'))
                    OR LOWER(COALESCE(d.aplicacaoNome, '')) LIKE LOWER(CONCAT('%', :busca, '%'))
                    OR LOWER(COALESCE(d.descricao, '')) LIKE LOWER(CONCAT('%', :busca, '%')))
            """)
    Page<Dispositivo> buscar(@Param("busca") String busca, @Param("inativos") boolean inativos, Pageable pageable);

    List<Dispositivo> findByAtivoTrue();

    long countByAtivoTrue();

    long countByAtivoTrueAndUltimoContatoEmAfter(Instant desde);
}

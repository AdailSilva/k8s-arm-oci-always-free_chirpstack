package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;

public interface PerifericoRepository extends JpaRepository<Periferico, Long> {

    List<Periferico> findByEquipamentoIdOrderByTipoAscCanalAsc(Long equipamentoId);

    Optional<Periferico> findByEquipamentoIdAndTipoAndCanal(Long equipamentoId, TipoPeriferico tipo, int canal);
}

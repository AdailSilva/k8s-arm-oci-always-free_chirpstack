package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Equipamento;

public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {

    Optional<Equipamento> findByDispositivoId(Long dispositivoId);

    List<Equipamento> findByDispositivoIdIn(Collection<Long> dispositivoIds);
}

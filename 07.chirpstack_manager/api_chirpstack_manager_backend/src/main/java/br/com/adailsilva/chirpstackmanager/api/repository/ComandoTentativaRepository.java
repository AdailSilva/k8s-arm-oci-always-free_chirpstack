package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.ComandoTentativa;

public interface ComandoTentativaRepository extends JpaRepository<ComandoTentativa, Long> {

    List<ComandoTentativa> findByComandoIdOrderByNumeroAsc(Long comandoId);

    Optional<ComandoTentativa> findFirstByComandoIdOrderByNumeroDesc(Long comandoId);
}

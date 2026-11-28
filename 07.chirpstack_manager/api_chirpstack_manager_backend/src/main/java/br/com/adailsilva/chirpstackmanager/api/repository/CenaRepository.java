package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Cena;

public interface CenaRepository extends JpaRepository<Cena, Long> {

    List<Cena> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

    boolean existsByNomeIgnoreCase(String nome);
}

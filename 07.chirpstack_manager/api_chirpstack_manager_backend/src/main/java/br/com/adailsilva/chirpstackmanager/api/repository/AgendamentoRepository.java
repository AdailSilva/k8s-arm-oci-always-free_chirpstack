package br.com.adailsilva.chirpstackmanager.api.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Agendamento;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    List<Agendamento> findAllByOrderByNomeAsc();

    List<Agendamento> findByDispositivoIdOrderByNomeAsc(Long dispositivoId);

    List<Agendamento> findByAtivoTrueAndProximaExecucaoEmLessThanEqual(Instant agora);
}

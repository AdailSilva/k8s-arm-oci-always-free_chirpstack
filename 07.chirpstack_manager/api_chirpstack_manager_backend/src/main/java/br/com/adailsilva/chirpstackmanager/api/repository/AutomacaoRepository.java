package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Automacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.GatilhoAutomacao;

public interface AutomacaoRepository extends JpaRepository<Automacao, Long> {

    List<Automacao> findAllByOrderByNomeAsc();

    List<Automacao> findByRegraIdAndGatilhoAndAtivoTrue(Long regraId, GatilhoAutomacao gatilho);
}

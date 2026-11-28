package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import java.util.List;
import java.util.Set;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;

/**
 * Como falar com um modelo de firmware. Um sensor ou atuador novo entra como um
 * {@link TipoPeriferico}, os comandos dele em {@link TipoComando} e a codificação aqui.
 */
public interface DriverModelo {

    ModeloFirmware modelo();

    Set<TipoComando> comandos();

    Set<TipoPeriferico> perifericos();

    /** Observações mostradas na tela de configuração (limitações, pré-requisitos). */
    List<String> observacoes();

    /**
     * O firmware não sabe fazer pulso sozinho: o servidor liga e agenda o comando de volta ao fim
     * da duração (origem PULSO).
     */
    default boolean pulsoPeloServidor() {
        return false;
    }

    /** Se o comando leva um número de sequência (deduplicação no firmware). */
    boolean usaSeq(TipoComando tipo);

    /**
     * Monta o quadro do comando. Lança {@code BusinessException} (422) para parâmetro inválido
     * ou periférico ausente.
     */
    Downlink codificar(TipoComando tipo, ParametrosComando p, ContextoEquipamento ctx, int seq);

    /** Decodifica um uplink; {@code null} quando a porta não é deste modelo. */
    Decodificacao decodificar(int porta, byte[] dados, ContextoEquipamento ctx);

    /**
     * Confere se o uplink prova (ou recusa) o comando em andamento; {@code null} = não tem
     * relação com ele.
     */
    Desfecho avaliar(Comando comando, ParametrosComando p, ContextoEquipamento ctx, int porta, byte[] dados);
}

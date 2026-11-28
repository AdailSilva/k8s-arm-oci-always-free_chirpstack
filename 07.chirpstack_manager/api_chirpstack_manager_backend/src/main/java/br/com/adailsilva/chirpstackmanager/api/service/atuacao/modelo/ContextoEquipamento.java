package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import java.util.List;
import java.util.Optional;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Equipamento;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;

/** Equipamento com seus periféricos, para codificar e decodificar. */
public record ContextoEquipamento(Equipamento equipamento, List<Periferico> perifericos) {

    public Optional<Periferico> periferico(TipoPeriferico tipo, int canal) {
        return perifericos.stream().filter(p -> p.getTipo() == tipo && p.getCanal() == canal).findFirst();
    }

    public List<Periferico> reles() {
        return perifericos.stream().filter(p -> p.getTipo() == TipoPeriferico.RELE).toList();
    }

    public List<Periferico> medidores() {
        return perifericos.stream().filter(p -> p.getTipo() == TipoPeriferico.PZEM004T).toList();
    }
}

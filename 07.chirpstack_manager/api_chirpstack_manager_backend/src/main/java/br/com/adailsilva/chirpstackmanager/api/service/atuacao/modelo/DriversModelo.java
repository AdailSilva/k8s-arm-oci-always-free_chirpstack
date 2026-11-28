package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;

/** Catálogo dos drivers de firmware (um por {@link ModeloFirmware}). */
@Component
public class DriversModelo {

    private final Map<ModeloFirmware, DriverModelo> porModelo = new EnumMap<>(ModeloFirmware.class);

    public DriversModelo(List<DriverModelo> drivers) {
        drivers.forEach(d -> porModelo.put(d.modelo(), d));
        for (ModeloFirmware m : ModeloFirmware.values()) {
            if (!porModelo.containsKey(m)) {
                throw new IllegalStateException("Sem driver para o modelo " + m);
            }
        }
    }

    public DriverModelo de(ModeloFirmware modelo) {
        return porModelo.get(modelo);
    }
}

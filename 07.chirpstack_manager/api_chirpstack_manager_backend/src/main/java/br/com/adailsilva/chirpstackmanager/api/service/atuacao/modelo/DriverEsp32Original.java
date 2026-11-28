package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Firmware ESP32 base (LMIC): envia um conteúdo fixo na porta configurada e só aceita o reset
 * remoto na porta 101. Sem resposta: o comando termina quando o gateway transmite.
 */
@Component
public class DriverEsp32Original implements DriverModelo {

    @Override
    public ModeloFirmware modelo() {
        return ModeloFirmware.ESP32_ORIGINAL;
    }

    @Override
    public Set<TipoComando> comandos() {
        return EnumSet.of(TipoComando.RESET_REMOTO, TipoComando.REINICIAR, TipoComando.BRUTO);
    }

    @Override
    public Set<TipoPeriferico> perifericos() {
        return EnumSet.noneOf(TipoPeriferico.class);
    }

    @Override
    public List<String> observacoes() {
        return List.of("Este firmware só aceita o reset remoto (porta 101); relés e medidores exigem o firmware com o protocolo ADAILSILVA_V1.");
    }

    @Override
    public boolean usaSeq(TipoComando tipo) {
        return false;
    }

    @Override
    public Downlink codificar(TipoComando tipo, ParametrosComando p, ContextoEquipamento ctx, int seq) {
        return switch (tipo) {
            case RESET_REMOTO, REINICIAR -> Comum.resetRemoto();
            case BRUTO -> Comum.bruto(p);
            default -> throw Comum.invalido("Comando não suportado pelo firmware ESP32 original.");
        };
    }

    @Override
    public Decodificacao decodificar(int porta, byte[] dados, ContextoEquipamento ctx) {
        if (dados == null || dados.length == 0) {
            return null;
        }
        // Conteúdo fixo configurado no nó: mostra como texto quando for ASCII imprimível
        String t = new String(dados, StandardCharsets.US_ASCII);
        if (!t.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
            return null;
        }
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("texto", t);
        return new Decodificacao(o, new Efeitos());
    }

    @Override
    public Desfecho avaliar(Comando c, ParametrosComando p, ContextoEquipamento ctx, int porta, byte[] dados) {
        return null;
    }
}

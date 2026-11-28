package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Modbus;
import tools.jackson.databind.node.ObjectNode;

/**
 * O que uma mensagem do equipamento revelou: estado dos relés (canal → ligado), medições
 * (canal → medição), medidores que não responderam, boot e a resposta de STATUS.
 */
public class Efeitos {

    public final Map<Integer, Boolean> reles = new LinkedHashMap<>();
    public final Map<Integer, Modbus.Medicao> medicoes = new LinkedHashMap<>();
    public final Set<Integer> medidoresSemResposta = new LinkedHashSet<>();
    public boolean boot;
    public ObjectNode status;

    public boolean vazio() {
        return reles.isEmpty() && medicoes.isEmpty() && medidoresSemResposta.isEmpty() && !boot && status == null;
    }
}

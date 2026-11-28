package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ComandoSuportado;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EquipamentoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EquipamentoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ModeloResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PerifericoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PerifericoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PerifericoSuportado;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Equipamento;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EquipamentoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.PerifericoRepository;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.ContextoEquipamento;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.DriverModelo;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.DriversModelo;

/** Modelo de firmware e periféricos de cada dispositivo. */
@Service
public class EquipamentoService {

    private final EquipamentoRepository equipamentos;
    private final PerifericoRepository perifericos;
    private final DispositivoRepository dispositivos;
    private final DriversModelo drivers;

    public EquipamentoService(EquipamentoRepository equipamentos, PerifericoRepository perifericos,
                              DispositivoRepository dispositivos, DriversModelo drivers) {
        this.equipamentos = equipamentos;
        this.perifericos = perifericos;
        this.dispositivos = dispositivos;
        this.drivers = drivers;
    }

    public List<ModeloResponse> modelos() {
        return Arrays.stream(ModeloFirmware.values()).map(m -> {
            DriverModelo d = drivers.de(m);
            return new ModeloResponse(m, m.rotulo(), m.descricao(), comandos(d),
                    d.perifericos().stream().map(t -> new PerifericoSuportado(t, t.rotulo(), t.atuador())).toList(),
                    d.observacoes());
        }).toList();
    }

    private static List<ComandoSuportado> comandos(DriverModelo d) {
        return d.comandos().stream().map(t -> new ComandoSuportado(t, t.rotulo(), t.periferico())).toList();
    }

    @Transactional(readOnly = true)
    public EquipamentoResponse buscar(Long dispositivoId) {
        exigirDispositivo(dispositivoId);
        return equipamentos.findByDispositivoId(dispositivoId).map(this::resposta)
                .orElse(new EquipamentoResponse(dispositivoId, false, null, null, true, null, null, null, List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public Optional<ContextoEquipamento> contexto(Long dispositivoId) {
        return equipamentos.findByDispositivoId(dispositivoId)
                .map(e -> new ContextoEquipamento(e, perifericos.findByEquipamentoIdOrderByTipoAscCanalAsc(e.getId())));
    }

    public DriverModelo driver(Equipamento e) {
        return drivers.de(e.getModelo());
    }

    @Transactional
    public EquipamentoResponse salvar(Long dispositivoId, EquipamentoRequest req, String usuario) {
        exigirDispositivo(dispositivoId);
        DriverModelo driver = drivers.de(req.modelo());
        List<PerifericoRequest> lista = req.perifericos() == null ? List.of() : req.perifericos();
        validar(driver, lista);

        Equipamento e = equipamentos.findByDispositivoId(dispositivoId).orElseGet(() -> {
            Equipamento n = new Equipamento();
            n.setDispositivoId(dispositivoId);
            n.setCriadoEm(Instant.now());
            n.setReconciliar(true);
            return n;
        });
        e.setModelo(req.modelo());
        e.setReconciliar(req.reconciliar() == null || req.reconciliar());
        e.setTarifaKwh(req.tarifaKwh());
        e.setAtualizadoEm(Instant.now());
        e.setAtualizadoPor(usuario);
        e = equipamentos.saveAndFlush(e);

        // Substitui a lista mantendo o estado dos periféricos que continuam (mesmo tipo e canal)
        Map<String, Periferico> atuais = new HashMap<>();
        for (Periferico p : perifericos.findByEquipamentoIdOrderByTipoAscCanalAsc(e.getId())) {
            atuais.put(p.getTipo() + ":" + p.getCanal(), p);
        }
        List<Periferico> salvar = new ArrayList<>();
        for (PerifericoRequest r : lista) {
            Periferico p = atuais.remove(r.tipo() + ":" + r.canal());
            if (p == null) {
                p = new Periferico();
                p.setEquipamentoId(e.getId());
                p.setTipo(r.tipo());
                p.setCanal(r.canal());
                p.setCriadoEm(Instant.now());
            }
            p.setNome(r.nome().trim());
            p.setGpio(r.tipo() == TipoPeriferico.RELE ? r.gpio() : null);
            p.setEnderecoModbus(r.tipo() == TipoPeriferico.PZEM004T ? r.enderecoModbus() : null);
            p.setCritico(r.tipo().atuador() && r.critico());
            p.setAtualizadoEm(Instant.now());
            salvar.add(p);
        }
        perifericos.deleteAll(atuais.values());
        perifericos.flush();
        perifericos.saveAll(salvar);
        return resposta(e);
    }

    @Transactional
    public void remover(Long dispositivoId) {
        equipamentos.findByDispositivoId(dispositivoId).ifPresent(equipamentos::delete);
    }

    private void validar(DriverModelo driver, List<PerifericoRequest> lista) {
        Set<String> chaves = new HashSet<>();
        Set<Integer> gpios = new HashSet<>();
        for (PerifericoRequest r : lista) {
            if (!driver.perifericos().contains(r.tipo())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        r.tipo().rotulo() + " não é suportado pelo firmware " + driver.modelo().rotulo() + ".");
            }
            if (!chaves.add(r.tipo() + ":" + r.canal())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Canal " + r.canal() + " repetido em " + r.tipo().rotulo() + ".");
            }
            if (driver.modelo() == ModeloFirmware.RADIOENGE_ORIGINAL && r.tipo() == TipoPeriferico.RELE) {
                if (r.gpio() == null || r.gpio() < 2 || r.gpio() > 9) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Relé " + r.canal() + ": informe a GPIO do módulo (2 a 9).");
                }
                if (!gpios.add(r.gpio())) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "GPIO " + r.gpio() + " usada em dois relés.");
                }
            }
        }
    }

    private void exigirDispositivo(Long id) {
        if (!dispositivos.existsById(id)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado.");
        }
    }

    private EquipamentoResponse resposta(Equipamento e) {
        List<PerifericoResponse> ps = perifericos.findByEquipamentoIdOrderByTipoAscCanalAsc(e.getId()).stream()
                .map(EquipamentoService::periferico).toList();
        return new EquipamentoResponse(e.getDispositivoId(), true, e.getModelo(), e.getModelo().rotulo(), e.isReconciliar(),
                e.getTarifaKwh(), e.getStatusJson(), e.getStatusEm(), ps, comandos(drivers.de(e.getModelo())));
    }

    static PerifericoResponse periferico(Periferico p) {
        return new PerifericoResponse(p.getId(), p.getTipo(), p.getTipo().rotulo(), p.getCanal(), p.getNome(), p.getGpio(),
                p.getEnderecoModbus(), p.isCritico(), p.getEstadoDesejado(), p.getEstadoReportado(), p.getReportadoEm(),
                p.getLeituraJson(), p.getLeituraEm());
    }
}

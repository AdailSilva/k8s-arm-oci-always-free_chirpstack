package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AutomacaoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AutomacaoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Automacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AlvoAcao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.GatilhoAutomacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.AutomacaoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.CenaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.RegraAlertaRepository;

/**
 * Automações: quando um alerta de uma regra abre (ou é resolvido), executa um comando ou uma cena.
 * Ex.: regra "pzem.0.potencia &gt; 2000" + automação "desligar o relé 0". Roda depois do commit
 * do alerta, numa transação própria; "intervalo_minimo_s" segura repetições.
 */
@Service
public class AutomacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutomacaoService.class);

    private final AutomacaoRepository automacoes;
    private final RegraAlertaRepository regras;
    private final DispositivoRepository dispositivos;
    private final CenaRepository cenas;
    private final ComandoService comandos;
    private final CenaService cenaService;
    private final TransactionTemplate tx;

    public AutomacaoService(AutomacaoRepository automacoes, RegraAlertaRepository regras, DispositivoRepository dispositivos,
                            CenaRepository cenas, ComandoService comandos, CenaService cenaService, PlatformTransactionManager tm) {
        this.automacoes = automacoes;
        this.regras = regras;
        this.dispositivos = dispositivos;
        this.cenas = cenas;
        this.comandos = comandos;
        this.cenaService = cenaService;
        this.tx = new TransactionTemplate(tm);
        // Transação própria: roda no afterCommit da transação do alerta.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoMudarAlerta(AlertaMudouEvento ev) {
        GatilhoAutomacao g = ev.aberto() ? GatilhoAutomacao.ALERTA_ABERTO : GatilhoAutomacao.ALERTA_RESOLVIDO;
        List<Long> ids = tx.execute(s -> automacoes.findByRegraIdAndGatilhoAndAtivoTrue(ev.regraId(), g).stream()
                .map(Automacao::getId).toList());
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> disparar(id, ev.dispositivoId()));
            } catch (RuntimeException e) {
                log.warn("Automação {}: {}", id, e.getMessage());
            }
        }
    }

    private void disparar(Long id, Long dispositivoAlerta) {
        Automacao a = automacoes.findById(id).orElse(null);
        Instant agora = Instant.now();
        if (a == null || !a.isAtivo()
                || (a.getUltimaExecucaoEm() != null && a.getUltimaExecucaoEm().plusSeconds(a.getIntervaloMinimoS()).isAfter(agora))) {
            return;
        }
        String quem = "automação: " + a.getNome();
        if (a.getAlvo() == AlvoAcao.COMANDO) {
            Long alvo = a.isMesmoDispositivo() ? dispositivoAlerta : a.getDispositivoId();
            comandos.criar(alvo, a.getTipo(), comandos.lerParametros(a.getParametros()), null, OrigemComando.AUTOMACAO,
                    a.getId(), quem, true);
        } else {
            cenaService.executar(a.getCenaId(), OrigemComando.AUTOMACAO, quem, true, null);
        }
        a.setUltimaExecucaoEm(agora);
        automacoes.save(a);
        log.info("Automação \"{}\" disparada.", a.getNome());
    }

    @Transactional(readOnly = true)
    public List<AutomacaoResponse> listar() {
        return automacoes.findAllByOrderByNomeAsc().stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public Automacao buscar(Long id) {
        return automacoes.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Automação não encontrada."));
    }

    @Transactional
    public AutomacaoResponse criar(AutomacaoRequest req, String usuario) {
        Automacao a = new Automacao();
        a.setCriadoEm(Instant.now());
        return salvar(a, req, usuario);
    }

    @Transactional
    public AutomacaoResponse atualizar(Long id, AutomacaoRequest req, String usuario) {
        return salvar(buscar(id), req, usuario);
    }

    @Transactional
    public void excluir(Long id) {
        automacoes.delete(buscar(id));
    }

    private AutomacaoResponse salvar(Automacao a, AutomacaoRequest req, String usuario) {
        if (!regras.existsById(req.regraId())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de alerta não encontrada.");
        }
        if (req.alvo() == AlvoAcao.COMANDO) {
            if (req.tipo() == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o comando.");
            }
            if (!req.mesmoDispositivo()) {
                if (req.dispositivoId() == null) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o dispositivo (ou use o mesmo que disparou o alerta).");
                }
                comandos.validar(req.dispositivoId(), req.tipo(), req.parametros());
                if (comandos.critico(req.dispositivoId(), req.tipo(), req.parametros()) && !req.confirmarCritico()) {
                    throw new BusinessException(HttpStatus.CONFLICT, "O comando aciona uma carga crítica: confirme a automação.");
                }
            }
        } else {
            if (req.cenaId() == null || !cenas.existsById(req.cenaId())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe a cena.");
            }
            if (cenaService.detalhe(req.cenaId()).temCritico() && !req.confirmarCritico()) {
                throw new BusinessException(HttpStatus.CONFLICT, "A cena aciona uma carga crítica: confirme a automação.");
            }
        }
        a.setNome(req.nome().trim());
        a.setRegraId(req.regraId());
        a.setGatilho(req.gatilho());
        a.setAlvo(req.alvo());
        boolean comando = req.alvo() == AlvoAcao.COMANDO;
        a.setMesmoDispositivo(comando && req.mesmoDispositivo());
        a.setDispositivoId(comando && !req.mesmoDispositivo() ? req.dispositivoId() : null);
        a.setTipo(comando ? req.tipo() : null);
        a.setParametros(comando ? comandos.escreverParametros(req.parametros()) : null);
        a.setCenaId(comando ? null : req.cenaId());
        a.setIntervaloMinimoS(req.intervaloMinimoS() == null ? 300 : req.intervaloMinimoS());
        a.setAtivo(req.ativo() == null || req.ativo());
        a.setAtualizadoEm(Instant.now());
        a.setAtualizadoPor(usuario);
        return resposta(automacoes.save(a));
    }

    private AutomacaoResponse resposta(Automacao a) {
        String regra = regras.findById(a.getRegraId()).map(r -> r.getNome()).orElse(null);
        String disp = a.getDispositivoId() == null ? null : dispositivos.findById(a.getDispositivoId()).map(d -> d.getNome()).orElse(null);
        String cena = a.getCenaId() == null ? null : cenas.findById(a.getCenaId()).map(c -> c.getNome()).orElse(null);
        var p = a.getAlvo() == AlvoAcao.COMANDO ? comandos.lerParametros(a.getParametros()) : null;
        String resumo;
        if (a.getAlvo() == AlvoAcao.CENA) {
            resumo = "Cena " + cena;
        } else if (a.isMesmoDispositivo()) {
            resumo = a.getTipo().rotulo() + " no dispositivo do alerta";
        } else {
            resumo = comandos.descrever(a.getDispositivoId(), a.getTipo(), p);
        }
        return new AutomacaoResponse(a.getId(), a.getNome(), a.getRegraId(), regra, a.getGatilho(), a.getAlvo(),
                a.isMesmoDispositivo(), a.getDispositivoId(), disp, a.getTipo(), a.getTipo() == null ? null : a.getTipo().rotulo(),
                p, a.getCenaId(), cena, resumo, a.getIntervaloMinimoS(), a.isAtivo(), a.getUltimaExecucaoEm(),
                a.getAtualizadoEm(), a.getAtualizadoPor());
    }
}

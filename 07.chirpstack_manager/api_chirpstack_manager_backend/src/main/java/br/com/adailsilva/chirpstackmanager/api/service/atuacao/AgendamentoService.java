package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AgendamentoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AgendamentoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PoliticaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Agendamento;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AlvoAcao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Recorrencia;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.AgendamentoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.CenaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;

/**
 * Agendamentos de comandos e cenas: uma vez (data e hora) ou semanal (hora nos dias marcados, no
 * fuso do sistema). A cada 30 s os vencidos viram comandos na fila com validade e tentativas do
 * próprio agendamento.
 */
@Service
public class AgendamentoService {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);

    private final AgendamentoRepository agendamentos;
    private final DispositivoRepository dispositivos;
    private final CenaRepository cenas;
    private final ComandoService comandos;
    private final CenaService cenaService;
    private final TransactionTemplate tx;
    private final ZoneId zona;

    public AgendamentoService(AgendamentoRepository agendamentos, DispositivoRepository dispositivos, CenaRepository cenas,
                              ComandoService comandos, CenaService cenaService, PlatformTransactionManager tm,
                              @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this.agendamentos = agendamentos;
        this.dispositivos = dispositivos;
        this.cenas = cenas;
        this.comandos = comandos;
        this.cenaService = cenaService;
        this.tx = new TransactionTemplate(tm);
        this.zona = ZoneId.of(fuso);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listar(Long dispositivoId) {
        List<Agendamento> l = dispositivoId == null ? agendamentos.findAllByOrderByNomeAsc()
                : agendamentos.findByDispositivoIdOrderByNomeAsc(dispositivoId);
        return l.stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public Agendamento buscar(Long id) {
        return agendamentos.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));
    }

    @Transactional
    public AgendamentoResponse criar(AgendamentoRequest req, String usuario) {
        Agendamento a = new Agendamento();
        a.setCriadoEm(Instant.now());
        return salvar(a, req, usuario);
    }

    @Transactional
    public AgendamentoResponse atualizar(Long id, AgendamentoRequest req, String usuario) {
        return salvar(buscar(id), req, usuario);
    }

    @Transactional
    public void excluir(Long id) {
        agendamentos.delete(buscar(id));
    }

    private AgendamentoResponse salvar(Agendamento a, AgendamentoRequest req, String usuario) {
        if (req.alvo() == AlvoAcao.COMANDO) {
            if (req.dispositivoId() == null || req.tipo() == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o dispositivo e o comando.");
            }
            comandos.validar(req.dispositivoId(), req.tipo(), req.parametros());
            if (comandos.critico(req.dispositivoId(), req.tipo(), req.parametros()) && !req.confirmarCritico()) {
                throw new BusinessException(HttpStatus.CONFLICT, "O comando aciona uma carga crítica: confirme o agendamento.");
            }
        } else {
            if (req.cenaId() == null || !cenas.existsById(req.cenaId())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe a cena.");
            }
            if (cenaService.detalhe(req.cenaId()).temCritico() && !req.confirmarCritico()) {
                throw new BusinessException(HttpStatus.CONFLICT, "A cena aciona uma carga crítica: confirme o agendamento.");
            }
        }
        if (req.recorrencia() == Recorrencia.UNICA && req.executarEm() == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe a data e a hora da execução.");
        }
        if (req.recorrencia() == Recorrencia.SEMANAL && req.horario() == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o horário.");
        }
        a.setNome(req.nome().trim());
        a.setAlvo(req.alvo());
        a.setDispositivoId(req.alvo() == AlvoAcao.COMANDO ? req.dispositivoId() : null);
        a.setTipo(req.alvo() == AlvoAcao.COMANDO ? req.tipo() : null);
        a.setParametros(req.alvo() == AlvoAcao.COMANDO ? comandos.escreverParametros(req.parametros()) : null);
        a.setCenaId(req.alvo() == AlvoAcao.CENA ? req.cenaId() : null);
        a.setRecorrencia(req.recorrencia());
        a.setExecutarEm(req.recorrencia() == Recorrencia.UNICA ? req.executarEm() : null);
        a.setHorario(req.recorrencia() == Recorrencia.SEMANAL ? req.horario().withSecond(0).withNano(0) : null);
        a.setDiasSemana(DiasSemana.mascara(req.diasSemana()));
        a.setValidadeMin(req.validadeMin() == null ? 60 : req.validadeMin());
        a.setMaxTentativas(req.maxTentativas() == null ? 5 : req.maxTentativas());
        a.setAtivo(req.ativo() == null || req.ativo());
        a.setProximaExecucaoEm(a.isAtivo() ? proxima(a, Instant.now()) : null);
        a.setAtualizadoEm(Instant.now());
        a.setAtualizadoPor(usuario);
        return resposta(agendamentos.save(a));
    }

    /** Próxima execução depois de {@code depois} ({@code null} = não há). */
    Instant proxima(Agendamento a, Instant depois) {
        if (a.getRecorrencia() == Recorrencia.UNICA) {
            return a.getExecutarEm() != null && a.getExecutarEm().isAfter(depois.minusSeconds(60)) ? a.getExecutarEm() : null;
        }
        ZonedDateTime base = depois.atZone(zona);
        for (int i = 0; i <= 7; i++) {
            LocalDate dia = base.toLocalDate().plusDays(i);
            ZonedDateTime t = dia.atTime(a.getHorario()).atZone(zona);
            if (t.toInstant().isAfter(depois) && DiasSemana.contem(a.getDiasSemana(), dia.getDayOfWeek())) {
                return t.toInstant();
            }
        }
        return null;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 45_000)
    public void executarVencidos() {
        executarVencidos(Instant.now());
    }

    public void executarVencidos(Instant agora) {
        List<Long> ids = tx.execute(s -> agendamentos.findByAtivoTrueAndProximaExecucaoEmLessThanEqual(agora).stream()
                .map(Agendamento::getId).toList());
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> executar(id, agora));
            } catch (RuntimeException e) {
                log.warn("Agendamento {}: {}", id, e.getMessage());
                tx.executeWithoutResult(s -> agendamentos.findById(id).ifPresent(a -> {
                    a.setProximaExecucaoEm(proxima(a, agora));
                    a.setAtivo(a.getProximaExecucaoEm() != null);
                    agendamentos.save(a);
                }));
            }
        }
    }

    private void executar(Long id, Instant agora) {
        Agendamento a = agendamentos.findById(id).orElse(null);
        if (a == null || !a.isAtivo()) {
            return;
        }
        PoliticaRequest pol = new PoliticaRequest(a.getMaxTentativas(), null, null, null, a.getValidadeMin(), null, null, null);
        if (a.getAlvo() == AlvoAcao.COMANDO) {
            comandos.criar(a.getDispositivoId(), a.getTipo(), comandos.lerParametros(a.getParametros()), pol,
                    OrigemComando.AGENDAMENTO, a.getId(), "agendamento: " + a.getNome(), true);
        } else {
            cenaService.executar(a.getCenaId(), OrigemComando.AGENDAMENTO, "agendamento: " + a.getNome(), true, pol);
        }
        a.setUltimaExecucaoEm(agora);
        a.setProximaExecucaoEm(proxima(a, agora));
        if (a.getProximaExecucaoEm() == null) {
            a.setAtivo(false);
        }
        agendamentos.save(a);
    }

    private AgendamentoResponse resposta(Agendamento a) {
        String disp = a.getDispositivoId() == null ? null : dispositivos.findById(a.getDispositivoId()).map(d -> d.getNome()).orElse(null);
        String cena = a.getCenaId() == null ? null : cenas.findById(a.getCenaId()).map(c -> c.getNome()).orElse(null);
        var p = a.getAlvo() == AlvoAcao.COMANDO ? comandos.lerParametros(a.getParametros()) : null;
        String resumo = a.getAlvo() == AlvoAcao.COMANDO ? comandos.descrever(a.getDispositivoId(), a.getTipo(), p) : "Cena " + cena;
        return new AgendamentoResponse(a.getId(), a.getNome(), a.getAlvo(), a.getDispositivoId(), disp, a.getTipo(),
                a.getTipo() == null ? null : a.getTipo().rotulo(), p, a.getCenaId(), cena, resumo, a.getRecorrencia(),
                a.getExecutarEm(), a.getHorario(), DiasSemana.lista(a.getDiasSemana()), a.getValidadeMin(),
                a.getMaxTentativas(), a.isAtivo(), a.getProximaExecucaoEm(), a.getUltimaExecucaoEm(), a.getAtualizadoEm(),
                a.getAtualizadoPor());
    }
}

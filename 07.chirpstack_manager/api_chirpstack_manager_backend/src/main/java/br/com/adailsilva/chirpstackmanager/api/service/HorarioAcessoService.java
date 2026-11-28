package br.com.adailsilva.chirpstackmanager.api.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.HorarioAcessoDtos.Dia;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.HorarioAcessoDtos.Situacao;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.HorarioAcesso;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Perfil;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.HorarioAcessoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.UsuarioRepository;

/**
 * Horário de acesso por dia da semana, mesmo padrão dos demais sistemas.
 * <ul>
 *   <li>Definido pelo ADMIN; vale para todo usuário que não é ADMIN (OPERADOR e VISUALIZADOR).</li>
 *   <li>Fora do horário o login é recusado com o motivo e quando o acesso volta.</li>
 *   <li>Dentro dele, o navegador avisa 5 minutos antes do fim e, no fim, encerra a sessão. O servidor
 *       confere a cada 30 s e na renovação do token: quem estiver com o navegador fechado ou com o
 *       relógio errado cai do mesmo jeito.</li>
 *   <li>Grade vazia (todos os dias livres) = sem restrição.</li>
 * </ul>
 * A grade (no máximo sete linhas) fica em memória e é recarregada ao salvar; com várias réplicas,
 * as outras recarregam em até um minuto.
 */
@Service
public class HorarioAcessoService {

    private static final Logger log = LoggerFactory.getLogger(HorarioAcessoService.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");
    private static final long CACHE_MS = 60_000;

    private record Grade(Map<Integer, HorarioAcesso> dias, long validoAte) {
    }

    private final HorarioAcessoRepository repository;
    private final UsuarioRepository usuarios;
    private final SessaoService sessoes;
    private final TransactionTemplate tx;
    private final ZoneId zona;

    private final AtomicReference<Grade> cache = new AtomicReference<>();

    public HorarioAcessoService(HorarioAcessoRepository repository, UsuarioRepository usuarios, SessaoService sessoes,
                                TransactionTemplate tx, @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.tx = tx;
        this.zona = ZoneId.of(fuso);
    }

    // ── Grade ────────────────────────────────────────────────────────────────

    public List<Dia> listar() {
        Map<Integer, HorarioAcesso> g = grade();
        List<Dia> out = new ArrayList<>(7);
        for (int d = 1; d <= 7; d++) {
            HorarioAcesso h = g.get(d);
            out.add(new Dia(d, nomeDia(d), h == null || h.isPermitido(),
                    h != null && h.getHoraInicio() != null ? h.getHoraInicio().format(HH_MM) : null,
                    h != null && h.getHoraFim() != null ? h.getHoraFim().format(HH_MM) : null));
        }
        return out;
    }

    /** Substitui a grade inteira. Dias ausentes da lista ficam livres. */
    public List<Dia> salvar(List<Dia> dias, String por) {
        Map<Integer, HorarioAcesso> novas = new HashMap<>();
        for (Dia d : dias == null ? List.<Dia>of() : dias) {
            int dia = d.diaSemana();
            if (novas.containsKey(dia)) {
                throw erro(nomeDia(dia) + " aparece mais de uma vez.");
            }
            boolean permitido = !Boolean.FALSE.equals(d.permitido());
            LocalTime ini = hora(d.horaInicio());
            LocalTime fim = hora(d.horaFim());
            if (!permitido) {
                novas.put(dia, new HorarioAcesso(dia, false, null, null, por));
                continue;
            }
            if ((ini == null) != (fim == null)) {
                throw erro(nomeDia(dia) + ": informe o início e o fim, ou deixe os dois em branco (dia livre).");
            }
            if (ini == null) {
                continue; // dia livre: sem linha
            }
            if (!fim.isAfter(ini)) {
                throw erro(nomeDia(dia) + ": o fim precisa ser depois do início.");
            }
            if (java.time.Duration.between(ini, fim).toMinutes() < 15) {
                throw erro(nomeDia(dia) + ": a janela precisa ter pelo menos 15 minutos.");
            }
            novas.put(dia, new HorarioAcesso(dia, true, ini, fim, por));
        }
        tx.executeWithoutResult(st -> {
            repository.deleteAllInBatch();
            repository.flush();
            repository.saveAll(novas.values());
        });
        cache.set(null);
        return listar();
    }

    // ── Regra ────────────────────────────────────────────────────────────────

    /** Motivo da recusa agora para quem tem este perfil, ou vazio se o acesso está liberado. */
    public Optional<String> recusaAgora(Perfil perfil) {
        if (perfil == Perfil.ADMIN) {
            return Optional.empty();
        }
        Situacao s = calcular(ZonedDateTime.now(zona));
        return s.permitidoAgora() ? Optional.empty() : Optional.of(s.mensagem());
    }

    /** Situação do usuário logado (aviso dos 5 minutos). */
    public Situacao situacao(Perfil perfil) {
        ZonedDateTime agora = ZonedDateTime.now(zona);
        if (perfil == Perfil.ADMIN) {
            return new Situacao(false, true, null, null, null, null, agora.toInstant());
        }
        return calcular(agora);
    }

    /** Há restrição configurada (algum dia com janela ou sem acesso)? */
    public boolean temRestricao() {
        return !grade().isEmpty();
    }

    private Situacao calcular(ZonedDateTime agora) {
        Map<Integer, HorarioAcesso> g = grade();
        if (g.isEmpty()) {
            return new Situacao(false, true, null, null, null, null, agora.toInstant());
        }
        int dia = agora.getDayOfWeek().getValue();
        HorarioAcesso h = g.get(dia);
        LocalTime t = agora.toLocalTime();

        if (h == null) {
            // Dia livre. Se o dia seguinte não começa liberado à meia-noite, a sessão acaba nela.
            HorarioAcesso amanha = g.get(agora.plusDays(1).getDayOfWeek().getValue());
            boolean acabaAMeiaNoite = amanha != null
                    && (!amanha.isPermitido() || amanha.getHoraInicio().isAfter(LocalTime.MIDNIGHT));
            if (!acabaAMeiaNoite) {
                return new Situacao(false, true, null, null, null, null, agora.toInstant());
            }
            ZonedDateTime fim = agora.toLocalDate().plusDays(1).atStartOfDay(zona);
            return new Situacao(true, true, fim.toInstant(), "00:00", null,
                    "O acesso de hoje vai até a meia-noite.", agora.toInstant());
        }
        if (h.isPermitido() && !t.isBefore(h.getHoraInicio()) && t.isBefore(h.getHoraFim())) {
            ZonedDateTime fim = agora.toLocalDate().atTime(h.getHoraFim()).atZone(zona);
            String hf = h.getHoraFim().format(HH_MM);
            return new Situacao(true, true, fim.toInstant(), hf, null,
                    "O horário de acesso de hoje termina às " + hf + ".", agora.toInstant());
        }

        LocalDateTime proximo = proximoInicio(g, agora.toLocalDateTime());
        String hoje = !h.isPermitido()
                ? "Hoje (" + nomeDia(dia).toLowerCase(PT_BR) + ") não há acesso ao sistema."
                : "O acesso de hoje é das " + h.getHoraInicio().format(HH_MM) + " às " + h.getHoraFim().format(HH_MM) + ".";
        String volta = proximo == null ? "" : " O acesso volta " + quando(proximo, agora.toLocalDate()) + ".";
        return new Situacao(true, false, null, null, proximo == null ? null : proximo.atZone(zona).toInstant(),
                "Fora do horário de acesso. " + hoje + volta, agora.toInstant());
    }

    private static LocalDateTime proximoInicio(Map<Integer, HorarioAcesso> g, LocalDateTime agora) {
        for (int i = 0; i <= 7; i++) {
            LocalDate d = agora.toLocalDate().plusDays(i);
            HorarioAcesso h = g.get(d.getDayOfWeek().getValue());
            if (h == null) {
                if (i > 0) {
                    return d.atStartOfDay(); // dia livre
                }
                continue;
            }
            if (h.isPermitido()) {
                LocalDateTime ini = d.atTime(h.getHoraInicio());
                if (ini.isAfter(agora)) {
                    return ini;
                }
            }
        }
        return null;
    }

    private static String quando(LocalDateTime t, LocalDate hoje) {
        String hora = t.format(HH_MM);
        if (t.toLocalDate().equals(hoje)) {
            return "hoje às " + hora;
        }
        if (t.toLocalDate().equals(hoje.plusDays(1))) {
            return "amanhã às " + hora;
        }
        return nomeDia(t.getDayOfWeek().getValue()).toLowerCase(PT_BR) + " (" + t.format(DD_MM) + ") às " + hora;
    }

    // ── Encerramento no fim do horário ───────────────────────────────────────

    /**
     * Encerra as sessões de quem não é ADMIN e está fora do horário agora. Roda a cada 30 s e logo
     * depois de salvar a grade (quando o ADMIN pede para aplicar na hora).
     */
    public int aplicarAgora() {
        if (!temRestricao() || calcular(ZonedDateTime.now(zona)).permitidoAgora()) {
            return 0;
        }
        Map<Long, Boolean> fora = new HashMap<>();
        int n = sessoes.encerrarForaDoHorario(uid -> fora.computeIfAbsent(uid,
                id -> usuarios.findById(id).map(Usuario::getPerfil).map(p -> p != Perfil.ADMIN).orElse(false)));
        if (n > 0) {
            log.info("Horário de acesso: {} sessão(ões) encerrada(s) no fim do horário.", n);
        }
        return n;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 45_000)
    public void varrer() {
        try {
            aplicarAgora();
        } catch (RuntimeException e) {
            log.warn("Falha ao aplicar o horário de acesso: {}", e.getMessage());
        }
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private Map<Integer, HorarioAcesso> grade() {
        Grade g = cache.get();
        long agora = System.currentTimeMillis();
        if (g == null || g.validoAte() < agora) {
            Map<Integer, HorarioAcesso> m = new HashMap<>();
            for (HorarioAcesso h : repository.findAll()) {
                m.put(h.getDiaSemana(), h);
            }
            g = new Grade(Map.copyOf(m), agora + CACHE_MS);
            cache.set(g);
        }
        return g.dias();
    }

    private static LocalTime hora(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(s.trim(), HH_MM);
        } catch (RuntimeException e) {
            throw erro("Hora inválida: " + s + " (use HH:mm).");
        }
    }

    static String nomeDia(int d) {
        String n = DayOfWeek.of(d).getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static BusinessException erro(String msg) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }
}

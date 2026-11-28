package br.com.adailsilva.chirpstackmanager.api.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.Preferencias;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.SuporteRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.SuporteResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.PreferenciaUsuario;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.MotivoEncerramento;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.exception.SessaoEncerradaException;
import br.com.adailsilva.chirpstackmanager.api.repository.PreferenciaUsuarioRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.UsuarioRepository;
import br.com.adailsilva.chirpstackmanager.api.service.email.EmailService;

/**
 * Conta do usuário logado, mesmo padrão dos demais sistemas:
 * <ul>
 *   <li><b>Preferências</b> de aparência guardadas no servidor (valem em qualquer navegador);</li>
 *   <li><b>Tela bloqueada</b>: a senha é conferida aqui; 5 erros seguidos encerram a sessão;</li>
 *   <li><b>Suporte</b>: o chamado vai por e-mail à equipe, com resposta direta para quem escreveu.</li>
 * </ul>
 */
@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);
    static final int MAX_ERROS_DESBLOQUEIO = 5;
    private static final int MAX_CHAMADOS = 5;
    private static final Duration JANELA_CHAMADOS = Duration.ofMinutes(30);

    private final PreferenciaUsuarioRepository preferencias;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SessaoService sessoes;
    private final AuditoriaService auditoria;
    private final EmailService email;
    private final TransactionTemplate tx;
    private final String destinoSuporte;
    private final DateTimeFormatter formatoData;

    /** Erros de senha seguidos na tela bloqueada, por sessão. */
    private final Map<String, Integer> errosDesbloqueio = new ConcurrentHashMap<>();
    /** Chamados recentes por usuário (limite contra abuso). */
    private final Map<String, Deque<Instant>> chamados = new ConcurrentHashMap<>();

    public ContaService(PreferenciaUsuarioRepository preferencias, UsuarioRepository usuarios, PasswordEncoder encoder,
                        SessaoService sessoes, AuditoriaService auditoria, EmailService email, TransactionTemplate tx,
                        @Value("${app.suporte.destino:adail101@hotmail.com}") String destinoSuporte,
                        @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this.preferencias = preferencias;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
        this.email = email;
        this.tx = tx;
        this.destinoSuporte = destinoSuporte;
        this.formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of(fuso));
    }

    // ── Preferências ─────────────────────────────────────────────────────────

    /** Preferências do usuário; {@code null} quando ele ainda não salvou nenhuma (o navegador usa o padrão). */
    public Preferencias preferencias(String emailUsuario) {
        Usuario u = usuario(emailUsuario);
        return preferencias.findById(u.getId()).map(ContaService::dto).orElse(null);
    }

    public Preferencias salvarPreferencias(String emailUsuario, Preferencias p) {
        Usuario u = usuario(emailUsuario);
        PreferenciaUsuario salva = tx.execute(st -> {
            PreferenciaUsuario e = preferencias.findById(u.getId()).orElseGet(() -> new PreferenciaUsuario(u.getId()));
            e.setPreset(p.preset());
            e.setCorPrimaria(p.primary());
            e.setSuperficie(p.surface() == null || p.surface().isBlank() ? null : p.surface());
            e.setTemaEscuro(Boolean.TRUE.equals(p.darkTheme()));
            e.setModoMenu(p.menuMode());
            return preferencias.save(e);
        });
        return dto(salva);
    }

    private static Preferencias dto(PreferenciaUsuario e) {
        return new Preferencias(e.getPreset(), e.getCorPrimaria(), e.getSuperficie(), e.isTemaEscuro(), e.getModoMenu(),
                e.getAtualizadoEm());
    }

    // ── Tela bloqueada ───────────────────────────────────────────────────────

    /**
     * Confere a senha de quem bloqueou a tela. Errar {@value #MAX_ERROS_DESBLOQUEIO} vezes seguidas
     * encerra a sessão (motivo {@code DESBLOQUEIO_FALHOU}).
     */
    public void desbloquear(String emailUsuario, String sessaoId, String senha, String ip) {
        Usuario u = usuario(emailUsuario);
        String chave = sessaoId == null ? emailUsuario : sessaoId;
        if (encoder.matches(senha, u.getSenhaHash())) {
            errosDesbloqueio.remove(chave);
            auditoria.registrar(u.getEmail(), "TELA_DESBLOQUEADA", "/conta/desbloquear", "POST", 204, true, null, ip);
            return;
        }
        int erros = errosDesbloqueio.merge(chave, 1, Integer::sum);
        auditoria.registrar(u.getEmail(), "DESBLOQUEIO_FALHA", "/conta/desbloquear", "POST", 401, false,
                "Senha incorreta (" + erros + "/" + MAX_ERROS_DESBLOQUEIO + ")", ip);
        if (erros >= MAX_ERROS_DESBLOQUEIO) {
            errosDesbloqueio.remove(chave);
            if (sessaoId != null) {
                sessoes.encerrar(sessaoId, MotivoEncerramento.DESBLOQUEIO_FALHOU, "sistema");
            }
            throw new SessaoEncerradaException(MotivoEncerramento.DESBLOQUEIO_FALHOU);
        }
        int restam = MAX_ERROS_DESBLOQUEIO - erros;
        throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SENHA_INCORRETA",
                "Senha incorreta. " + (restam == 1 ? "Mais um erro encerra a sessão."
                        : "Restam " + restam + " tentativas antes de a sessão ser encerrada."));
    }

    // ── Suporte ──────────────────────────────────────────────────────────────

    public SuporteResponse abrirChamado(String emailUsuario, SuporteRequest req, String ip) {
        Usuario u = usuario(emailUsuario);
        Instant agora = Instant.now();
        Deque<Instant> recentes = chamados.computeIfAbsent(u.getEmail(), k -> new ArrayDeque<>());
        synchronized (recentes) {
            while (!recentes.isEmpty() && recentes.peekFirst().isBefore(agora.minus(JANELA_CHAMADOS))) {
                recentes.pollFirst();
            }
            if (recentes.size() >= MAX_CHAMADOS) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                        "Você já enviou " + MAX_CHAMADOS + " mensagens nos últimos 30 minutos. Aguarde um pouco para enviar outra.");
            }
            recentes.addLast(agora);
        }
        String assunto = req.assunto().trim();
        String html = email.corpoSuporte(assunto, req.mensagem().trim(), u.getNome(), u.getEmail(),
                u.getPerfil() == null ? "—" : u.getPerfil().name(), vazioComoNulo(req.telefone()),
                vazioComoNulo(req.whatsapp()), formatoData.format(agora));
        try {
            email.enviarOuFalhar(new String[] {destinoSuporte}, "Suporte: " + assunto, html, u.getEmail());
        } catch (Exception e) {
            synchronized (recentes) {
                recentes.remove(agora);
            }
            log.warn("Falha ao enviar chamado de suporte de {}: {}", u.getEmail(), e.getMessage());
            auditoria.registrar(u.getEmail(), "SUPORTE", "/conta/suporte", "POST", 502, false, e.getMessage(), ip);
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível enviar a mensagem agora (a conta de e-mail do sistema recusou o envio). Tente de novo em instantes.");
        }
        auditoria.registrar(u.getEmail(), "SUPORTE", "/conta/suporte", "POST", 200, true, assunto, ip);
        return new SuporteResponse(destinoSuporte, agora);
    }

    private Usuario usuario(String emailUsuario) {
        return usuarios.findByEmailIgnoreCase(emailUsuario)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
    }

    private static String vazioComoNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package br.com.adailsilva.chirpstackmanager.api.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.repository.RefreshTokenRepository;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.MotivoEncerramento;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.DadosCadastro;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.UsuarioCreateRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.UsuarioUpdateRequest;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Perfil;
import br.com.adailsilva.chirpstackmanager.api.repository.UsuarioRepository;
import br.com.adailsilva.chirpstackmanager.api.security.service.SenhaInicialService;

/**
 * Regras dos usuários do painel.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final SessaoService sessoes;
    private final SenhaInicialService senhaInicial;
    private final RecuperacaoSenhaService links;

    public UsuarioService(UsuarioRepository repository, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                          SessaoService sessoes, SenhaInicialService senhaInicial, RecuperacaoSenhaService links) {
        this.repository = repository;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.sessoes = sessoes;
        this.senhaInicial = senhaInicial;
        this.links = links;
    }

    @Transactional(readOnly = true)
    public Page<Usuario> listar(String busca, int pagina, int tamanho) {
        int size = Math.min(Math.max(tamanho, 1), 200);
        return repository.buscar(busca == null ? "" : busca.trim(),
                PageRequest.of(Math.max(pagina, 0), size, Sort.by("nome")));
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    /** Compatibilidade: criação sem quem convidou (o convite sai sem o nome do administrador). */
    @Transactional
    public Usuario criar(UsuarioCreateRequest req) {
        return criar(req, null);
    }

    /**
     * Novo usuário, como nos demais sistemas: por <b>convite</b> (padrão quando a senha vem vazia) o
     * usuário recebe o link para definir a própria senha; com a senha informada pelo administrador,
     * recebe o link de confirmação do e-mail e só entra depois de confirmar.
     */
    @Transactional
    public Usuario criar(UsuarioCreateRequest req, String convidadoPor) {
        String email = req.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        boolean temSenha = req.senha() != null && !req.senha().isBlank();
        boolean convite = !temSenha || Boolean.TRUE.equals(req.enviarConvite());
        Usuario u = new Usuario();
        u.setNome(req.nome().trim());
        u.setEmail(email);
        // Convite: senha aleatória que ninguém conhece até o usuário definir a dele pelo link.
        u.setSenhaHash(encoder.encode(convite ? UUID.randomUUID() + "-" + UUID.randomUUID() : req.senha()));
        u.setPerfil(req.perfil());
        u.setAtivo(req.ativo() == null || req.ativo());
        u.setConvitePendente(convite);
        u.setEmailVerificado(false);
        aplicarCadastro(u, req.cadastro());
        Usuario salvo = repository.save(u);
        if (convite) {
            links.enviarConvite(salvo, convidadoPor);
        } else {
            links.enviarConfirmacao(salvo);
        }
        return salvo;
    }

    /** Reenvia o convite (convidado sem senha) ou o link de confirmação (e-mail ainda não confirmado). */
    @Transactional
    public Usuario reenviarLink(Long id, String convidadoPor) {
        Usuario u = buscar(id);
        if (!u.isAtivo()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Usuário inativo não recebe convite.");
        }
        if (u.isConvitePendente()) {
            links.enviarConvite(u, convidadoPor);
        } else if (!u.isEmailVerificado()) {
            links.enviarConfirmacao(u);
        } else {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Este usuário já definiu a senha e confirmou o e-mail.");
        }
        return u;
    }

    @Transactional
    public Usuario atualizar(Long id, UsuarioUpdateRequest req, String emailLogado) {
        Usuario u = buscar(id);
        String email = req.email().trim().toLowerCase();
        repository.findByEmailIgnoreCase(email)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new BusinessException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail.");
                });

        boolean proprio = u.getEmail().equalsIgnoreCase(emailLogado);
        if (proprio && (req.perfil() != Perfil.ADMIN || !req.ativo())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Você não pode remover o seu próprio perfil de administrador nem desativar a si mesmo.");
        }
        garantirOutroAdmin(u, req.perfil(), req.ativo());

        boolean emailMudou = !u.getEmail().equalsIgnoreCase(email);
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(req.perfil());
        if (req.cadastro() != null) {
            aplicarCadastro(u, req.cadastro());
        }
        if (req.ativo() && !u.isAtivo()) {
            // Reativação: o motivo da desativação não vale mais.
            u.setMotivoBloqueio(null);
            u.setBloqueadoPor(null);
        }
        u.setAtivo(req.ativo());
        if (req.novaSenha() != null && !req.novaSenha().isBlank()) {
            u.setSenhaHash(encoder.encode(req.novaSenha()));
            u.setConvitePendente(false);
            refreshTokens.revogarTodos(u.getId());
            sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.SENHA_ALTERADA, emailLogado, null);
        }
        if (emailMudou && proprio) {
            // O administrador trocando o próprio e-mail não fica preso esperando a confirmação
            // (um e-mail digitado errado o deixaria sem acesso, e pode ser o único ADMIN).
            u.setEmailVerificado(true);
            u.setEmailVerificadoEm(Instant.now());
        } else if (emailMudou) {
            // Novo endereço precisa ser confirmado (o convite pendente vai para o e-mail novo).
            if (u.isConvitePendente()) {
                u.setEmailVerificado(false);
                links.enviarConvite(u, emailLogado);
            } else {
                links.enviarConfirmacao(u);
                refreshTokens.revogarTodos(u.getId());
                sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.ENCERRADA_ADMIN, emailLogado, null);
            }
        }
        if (!req.ativo()) {
            refreshTokens.revogarTodos(u.getId());
            sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.USUARIO_DESATIVADO, emailLogado, null);
        }
        return u;
    }

    @Transactional
    public void desbloquear(Long id) {
        Usuario u = buscar(id);
        u.setTentativasFalhas(0);
        u.setBloqueadoAte(null);
        u.setMotivoBloqueio(null);
        u.setBloqueadoPor(null);
    }

    /**
     * Bloqueio manual (tela de Visitantes): impede o login até {@code ate} e encerra na hora as
     * sessões abertas do usuário.
     */
    @Transactional
    public Usuario bloquear(Long id, Instant ate, String motivo, String emailLogado) {
        Usuario u = buscar(id);
        if (u.getEmail().equalsIgnoreCase(emailLogado)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Você não pode bloquear o seu próprio acesso.");
        }
        if (ate == null || !ate.isAfter(Instant.now())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe uma data de liberação no futuro.");
        }
        u.setBloqueadoAte(ate);
        u.setMotivoBloqueio(motivo == null || motivo.isBlank() ? "Bloqueio administrativo" : motivo.trim());
        u.setBloqueadoPor(emailLogado);
        u.setTentativasFalhas(0);
        refreshTokens.revogarTodos(u.getId());
        sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.USUARIO_BLOQUEADO, emailLogado, null);
        return u;
    }

    /** Bloqueio permanente = desativar o usuário (mantendo pelo menos um administrador ativo). */
    @Transactional
    public Usuario desativar(Long id, String motivo, String emailLogado) {
        Usuario u = buscar(id);
        if (u.getEmail().equalsIgnoreCase(emailLogado)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Você não pode desativar a si mesmo.");
        }
        garantirOutroAdmin(u, u.getPerfil(), false);
        u.setAtivo(false);
        u.setMotivoBloqueio(motivo == null || motivo.isBlank() ? null : motivo.trim());
        u.setBloqueadoPor(emailLogado);
        refreshTokens.revogarTodos(u.getId());
        sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.USUARIO_DESATIVADO, emailLogado, null);
        return u;
    }

    @Transactional
    public void excluir(Long id, String emailLogado) {
        Usuario u = buscar(id);
        if (u.getEmail().equalsIgnoreCase(emailLogado)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Você não pode excluir o seu próprio usuário.");
        }
        garantirOutroAdmin(u, null, false);
        sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.USUARIO_EXCLUIDO, emailLogado, null);
        repository.delete(u);
    }

    @Transactional
    public Usuario atualizarNome(String email, String nome) {
        return atualizarMe(email, nome, null);
    }

    /** Minha Conta: nome e cadastro do próprio usuário (e-mail e perfil só o administrador altera). */
    @Transactional
    public Usuario atualizarMe(String email, String nome, DadosCadastro cadastro) {
        Usuario u = porEmail(email);
        u.setNome(nome.trim());
        if (cadastro != null) {
            aplicarCadastro(u, cadastro);
        }
        return u;
    }

    @Transactional
    public void trocarSenha(String email, String senhaAtual, String novaSenha) {
        Usuario u = porEmail(email);
        if (!encoder.matches(senhaAtual, u.getSenhaHash())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "A senha atual não confere.");
        }
        if (senhaInicial.ehSenhaInicial(novaSenha) || encoder.matches(novaSenha, u.getSenhaHash())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "A nova senha deve ser diferente da atual.");
        }
        u.setSenhaHash(encoder.encode(novaSenha));
        refreshTokens.revogarTodos(u.getId());
        sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.SENHA_ALTERADA, email, null);
    }

    @Transactional(readOnly = true)
    public Usuario porEmail(String email) {
        return repository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    /** Grava o cadastro complementar; texto vazio vira nulo, telefone e CEP só com dígitos. */
    private static void aplicarCadastro(Usuario u, DadosCadastro c) {
        if (c == null) {
            return;
        }
        String telefone = digitos(c.telefone());
        if (telefone != null && (telefone.length() < 10 || telefone.length() > 13)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Telefone inválido. Informe DDD e número.");
        }
        String cep = digitos(c.cep());
        if (cep != null && cep.length() != 8) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CEP inválido. Informe os 8 dígitos.");
        }
        u.setTelefone(telefone);
        u.setCargo(texto(c.cargo()));
        u.setFotoPerfil(texto(c.fotoPerfil()));
        u.setCep(cep);
        u.setEnderecoTipo(texto(c.enderecoTipo()) == null ? null : c.enderecoTipo().trim().toUpperCase());
        u.setEndereco(texto(c.endereco()));
        u.setNumero(texto(c.numero()));
        u.setComplemento(texto(c.complemento()));
        u.setBairro(texto(c.bairro()));
        u.setCidade(texto(c.cidade()));
        u.setUf(texto(c.uf()) == null ? null : c.uf().trim().toUpperCase());
        u.setObservacoes(texto(c.observacoes()));
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String digitos(String v) {
        if (v == null) {
            return null;
        }
        String d = v.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    /** Impede que o sistema fique sem nenhum administrador ativo. */
    private void garantirOutroAdmin(Usuario u, Perfil novoPerfil, boolean continuaAtivo) {
        boolean eraAdminAtivo = u.getPerfil() == Perfil.ADMIN && u.isAtivo();
        boolean continuaAdmin = novoPerfil == Perfil.ADMIN && continuaAtivo;
        if (eraAdminAtivo && !continuaAdmin && repository.countByPerfilAndAtivoTrue(Perfil.ADMIN) <= 1) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "É preciso manter pelo menos um administrador ativo.");
        }
    }
}

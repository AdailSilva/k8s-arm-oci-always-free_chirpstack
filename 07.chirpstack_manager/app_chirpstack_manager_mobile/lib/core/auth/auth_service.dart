import 'dart:async';

import 'package:chirpstack_manager/core/auth/auth_modelos.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';

/// Login, renovação, troca de senha e saída — o mesmo fluxo do painel web.
///
/// O refresh token da API vai no cookie `csm_refresh` (HttpOnly no navegador). No app ele é lido
/// do Set-Cookie do login/refresh, guardado na [Sessao] e reenviado no cabeçalho Cookie.
class AuthService {
  AuthService._();

  static final AuthService instancia = AuthService._();

  final ApiClient _api = ApiClient.instancia;
  final Sessao _sessao = Sessao.instancia;

  Future<void>? _renovando;

  String? _cookie() {
    final r = _sessao.refreshToken;
    return r == null ? null : '$cookieRefresh=$r';
  }

  Future<RespostaAuth> login(String email, String senha, {required bool lembrar}) async {
    final r = await _api.bruta('POST', '/api/v1/auth/login', corpo: {'email': email, 'senha': senha});
    final resp = RespostaAuth.deJson(comoMapa(r.corpo), refreshToken: refreshDoSetCookie(r.cabecalhos['set-cookie']));
    await _sessao.iniciar(resp, lembrar: lembrar);
    return resp;
  }

  /// Renova o token de acesso com o refresh (uma renovação por vez, mesmo com várias telas pedindo).
  Future<void> renovar() {
    return _renovando ??= _renovarAgora().whenComplete(() => _renovando = null);
  }

  Future<void> _renovarAgora() async {
    final cookie = _cookie();
    if (cookie == null) throw ApiException(status: 401, mensagem: 'Sua sessão expirou. Entre novamente.');
    final r = await _api.bruta('POST', '/api/v1/auth/refresh', cookie: cookie);
    final resp = RespostaAuth.deJson(comoMapa(r.corpo), refreshToken: refreshDoSetCookie(r.cabecalhos['set-cookie']));
    await _sessao.iniciar(resp);
  }

  /// Troca de senha (inclusive a obrigatória do primeiro acesso). A API encerra a sessão: o
  /// usuário entra de novo com a senha nova.
  Future<void> trocarSenha(String senhaAtual, String novaSenha) async {
    await _api.post('/api/v1/auth/me/senha', corpo: {'senhaAtual': senhaAtual, 'novaSenha': novaSenha});
    await _sessao.encerrar();
  }

  Future<void> esqueciSenha(String email) async {
    await _api.bruta('POST', '/api/v1/auth/senha/esqueci', corpo: {'email': email});
  }

  Future<UsuarioSessao> atualizarNome(String nome) async {
    final j = comoMapa(await _api.put('/api/v1/auth/me', corpo: {'nome': nome}));
    final u = UsuarioSessao.deJson(j);
    await _sessao.atualizarUsuario(u);
    return u;
  }

  /// Reenvia o link de confirmação do e-mail (ou o convite). A API responde sempre igual.
  Future<void> reenviarConfirmacao(String email) async {
    await _api.bruta('POST', '/api/v1/auth/email/reenviar', corpo: {'email': email});
  }

  /// Dados completos do usuário logado (inclui o cadastro: foto, contato e endereço).
  Future<Json> me() async => comoMapa(await _api.get('/api/v1/auth/me'));

  /// Minha Conta: nome e cadastro (contato e endereço; a foto atual é mantida).
  Future<Json> atualizarMe(String nome, Json cadastro) async {
    final j = comoMapa(await _api.put('/api/v1/auth/me', corpo: {'nome': nome, 'cadastro': cadastro}));
    await _sessao.atualizarUsuario(UsuarioSessao.deJson(j));
    return j;
  }

  /// Sai do sistema: avisa a API (encerra a sessão e o refresh) e limpa o aparelho. [motivo]:
  /// LOGOUT (padrão), INATIVIDADE ou FORA_HORARIO; [aviso] aparece na tela de login.
  Future<void> sair({String? motivo, String? aviso}) async {
    final token = _sessao.token;
    final cookie = _cookie();
    await _sessao.encerrar();
    try {
      await _api.bruta('POST', '/api/v1/auth/logout',
          corpo: motivo == null ? null : {'motivo': motivo}, token: token, cookie: cookie);
    } catch (_) {
      // Sem rede: a sessão já foi apagada do aparelho; o servidor a encerra por inatividade.
    }
    Navegacao.irParaLogin(aviso: aviso);
  }

  /// Sessão encerrada ou expirada no servidor: limpa o aparelho e volta ao login com o motivo.
  Future<void> encerrarComMotivo(String? motivo) async {
    if (!_sessao.autenticado && _sessao.refreshToken == null) return;
    await _sessao.encerrar();
    Navegacao.irParaLogin(aviso: motivo ?? 'Sua sessão foi encerrada. Entre novamente.');
  }
}

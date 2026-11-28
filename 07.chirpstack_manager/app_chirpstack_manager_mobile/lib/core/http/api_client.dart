import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:http/http.dart' as http;

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/config/ambiente.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';

/// Cabeçalho com que a API avisa que a sessão foi encerrada no servidor (SessaoAtivaFilter).
const String headerStatusSessao = 'x-sessao-status';

/// Erro da API no formato ApiError {status, erro, mensagem, campos}.
class ApiException implements Exception {
  ApiException({required this.status, this.codigo, this.mensagem, this.campos = const {}, this.sessaoEncerrada = false});

  final int status;
  final String? codigo;
  final String? mensagem;
  final Map<String, String> campos;
  final bool sessaoEncerrada;

  bool get semConexao => status == 0;

  @override
  String toString() => mensagem ?? 'Erro $status';
}

/// Mensagem para o usuário a partir de qualquer erro.
String mensagemDeErro(Object erro, [String padrao = 'Não foi possível concluir a operação.']) {
  if (erro is ApiException) {
    if (erro.semConexao) {
      return erro.mensagem ?? 'Sem comunicação com o servidor. Verifique sua conexão e tente novamente.';
    }
    final m = erro.mensagem;
    if (m != null && m.trim().isNotEmpty) return m;
  }
  return padrao;
}

/// Resposta bruta (corpo decodificado + cabeçalhos), usada pelo login/refresh para ler o Set-Cookie.
class RespostaBruta {
  const RespostaBruta(this.corpo, this.cabecalhos);

  final dynamic corpo;
  final Map<String, String> cabecalhos;
}

/// Cliente da API do ChirpStack Manager.
///
/// Envia o token de acesso, renova a sessão uma vez num 401 (refresh pelo cookie) e trata a
/// sessão encerrada no servidor e a troca obrigatória da senha.
class ApiClient {
  ApiClient._();

  static final ApiClient instancia = ApiClient._();

  http.Client _http = http.Client();

  /// Troca o cliente HTTP (testes de tela com respostas simuladas).
  @visibleForTesting
  set clienteHttp(http.Client cliente) => _http = cliente;
  static const Duration _tempoPadrao = Duration(seconds: 30);
  static const String _prefixoAuth = '/api/v1/auth/';

  String get base => Ambiente.apiUrl;

  static String get userAgent =>
      Platform.isIOS ? 'ChirpStackManager/Mobile (iPhone; iOS)' : 'ChirpStackManager/Mobile (Linux; Android)';

  Uri uri(String caminho, [Map<String, Object?>? query]) {
    final q = <String, String>{};
    query?.forEach((k, v) {
      if (v == null) return;
      final s = v.toString();
      if (s.isEmpty) return;
      q[k] = s;
    });
    final u = Uri.parse('$base$caminho');
    return q.isEmpty ? u : u.replace(queryParameters: q);
  }

  Map<String, String> cabecalhos({bool comCorpo = false, String? token, String? cookie}) {
    final h = <String, String>{'Accept': 'application/json', 'User-Agent': userAgent};
    if (comCorpo) h['Content-Type'] = 'application/json; charset=utf-8';
    if (token != null && token.isNotEmpty) h['Authorization'] = 'Bearer $token';
    if (cookie != null && cookie.isNotEmpty) h['Cookie'] = cookie;
    return h;
  }

  Future<dynamic> get(String caminho, {Map<String, Object?>? query}) => _enviar('GET', caminho, query: query);

  Future<dynamic> post(String caminho, {Object? corpo, Map<String, Object?>? query}) =>
      _enviar('POST', caminho, corpo: corpo ?? const <String, dynamic>{}, query: query);

  Future<dynamic> put(String caminho, {Object? corpo}) =>
      _enviar('PUT', caminho, corpo: corpo ?? const <String, dynamic>{});

  Future<dynamic> delete(String caminho) => _enviar('DELETE', caminho);

  /// Requisição sem renovação automática, devolvendo também os cabeçalhos (login, refresh, logout).
  Future<RespostaBruta> bruta(String metodo, String caminho, {Object? corpo, String? token, String? cookie}) async {
    final resp = await _executar(metodo, caminho, corpo: corpo, token: token, cookie: cookie);
    if (resp.statusCode >= 200 && resp.statusCode < 300) return RespostaBruta(_decodificar(resp), resp.headers);
    throw _erroDe(resp);
  }

  Future<http.Response> _executar(String metodo, String caminho,
      {Object? corpo, Map<String, Object?>? query, String? token, String? cookie}) async {
    try {
      final req = http.Request(metodo, uri(caminho, query));
      req.headers.addAll(cabecalhos(comCorpo: corpo != null, token: token, cookie: cookie));
      if (corpo != null) req.body = jsonEncode(corpo);
      final streamed = await _http.send(req).timeout(_tempoPadrao);
      return await http.Response.fromStream(streamed);
    } on TimeoutException {
      throw ApiException(status: 0, mensagem: 'O servidor demorou a responder. Tente novamente.');
    } catch (_) {
      throw ApiException(status: 0);
    }
  }

  Future<dynamic> _enviar(String metodo, String caminho,
      {Object? corpo, Map<String, Object?>? query, bool repetida = false}) async {
    final sessao = Sessao.instancia;
    final tokenEnviado = sessao.token;
    final resp = await _executar(metodo, caminho, corpo: corpo, query: query, token: tokenEnviado);

    if (resp.statusCode >= 200 && resp.statusCode < 300) return _decodificar(resp);

    final erro = _erroDe(resp);

    // Senha inicial ainda não trocada: a API recusa tudo, menos a própria troca.
    if (erro.status == 403 && erro.codigo == 'TROCA_SENHA_OBRIGATORIA') {
      Navegacao.irParaTrocaSenha();
      throw erro;
    }
    if (erro.status != 401 || caminho.startsWith(_prefixoAuth) && caminho != '/api/v1/auth/me') throw erro;

    // Sessão encerrada no servidor (inatividade, administrador, outra entrada...): não renova.
    if (erro.sessaoEncerrada) {
      await AuthService.instancia.encerrarComMotivo(erro.mensagem);
      throw erro;
    }
    if (repetida) throw erro;
    try {
      await AuthService.instancia.renovar();
    } on ApiException catch (e) {
      await AuthService.instancia.encerrarComMotivo(
          e.sessaoEncerrada ? e.mensagem : 'Sua sessão expirou. Entre novamente.');
      throw erro;
    }
    return _enviar(metodo, caminho, corpo: corpo, query: query, repetida: true);
  }

  dynamic _decodificar(http.Response resp) {
    if (resp.bodyBytes.isEmpty) return null;
    final texto = utf8.decode(resp.bodyBytes, allowMalformed: true);
    if (texto.trim().isEmpty) return null;
    try {
      return jsonDecode(texto);
    } catch (_) {
      return texto;
    }
  }

  ApiException _erroDe(http.Response resp) {
    Json corpo = <String, dynamic>{};
    try {
      corpo = comoMapa(_decodificar(resp));
    } catch (_) {
      // Corpo que não é JSON (página de erro do proxy): fica a mensagem padrão.
    }
    final campos = <String, String>{};
    for (final c in corpo.lista('campos')) {
      final nome = c.texto('campo');
      final msg = c.texto('mensagem');
      if (nome != null && msg != null) campos[nome] = msg;
    }
    final codigo = corpo.texto('erro');
    String? mensagem = corpo.texto('mensagem');
    if ((mensagem == null || mensagem.trim().isEmpty) && resp.statusCode >= 500) {
      mensagem = 'O servidor encontrou um problema (${resp.statusCode}). Tente novamente em instantes.';
    }
    if ((mensagem == null || mensagem.trim().isEmpty) && resp.statusCode == 403) {
      mensagem = 'Você não tem permissão para esta operação.';
    }
    final statusSessao = (resp.headers[headerStatusSessao] ?? '').toUpperCase();
    return ApiException(
      status: resp.statusCode,
      codigo: codigo,
      mensagem: mensagem,
      campos: campos,
      sessaoEncerrada: statusSessao == 'ENCERRADA' || codigo == 'SESSAO_ENCERRADA',
    );
  }
}

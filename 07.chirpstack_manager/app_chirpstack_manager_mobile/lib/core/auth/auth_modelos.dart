import 'dart:convert';

import 'package:chirpstack_manager/core/http/json.dart';

/// Nome do cookie do refresh token emitido pela API (AuthController.COOKIE).
const String cookieRefresh = 'csm_refresh';

/// Usuário logado (UsuarioResponse da API).
class UsuarioSessao {
  const UsuarioSessao({required this.id, required this.nome, required this.email, required this.perfil});

  final int id;
  final String nome;
  final String email;

  /// ADMIN, OPERADOR ou VISUALIZADOR.
  final String perfil;

  bool get admin => perfil == 'ADMIN';
  bool get podeAlterar => perfil == 'ADMIN' || perfil == 'OPERADOR';

  String get perfilRotulo => switch (perfil) {
        'ADMIN' => 'Administrador',
        'OPERADOR' => 'Operador',
        'VISUALIZADOR' => 'Visualizador',
        _ => perfil,
      };

  String get iniciais {
    final partes = nome.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (partes.isEmpty) return email.isEmpty ? '?' : email[0].toUpperCase();
    final primeira = partes.first[0];
    final ultima = partes.length > 1 ? partes.last[0] : '';
    return (primeira + ultima).toUpperCase();
  }

  factory UsuarioSessao.deJson(Json j) => UsuarioSessao(
        id: j.inteiro('id') ?? 0,
        nome: j.texto('nome') ?? '',
        email: j.texto('email') ?? '',
        perfil: j.texto('perfil') ?? 'VISUALIZADOR',
      );

  Json paraJson() => {'id': id, 'nome': nome, 'email': email, 'perfil': perfil};

  String paraTexto() => jsonEncode(paraJson());
}

/// Resposta do login e do refresh (TokenResponse) + o refresh token lido do cookie.
class RespostaAuth {
  const RespostaAuth({
    required this.token,
    required this.refreshToken,
    required this.usuario,
    required this.trocaSenhaObrigatoria,
  });

  final String token;
  final String? refreshToken;
  final UsuarioSessao usuario;
  final bool trocaSenhaObrigatoria;

  factory RespostaAuth.deJson(Json j, {String? refreshToken}) => RespostaAuth(
        token: j.texto('accessToken') ?? '',
        refreshToken: refreshToken,
        usuario: UsuarioSessao.deJson(j.mapa('usuario')),
        trocaSenhaObrigatoria: j.logico('trocaSenhaObrigatoria'),
      );
}

/// Extrai o refresh token do cabeçalho Set-Cookie da API.
///
/// O pacote http junta vários Set-Cookie numa linha separada por vírgula; o valor do token não
/// tem vírgula nem ponto e vírgula. Devolve null se o cookie não veio ou veio vazio (apagado).
String? refreshDoSetCookie(String? setCookie) {
  if (setCookie == null || setCookie.isEmpty) return null;
  final m = RegExp('(?:^|[,;\\s])$cookieRefresh=([^;,\\s]*)').firstMatch(setCookie);
  final valor = m?.group(1);
  return (valor == null || valor.isEmpty) ? null : valor;
}

/// Validade do token de acesso (claim exp do JWT), sem verificar a assinatura.
DateTime? expiracaoDoToken(String? token) {
  if (token == null) return null;
  final partes = token.split('.');
  if (partes.length != 3) return null;
  try {
    final carga = comoMapa(jsonDecode(utf8.decode(base64Url.decode(base64Url.normalize(partes[1])))));
    final exp = carga.inteiro('exp');
    return exp == null ? null : DateTime.fromMillisecondsSinceEpoch(exp * 1000);
  } catch (_) {
    return null;
  }
}

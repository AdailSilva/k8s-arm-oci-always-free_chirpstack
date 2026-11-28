import 'package:flutter/material.dart';

/// Rotas nomeadas do app.
class Rotas {
  Rotas._();

  static const String login = '/login';
  static const String recuperarSenha = '/recuperar-senha';
  static const String trocarSenha = '/trocar-senha';
  static const String inicio = '/';
  static const String telaBloqueada = '/tela-bloqueada';
  static const String meuPerfil = '/meu-perfil';
  static const String suporte = '/suporte';
}

/// Argumentos da tela de login: aviso a mostrar ao abrir (sessão expirada ou encerrada).
class ArgumentosLogin {
  const ArgumentosLogin({this.aviso});

  final String? aviso;
}

/// Navegação global (fora de um BuildContext): usada pela camada HTTP e pelo monitor de sessão.
class Navegacao {
  Navegacao._();

  static final GlobalKey<NavigatorState> chave = GlobalKey<NavigatorState>();
  static final GlobalKey<ScaffoldMessengerState> mensageiro = GlobalKey<ScaffoldMessengerState>();

  static bool _indoParaLogin = false;

  /// Volta ao login limpando a pilha, com o aviso opcional.
  static void irParaLogin({String? aviso}) {
    final nav = chave.currentState;
    if (nav == null || _indoParaLogin) return;
    _indoParaLogin = true;
    nav.pushNamedAndRemoveUntil(Rotas.login, (_) => false, arguments: ArgumentosLogin(aviso: aviso));
    WidgetsBinding.instance.addPostFrameCallback((_) => _indoParaLogin = false);
  }

  static void irParaTrocaSenha() {
    final nav = chave.currentState;
    if (nav == null) return;
    nav.pushNamedAndRemoveUntil(Rotas.trocarSenha, (_) => false);
  }

  /// Tela bloqueada: esconde o app até a senha ser digitada (a sessão continua).
  static void irParaBloqueio() {
    final nav = chave.currentState;
    if (nav == null) return;
    nav.pushNamedAndRemoveUntil(Rotas.telaBloqueada, (_) => false);
  }

  static void avisar(String texto) {
    mensageiro.currentState
      ?..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(texto), behavior: SnackBarBehavior.floating));
  }
}

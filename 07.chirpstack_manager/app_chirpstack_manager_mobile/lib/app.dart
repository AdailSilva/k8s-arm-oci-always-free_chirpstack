import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'package:chirpstack_manager/core/auth/bloqueio_tela.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/config/ambiente.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/sessao/sessao_monitor.dart';
import 'package:chirpstack_manager/core/tema/tema_controller.dart';
import 'package:chirpstack_manager/core/ui/tema_app.dart';
import 'package:chirpstack_manager/features/auth/login_page.dart';
import 'package:chirpstack_manager/features/auth/recuperar_senha_page.dart';
import 'package:chirpstack_manager/features/auth/tela_bloqueada_page.dart';
import 'package:chirpstack_manager/features/auth/trocar_senha_page.dart';
import 'package:chirpstack_manager/features/conta/meu_perfil_page.dart';
import 'package:chirpstack_manager/features/conta/suporte_page.dart';
import 'package:chirpstack_manager/features/shell/shell_page.dart';

/// App ChirpStack Manager: rotas, tema e o registro de atividade da sessão.
class ChirpStackManagerApp extends StatelessWidget {
  const ChirpStackManagerApp({super.key, this.avisoInicial});

  /// Aviso para o login na abertura (ex.: a sessão lembrada expirou).
  final String? avisoInicial;

  String get _rotaInicial {
    final s = Sessao.instancia;
    if (!s.autenticado) return Rotas.login;
    if (s.trocarSenha) return Rotas.trocarSenha;
    if (BloqueioTela.instancia.bloqueada) return Rotas.telaBloqueada;
    return Rotas.inicio;
  }

  Route<dynamic> _rota(RouteSettings settings) {
    final s = Sessao.instancia;
    var nome = settings.name ?? Rotas.inicio;
    var argumentos = settings.arguments;
    // Telas que exigem sessão
    const logadas = {Rotas.inicio, Rotas.trocarSenha, Rotas.telaBloqueada, Rotas.meuPerfil, Rotas.suporte};
    if (logadas.contains(nome) && !s.autenticado) {
      nome = Rotas.login;
    } else if (nome != Rotas.trocarSenha && logadas.contains(nome) && s.trocarSenha) {
      nome = Rotas.trocarSenha;
    } else if (logadas.contains(nome) && nome != Rotas.trocarSenha && BloqueioTela.instancia.bloqueada) {
      // Tela bloqueada: nenhuma tela do sistema abre antes da senha.
      nome = Rotas.telaBloqueada;
    } else if (nome == Rotas.telaBloqueada && !BloqueioTela.instancia.bloqueada) {
      nome = Rotas.inicio;
    }
    final Widget pagina = switch (nome) {
      Rotas.login => LoginPage(argumentos: argumentos is ArgumentosLogin ? argumentos : const ArgumentosLogin()),
      Rotas.recuperarSenha => RecuperarSenhaPage(emailInicial: argumentos is String ? argumentos : null),
      Rotas.trocarSenha => const TrocarSenhaPage(),
      Rotas.telaBloqueada => const TelaBloqueadaPage(),
      Rotas.meuPerfil => const MeuPerfilPage(),
      Rotas.suporte => const SuportePage(),
      _ => const ShellPage(),
    };
    return MaterialPageRoute<dynamic>(builder: (_) => pagina, settings: RouteSettings(name: nome, arguments: argumentos));
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: TemaController.instancia,
      builder: (context, _) => MaterialApp(
        title: Ambiente.nomeApp,
        debugShowCheckedModeBanner: false,
        navigatorKey: Navegacao.chave,
        scaffoldMessengerKey: Navegacao.mensageiro,
        theme: construirTema(Brightness.light),
        darkTheme: construirTema(Brightness.dark),
        themeMode: TemaController.instancia.modo,
        locale: const Locale('pt', 'BR'),
        supportedLocales: const [Locale('pt', 'BR')],
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        initialRoute: _rotaInicial,
        onGenerateInitialRoutes: (nome) => [
          _rota(RouteSettings(name: nome, arguments: nome == Rotas.login ? ArgumentosLogin(aviso: avisoInicial) : null)),
        ],
        onGenerateRoute: _rota,
        // Todo toque conta como atividade da sessão (POST /sessao/atividade).
        builder: (context, child) => Listener(
          behavior: HitTestBehavior.translucent,
          onPointerDown: (_) => SessaoMonitor.instancia.registrarInteracao(),
          child: MediaQuery.withClampedTextScaling(maxScaleFactor: 1.15, child: child ?? const SizedBox.shrink()),
        ),
      ),
    );
  }
}

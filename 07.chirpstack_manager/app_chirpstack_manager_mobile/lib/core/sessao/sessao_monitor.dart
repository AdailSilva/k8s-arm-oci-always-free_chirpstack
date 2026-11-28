import 'dart:async';

import 'package:flutter/widgets.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';

/// Sinal de atividade da sessão (POST /api/v1/sessao/atividade), como o monitor do painel web.
///
/// A API mede a inatividade pelo último sinal com `ativo=true`: sem ele, a sessão seria encerrada
/// por inatividade mesmo com o usuário usando o app. Com o app aberto, envia um sinal por minuto,
/// `ativo` só se houve toque desde o anterior. Em segundo plano, para.
class SessaoMonitor with WidgetsBindingObserver {
  SessaoMonitor._();

  static final SessaoMonitor instancia = SessaoMonitor._();

  static const Duration intervalo = Duration(seconds: 60);

  Timer? _relogio;
  bool _interagiu = true;
  bool _emPrimeiroPlano = true;

  void instalar() {
    WidgetsBinding.instance.addObserver(this);
    Sessao.instancia.addListener(_reavaliar);
    _reavaliar();
  }

  /// Chamado a cada toque na tela (Listener no topo do app).
  void registrarInteracao() => _interagiu = true;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _emPrimeiroPlano = state == AppLifecycleState.resumed;
    if (_emPrimeiroPlano) _interagiu = true;
    _reavaliar();
    if (_emPrimeiroPlano) unawaited(_sinalizar());
  }

  void _reavaliar() {
    final ativo = _emPrimeiroPlano && Sessao.instancia.autenticado && !Sessao.instancia.trocarSenha;
    if (ativo && _relogio == null) {
      _relogio = Timer.periodic(intervalo, (_) => unawaited(_sinalizar()));
    } else if (!ativo) {
      _relogio?.cancel();
      _relogio = null;
    }
  }

  Future<void> _sinalizar() async {
    if (!Sessao.instancia.autenticado || Sessao.instancia.trocarSenha) return;
    final ativo = _interagiu;
    _interagiu = false;
    try {
      await ApiClient.instancia.post('/api/v1/sessao/atividade', corpo: {'ativo': ativo});
    } catch (_) {
      // Sem rede ou sessão encerrada: o ApiClient já leva ao login quando é o caso.
    }
  }
}

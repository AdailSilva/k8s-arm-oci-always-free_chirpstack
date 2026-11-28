import 'dart:async';

import 'package:flutter/widgets.dart';

import 'package:chirpstack_manager/core/auth/bloqueio_tela.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';

/// Contador de alertas abertos (o sino do painel web): mostrado na aba Alertas. Atualiza a cada
/// minuto com o app em primeiro plano e a sessão ativa; falhas são silenciosas.
class Avisos extends ChangeNotifier with WidgetsBindingObserver {
  Avisos._();

  static final Avisos instancia = Avisos._();

  static const Duration intervalo = Duration(seconds: 60);

  Timer? _relogio;
  bool _primeiroPlano = true;
  int _total = 0;
  int _criticos = 0;

  int get total => _total;
  int get criticos => _criticos;
  String get rotulo => _total > 99 ? '99+' : '$_total';

  void instalar() {
    WidgetsBinding.instance.addObserver(this);
    Sessao.instancia.addListener(_reavaliar);
    BloqueioTela.instancia.addListener(_reavaliar);
    _reavaliar();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _primeiroPlano = state == AppLifecycleState.resumed;
    _reavaliar();
  }

  void _reavaliar() {
    final s = Sessao.instancia;
    final ativo = _primeiroPlano && s.autenticado && !s.trocarSenha && !BloqueioTela.instancia.bloqueada;
    if (ativo && _relogio == null) {
      unawaited(atualizar());
      _relogio = Timer.periodic(intervalo, (_) => unawaited(atualizar()));
    } else if (!ativo) {
      _relogio?.cancel();
      _relogio = null;
      if (!s.autenticado && (_total != 0 || _criticos != 0)) {
        _total = 0;
        _criticos = 0;
        notifyListeners();
      }
    }
  }

  Future<void> atualizar() async {
    if (!Sessao.instancia.autenticado) return;
    try {
      final r = comoMapa(await ApiClient.instancia.get('/api/v1/alertas', query: {'status': 'ABERTO', 'pagina': 0, 'tamanho': 50}));
      final lista = r.lista('result');
      final total = r.inteiro('totalCount') ?? lista.length;
      final criticos = lista.where((a) => a.texto('severidade') == 'CRITICO').length;
      if (total != _total || criticos != _criticos) {
        _total = total;
        _criticos = criticos;
        notifyListeners();
      }
    } catch (_) {
      // sem rede: o contador só deixa de atualizar
    }
  }
}

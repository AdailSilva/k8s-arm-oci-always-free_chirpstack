import 'dart:async';

import 'package:flutter/material.dart';

/// Atualização leve e periódica (~10 s) enquanto a tela está à vista: para quando outra tela a
/// cobre, quando outra aba está selecionada ou quando ela sai da árvore.
mixin AtualizacaoPeriodica<T extends StatefulWidget> on State<T> {
  Timer? _timer;
  bool _atualizando = false;

  Duration get intervaloAtualizacao => const Duration(seconds: 10);

  /// Índice desta tela no DefaultTabController acima dela (nulo = não está numa aba).
  int? get indiceAba => null;

  /// Recarrega sem indicador de carregamento.
  Future<void> atualizarEmSegundoPlano();

  void iniciarAtualizacaoPeriodica() {
    _timer?.cancel();
    _timer = Timer.periodic(intervaloAtualizacao, (_) => _tique());
  }

  bool get _visivel {
    if (!mounted || !TickerMode.of(context)) return false;
    final rota = ModalRoute.of(context);
    if (rota != null && !rota.isCurrent) return false;
    final aba = indiceAba;
    if (aba != null) {
      final abas = DefaultTabController.maybeOf(context);
      if (abas != null && abas.index != aba) return false;
    }
    return true;
  }

  Future<void> _tique() async {
    if (_atualizando || !_visivel) return;
    _atualizando = true;
    try {
      await atualizarEmSegundoPlano();
    } catch (_) {
      // Falha passageira: a próxima volta tenta de novo.
    } finally {
      _atualizando = false;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}

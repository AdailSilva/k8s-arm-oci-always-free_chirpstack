import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/storage/armazenamento.dart';

/// Tema claro, escuro ou do sistema, lembrado no aparelho.
class TemaController extends ChangeNotifier {
  TemaController._();

  static final TemaController instancia = TemaController._();

  ThemeMode _modo = ThemeMode.system;

  ThemeMode get modo => _modo;

  Future<void> carregar() async {
    _modo = switch (await Armazenamento.ler(ChavesArmazenamento.tema)) {
      'claro' => ThemeMode.light,
      'escuro' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
    notifyListeners();
  }

  Future<void> definir(ThemeMode modo) async {
    if (modo == _modo) return;
    _modo = modo;
    notifyListeners();
    await Armazenamento.gravar(ChavesArmazenamento.tema, switch (modo) {
      ThemeMode.light => 'claro',
      ThemeMode.dark => 'escuro',
      ThemeMode.system => null,
    });
  }
}

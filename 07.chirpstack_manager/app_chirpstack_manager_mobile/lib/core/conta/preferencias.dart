import 'dart:async';

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/tema/tema_controller.dart';

/// Preferências de aparência no servidor (GET/PUT /api/v1/conta/preferencias), as mesmas do painel
/// web. No app vale o modo escuro: ao entrar, o modo salvo do usuário é aplicado (claro ou escuro);
/// trocar para Claro ou Escuro em Conta grava no servidor, preservando tema, cor e menu do painel
/// web. "Sistema" é uma escolha só deste aparelho e não é enviada.
class Preferencias {
  Preferencias._();

  static final Preferencias instancia = Preferencias._();

  int? _usuarioAtual;
  bool _aplicando = false;
  ThemeMode _ultimoModo = ThemeMode.system;

  void instalar() {
    _ultimoModo = TemaController.instancia.modo;
    Sessao.instancia.addListener(_aoMudarSessao);
    TemaController.instancia.addListener(_aoMudarTema);
    _aoMudarSessao();
  }

  void _aoMudarSessao() {
    final s = Sessao.instancia;
    final id = s.autenticado && !s.trocarSenha ? s.usuario?.id : null;
    if (id == _usuarioAtual) return;
    _usuarioAtual = id;
    if (id != null) unawaited(_carregar());
  }

  Future<void> _carregar() async {
    try {
      final corpo = await ApiClient.instancia.get('/api/v1/conta/preferencias');
      if (corpo == null) return; // ainda sem preferências: vale a escolha deste aparelho
      final escuro = comoMapa(corpo).logico('darkTheme');
      _aplicando = true;
      await TemaController.instancia.definir(escuro ? ThemeMode.dark : ThemeMode.light);
    } catch (_) {
      // sem rede ou API antiga: segue com o tema do aparelho
    } finally {
      _aplicando = false;
      _ultimoModo = TemaController.instancia.modo;
    }
  }

  void _aoMudarTema() {
    final modo = TemaController.instancia.modo;
    if (modo == _ultimoModo) return;
    _ultimoModo = modo;
    if (_aplicando || modo == ThemeMode.system || !Sessao.instancia.autenticado) return;
    unawaited(_gravar(modo == ThemeMode.dark));
  }

  Future<void> _gravar(bool escuro) async {
    try {
      final atual = comoMapa(await ApiClient.instancia.get('/api/v1/conta/preferencias'));
      await ApiClient.instancia.put('/api/v1/conta/preferencias', corpo: {
        'preset': atual.texto('preset') ?? 'Aura',
        'primary': atual.texto('primary') ?? 'blue',
        'surface': atual.texto('surface'),
        'darkTheme': escuro,
        'menuMode': atual.texto('menuMode') ?? 'static',
      });
    } catch (_) {
      // sem rede: a escolha fica só neste aparelho
    }
  }
}

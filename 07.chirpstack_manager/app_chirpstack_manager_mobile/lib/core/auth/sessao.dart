import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'package:chirpstack_manager/core/auth/auth_modelos.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/storage/armazenamento.dart';

/// Sessão do usuário: token de acesso, refresh token (o cookie da API) e usuário.
///
/// Com "Lembre de mim" a sessão fica no cofre cifrado e sobrevive ao fechamento do app; sem ele,
/// fica só em memória.
class Sessao extends ChangeNotifier {
  Sessao._();

  static final Sessao instancia = Sessao._();

  String? _token;
  String? _refresh;
  UsuarioSessao? _usuario;
  bool _trocarSenha = false;
  bool _persistente = false;

  String? get token => _token;
  String? get refreshToken => _refresh;
  UsuarioSessao? get usuario => _usuario;
  bool get autenticado => _token != null && _usuario != null;
  bool get trocarSenha => _trocarSenha;
  DateTime? get expiraEm => expiracaoDoToken(_token);

  Future<void> carregar() async {
    final token = await Armazenamento.ler(ChavesArmazenamento.token);
    final refresh = await Armazenamento.ler(ChavesArmazenamento.refresh);
    final bruto = await Armazenamento.ler(ChavesArmazenamento.usuario);
    if (token == null || refresh == null || bruto == null) return;
    try {
      _usuario = UsuarioSessao.deJson(comoMapa(jsonDecode(bruto)));
      _token = token;
      _refresh = refresh;
      _persistente = true;
      notifyListeners();
    } catch (_) {
      await _apagarCofre();
    }
  }

  Future<void> iniciar(RespostaAuth resp, {bool? lembrar}) async {
    if (lembrar != null) _persistente = lembrar;
    _token = resp.token;
    _refresh = resp.refreshToken ?? _refresh;
    _usuario = resp.usuario;
    _trocarSenha = resp.trocaSenhaObrigatoria;
    notifyListeners();
    if (_persistente && !_trocarSenha) {
      await Armazenamento.gravar(ChavesArmazenamento.token, _token);
      await Armazenamento.gravar(ChavesArmazenamento.refresh, _refresh);
      await Armazenamento.gravar(ChavesArmazenamento.usuario, resp.usuario.paraTexto());
    } else {
      await _apagarCofre();
    }
  }

  Future<void> atualizarUsuario(UsuarioSessao usuario) async {
    _usuario = usuario;
    notifyListeners();
    if (_persistente) await Armazenamento.gravar(ChavesArmazenamento.usuario, usuario.paraTexto());
  }

  Future<void> encerrar() async {
    final havia = _token != null || _refresh != null || _usuario != null;
    _token = null;
    _refresh = null;
    _usuario = null;
    _trocarSenha = false;
    if (havia) notifyListeners();
    await _apagarCofre();
  }

  Future<void> _apagarCofre() async {
    await Armazenamento.remover(ChavesArmazenamento.token);
    await Armazenamento.remover(ChavesArmazenamento.refresh);
    await Armazenamento.remover(ChavesArmazenamento.usuario);
  }
}

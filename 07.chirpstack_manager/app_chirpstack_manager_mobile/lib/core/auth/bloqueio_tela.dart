import 'package:flutter/foundation.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/storage/armazenamento.dart';

/// Tela bloqueada — o bloquearTela()/desbloquear() do painel web.
///
/// "Bloquear tela" (em Conta) esconde o app sem encerrar a sessão: presença, horário de acesso e
/// renovação continuam valendo. Para voltar, a senha é conferida no servidor
/// (POST /api/v1/conta/desbloquear); 5 erros seguidos encerram a sessão.
///
/// A marca fica no cofre do aparelho: fechar e reabrir o app com a sessão lembrada volta à tela
/// bloqueada. Ela sai sozinha quando a sessão acaba ou troca de usuário.
class BloqueioTela extends ChangeNotifier {
  BloqueioTela._();

  static final BloqueioTela instancia = BloqueioTela._();

  String? _email;
  bool _instalado = false;

  bool get bloqueada => _email != null;

  Future<void> instalar() async {
    if (!_instalado) {
      _instalado = true;
      Sessao.instancia.addListener(_aoMudarSessao);
    }
    final salvo = await Armazenamento.ler(ChavesArmazenamento.telaBloqueada);
    if (salvo == null) return;
    if (_mesmoUsuario(salvo)) {
      _email = salvo;
      notifyListeners();
    } else {
      await Armazenamento.remover(ChavesArmazenamento.telaBloqueada);
    }
  }

  Future<void> bloquear() async {
    final email = Sessao.instancia.usuario?.email;
    if (!Sessao.instancia.autenticado || email == null || email.isEmpty) return;
    _email = email;
    notifyListeners();
    await Armazenamento.gravar(ChavesArmazenamento.telaBloqueada, email);
  }

  /// Confere a senha no servidor e destrava. Senha errada: ApiException 422 com a mensagem
  /// (quantas tentativas restam); a quinta encerra a sessão (o ApiClient leva ao login).
  Future<void> desbloquear(String senha) async {
    await ApiClient.instancia.post('/api/v1/conta/desbloquear', corpo: {'senha': senha});
    await liberar();
  }

  Future<void> liberar() async {
    final havia = _email != null;
    _email = null;
    if (havia) notifyListeners();
    await Armazenamento.remover(ChavesArmazenamento.telaBloqueada);
  }

  bool _mesmoUsuario(String email) {
    final atual = Sessao.instancia.usuario?.email;
    return Sessao.instancia.autenticado && atual != null && atual.toLowerCase() == email.toLowerCase();
  }

  /// Logout, expiração ou troca de usuário: o bloqueio deixa de valer.
  void _aoMudarSessao() {
    final e = _email;
    if (e == null || _mesmoUsuario(e)) return;
    liberar();
  }
}

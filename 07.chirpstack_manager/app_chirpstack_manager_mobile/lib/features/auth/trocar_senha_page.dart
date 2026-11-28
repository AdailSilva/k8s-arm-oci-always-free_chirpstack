import 'dart:convert';

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/dialogos.dart';
import 'package:chirpstack_manager/features/auth/tela_auth.dart';

/// Regra de senha forte da API (UsuarioDtos.SENHA_REGEX).
final RegExp senhaForte = RegExp(r'^(?=.*[A-Za-z])(?=.*\d).{8,72}$');
const String regraSenha = 'De 8 a 72 caracteres, com letras e números.';

/// Troca de senha — obrigatória no primeiro acesso (senha provisória) ou pedida em Conta.
/// Depois da troca a API encerra a sessão: o usuário entra de novo com a senha nova.
class TrocarSenhaPage extends StatefulWidget {
  const TrocarSenhaPage({super.key});

  @override
  State<TrocarSenhaPage> createState() => _TrocarSenhaPageState();
}

class _TrocarSenhaPageState extends State<TrocarSenhaPage> {
  final _atual = TextEditingController();
  final _nova = TextEditingController();
  final _confirma = TextEditingController();
  final _form = GlobalKey<FormState>();
  bool _salvando = false;
  bool _mostrar = false;
  String? _erro;

  bool get _obrigatoria => Sessao.instancia.trocarSenha;

  @override
  void dispose() {
    _atual.dispose();
    _nova.dispose();
    _confirma.dispose();
    super.dispose();
  }

  Future<void> _salvar() async {
    FocusScope.of(context).unfocus();
    if (!(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _salvando = true;
      _erro = null;
    });
    try {
      await AuthService.instancia.trocarSenha(_atual.text, _nova.text);
      Navegacao.irParaLogin(aviso: 'Senha alterada. Entre novamente com a senha nova.');
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  InputDecoration _decoracao(String dica) => InputDecoration(
        hintText: dica,
        prefixIcon: const Icon(Icons.key_outlined),
        suffixIcon: IconButton(
          tooltip: _mostrar ? 'Ocultar senhas' : 'Mostrar senhas',
          icon: Icon(_mostrar ? Icons.visibility_off_outlined : Icons.visibility_outlined),
          onPressed: () => setState(() => _mostrar = !_mostrar),
        ),
      );

  /// Retrato do formulário para a trava "Deseja realmente sair?".
  String _retrato() {
    return jsonEncode([_atual.text, _nova.text, _confirma.text]);
  }

  @override
  Widget build(BuildContext context) => TravaEdicao(retrato: _retrato, ativa: !_obrigatoria, child: Builder(builder: _construir));

  Widget _construir(BuildContext context) {
    return TelaAuth(
      titulo: _obrigatoria ? 'Crie a sua senha' : 'Trocar a senha',
      subtitulo: _obrigatoria
          ? 'Este é o seu primeiro acesso: troque a senha provisória para continuar.'
          : 'Depois da troca você entra de novo com a senha nova.',
      icone: Icons.password,
      voltar: !_obrigatoria,
      conteudo: Form(
        key: _form,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_erro != null) MensagemCartao(texto: _erro!),
            const RotuloCampo('Senha atual'),
            TextFormField(
              controller: _atual,
              obscureText: !_mostrar,
              textInputAction: TextInputAction.next,
              decoration: _decoracao('Sua senha atual'),
              validator: (v) => (v ?? '').isEmpty ? 'Informe a senha atual.' : null,
            ),
            const SizedBox(height: 16),
            const RotuloCampo('Nova senha'),
            TextFormField(
              controller: _nova,
              obscureText: !_mostrar,
              textInputAction: TextInputAction.next,
              decoration: _decoracao('Letras e números, 8+'),
              validator: (v) {
                final s = v ?? '';
                if (!senhaForte.hasMatch(s)) return regraSenha;
                if (s == _atual.text) return 'A nova senha precisa ser diferente da atual.';
                return null;
              },
            ),
            const SizedBox(height: 16),
            const RotuloCampo('Confirme a nova senha'),
            TextFormField(
              controller: _confirma,
              obscureText: !_mostrar,
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (_) => _salvar(),
              decoration: _decoracao('Repita a nova senha'),
              validator: (v) => v == _nova.text ? null : 'As senhas não conferem.',
            ),
            const SizedBox(height: 8),
            Text(regraSenha, style: TextStyle(fontSize: 12.5, color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: 18),
            FilledButton(onPressed: _salvando ? null : _salvar, child: Text(_salvando ? 'Salvando…' : 'Salvar nova senha')),
            if (_obrigatoria) ...[
              const SizedBox(height: 8),
              TextButton(onPressed: () => AuthService.instancia.sair(), child: const Text('Sair')),
            ],
          ],
        ),
      ),
    );
  }
}

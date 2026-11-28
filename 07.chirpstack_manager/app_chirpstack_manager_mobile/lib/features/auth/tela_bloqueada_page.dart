import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/auth/bloqueio_tela.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/features/auth/tela_auth.dart';

/// Tela bloqueada (o mesmo do painel web): a sessão continua, mas o app só volta com a senha,
/// conferida no servidor. 5 erros seguidos encerram a sessão. "Entrar com outra conta" sai.
class TelaBloqueadaPage extends StatefulWidget {
  const TelaBloqueadaPage({super.key});

  @override
  State<TelaBloqueadaPage> createState() => _TelaBloqueadaPageState();
}

class _TelaBloqueadaPageState extends State<TelaBloqueadaPage> {
  final _senha = TextEditingController();
  bool _mostrar = false;
  bool _carregando = false;
  String? _erro;

  @override
  void dispose() {
    _senha.dispose();
    super.dispose();
  }

  Future<void> _desbloquear() async {
    FocusScope.of(context).unfocus();
    if (_senha.text.isEmpty || _carregando) return;
    setState(() {
      _carregando = true;
      _erro = null;
    });
    try {
      await BloqueioTela.instancia.desbloquear(_senha.text);
      if (!mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil(Rotas.inicio, (_) => false);
    } on ApiException catch (e) {
      // 401: a sessão foi encerrada (5 erros, administrador, horário): o ApiClient já foi ao login.
      if (!mounted || e.status == 401) return;
      setState(() {
        _carregando = false;
        _senha.clear();
        _erro = mensagemDeErro(e, 'Não foi possível desbloquear. Confira a senha.');
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _carregando = false;
          _erro = mensagemDeErro(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final u = Sessao.instancia.usuario;
    final cs = Theme.of(context).colorScheme;
    return PopScope(
      canPop: false,
      child: TelaAuth(
        titulo: 'Tela bloqueada',
        subtitulo: 'Digite sua senha para continuar de onde parou.',
        icone: Icons.lock_outline,
        voltar: false,
        conteudo: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                border: Border.all(color: cs.outlineVariant),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Row(children: [
                CircleAvatar(
                  radius: 22,
                  backgroundColor: CmCores.azul600,
                  child: Text(u?.iniciais ?? '?', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(u?.nome ?? '—', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
                    Text(u?.email ?? '', maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13)),
                  ]),
                ),
              ]),
            ),
            const SizedBox(height: 18),
            if (_erro != null) MensagemCartao(texto: _erro!),
            const RotuloCampo('Senha'),
            TextField(
              controller: _senha,
              obscureText: !_mostrar,
              autofocus: true,
              autofillHints: const [AutofillHints.password],
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _desbloquear(),
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: 'Sua senha de acesso',
                prefixIcon: const Icon(Icons.key_outlined),
                suffixIcon: IconButton(
                  tooltip: _mostrar ? 'Ocultar senha' : 'Mostrar senha',
                  icon: Icon(_mostrar ? Icons.visibility_off_outlined : Icons.visibility_outlined),
                  onPressed: () => setState(() => _mostrar = !_mostrar),
                ),
              ),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: _carregando || _senha.text.isEmpty ? null : _desbloquear,
              icon: const Icon(Icons.lock_open),
              label: Text(_carregando ? 'Conferindo…' : 'Desbloquear'),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: _carregando ? null : () => AuthService.instancia.sair(),
              child: const Text('Não é você? Entrar com outra conta', textAlign: TextAlign.center),
            ),
          ],
        ),
      ),
    );
  }
}

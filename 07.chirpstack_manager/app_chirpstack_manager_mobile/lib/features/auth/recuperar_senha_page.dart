import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/features/auth/tela_auth.dart';

/// "Esqueceu a senha?": a API responde sempre do mesmo jeito, exista ou não o e-mail.
class RecuperarSenhaPage extends StatefulWidget {
  const RecuperarSenhaPage({super.key, this.emailInicial});

  final String? emailInicial;

  @override
  State<RecuperarSenhaPage> createState() => _RecuperarSenhaPageState();
}

class _RecuperarSenhaPageState extends State<RecuperarSenhaPage> {
  late final TextEditingController _email = TextEditingController(text: widget.emailInicial ?? '');
  final _form = GlobalKey<FormState>();
  bool _enviando = false;
  bool _enviado = false;
  String? _erro;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    FocusScope.of(context).unfocus();
    if (!(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _enviando = true;
      _erro = null;
    });
    try {
      await AuthService.instancia.esqueciSenha(_email.text.trim());
      if (mounted) setState(() => _enviado = true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return TelaAuth(
      titulo: 'Recuperar a senha',
      subtitulo: 'Informe o e-mail da sua conta. Se ele estiver cadastrado, você receberá um link para criar uma senha nova.',
      icone: Icons.lock_reset,
      conteudo: Form(
        key: _form,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_enviado)
              const MensagemCartao(
                sucesso: true,
                texto: 'Pronto. Se o e-mail estiver cadastrado, o link chega em instantes (confira também o spam). Abra-o no navegador.',
              ),
            if (_erro != null) MensagemCartao(texto: _erro!),
            const RotuloCampo('E-mail'),
            TextFormField(
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autocorrect: false,
              textInputAction: TextInputAction.send,
              onFieldSubmitted: (_) => _enviar(),
              decoration: const InputDecoration(hintText: 'voce@empresa.com.br', prefixIcon: Icon(Icons.mail_outline)),
              validator: (v) => (v ?? '').trim().contains('@') ? null : 'Informe um e-mail válido.',
            ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _enviando ? null : _enviar,
              child: Text(_enviando ? 'Enviando…' : 'Enviar link'),
            ),
          ],
        ),
      ),
    );
  }
}

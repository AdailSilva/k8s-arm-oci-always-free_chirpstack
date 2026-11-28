import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/dialogos.dart';
import 'package:chirpstack_manager/features/conta/mascaras.dart';

/// Suporte (o mesmo do painel web): o chamado vai por e-mail à equipe (POST /api/v1/conta/suporte),
/// com o e-mail do usuário como "responder para"; telefone e WhatsApp opcionais.
class SuportePage extends StatefulWidget {
  const SuportePage({super.key});

  @override
  State<SuportePage> createState() => _SuportePageState();
}

class _SuportePageState extends State<SuportePage> {
  final _assunto = TextEditingController();
  final _mensagem = TextEditingController();
  final _telefone = TextEditingController();
  final _whats = TextEditingController();
  final _form = GlobalKey<FormState>();
  bool _enviando = false;
  String? _erro;

  static const _whatsSuporte = '(89) 99412-9256';

  @override
  void dispose() {
    for (final c in [_assunto, _mensagem, _telefone, _whats]) {
      c.dispose();
    }
    super.dispose();
  }

  String _retrato() => jsonEncode([_assunto.text, _mensagem.text, _telefone.text, _whats.text]);

  Future<void> _enviar() async {
    FocusScope.of(context).unfocus();
    if (!(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _enviando = true;
      _erro = null;
    });
    try {
      await ApiClient.instancia.post('/api/v1/conta/suporte', corpo: {
        'assunto': _assunto.text.trim(),
        'mensagem': _mensagem.text.trim(),
        'telefone': _telefone.text.trim(),
        'whatsapp': _whats.text.trim(),
      });
      if (!mounted) return;
      Navegacao.avisar('Mensagem enviada. Responderemos pelo seu e-mail.');
      Navigator.of(context).pop();
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final email = Sessao.instancia.usuario?.email ?? '';
    return TravaEdicao(
      retrato: _retrato,
      child: Scaffold(
        appBar: AppBar(title: const Text('Suporte')),
        body: Form(
          key: _form,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
            children: [
              Text('A resposta chega no seu e-mail ($email).', style: TextStyle(color: cs.onSurfaceVariant, height: 1.4)),
              const SizedBox(height: 16),
              if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
              CampoRotulado(
                rotulo: 'Assunto',
                child: TextFormField(
                  controller: _assunto,
                  maxLength: 150,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(hintText: 'Ex.: Gateway da sede off-line', counterText: ''),
                  validator: (v) => (v ?? '').trim().isEmpty ? 'Informe o assunto.' : null,
                ),
              ),
              CampoRotulado(
                rotulo: 'Telefone (opcional)',
                child: TextFormField(
                  controller: _telefone,
                  keyboardType: TextInputType.phone,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, MascaraTelefone(celular: false)],
                  decoration: const InputDecoration(hintText: '(00) 0000-0000'),
                  validator: (v) => telefoneValido(v, celular: false) ? null : 'Telefone incompleto. Ex.: (88) 3333-4444',
                ),
              ),
              CampoRotulado(
                rotulo: 'Celular / WhatsApp (opcional)',
                child: TextFormField(
                  controller: _whats,
                  keyboardType: TextInputType.phone,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, MascaraTelefone(celular: true)],
                  decoration: const InputDecoration(hintText: '(00) 90000-0000'),
                  validator: (v) => telefoneValido(v, celular: true) ? null : 'WhatsApp incompleto. Ex.: (88) 99999-8888',
                ),
              ),
              CampoRotulado(
                rotulo: 'Mensagem',
                child: TextFormField(
                  controller: _mensagem,
                  minLines: 5,
                  maxLines: 10,
                  maxLength: 5000,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(hintText: 'Descreva o que tentou fazer, em qual tela, o que apareceu e desde quando acontece'),
                  validator: (v) => (v ?? '').trim().isEmpty ? 'Escreva a mensagem.' : null,
                ),
              ),
              FilledButton.icon(
                onPressed: _enviando ? null : _enviar,
                icon: const Icon(Icons.send),
                label: Text(_enviando ? 'Enviando…' : 'Enviar'),
              ),
              const TituloSecao('Outros canais'),
              Card(
                clipBehavior: Clip.antiAlias,
                child: Column(children: [
                  ListTile(
                    leading: const Icon(Icons.chat_outlined),
                    title: const Text('WhatsApp'),
                    subtitle: const Text(_whatsSuporte),
                    trailing: const Icon(Icons.open_in_new, size: 18),
                    onTap: () => launchUrl(Uri.parse('https://wa.me/5589994129256'), mode: LaunchMode.externalApplication),
                  ),
                  const Divider(height: 1),
                  const ListTile(
                    leading: Icon(Icons.schedule),
                    title: Text('Atendimento'),
                    subtitle: Text('Segunda a sexta, das 8h às 18h'),
                  ),
                ]),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

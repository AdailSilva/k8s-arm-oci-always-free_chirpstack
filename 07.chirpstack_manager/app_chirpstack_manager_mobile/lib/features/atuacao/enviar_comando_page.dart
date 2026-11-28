import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';
import 'package:chirpstack_manager/features/atuacao/parametros.dart';

/// Envio de qualquer comando suportado pelo equipamento, com parâmetros e política opcionais.
class EnviarComandoPage extends StatefulWidget {
  const EnviarComandoPage({
    super.key,
    required this.dispositivoId,
    required this.dispositivoNome,
    required this.equipamento,
    this.tipoInicial,
    this.politicaAberta = false,
  });

  final int dispositivoId;
  final String dispositivoNome;

  /// EquipamentoResponse: comandos suportados e periféricos.
  final Json equipamento;
  final String? tipoInicial;
  final bool politicaAberta;

  @override
  State<EnviarComandoPage> createState() => _EnviarComandoPageState();
}

class _EnviarComandoPageState extends State<EnviarComandoPage> {
  late final _valores = ValoresComando(tipo: widget.tipoInicial);
  final _politica = ValoresPolitica();
  Map<String, String> _erros = const {};
  Map<String, String> _errosPolitica = const {};
  String? _erro;
  bool _enviando = false;

  Future<void> _enviar() async {
    FocusScope.of(context).unfocus();
    final erros = _valores.validar();
    final errosPol = _politica.validar();
    setState(() {
      _erros = erros;
      _errosPolitica = errosPol;
      _erro = erros.isEmpty && errosPol.isEmpty ? null : 'Corrija os campos destacados.';
    });
    if (erros.isNotEmpty || errosPol.isNotEmpty) return;
    setState(() => _enviando = true);
    try {
      final r = await executarComCritico(
        context,
        (c) => AtuacaoApi.enviarComando(widget.dispositivoId, _valores.tipo!, _valores.paraJson(), politica: _politica.paraJson(), confirmarCritico: c),
      );
      if (r == null || !mounted) return;
      Navegacao.avisar('Comando enviado para a fila.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Enviar comando')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          Text(widget.dispositivoNome, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
          const SizedBox(height: 2),
          Text(widget.equipamento.texto('modeloRotulo') ?? '', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13)),
          const SizedBox(height: 16),
          if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
              child: FormComando(
                valores: _valores,
                comandos: widget.equipamento.lista('comandos'),
                perifericos: widget.equipamento.lista('perifericos'),
                erros: _erros,
                aoMudar: () => setState(() {}),
              ),
            ),
          ),
          const SizedBox(height: 12),
          FormPolitica(valores: _politica, erros: _errosPolitica, aberta: widget.politicaAberta),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: _enviando ? null : _enviar,
            icon: const Icon(Icons.send),
            label: Text(_enviando ? 'Enviando…' : 'Enviar comando'),
          ),
        ],
      ),
    );
  }
}

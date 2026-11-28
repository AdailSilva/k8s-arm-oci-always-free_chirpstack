import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/ui/componentes.dart';

/// "Deseja realmente sair?" — a trava de formulário com alterações não salvas, com os mesmos
/// textos do painel web. [janela] usa os textos de diálogo/folha ("Fechar sem salvar?").
Future<bool> confirmarDescarte(BuildContext context, {bool janela = false}) => confirmar(
      context,
      titulo: janela ? 'Fechar sem salvar?' : 'Deseja realmente sair?',
      texto: 'As alterações não salvas serão perdidas.',
      rotuloConfirmar: janela ? 'Fechar sem salvar' : 'Sair sem salvar',
      perigo: true,
      icone: Icons.warning_amber_rounded,
    );

/// Envolve um formulário com a trava "Deseja realmente sair?" (o TravaEdicao do painel web).
///
/// [retrato] devolve o estado atual do formulário (ex.: `jsonEncode([...campos])`). O retrato de
/// partida é tirado no primeiro toque na tela — depois que os dados do servidor e os padrões já
/// foram preenchidos —, e o voltar do aparelho (ou da barra) só pergunta se o formulário mudou
/// desde então. Salvar com `Navigator.pop` não passa pela trava.
class TravaEdicao extends StatefulWidget {
  const TravaEdicao({super.key, required this.retrato, required this.child, this.janela = false, this.ativa = true});

  final String Function() retrato;
  final Widget child;
  final bool janela;

  /// false = sem trava (ex.: troca de senha obrigatória, que não tem para onde voltar).
  final bool ativa;

  @override
  State<TravaEdicao> createState() => _TravaEdicaoState();
}

class _TravaEdicaoState extends State<TravaEdicao> {
  String? _base;

  String _ler() {
    try {
      return widget.retrato();
    } catch (_) {
      return '';
    }
  }

  bool get _sujo => _base != null && _ler() != _base;

  @override
  Widget build(BuildContext context) {
    if (!widget.ativa) return widget.child;
    return PopScope<Object?>(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        final nav = Navigator.of(context);
        if (!_sujo || await confirmarDescarte(context, janela: widget.janela)) {
          if (mounted) nav.pop(result);
        }
      },
      child: Listener(
        behavior: HitTestBehavior.translucent,
        onPointerDown: (_) => _base ??= _ler(),
        child: widget.child,
      ),
    );
  }
}

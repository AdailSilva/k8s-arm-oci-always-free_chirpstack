import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// "Ops! Algo deu errado" (a página oops do painel web): no lugar da tela vermelha (debug) ou
/// cinza (release) que o Flutter mostra quando um widget falha ao montar.
void instalarTelaDeErro() {
  ErrorWidget.builder = (detalhes) {
    if (kDebugMode) return ErrorWidget(detalhes.exception);
    return const _TelaDeErro();
  };
}

class _TelaDeErro extends StatelessWidget {
  const _TelaDeErro();

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.surface,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(Icons.error_outline, size: 56, color: cs.primary),
            const SizedBox(height: 16),
            const Text('Ops! Algo deu errado', textAlign: TextAlign.center, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(
              'Não conseguimos mostrar esta parte da tela. Volte e tente de novo; se continuar, fale com o suporte em Conta › Suporte.',
              textAlign: TextAlign.center,
              style: TextStyle(color: cs.onSurfaceVariant, height: 1.45),
            ),
          ]),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/ui/cores.dart';

/// Indicador de carregamento centralizado.
class Carregando extends StatelessWidget {
  const Carregando({super.key});

  @override
  Widget build(BuildContext context) => const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator()));
}

/// Mensagem de estado (lista vazia, erro), rolável para o "puxar para atualizar" funcionar.
class EstadoMensagem extends StatelessWidget {
  const EstadoMensagem({super.key, required this.icone, required this.titulo, this.texto, this.acao, this.rotuloAcao, this.cabecalho});

  final IconData icone;
  final String titulo;
  final String? texto;
  final VoidCallback? acao;
  final String? rotuloAcao;

  /// Conteúdo fixo acima da mensagem (ex.: os filtros de uma lista que ficou vazia).
  final Widget? cabecalho;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: cabecalho == null ? const EdgeInsets.fromLTRB(28, 72, 28, 28) : const EdgeInsets.fromLTRB(16, 4, 16, 28),
      children: [
        if (cabecalho != null) ...[cabecalho!, const SizedBox(height: 40)],
        Icon(icone, size: 48, color: cs.onSurfaceVariant),
        const SizedBox(height: 16),
        Text(titulo, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
        if (texto != null) ...[
          const SizedBox(height: 8),
          Text(texto!, textAlign: TextAlign.center, style: TextStyle(color: cs.onSurfaceVariant, height: 1.45)),
        ],
        if (acao != null) ...[
          const SizedBox(height: 20),
          Center(child: OutlinedButton.icon(onPressed: acao, icon: const Icon(Icons.refresh), label: Text(rotuloAcao ?? 'Tentar de novo'))),
        ],
      ],
    );
  }
}

/// Selo de estado (Online, Offline, Aberto...).
class ChipEstado extends StatelessWidget {
  const ChipEstado({super.key, required this.texto, required this.cor, this.piscar = false});

  final String texto;
  final Color cor;
  final bool piscar;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(color: cor.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(999)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 7, height: 7, decoration: BoxDecoration(color: cor, shape: BoxShape.circle)),
          const SizedBox(width: 6),
          Text(texto.toUpperCase(), style: TextStyle(color: cor, fontSize: 10.5, fontWeight: FontWeight.w800, letterSpacing: 0.6)),
        ],
      ),
    );
  }
}

/// Cartão de indicador do painel (número grande + rótulo).
class CartaoIndicador extends StatelessWidget {
  const CartaoIndicador({super.key, required this.rotulo, required this.valor, required this.icone, this.cor = CmCores.azul500, this.aoTocar});

  final String rotulo;
  final String valor;
  final IconData icone;
  final Color cor;
  final VoidCallback? aoTocar;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: aoTocar,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: cor.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(10)),
                child: Icon(icone, size: 20, color: cor),
              ),
              const SizedBox(height: 12),
              FittedBox(
                fit: BoxFit.scaleDown,
                alignment: Alignment.centerLeft,
                child: Text(valor, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, letterSpacing: -0.5)),
              ),
              const SizedBox(height: 2),
              Text(rotulo, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
            ],
          ),
        ),
      ),
    );
  }
}

/// Título de seção dentro de uma tela.
class TituloSecao extends StatelessWidget {
  const TituloSecao(this.texto, {super.key});

  final String texto;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 20, 4, 10),
        child: Text(
          texto.toUpperCase(),
          style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, letterSpacing: 1.2, color: Theme.of(context).colorScheme.onSurfaceVariant),
        ),
      );
}

/// Linha "rótulo: valor" dos detalhes.
class LinhaDado extends StatelessWidget {
  const LinhaDado(this.rotulo, this.valor, {super.key});

  final String rotulo;
  final String valor;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 128, child: Text(rotulo, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13.5))),
          const SizedBox(width: 12),
          Expanded(child: Text(valor, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600))),
        ],
      ),
    );
  }
}

/// Campo de formulário com o rótulo acima (nunca dentro da caixa, para não ser cortado) e uma
/// ajuda opcional abaixo.
class CampoRotulado extends StatelessWidget {
  const CampoRotulado({super.key, required this.rotulo, required this.child, this.ajuda, this.espacoAbaixo = 16});

  final String rotulo;
  final Widget child;
  final String? ajuda;
  final double espacoAbaixo;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: EdgeInsets.only(bottom: espacoAbaixo),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 7),
            child: Text(rotulo, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
          ),
          child,
          if (ajuda != null) ...[
            const SizedBox(height: 6),
            Text(ajuda!, style: TextStyle(fontSize: 12.5, height: 1.4, color: cs.onSurfaceVariant)),
          ],
        ],
      ),
    );
  }
}

/// Caixa de mensagem (erro, aviso ou informação) dentro de uma tela.
class CaixaMensagem extends StatelessWidget {
  const CaixaMensagem({super.key, required this.texto, this.cor = CmCores.offline, this.icone = Icons.warning_amber_rounded});

  final String texto;
  final Color cor;
  final IconData icone;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cor.withValues(alpha: 0.1),
        border: Border.all(color: cor.withValues(alpha: 0.3)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icone, color: cor, size: 18),
        const SizedBox(width: 10),
        Expanded(child: Text(texto, style: TextStyle(color: cor, fontSize: 13.5, height: 1.45))),
      ]),
    );
  }
}

/// Pergunta de confirmação (Cancelar / ação). [perigo] pinta a ação de vermelho.
Future<bool> confirmar(
  BuildContext context, {
  required String titulo,
  required String texto,
  required String rotuloConfirmar,
  bool perigo = false,
  IconData? icone,
}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) {
      final cs = Theme.of(c).colorScheme;
      return AlertDialog(
        icon: icone == null ? null : Icon(icone, color: perigo ? cs.error : null),
        title: Text(titulo),
        content: SingleChildScrollView(child: Text(texto, style: const TextStyle(height: 1.45))),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')),
          FilledButton(
            style: perigo ? FilledButton.styleFrom(backgroundColor: cs.error, foregroundColor: cs.onError, minimumSize: const Size(0, 44)) : FilledButton.styleFrom(minimumSize: const Size(0, 44)),
            onPressed: () => Navigator.pop(c, true),
            child: Text(rotuloConfirmar),
          ),
        ],
      );
    },
  );
  return ok == true;
}

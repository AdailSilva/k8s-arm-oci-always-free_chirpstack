import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/marca.dart';

/// Molde das telas de autenticação (esqueci a senha, troca de senha): o mesmo fundo azul-noite e o
/// mesmo cartão do login.
class TelaAuth extends StatelessWidget {
  const TelaAuth({super.key, required this.titulo, required this.subtitulo, required this.icone, required this.conteudo, this.voltar = true});

  final String titulo;
  final String subtitulo;
  final IconData icone;
  final Widget conteudo;
  final bool voltar;

  @override
  Widget build(BuildContext context) {
    final escuro = Theme.of(context).brightness == Brightness.dark;
    final destaque = escuro ? CmCores.azul300 : CmCores.azul600;
    return Scaffold(
      backgroundColor: CmCores.noite950,
      body: Container(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(-0.6, -0.8),
            radius: 1.3,
            colors: [CmCores.noite800, CmCores.noite, CmCores.noite950],
            stops: [0, 0.45, 1],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(children: [
                      if (voltar)
                        IconButton(
                          tooltip: 'Voltar',
                          color: Colors.white,
                          onPressed: () => Navigator.of(context).maybePop(),
                          icon: const Icon(Icons.arrow_back),
                        ),
                      const Flexible(child: MarcaManager(negativo: true, tamanho: 36, apoio: false)),
                    ]),
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.fromLTRB(22, 24, 22, 22),
                      decoration: BoxDecoration(
                        color: escuro ? CmCores.noite800 : Colors.white,
                        borderRadius: BorderRadius.circular(24),
                        boxShadow: const [BoxShadow(color: Color(0x66000000), blurRadius: 40, offset: Offset(0, 24))],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Row(children: [
                            Icon(icone, size: 13, color: destaque),
                            const SizedBox(width: 6),
                            Flexible(
                              child: Text('ACESSO SEGURO',
                                  style: TextStyle(color: destaque, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1.6)),
                            ),
                          ]),
                          const SizedBox(height: 10),
                          Text(titulo, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, letterSpacing: -0.4)),
                          const SizedBox(height: 6),
                          Text(subtitulo, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant, fontSize: 14, height: 1.5)),
                          const SizedBox(height: 20),
                          conteudo,
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Rótulo acima de um campo (o mesmo do login).
class RotuloCampo extends StatelessWidget {
  const RotuloCampo(this.texto, {super.key});

  final String texto;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 7),
        child: Text(texto, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
      );
}

/// Mensagem de resultado (sucesso ou erro) dentro do cartão.
class MensagemCartao extends StatelessWidget {
  const MensagemCartao({super.key, required this.texto, this.sucesso = false});

  final String texto;
  final bool sucesso;

  @override
  Widget build(BuildContext context) {
    final cor = sucesso ? CmCores.online : CmCores.offline;
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cor.withValues(alpha: 0.1),
        border: Border.all(color: cor.withValues(alpha: 0.3)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(sucesso ? Icons.check_circle_outline : Icons.warning_amber_rounded, color: cor, size: 18),
        const SizedBox(width: 10),
        Expanded(child: Text(texto, style: TextStyle(color: cor, fontSize: 13.5, height: 1.45))),
      ]),
    );
  }
}

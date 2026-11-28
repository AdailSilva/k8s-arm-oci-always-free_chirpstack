import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/estados.dart';
import 'package:chirpstack_manager/core/ui/lista_paginada.dart';

/// Alertas disparados pelas regras (GET /api/v1/alertas), com o reconhecimento.
class AlertasPage extends StatefulWidget {
  const AlertasPage({super.key});

  @override
  State<AlertasPage> createState() => _AlertasPageState();
}

class _AlertasPageState extends State<AlertasPage> {
  /// ABERTO, RECONHECIDO ou vazio (todos).
  String _status = 'ABERTO';
  final _lista = GlobalKey<ListaPaginadaState>();

  Future<PaginaResultado> _buscar(int pagina, int tamanho, String _) async =>
      PaginaResultado.deJson(await ApiClient.instancia.get('/api/v1/alertas', query: {
        'status': _status,
        'pagina': pagina,
        'tamanho': tamanho,
      }));

  Future<void> _reconhecer(Json a) async {
    final id = a.inteiro('id');
    if (id == null) return;
    try {
      await ApiClient.instancia.post('/api/v1/alertas/$id/reconhecer');
      Navegacao.avisar('Alerta reconhecido.');
      await _lista.currentState?.recarregar();
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final podeReconhecer = Sessao.instancia.usuario?.podeAlterar ?? false;
    return ListaPaginada(
      key: _lista,
      vazio: _status == 'ABERTO' ? 'Nenhum alerta aberto.' : 'Nenhum alerta.',
      iconeVazio: Icons.notifications_none,
      buscar: _buscar,
      cabecalho: Padding(
        padding: const EdgeInsets.only(top: 8),
        child: SegmentedButton<String>(
          showSelectedIcon: false,
          // Rótulo numa linha só: em telas estreitas ele encolhe em vez de quebrar no meio da palavra.
          segments: const [
            ButtonSegment(value: 'ABERTO', label: _RotuloFiltro('Abertos')),
            ButtonSegment(value: 'RECONHECIDO', label: _RotuloFiltro('Reconhecidos')),
            ButtonSegment(value: '', label: _RotuloFiltro('Todos')),
          ],
          selected: {_status},
          onSelectionChanged: (s) {
            setState(() => _status = s.first);
            _lista.currentState?.recarregar();
          },
        ),
      ),
      item: (context, a) => _CartaoAlerta(a: a, aoReconhecer: podeReconhecer && a.texto('status') == 'ABERTO' ? () => _reconhecer(a) : null),
    );
  }
}

class _RotuloFiltro extends StatelessWidget {
  const _RotuloFiltro(this.texto);

  final String texto;

  @override
  Widget build(BuildContext context) =>
      FittedBox(fit: BoxFit.scaleDown, child: Text(texto, maxLines: 1, softWrap: false));
}

class _CartaoAlerta extends StatelessWidget {
  const _CartaoAlerta({required this.a, this.aoReconhecer});

  final Json a;
  final VoidCallback? aoReconhecer;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final sev = a.texto('severidade');
    final corSev = Estados.severidade(sev);
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(padding: const EdgeInsets.only(top: 2), child: Icon(Icons.warning_amber_rounded, color: corSev)),
            const SizedBox(width: 10),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(a.texto('regraNome') ?? 'Alerta', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 2),
                Text(a.texto('dispositivoNome') ?? a.texto('devEui') ?? '', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13)),
              ]),
            ),
            const SizedBox(width: 8),
            Estados.alerta(a.texto('status')),
          ]),
          if (a.texto('mensagem') != null) ...[
            const SizedBox(height: 8),
            Text(a.texto('mensagem')!, style: const TextStyle(fontSize: 13.5, height: 1.4)),
          ],
          const SizedBox(height: 10),
          Wrap(
            spacing: 10,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              if (sev != null) ChipEstado(texto: Estados.severidadeRotulo(sev), cor: corSev),
              Text(Formatos.dataHora(a.data('disparadoEm')), style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
              if (a.texto('reconhecidoPor') != null)
                Text('Reconhecido por ${a.texto('reconhecidoPor')}', style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
            ],
          ),
          if (aoReconhecer != null) ...[
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.tonalIcon(
                style: FilledButton.styleFrom(minimumSize: const Size(0, 40)),
                onPressed: aoReconhecer,
                icon: const Icon(Icons.check, size: 18),
                label: const Text('Reconhecer'),
              ),
            ),
          ],
        ]),
      ),
    );
  }
}

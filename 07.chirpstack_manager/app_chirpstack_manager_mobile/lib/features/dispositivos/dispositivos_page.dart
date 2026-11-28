import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/estados.dart';
import 'package:chirpstack_manager/core/ui/lista_paginada.dart';
import 'package:chirpstack_manager/features/dispositivos/dispositivo_detalhe_page.dart';

/// Dispositivos monitorados (GET /api/v1/dispositivos), com busca por nome ou DevEUI.
class DispositivosPage extends StatelessWidget {
  const DispositivosPage({super.key});

  static Future<PaginaResultado> buscar(int pagina, int tamanho, String busca) async =>
      PaginaResultado.deJson(await ApiClient.instancia.get('/api/v1/dispositivos', query: {
        'pagina': pagina,
        'tamanho': tamanho,
        'busca': busca,
      }));

  @override
  Widget build(BuildContext context) {
    return ListaPaginada(
      dicaBusca: 'Nome ou DevEUI',
      vazio: 'Nenhum dispositivo encontrado.',
      iconeVazio: Icons.sensors_off,
      buscar: buscar,
      item: (context, d) => CartaoDispositivo(d: d),
    );
  }
}

class CartaoDispositivo extends StatelessWidget {
  const CartaoDispositivo({super.key, required this.d});

  final Json d;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final rssi = d.inteiro('ultimoRssi');
    final bateria = d.inteiro('ultimaBateria');
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () {
          final id = d.inteiro('id');
          if (id == null) return;
          Navigator.of(context).push(MaterialPageRoute<void>(
            builder: (_) => DispositivoDetalhePage(id: id, nome: d.texto('nome') ?? 'Dispositivo'),
          ));
        },
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(d.texto('nome') ?? '—', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                    const SizedBox(height: 2),
                    Text(d.texto('devEui') ?? '', style: TextStyle(fontFamily: 'monospace', fontSize: 12, color: cs.onSurfaceVariant)),
                  ]),
                ),
                const SizedBox(width: 8),
                Estados.situacao(d.texto('situacao')),
              ]),
              const SizedBox(height: 10),
              Wrap(
                spacing: 14,
                runSpacing: 6,
                children: [
                  _Info(icone: Icons.schedule, texto: Formatos.relativo(d.data('ultimoContatoEm'))),
                  if (rssi != null) _Info(icone: Icons.signal_cellular_alt, texto: '$rssi dBm'),
                  if (bateria != null) _Info(icone: Icons.battery_std, texto: '$bateria%', cor: Estados.bateria(bateria)),
                  if (d.texto('aplicacaoNome') != null) _Info(icone: Icons.apps, texto: d.texto('aplicacaoNome')!),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Info extends StatelessWidget {
  const _Info({required this.icone, required this.texto, this.cor});

  final IconData icone;
  final String texto;
  final Color? cor;

  @override
  Widget build(BuildContext context) {
    final c = cor ?? Theme.of(context).colorScheme.onSurfaceVariant;
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(icone, size: 15, color: c),
      const SizedBox(width: 4),
      ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 180),
        child: Text(texto, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: c)),
      ),
    ]);
  }
}

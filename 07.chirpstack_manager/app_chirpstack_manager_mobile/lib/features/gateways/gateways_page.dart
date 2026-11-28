import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/estados.dart';
import 'package:chirpstack_manager/core/ui/lista_paginada.dart';

/// Gateways da rede (GET /api/v1/chirpstack/gateways — a lista do ChirpStack, via Manager).
class GatewaysPage extends StatelessWidget {
  const GatewaysPage({super.key});

  static Future<PaginaResultado> buscar(int pagina, int tamanho, String busca) async =>
      PaginaResultado.deJson(await ApiClient.instancia.get('/api/v1/chirpstack/gateways', query: {
        'limit': tamanho,
        'offset': pagina * tamanho,
        'search': busca,
      }));

  @override
  Widget build(BuildContext context) {
    return ListaPaginada(
      dicaBusca: 'Nome ou Gateway ID',
      vazio: 'Nenhum gateway encontrado.',
      iconeVazio: Icons.router_outlined,
      buscar: buscar,
      item: (context, g) => CartaoGateway(g: g),
    );
  }
}

class CartaoGateway extends StatelessWidget {
  const CartaoGateway({super.key, required this.g});

  final Json g;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final descricao = g.texto('description');
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Padding(padding: EdgeInsets.only(top: 2), child: Icon(Icons.router_outlined)),
            const SizedBox(width: 10),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(g.texto('name') ?? '—', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 2),
                Text(g.texto('gatewayId') ?? '', style: TextStyle(fontFamily: 'monospace', fontSize: 12, color: cs.onSurfaceVariant)),
              ]),
            ),
            const SizedBox(width: 8),
            Estados.gateway(g.texto('state')),
          ]),
          if (descricao != null) ...[
            const SizedBox(height: 8),
            Text(descricao, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13)),
          ],
          const SizedBox(height: 8),
          Row(children: [
            Icon(Icons.schedule, size: 15, color: cs.onSurfaceVariant),
            const SizedBox(width: 4),
            Expanded(
              child: Text('Visto por último: ${Formatos.relativo(g.data('lastSeenAt'))}',
                  maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
            ),
          ]),
        ]),
      ),
    );
  }
}

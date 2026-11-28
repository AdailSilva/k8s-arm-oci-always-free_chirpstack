import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';

/// Dispositivos monitorados (id e nome) para os seletores das cenas, agendamentos e automações.
Future<List<Json>> carregarDispositivos() async {
  final r = comoMapa(await ApiClient.instancia.get('/api/v1/dispositivos', query: {'busca': '', 'inativos': true, 'pagina': 0, 'tamanho': 500}));
  final l = r.lista('result');
  l.sort((a, b) => (a.texto('nome') ?? '').toLowerCase().compareTo((b.texto('nome') ?? '').toLowerCase()));
  return l;
}

/// Equipamentos (comandos e periféricos) dos dispositivos escolhidos nos formulários.
class CacheEquipamentos {
  final Map<int, Json> _mapa = {};
  final Set<int> _carregando = {};
  List<Json>? _todosComandos;

  Json? equipamento(int? id) => id == null ? null : _mapa[id];
  bool carregando(int? id) => id != null && _carregando.contains(id);
  List<Json> comandos(int? id) => equipamento(id)?.lista('comandos') ?? const [];
  List<Json> perifericos(int? id) => equipamento(id)?.lista('perifericos') ?? const [];

  /// União dos comandos de todos os modelos (automação "no dispositivo do alerta").
  List<Json> get todosComandos => _todosComandos ?? const [];

  String semComandos(int? id) {
    if (id == null) return 'Escolha o dispositivo primeiro.';
    if (carregando(id)) return 'Carregando comandos…';
    final e = equipamento(id);
    if (e != null && !e.logico('configurado')) return 'Configure o equipamento deste dispositivo (aba Controle).';
    return 'Nenhum comando disponível.';
  }

  Future<void> garantir(int? id, VoidCallback aoMudar) async {
    if (id == null || _mapa.containsKey(id) || _carregando.contains(id)) return;
    _carregando.add(id);
    try {
      _mapa[id] = await AtuacaoApi.equipamento(id);
    } catch (_) {
      // Sem equipamento: o formulário mostra a mensagem de "nenhum comando".
    } finally {
      _carregando.remove(id);
      aoMudar();
    }
  }

  Future<void> garantirModelos(VoidCallback aoMudar) async {
    if (_todosComandos != null) return;
    try {
      final vistos = <String, Json>{};
      for (final m in await AtuacaoApi.modelos()) {
        for (final c in m.lista('comandos')) {
          final t = c.texto('tipo');
          if (t != null) vistos.putIfAbsent(t, () => c);
        }
      }
      _todosComandos = vistos.values.toList();
      aoMudar();
    } catch (_) {
      // Tenta de novo na próxima abertura.
    }
  }
}

/// Seletor de dispositivo (lista suspensa com os nomes).
class SeletorDispositivo extends StatelessWidget {
  const SeletorDispositivo({super.key, required this.dispositivos, required this.valor, required this.aoMudar, this.erro, this.rotulo = 'Dispositivo', this.habilitado = true});

  final List<Json> dispositivos;
  final int? valor;
  final ValueChanged<int?> aoMudar;
  final String? erro;
  final String rotulo;
  final bool habilitado;

  @override
  Widget build(BuildContext context) {
    final ids = dispositivos.map((d) => d.inteiro('id')).whereType<int>().toSet();
    final v = ids.contains(valor) ? valor : null;
    return CampoRotulado(
      rotulo: rotulo,
      child: DropdownButtonFormField<int>(
        key: ValueKey('disp-$v-${dispositivos.length}'),
        initialValue: v,
        isExpanded: true,
        hint: const Text('Escolha o dispositivo', maxLines: 1, overflow: TextOverflow.ellipsis),
        decoration: InputDecoration(errorText: erro, errorMaxLines: 3),
        items: [
          for (final d in dispositivos)
            if (d.inteiro('id') != null) itemMenu(d.inteiro('id')!, d.texto('nome') ?? d.texto('devEui') ?? '#${d.inteiro('id')}'),
        ],
        onChanged: habilitado ? aoMudar : null,
      ),
    );
  }
}

/// Seletor de cena.
class SeletorCena extends StatelessWidget {
  const SeletorCena({super.key, required this.cenas, required this.valor, required this.aoMudar, this.erro});

  final List<Json> cenas;
  final int? valor;
  final ValueChanged<int?> aoMudar;
  final String? erro;

  @override
  Widget build(BuildContext context) {
    final ids = cenas.map((c) => c.inteiro('id')).whereType<int>().toSet();
    final v = ids.contains(valor) ? valor : null;
    return CampoRotulado(
      rotulo: 'Cena',
      ajuda: cenas.isEmpty ? 'Nenhuma cena cadastrada (Atuação › Cenas).' : null,
      child: DropdownButtonFormField<int>(
        key: ValueKey('cena-$v-${cenas.length}'),
        initialValue: v,
        isExpanded: true,
        hint: const Text('Escolha a cena', maxLines: 1, overflow: TextOverflow.ellipsis),
        decoration: InputDecoration(errorText: erro, errorMaxLines: 3),
        items: [for (final c in cenas) if (c.inteiro('id') != null) itemMenu(c.inteiro('id')!, c.texto('nome') ?? '#${c.inteiro('id')}')],
        onChanged: aoMudar,
      ),
    );
  }
}

/// "Comando" ou "Cena".
class SeletorAlvo extends StatelessWidget {
  const SeletorAlvo({super.key, required this.valor, required this.aoMudar});

  final String valor;
  final ValueChanged<String> aoMudar;

  @override
  Widget build(BuildContext context) => CampoRotulado(
        rotulo: 'Executar',
        child: SegmentedButton<String>(
          showSelectedIcon: false,
          segments: const [
            ButtonSegment(value: 'COMANDO', label: RotuloEncolhe('Comando')),
            ButtonSegment(value: 'CENA', label: RotuloEncolhe('Cena')),
          ],
          selected: {valor},
          onSelectionChanged: (s) => aoMudar(s.first),
        ),
      );
}

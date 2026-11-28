import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/estados.dart';
import 'package:chirpstack_manager/features/atuacao/agendamentos_page.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/controle_page.dart';
import 'package:chirpstack_manager/features/atuacao/energia_page.dart';

/// Detalhe do dispositivo em abas: Resumo (situação, último contato e as últimas leituras), Controle
/// (equipamento e relés), Energia (PZEM-004T), Comandos e Agendamentos.
class DispositivoDetalhePage extends StatefulWidget {
  const DispositivoDetalhePage({super.key, required this.id, required this.nome, this.abaInicial = 0});

  final int id;
  final String nome;

  /// 0 = Resumo, 1 = Controle, 2 = Energia, 3 = Comandos, 4 = Agendamentos.
  final int abaInicial;

  @override
  State<DispositivoDetalhePage> createState() => _DispositivoDetalhePageState();
}

class _DispositivoDetalhePageState extends State<DispositivoDetalhePage> {
  Json? _d;
  Json? _equipamento;
  List<Json> _leituras = const [];
  Object? _erro;
  bool _carregando = true;

  @override
  void initState() {
    super.initState();
    _carregar();
    _carregarEquipamento();
  }

  Future<void> _carregarEquipamento() async {
    try {
      final e = await AtuacaoApi.equipamento(widget.id);
      if (mounted) setState(() => _equipamento = e);
    } catch (_) {
      // Sem equipamento: as abas de atuação mostram o próprio aviso.
    }
  }

  Future<void> _carregar() async {
    try {
      final api = ApiClient.instancia;
      final d = comoMapa(await api.get('/api/v1/dispositivos/${widget.id}'));
      final l = comoMapa(await api.get('/api/v1/dispositivos/${widget.id}/leituras', query: {'pagina': 0, 'tamanho': 20}));
      if (mounted) {
        setState(() {
          _d = d;
          _leituras = l.lista('result');
          _erro = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _erro = e);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = _d;
    Widget corpo;
    if (_carregando) {
      corpo = const Carregando();
    } else if (d == null) {
      corpo = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar', texto: mensagemDeErro(_erro ?? 'erro'), acao: _carregar);
    } else {
      final snr = d.decimal('ultimoSnr');
      corpo = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  const Expanded(child: Text('Situação', style: TextStyle(fontWeight: FontWeight.w700))),
                  Estados.situacao(d.texto('situacao')),
                ]),
                const SizedBox(height: 4),
                LinhaDado('DevEUI', d.texto('devEui') ?? '—'),
                LinhaDado('Aplicação', d.texto('aplicacaoNome') ?? '—'),
                LinhaDado('Tenant', d.texto('tenantNome') ?? '—'),
                LinhaDado('Perfil', d.texto('perfilNome') ?? '—'),
                LinhaDado('Último contato', Formatos.dataHora(d.data('ultimoContatoEm'))),
                LinhaDado('Último evento', d.texto('ultimoEvento') ?? '—'),
                LinhaDado('Gateway', d.texto('ultimoGateway') ?? '—'),
                LinhaDado('Sinal', [
                  if (d.inteiro('ultimoRssi') != null) '${d.inteiro('ultimoRssi')} dBm',
                  if (snr != null) 'SNR ${Formatos.decimal(snr)} dB',
                ].join(' · ').ifEmpty('—')),
                if (d.inteiro('ultimaBateria') != null) LinhaDado('Bateria', '${d.inteiro('ultimaBateria')}%'),
                LinhaDado('Uplinks', Formatos.inteiro(d.inteiro('totalUplinks'))),
              ]),
            ),
          ),
          const TituloSecao('Últimas leituras'),
          if (_leituras.isEmpty)
            const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Nenhuma leitura recebida ainda.')))
          else
            Card(
              child: Column(children: [
                for (var i = 0; i < _leituras.length; i++) ...[
                  if (i > 0) const Divider(height: 1),
                  _Leitura(l: _leituras[i]),
                ],
              ]),
            ),
        ],
      );
    }
    return DefaultTabController(
      length: 5,
      initialIndex: widget.abaInicial.clamp(0, 4),
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.nome, maxLines: 1, overflow: TextOverflow.ellipsis),
          bottom: const TabBar(
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            tabs: [
              Tab(text: 'Resumo'),
              Tab(text: 'Controle'),
              Tab(text: 'Energia'),
              Tab(text: 'Comandos'),
              Tab(text: 'Agendamentos'),
            ],
          ),
        ),
        body: TabBarView(children: [
          RefreshIndicator(onRefresh: _carregar, child: corpo),
          ControleDispositivo(
            dispositivoId: widget.id,
            dispositivoNome: widget.nome,
            indiceAba: 1,
            aoMudarEquipamento: (e) {
              if (mounted) setState(() => _equipamento = e);
            },
          ),
          EnergiaDispositivo(dispositivoId: widget.id, indiceAba: 2),
          ComandosDoDispositivo(dispositivoId: widget.id, dispositivoNome: widget.nome, equipamento: _equipamento, indiceAba: 3),
          AgendamentosLista(dispositivoId: widget.id, dispositivoNome: widget.nome),
        ]),
      ),
    );
  }
}

class _Leitura extends StatelessWidget {
  const _Leitura({required this.l});

  final Json l;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final objeto = l.texto('objeto');
    final partes = [
      if (l.inteiro('fCnt') != null) 'fCnt ${l.inteiro('fCnt')}',
      if (l.inteiro('fPort') != null) 'porta ${l.inteiro('fPort')}',
      if (l.inteiro('spreadingFactor') != null) 'SF${l.inteiro('spreadingFactor')}',
      if (l.inteiro('rssi') != null) '${l.inteiro('rssi')} dBm',
      if (l.decimal('snr') != null) 'SNR ${Formatos.decimal(l.decimal('snr'))}',
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(Formatos.dataHora(l.data('recebidoEm')), style: const TextStyle(fontWeight: FontWeight.w600)),
        const SizedBox(height: 2),
        Text(partes.join(' · '), style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
        if (objeto != null && objeto != 'null' && objeto != '{}') ...[
          const SizedBox(height: 6),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: cs.surfaceContainerHighest, borderRadius: BorderRadius.circular(10)),
            child: Text(objeto, maxLines: 6, overflow: TextOverflow.ellipsis, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
          ),
        ],
      ]),
    );
  }
}

extension on String {
  String ifEmpty(String alternativa) => isEmpty ? alternativa : this;
}

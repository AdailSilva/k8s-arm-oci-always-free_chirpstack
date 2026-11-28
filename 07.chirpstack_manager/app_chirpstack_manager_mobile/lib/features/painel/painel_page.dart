import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/features/atuacao/agendamentos_page.dart';
import 'package:chirpstack_manager/features/atuacao/automacoes_page.dart';
import 'package:chirpstack_manager/features/atuacao/cenas_page.dart';
import 'package:chirpstack_manager/features/atuacao/comandos_page.dart';

/// Painel: resumo da rede (GET /api/v1/painel), o mesmo do painel web.
class PainelPage extends StatefulWidget {
  const PainelPage({super.key, this.aoAbrirAba});

  /// Leva a outra aba (1 = Dispositivos, 3 = Alertas) ao tocar num indicador.
  final ValueChanged<int>? aoAbrirAba;

  @override
  State<PainelPage> createState() => _PainelPageState();
}

class _PainelPageState extends State<PainelPage> {
  Json? _dados;
  Object? _erro;
  bool _carregando = true;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    setState(() => _carregando = _dados == null);
    try {
      final d = comoMapa(await ApiClient.instancia.get('/api/v1/painel'));
      if (mounted) {
        setState(() {
          _dados = d;
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
    final d = _dados;
    Widget corpo;
    if (_carregando) {
      corpo = const Carregando();
    } else if (d == null) {
      corpo = EstadoMensagem(
        icone: Icons.cloud_off_outlined,
        titulo: 'Não foi possível carregar o painel',
        texto: mensagemDeErro(_erro ?? 'erro'),
        acao: _carregar,
      );
    } else {
      corpo = _conteudo(context, d);
    }
    return RefreshIndicator(onRefresh: _carregar, child: corpo);
  }

  Widget _conteudo(BuildContext context, Json d) {
    final mqtt = d.mapa('mqtt');
    final leituras = d.lista('ultimasLeituras');
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        const TituloSecao('Dispositivos'),
        _grade([
          CartaoIndicador(rotulo: 'Cadastrados', valor: Formatos.inteiro(d.inteiro('dispositivos')), icone: Icons.sensors, aoTocar: () => widget.aoAbrirAba?.call(1)),
          CartaoIndicador(rotulo: 'On-line', valor: Formatos.inteiro(d.inteiro('online')), icone: Icons.wifi_tethering, cor: CmCores.online, aoTocar: () => widget.aoAbrirAba?.call(1)),
          CartaoIndicador(rotulo: 'Sem comunicação', valor: Formatos.inteiro(d.inteiro('semComunicacao')), icone: Icons.portable_wifi_off, cor: CmCores.offline, aoTocar: () => widget.aoAbrirAba?.call(1)),
          CartaoIndicador(rotulo: 'Leituras em 24 h', valor: Formatos.inteiro(d.inteiro('leiturasUltimas24h')), icone: Icons.show_chart, cor: CmCores.tealEscuro),
        ]),
        const TituloSecao('Alertas'),
        _grade([
          CartaoIndicador(rotulo: 'Abertos', valor: Formatos.inteiro(d.inteiro('alertasAbertos')), icone: Icons.notification_important_outlined, cor: CmCores.offline, aoTocar: () => widget.aoAbrirAba?.call(3)),
          CartaoIndicador(rotulo: 'Reconhecidos', valor: Formatos.inteiro(d.inteiro('alertasReconhecidos')), icone: Icons.task_alt, cor: CmCores.aviso, aoTocar: () => widget.aoAbrirAba?.call(3)),
        ]),
        const TituloSecao('Atuação'),
        const _AtalhosAtuacao(),
        const TituloSecao('Integração MQTT'),
        _CartaoMqtt(mqtt: mqtt),
        const TituloSecao('Últimas leituras'),
        if (leituras.isEmpty)
          const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Nenhuma leitura recebida ainda.')))
        else
          Card(
            child: Column(children: [
              for (var i = 0; i < leituras.length; i++) ...[
                if (i > 0) const Divider(height: 1),
                _LinhaLeitura(l: leituras[i]),
              ],
            ]),
          ),
      ],
    );
  }

  /// Dois cartões por linha; em telas largas, quatro.
  Widget _grade(List<Widget> cartoes) => LayoutBuilder(builder: (context, c) {
        final colunas = c.maxWidth >= 600 ? 4 : 2;
        const espaco = 12.0;
        final largura = (c.maxWidth - espaco * (colunas - 1)) / colunas;
        return Wrap(
          spacing: espaco,
          runSpacing: espaco,
          children: [for (final w in cartoes) SizedBox(width: largura, child: w)],
        );
      });
}

/// Atalhos para a fila de comandos, cenas, agendamentos e automações.
class _AtalhosAtuacao extends StatelessWidget {
  const _AtalhosAtuacao();

  @override
  Widget build(BuildContext context) {
    final itens = <(IconData, String, String, Widget Function())>[
      (Icons.inbox_outlined, 'Fila de comandos', 'Situação de cada comando e tentativas', () => const FilaComandosPage()),
      (Icons.collections_outlined, 'Cenas', 'Vários comandos em um toque', () => const CenasPage()),
      (Icons.event_outlined, 'Agendamentos', 'Comando ou cena em data/hora ou toda semana', () => const AgendamentosPage()),
      (Icons.account_tree_outlined, 'Automações', 'Alerta de uma regra → comando ou cena', () => const AutomacoesPage()),
    ];
    return Card(
      child: Column(children: [
        for (var i = 0; i < itens.length; i++) ...[
          if (i > 0) const Divider(height: 1),
          ListTile(
            leading: Icon(itens[i].$1, color: CmCores.azul500),
            title: Text(itens[i].$2, maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text(itens[i].$3, maxLines: 2, overflow: TextOverflow.ellipsis),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => itens[i].$4())),
          ),
        ],
      ]),
    );
  }
}

class _CartaoMqtt extends StatelessWidget {
  const _CartaoMqtt({required this.mqtt});

  final Json mqtt;

  @override
  Widget build(BuildContext context) {
    final habilitado = mqtt.logico('habilitado');
    final conectado = mqtt.logico('conectado');
    final cor = !habilitado ? CmCores.neutro : (conectado ? CmCores.online : CmCores.offline);
    final estado = !habilitado ? 'Desligada' : (conectado ? 'Conectada' : 'Desconectada');
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(Icons.hub_outlined, color: cor),
              const SizedBox(width: 10),
              const Expanded(child: Text('Eventos do ChirpStack', style: TextStyle(fontWeight: FontWeight.w700))),
              ChipEstado(texto: estado, cor: cor),
            ]),
            const SizedBox(height: 6),
            LinhaDado('Última mensagem', Formatos.relativo(mqtt.data('ultimaMensagemEm'))),
            LinhaDado('Mensagens', Formatos.inteiro(mqtt.inteiro('mensagens'))),
            if (mqtt.texto('ultimoErro') != null) LinhaDado('Último erro', mqtt.texto('ultimoErro')!),
          ],
        ),
      ),
    );
  }
}

class _LinhaLeitura extends StatelessWidget {
  const _LinhaLeitura({required this.l});

  final Json l;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final rssi = l.inteiro('rssi');
    final snr = l.decimal('snr');
    return ListTile(
      leading: const CircleAvatar(radius: 18, child: Icon(Icons.sensors, size: 18)),
      title: Text(l.texto('dispositivoNome') ?? 'Dispositivo', maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        [Formatos.relativo(l.data('recebidoEm')), if (rssi != null) '$rssi dBm', if (snr != null) 'SNR ${Formatos.decimal(snr)}'].join(' · '),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(color: cs.onSurfaceVariant),
      ),
    );
  }
}

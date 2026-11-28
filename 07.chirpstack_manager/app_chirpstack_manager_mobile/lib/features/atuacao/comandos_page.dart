import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/lista_paginada.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/atualizacao_periodica.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';

bool get _podeAlterar => Sessao.instancia.usuario?.podeAlterar ?? false;

/// Cartão de um comando da fila: comando, status, tentativas e a próxima tentativa.
class CartaoComando extends StatelessWidget {
  const CartaoComando({super.key, required this.c, this.mostrarDispositivo = true, this.aoVoltar});

  final Json c;
  final bool mostrarDispositivo;

  /// Chamado ao voltar do detalhe (para recarregar a lista).
  final VoidCallback? aoVoltar;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final status = c.texto('status');
    final proxima = c.data('proximaTentativaEm');
    final info = TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () async {
          final id = c.inteiro('id');
          if (id == null) return;
          await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ComandoDetalhePage(id: id)));
          aoVoltar?.call();
        },
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(c.texto('tipoRotulo') ?? Atuacao.rotuloComando(c.texto('tipo')),
                      maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                  if (mostrarDispositivo && c.texto('dispositivoNome') != null) ...[
                    const SizedBox(height: 2),
                    Text(c.texto('dispositivoNome')!, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13)),
                  ],
                ]),
              ),
              const SizedBox(width: 8),
              Atuacao.chipStatus(status),
            ]),
            if (c.texto('resumo') != null) ...[
              const SizedBox(height: 6),
              Text(c.texto('resumo')!, maxLines: 3, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13.5, height: 1.4)),
            ],
            const SizedBox(height: 8),
            Wrap(spacing: 12, runSpacing: 4, children: [
              Text('Tentativas ${c.inteiro('tentativas') ?? 0}/${c.inteiro('maxTentativas') ?? 0}', style: info),
              Text(Atuacao.rotuloOrigem(c.texto('origem')), style: info),
              Text(Formatos.dataHora(c.data('criadoEm')), style: info),
              if (proxima != null && Atuacao.cancelavel(status)) Text('Próxima tentativa ${Formatos.dataHora(proxima)}', style: info),
            ]),
            if (c.texto('detalhe') != null && (status == 'FALHOU' || status == 'EXPIRADO')) ...[
              const SizedBox(height: 6),
              Text(c.texto('detalhe')!, maxLines: 3, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: Atuacao.corStatus(status))),
            ],
          ]),
        ),
      ),
    );
  }
}

/// Histórico de comandos de um dispositivo (aba "Comandos" do detalhe).
class ComandosDispositivo extends StatefulWidget {
  const ComandosDispositivo({super.key, required this.dispositivoId, this.indiceAba, this.aoEnviar});

  final int dispositivoId;
  final int? indiceAba;

  /// Abre o envio de comando (nulo = sem permissão ou equipamento não configurado).
  final Future<void> Function()? aoEnviar;

  @override
  State<ComandosDispositivo> createState() => _ComandosDispositivoState();
}

class _ComandosDispositivoState extends State<ComandosDispositivo> with AtualizacaoPeriodica {
  final _lista = GlobalKey<ListaPaginadaState>();

  @override
  int? get indiceAba => widget.indiceAba;

  @override
  void initState() {
    super.initState();
    iniciarAtualizacaoPeriodica();
  }

  @override
  Future<void> atualizarEmSegundoPlano() async => _lista.currentState?.recarregar();

  @override
  Widget build(BuildContext context) {
    return ListaPaginada(
      key: _lista,
      vazio: 'Nenhum comando enviado a este dispositivo.',
      iconeVazio: Icons.send_outlined,
      tamanhoPagina: 20,
      buscar: (p, t, _) => AtuacaoApi.comandosDoDispositivo(widget.dispositivoId, p, t),
      cabecalho: widget.aoEnviar == null
          ? null
          : Padding(
              padding: const EdgeInsets.only(top: 8),
              child: FilledButton.icon(
                onPressed: () async {
                  await widget.aoEnviar!();
                  await _lista.currentState?.recarregar();
                },
                icon: const Icon(Icons.send),
                label: const Text('Enviar comando'),
              ),
            ),
      item: (context, c) => CartaoComando(c: c, mostrarDispositivo: false, aoVoltar: () => _lista.currentState?.recarregar()),
    );
  }
}

/// Fila global de comandos: indicadores do resumo, filtro por status e o histórico.
class FilaComandosPage extends StatefulWidget {
  const FilaComandosPage({super.key});

  @override
  State<FilaComandosPage> createState() => _FilaComandosPageState();
}

class _FilaComandosPageState extends State<FilaComandosPage> with AtualizacaoPeriodica {
  final _lista = GlobalKey<ListaPaginadaState>();
  String _status = '';
  Json? _resumo;

  @override
  void initState() {
    super.initState();
    _carregarResumo();
    iniciarAtualizacaoPeriodica();
  }

  Future<void> _carregarResumo() async {
    try {
      final r = await AtuacaoApi.resumoComandos();
      if (mounted) setState(() => _resumo = r);
    } catch (_) {
      // O resumo é complementar: a lista mostra o erro, se houver.
    }
  }

  @override
  Future<void> atualizarEmSegundoPlano() async {
    await Future.wait([_carregarResumo(), _lista.currentState?.recarregar() ?? Future<void>.value()]);
  }

  Widget _indicadores() {
    final r = _resumo ?? const <String, dynamic>{};
    final cartoes = [
      CartaoIndicador(rotulo: 'Pendentes', valor: Formatos.inteiro(r.inteiro('pendentes') ?? 0), icone: Icons.hourglass_empty, cor: CmCores.azul500),
      CartaoIndicador(rotulo: 'Em andamento', valor: Formatos.inteiro(r.inteiro('emAndamento') ?? 0), icone: Icons.cell_tower, cor: CmCores.tealEscuro),
      CartaoIndicador(rotulo: 'Executados em 24 h', valor: Formatos.inteiro(r.inteiro('executados24h') ?? 0), icone: Icons.task_alt, cor: CmCores.online),
      CartaoIndicador(rotulo: 'Falhas em 24 h', valor: Formatos.inteiro(r.inteiro('falhas24h') ?? 0), icone: Icons.error_outline, cor: CmCores.offline),
    ];
    return LayoutBuilder(builder: (context, c) {
      final colunas = c.maxWidth >= 600 ? 4 : 2;
      const espaco = 12.0;
      final largura = (c.maxWidth - espaco * (colunas - 1)) / colunas;
      return Wrap(spacing: espaco, runSpacing: espaco, children: [for (final w in cartoes) SizedBox(width: largura, child: w)]);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Fila de comandos')),
      body: ListaPaginada(
        key: _lista,
        vazio: _status.isEmpty ? 'Nenhum comando na fila.' : 'Nenhum comando com este status.',
        iconeVazio: Icons.inbox_outlined,
        tamanhoPagina: 20,
        buscar: (p, t, _) => AtuacaoApi.comandos(status: _status, pagina: p, tamanho: t),
        cabecalho: Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            _indicadores(),
            const SizedBox(height: 16),
            CampoRotulado(
              rotulo: 'Status',
              espacoAbaixo: 4,
              child: DropdownButtonFormField<String>(
                initialValue: _status,
                isExpanded: true,
                items: [itemMenu('', 'Todos'), for (final s in Atuacao.statusComando) itemMenu(s, Atuacao.rotuloStatus(s))],
                onChanged: (s) {
                  setState(() => _status = s ?? '');
                  _lista.currentState?.recarregar();
                },
              ),
            ),
          ]),
        ),
        item: (context, c) => CartaoComando(c: c, aoVoltar: () => _lista.currentState?.recarregar()),
      ),
    );
  }
}

/// Detalhe de um comando: dados do envio, resposta e a linha do tempo das tentativas.
class ComandoDetalhePage extends StatefulWidget {
  const ComandoDetalhePage({super.key, required this.id});

  final int id;

  @override
  State<ComandoDetalhePage> createState() => _ComandoDetalhePageState();
}

class _ComandoDetalhePageState extends State<ComandoDetalhePage> with AtualizacaoPeriodica {
  Json? _d;
  Object? _erro;
  bool _carregando = true;
  bool _ocupado = false;

  @override
  void initState() {
    super.initState();
    _carregar();
    iniciarAtualizacaoPeriodica();
  }

  Future<void> _carregar() async {
    try {
      final d = await AtuacaoApi.comando(widget.id);
      if (mounted) {
        setState(() {
          _d = d;
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
  Future<void> atualizarEmSegundoPlano() async {
    if (Atuacao.cancelavel(_d?.mapa('comando').texto('status'))) await _carregar();
  }

  Future<void> _cancelar() async {
    final ok = await confirmar(context,
        titulo: 'Cancelar o comando?', texto: 'O comando sai da fila e não será mais transmitido. Se já estiver no ar, pode chegar ao dispositivo mesmo assim.', rotuloConfirmar: 'Cancelar comando', perigo: true);
    if (!ok) return;
    setState(() => _ocupado = true);
    try {
      await AtuacaoApi.cancelarComando(widget.id);
      Navegacao.avisar('Comando cancelado.');
      await _carregar();
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _reenviar() async {
    setState(() => _ocupado = true);
    try {
      final novo = await executarComCritico(context, (c) => AtuacaoApi.reenviarComando(widget.id, confirmarCritico: c));
      if (novo == null || !mounted) return;
      Navegacao.avisar('Comando reenviado.');
      final id = novo.inteiro('id');
      if (id != null) {
        await Navigator.of(context).pushReplacement(MaterialPageRoute<void>(builder: (_) => ComandoDetalhePage(id: id)));
      }
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _ocupado = false);
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
      corpo = _conteudo(context, d.mapa('comando'), d.lista('tentativas'));
    }
    return Scaffold(
      appBar: AppBar(title: Text('Comando #${widget.id}', maxLines: 1, overflow: TextOverflow.ellipsis)),
      body: RefreshIndicator(onRefresh: _carregar, child: corpo),
    );
  }

  Widget _conteudo(BuildContext context, Json c, List<Json> tentativas) {
    final cs = Theme.of(context).colorScheme;
    final status = c.texto('status');
    final janela = c.texto('janelaInicio') == null ? null : '${Atuacao.horaCurta(c.texto('janelaInicio'))} às ${Atuacao.horaCurta(c.texto('janelaFim'))}';
    final dias = c['diasSemana'] is List ? (c['diasSemana'] as List).whereType<num>().map((e) => e.toInt()).toList() : <int>[];
    final podeCancelar = _podeAlterar && Atuacao.cancelavel(status);
    final podeReenviar = _podeAlterar && !Atuacao.cancelavel(status);
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(child: Text(c.texto('tipoRotulo') ?? Atuacao.rotuloComando(c.texto('tipo')), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
                const SizedBox(width: 8),
                Atuacao.chipStatus(status),
              ]),
              if (c.texto('resumo') != null) ...[
                const SizedBox(height: 6),
                Text(c.texto('resumo')!, style: const TextStyle(fontSize: 13.5, height: 1.4)),
              ],
              const SizedBox(height: 4),
              LinhaDado('Dispositivo', c.texto('dispositivoNome') ?? '—'),
              LinhaDado('Origem', Atuacao.rotuloOrigem(c.texto('origem'))),
              LinhaDado('Pedido por', c.texto('criadoPor') ?? '—'),
              LinhaDado('Criado em', Formatos.dataHora(c.data('criadoEm'))),
              LinhaDado('Tentativas', '${c.inteiro('tentativas') ?? 0} de ${c.inteiro('maxTentativas') ?? 0}'),
              if (Atuacao.cancelavel(status)) LinhaDado('Próxima tentativa', Formatos.dataHora(c.data('proximaTentativaEm'))),
              LinhaDado('Validade', Formatos.dataHora(c.data('validadeEm'))),
              if (janela != null) LinhaDado('Janela', '$janela · ${Atuacao.dias(dias)}'),
              LinhaDado('Confirmação', Atuacao.rotuloEstrategia(c.texto('estrategia'))),
            ]),
          ),
        ),
        const TituloSecao('Envio'),
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              LinhaDado('Porta (fPort)', '${c.inteiro('fPort') ?? '—'}'),
              LinhaDado('Seq', c.inteiro('seq') == null || c.inteiro('seq') == 0 ? '—' : '${c.inteiro('seq')}'),
              LinhaDado('Downlink confirmado', c.logico('confirmado') ? 'Sim' : 'Não'),
              LinhaDado('Enfileirado', Formatos.dataHora(c.data('enfileiradoEm'))),
              LinhaDado('Transmitido', Formatos.dataHora(c.data('transmitidoEm'))),
              LinhaDado('Concluído', Formatos.dataHora(c.data('concluidoEm'))),
              const SizedBox(height: 6),
              Text('Bytes (hex)', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13.5)),
              const SizedBox(height: 6),
              CaixaCodigo(texto: c.texto('dadosHex') ?? '—'),
            ]),
          ),
        ),
        if (c.texto('resposta') != null || c.texto('detalhe') != null) ...[
          const TituloSecao('Resposta'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                if (c.texto('detalhe') != null) Text(c.texto('detalhe')!, style: const TextStyle(fontSize: 13.5, height: 1.45)),
                if (c.texto('resposta') != null) ...[
                  if (c.texto('detalhe') != null) const SizedBox(height: 10),
                  CaixaCodigo(texto: c.texto('resposta')!),
                ],
              ]),
            ),
          ),
        ],
        const TituloSecao('Tentativas'),
        if (tentativas.isEmpty)
          const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Nenhuma tentativa ainda: o comando aguarda a vez na fila.')))
        else
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
              child: Column(children: [
                for (var i = 0; i < tentativas.length; i++) _Tentativa(t: tentativas[i], ultima: i == tentativas.length - 1),
              ]),
            ),
          ),
        if (podeCancelar || podeReenviar) ...[
          const SizedBox(height: 20),
          if (podeCancelar)
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(foregroundColor: cs.error, minimumSize: const Size.fromHeight(48)),
              onPressed: _ocupado ? null : _cancelar,
              icon: const Icon(Icons.cancel_outlined),
              label: const Text('Cancelar comando'),
            ),
          if (podeReenviar)
            FilledButton.icon(onPressed: _ocupado ? null : _reenviar, icon: const Icon(Icons.replay), label: const Text('Reenviar')),
        ],
      ],
    );
  }
}

/// Texto técnico (hex, JSON) numa caixa monoespaçada que quebra linhas.
class CaixaCodigo extends StatelessWidget {
  const CaixaCodigo({super.key, required this.texto, this.maxLinhas});

  final String texto;
  final int? maxLinhas;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(color: cs.surfaceContainerHighest, borderRadius: BorderRadius.circular(10)),
      child: Text(texto, maxLines: maxLinhas, overflow: maxLinhas == null ? null : TextOverflow.ellipsis, style: const TextStyle(fontFamily: 'monospace', fontSize: 12, height: 1.4)),
    );
  }
}

class _Tentativa extends StatelessWidget {
  const _Tentativa({required this.t, required this.ultima});

  final Json t;
  final bool ultima;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final r = t.texto('resultado');
    final cor = Atuacao.corResultado(r);
    final info = TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant, height: 1.4);
    return IntrinsicHeight(
      child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Column(children: [
          Container(
            width: 28,
            height: 28,
            alignment: Alignment.center,
            decoration: BoxDecoration(color: cor.withValues(alpha: 0.16), shape: BoxShape.circle),
            child: Text('${t.inteiro('numero') ?? '?'}', style: TextStyle(color: cor, fontWeight: FontWeight.w800, fontSize: 12)),
          ),
          if (!ultima) Expanded(child: Container(width: 2, margin: const EdgeInsets.symmetric(vertical: 4), color: cs.outlineVariant)),
        ]),
        const SizedBox(width: 12),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.only(bottom: 14, top: 3),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              ChipEstado(texto: Atuacao.rotuloResultado(r), cor: cor),
              const SizedBox(height: 6),
              Text('Enfileirada ${Formatos.dataHora(t.data('enfileiradoEm'))}', style: info),
              if (t.data('transmitidoEm') != null) Text('Transmitida ${Formatos.dataHora(t.data('transmitidoEm'))}', style: info),
              if (t.data('concluidaEm') != null) Text('Concluída ${Formatos.dataHora(t.data('concluidaEm'))}', style: info),
              if (t.texto('detalhe') != null) ...[
                const SizedBox(height: 4),
                Text(t.texto('detalhe')!, style: const TextStyle(fontSize: 13, height: 1.4)),
              ],
              if (t.texto('chirpstackItemId') != null) ...[
                const SizedBox(height: 4),
                Text('Item ${t.texto('chirpstackItemId')}', style: info.copyWith(fontFamily: 'monospace', fontSize: 11.5)),
              ],
            ]),
          ),
        ),
      ]),
    );
  }
}

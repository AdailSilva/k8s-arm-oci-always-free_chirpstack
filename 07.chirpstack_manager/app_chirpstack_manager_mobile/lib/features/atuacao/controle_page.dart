import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/atualizacao_periodica.dart';
import 'package:chirpstack_manager/features/atuacao/comandos_page.dart';
import 'package:chirpstack_manager/features/atuacao/enviar_comando_page.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';

/// Envia um comando simples (sem tela de formulário) tratando a confirmação de carga crítica.
/// Devolve true se o comando entrou na fila.
Future<bool> enviarComandoRapido(BuildContext context, int dispositivoId, String tipo, Json parametros, {String? aviso}) async {
  try {
    final r = await executarComCritico(context, (c) => AtuacaoApi.enviarComando(dispositivoId, tipo, parametros, confirmarCritico: c));
    if (r == null) return false;
    Navegacao.avisar(aviso ?? '${r.texto('tipoRotulo') ?? Atuacao.rotuloComando(tipo)}: comando #${r.inteiro('id') ?? '?'} na fila.');
    return true;
  } catch (e) {
    Navegacao.avisar(mensagemDeErro(e));
    return false;
  }
}

/// Aba "Controle" do dispositivo: equipamento (modelo e periféricos), relés com estado desejado ×
/// reportado, ligar/desligar/alternar/pulso, enviar qualquer comando e o último STATUS.
class ControleDispositivo extends StatefulWidget {
  const ControleDispositivo({super.key, required this.dispositivoId, required this.dispositivoNome, this.indiceAba, this.aoMudarEquipamento});

  final int dispositivoId;
  final String dispositivoNome;
  final int? indiceAba;

  /// Avisado quando o equipamento é carregado, salvo ou removido.
  final ValueChanged<Json?>? aoMudarEquipamento;

  @override
  State<ControleDispositivo> createState() => _ControleDispositivoState();
}

class _ControleDispositivoState extends State<ControleDispositivo> with AtualizacaoPeriodica {
  Json? _eq;
  Object? _erro;
  bool _carregando = true;
  String? _enviando;

  @override
  int? get indiceAba => widget.indiceAba;

  @override
  Duration get intervaloAtualizacao => const Duration(seconds: 15);

  bool get _podeAlterar => Sessao.instancia.usuario?.podeAlterar ?? false;

  @override
  void initState() {
    super.initState();
    _carregar();
    iniciarAtualizacaoPeriodica();
  }

  Future<void> _carregar() async {
    try {
      final e = await AtuacaoApi.equipamento(widget.dispositivoId);
      if (mounted) {
        setState(() {
          _eq = e;
          _erro = null;
        });
        widget.aoMudarEquipamento?.call(e);
      }
    } catch (e) {
      if (mounted) setState(() => _erro = e);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  @override
  Future<void> atualizarEmSegundoPlano() => _carregar();

  bool _suporta(String tipo) => (_eq?.lista('comandos') ?? const []).any((c) => c.texto('tipo') == tipo);

  Future<void> _rapido(String chave, String tipo, Json parametros) async {
    setState(() => _enviando = chave);
    final ok = await enviarComandoRapido(context, widget.dispositivoId, tipo, parametros);
    if (!mounted) return;
    setState(() => _enviando = null);
    if (ok) await _carregar();
  }

  Future<void> _pulso(Json rele) async {
    final canal = rele.inteiro('canal');
    if (canal == null) return;
    final r = await showDialog<({double segundos, bool ligado})>(context: context, builder: (_) => _DialogoPulso(nome: rele.texto('nome') ?? 'Relé $canal'));
    if (r == null || !mounted) return;
    await _rapido('pulso-$canal', 'RELE_PULSO', {'canal': canal, 'estado': r.ligado, 'duracaoMs': (r.segundos * 1000).round()});
  }

  Future<void> _enviarComando() async {
    final eq = _eq;
    if (eq == null) return;
    final ok = await Navigator.of(context).push<bool>(MaterialPageRoute(
      builder: (_) => EnviarComandoPage(dispositivoId: widget.dispositivoId, dispositivoNome: widget.dispositivoNome, equipamento: eq),
    ));
    if (ok == true) await _carregar();
  }

  Future<void> _configurar() async {
    final salvo = await Navigator.of(context).push<bool>(MaterialPageRoute(
      builder: (_) => EquipamentoPage(dispositivoId: widget.dispositivoId, dispositivoNome: widget.dispositivoNome, atual: _eq),
    ));
    if (salvo == true) await _carregar();
  }

  @override
  Widget build(BuildContext context) {
    final eq = _eq;
    Widget corpo;
    if (_carregando) {
      corpo = const Carregando();
    } else if (eq == null) {
      corpo = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar o equipamento', texto: mensagemDeErro(_erro ?? 'erro'), acao: _carregar);
    } else if (!eq.logico('configurado')) {
      final cs = Theme.of(context).colorScheme;
      corpo = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(28, 56, 28, 28),
        children: [
          Icon(Icons.tune, size: 48, color: cs.onSurfaceVariant),
          const SizedBox(height: 16),
          Text('Equipamento não configurado', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Text(
            'Informe o modelo do firmware e o que está ligado a ele (relés, medidor PZEM-004T) para acionar os relés, ler a energia e usar cenas, agendamentos e automações.',
            textAlign: TextAlign.center,
            style: TextStyle(color: cs.onSurfaceVariant, height: 1.45),
          ),
          if (_podeAlterar) ...[
            const SizedBox(height: 20),
            Center(child: FilledButton.icon(style: FilledButton.styleFrom(minimumSize: const Size(0, 44)), onPressed: _configurar, icon: const Icon(Icons.settings_outlined), label: const Text('Configurar equipamento'))),
          ],
        ],
      );
    } else {
      corpo = _conteudo(context, eq);
    }
    return RefreshIndicator(onRefresh: _carregar, child: corpo);
  }

  Widget _conteudo(BuildContext context, Json eq) {
    final cs = Theme.of(context).colorScheme;
    final reles = eq.lista('perifericos').where((p) => p.texto('tipo') == 'RELE').toList()..sort((a, b) => (a.inteiro('canal') ?? 0).compareTo(b.inteiro('canal') ?? 0));
    final medidores = eq.lista('perifericos').where((p) => p.texto('tipo') == 'PZEM004T').toList();
    final status = eq.texto('status');
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Icon(Icons.memory, color: CmCores.azul500),
                const SizedBox(width: 10),
                Expanded(child: Text(eq.texto('modeloRotulo') ?? eq.texto('modelo') ?? '—', style: const TextStyle(fontWeight: FontWeight.w700))),
              ]),
              const SizedBox(height: 6),
              LinhaDado('Reconciliar relés', eq.logico('reconciliar') ? 'Sim' : 'Não'),
              if (eq.decimal('tarifaKwh') != null) LinhaDado('Tarifa', '${_moeda(eq.decimal('tarifaKwh'))}/kWh'),
              LinhaDado('Último STATUS', Formatos.relativo(eq.data('statusEm'))),
            ]),
          ),
        ),
        if (_podeAlterar) ...[
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [
            FilledButton.icon(style: FilledButton.styleFrom(minimumSize: const Size(0, 44)), onPressed: _enviarComando, icon: const Icon(Icons.send), label: const Text('Enviar comando')),
            if (_suporta('STATUS'))
              OutlinedButton.icon(
                onPressed: _enviando != null ? null : () => _rapido('status', 'STATUS', const {}),
                icon: const Icon(Icons.info_outline),
                label: const Text('Pedir STATUS'),
              ),
            OutlinedButton.icon(onPressed: _configurar, icon: const Icon(Icons.settings_outlined), label: const Text('Equipamento')),
          ]),
        ],
        if (reles.isNotEmpty) ...[
          const TituloSecao('Relés'),
          for (final r in reles) _CartaoRele(
            r: r,
            podeAlterar: _podeAlterar,
            suportaDefinir: _suporta('RELE_DEFINIR'),
            suportaPulso: _suporta('RELE_PULSO'),
            ocupado: _enviando != null && _enviando!.endsWith('-${r.inteiro('canal')}'),
            aoDefinir: (acao) => _rapido('$acao-${r.inteiro('canal')}', 'RELE_DEFINIR', {'canal': r.inteiro('canal'), 'acao': acao}),
            aoPulso: () => _pulso(r),
          ),
        ],
        if (medidores.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(
            '${medidores.length == 1 ? '1 medidor de energia' : '${medidores.length} medidores de energia'}: veja a aba Energia.',
            style: TextStyle(color: cs.onSurfaceVariant),
          ),
        ],
        if (status != null) ...[
          const TituloSecao('Último STATUS'),
          CaixaCodigo(texto: Atuacao.jsonDeTexto(status) == null ? status : _bonito(status), maxLinhas: 14),
        ],
      ],
    );
  }

  static String _bonito(String json) {
    final m = Atuacao.jsonDeTexto(json);
    if (m == null) return json;
    return m.entries.map((e) => '${e.key}: ${e.value}').join('\n');
  }
}

String _moeda(num? v) {
  if (v == null) return '—';
  final s = v.toStringAsFixed(v.abs() < 1 ? 4 : 2).replaceAll('.', ',');
  return 'R\$ $s';
}

class _CartaoRele extends StatelessWidget {
  const _CartaoRele({
    required this.r,
    required this.podeAlterar,
    required this.suportaDefinir,
    required this.suportaPulso,
    required this.ocupado,
    required this.aoDefinir,
    required this.aoPulso,
  });

  final Json r;
  final bool podeAlterar;
  final bool suportaDefinir;
  final bool suportaPulso;
  final bool ocupado;
  final ValueChanged<String> aoDefinir;
  final VoidCallback aoPulso;

  static String _estado(Json r, String chave) {
    final v = r[chave];
    return v == true ? 'Ligado' : v == false ? 'Desligado' : '—';
  }

  static Color _cor(Json r, String chave) {
    final v = r[chave];
    return v == true ? CmCores.online : v == false ? CmCores.neutro : CmCores.neutro.withValues(alpha: 0.6);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final desejado = r['estadoDesejado'];
    final reportado = r['estadoReportado'];
    final divergente = desejado is bool && reportado is bool && desejado != reportado;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(r.texto('nome') ?? 'Relé', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                Text('Canal ${r.inteiro('canal') ?? '?'}${r.inteiro('gpio') != null ? ' · GPIO ${r.inteiro('gpio')}' : ''}',
                    style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
              ]),
            ),
            if (r.logico('critico')) const ChipEstado(texto: 'Crítico', cor: CmCores.offline),
          ]),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: _Estado(rotulo: 'Desejado', valor: _estado(r, 'estadoDesejado'), cor: _cor(r, 'estadoDesejado'))),
            const SizedBox(width: 8),
            Expanded(
              child: _Estado(
                rotulo: 'Reportado',
                valor: _estado(r, 'estadoReportado'),
                cor: _cor(r, 'estadoReportado'),
                detalhe: r.data('reportadoEm') == null ? null : Formatos.relativo(r.data('reportadoEm')),
              ),
            ),
          ]),
          if (divergente) ...[
            const SizedBox(height: 8),
            const CaixaMensagem(texto: 'Estado reportado diferente do desejado.', cor: CmCores.aviso, icone: Icons.sync_problem),
          ],
          if (podeAlterar && (suportaDefinir || suportaPulso)) ...[
            const SizedBox(height: 10),
            if (ocupado)
              const LinearProgressIndicator()
            else
              Wrap(spacing: 8, runSpacing: 8, children: [
                if (suportaDefinir) ...[
                  FilledButton.tonal(style: FilledButton.styleFrom(minimumSize: const Size(0, 44)), onPressed: () => aoDefinir('LIGAR'), child: const Text('Ligar')),
                  OutlinedButton(onPressed: () => aoDefinir('DESLIGAR'), child: const Text('Desligar')),
                  OutlinedButton(onPressed: () => aoDefinir('ALTERNAR'), child: const Text('Alternar')),
                ],
                if (suportaPulso) OutlinedButton.icon(onPressed: aoPulso, icon: const Icon(Icons.timer_outlined, size: 18), label: const Text('Pulso')),
              ]),
          ],
        ]),
      ),
    );
  }
}

class _Estado extends StatelessWidget {
  const _Estado({required this.rotulo, required this.valor, required this.cor, this.detalhe});

  final String rotulo;
  final String valor;
  final Color cor;
  final String? detalhe;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(color: cs.surfaceContainerHighest, borderRadius: BorderRadius.circular(10)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(rotulo, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
        Text(valor, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontWeight: FontWeight.w700, color: cor)),
        if (detalhe != null) Text(detalhe!, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 11.5, color: cs.onSurfaceVariant)),
      ]),
    );
  }
}

class _DialogoPulso extends StatefulWidget {
  const _DialogoPulso({required this.nome});

  final String nome;

  @override
  State<_DialogoPulso> createState() => _DialogoPulsoState();
}

class _DialogoPulsoState extends State<_DialogoPulso> {
  final _segundos = TextEditingController(text: '5');
  bool _ligado = true;
  String? _erro;

  @override
  void dispose() {
    _segundos.dispose();
    super.dispose();
  }

  void _ok() {
    final s = double.tryParse(_segundos.text.trim().replaceAll(',', '.'));
    if (s == null || s < 0.1 || s > 86400) {
      setState(() => _erro = 'De 0,1 s a 86.400 s (24 h).');
      return;
    }
    Navigator.pop(context, (segundos: s, ligado: _ligado));
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text('Pulso: ${widget.nome}', maxLines: 2, overflow: TextOverflow.ellipsis),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            CampoRotulado(
              rotulo: 'Duração (s)',
              ajuda: 'Depois do tempo o relé volta ao estado anterior.',
              child: TextField(
                controller: _segundos,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
                decoration: InputDecoration(hintText: 'Ex.: 5', errorText: _erro, errorMaxLines: 3),
              ),
            ),
            InterruptorRotulado(
              titulo: _ligado ? 'Ligar durante o pulso' : 'Desligar durante o pulso',
              valor: _ligado,
              aoMudar: (b) => setState(() => _ligado = b),
            ),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(0, 44)), onPressed: _ok, child: const Text('Enviar pulso')),
        ],
      );
}

// ── configuração do equipamento ──

class _PerifericoForm {
  _PerifericoForm({required this.tipo, required int canal, required String nome, int? gpio, int? enderecoModbus, this.critico = false})
      : canal = TextEditingController(text: '$canal'),
        nome = TextEditingController(text: nome),
        gpio = TextEditingController(text: gpio?.toString() ?? ''),
        modbus = TextEditingController(text: enderecoModbus?.toString() ?? '');

  final String tipo;
  final TextEditingController canal;
  final TextEditingController nome;
  final TextEditingController gpio;
  final TextEditingController modbus;
  bool critico;

  void dispose() {
    for (final c in [canal, nome, gpio, modbus]) {
      c.dispose();
    }
  }
}

/// Configuração do equipamento: modelo do firmware, reconciliação, tarifa e periféricos.
class EquipamentoPage extends StatefulWidget {
  const EquipamentoPage({super.key, required this.dispositivoId, required this.dispositivoNome, this.atual});

  final int dispositivoId;
  final String dispositivoNome;

  /// EquipamentoResponse atual (configurado ou não).
  final Json? atual;

  @override
  State<EquipamentoPage> createState() => _EquipamentoPageState();
}

class _EquipamentoPageState extends State<EquipamentoPage> {
  List<Json> _modelos = const [];
  bool _carregando = true;
  bool _salvando = false;
  Object? _erroCarga;
  String? _erro;

  String? _modelo;
  bool _reconciliar = true;
  final _tarifa = TextEditingController();
  final List<_PerifericoForm> _perifericos = [];

  bool get _configurado => widget.atual?.logico('configurado') ?? false;

  @override
  void initState() {
    super.initState();
    final a = widget.atual;
    if (a != null && a.logico('configurado')) {
      _modelo = a.texto('modelo');
      _reconciliar = a.logico('reconciliar', true);
      final t = a.decimal('tarifaKwh');
      if (t != null) _tarifa.text = t.toString().replaceAll('.', ',');
      for (final p in a.lista('perifericos')) {
        _perifericos.add(_PerifericoForm(
          tipo: p.texto('tipo') ?? 'RELE',
          canal: p.inteiro('canal') ?? 0,
          nome: p.texto('nome') ?? '',
          gpio: p.inteiro('gpio'),
          enderecoModbus: p.inteiro('enderecoModbus'),
          critico: p.logico('critico'),
        ));
      }
    }
    _carregar();
  }

  @override
  void dispose() {
    _tarifa.dispose();
    for (final p in _perifericos) {
      p.dispose();
    }
    super.dispose();
  }

  Future<void> _carregar() async {
    try {
      final m = await AtuacaoApi.modelos();
      if (mounted) {
        setState(() {
          _modelos = m;
          _modelo ??= m.isEmpty ? null : m.first.texto('modelo');
          _erroCarga = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _erroCarga = e);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  Json? get _modeloSel {
    for (final m in _modelos) {
      if (m.texto('modelo') == _modelo) return m;
    }
    return null;
  }

  List<Json> get _suportados => _modeloSel?.lista('perifericos') ?? const [];

  String _rotuloTipo(String tipo) {
    for (final m in _modelos) {
      for (final p in m.lista('perifericos')) {
        if (p.texto('tipo') == tipo) return p.texto('rotulo') ?? tipo;
      }
    }
    return tipo == 'RELE' ? 'Relé' : 'Medidor PZEM-004T';
  }

  void _mudarModelo(String? m) {
    setState(() {
      _modelo = m;
      final aceitos = _suportados.map((p) => p.texto('tipo')).toSet();
      _perifericos.removeWhere((p) {
        final sai = !aceitos.contains(p.tipo);
        if (sai) p.dispose();
        return sai;
      });
    });
  }

  void _adicionar(String tipo) {
    final usados = _perifericos.where((p) => p.tipo == tipo).map((p) => int.tryParse(p.canal.text)).toSet();
    var canal = 0;
    while (usados.contains(canal) && canal < 15) {
      canal++;
    }
    setState(() => _perifericos.add(_PerifericoForm(
          tipo: tipo,
          canal: canal,
          nome: tipo == 'RELE' ? 'Relé ${canal + 1}' : 'Medidor ${canal + 1}',
          enderecoModbus: tipo == 'PZEM004T' ? canal + 1 : null,
        )));
  }

  void _remover(int i) {
    setState(() => _perifericos.removeAt(i).dispose());
  }

  Future<void> _salvar() async {
    FocusScope.of(context).unfocus();
    final modelo = _modelo;
    String? erro;
    final vistos = <String>{};
    final lista = <Json>[];
    if (modelo == null) erro = 'Escolha o modelo do firmware.';
    final tarifaTexto = _tarifa.text.trim().replaceAll(',', '.');
    final tarifa = tarifaTexto.isEmpty ? null : double.tryParse(tarifaTexto);
    if (erro == null && tarifaTexto.isNotEmpty && (tarifa == null || tarifa < 0)) erro = 'Tarifa inválida (ex.: 0,95).';
    for (final p in _perifericos) {
      if (erro != null) break;
      final canal = int.tryParse(p.canal.text.trim());
      final nome = p.nome.text.trim();
      if (canal == null || canal < 0 || canal > 15) {
        erro = 'Canal de 0 a 15 em todos os periféricos.';
      } else if (nome.isEmpty) {
        erro = 'Informe o nome de todos os periféricos.';
      } else if (p.tipo == 'RELE' && modelo == 'RADIOENGE_ORIGINAL' && int.tryParse(p.gpio.text.trim()) == null) {
        erro = 'Informe o GPIO do relé "$nome".';
      } else if (!vistos.add('${p.tipo}-$canal')) {
        erro = 'Canal $canal repetido em ${_rotuloTipo(p.tipo)}.';
      } else {
        lista.add({
          'tipo': p.tipo,
          'canal': canal,
          'nome': nome,
          'gpio': p.tipo == 'RELE' ? int.tryParse(p.gpio.text.trim()) : null,
          'enderecoModbus': p.tipo == 'PZEM004T' ? int.tryParse(p.modbus.text.trim()) : null,
          'critico': p.tipo == 'RELE' && p.critico,
        });
      }
    }
    if (erro != null) {
      setState(() => _erro = erro);
      return;
    }
    setState(() {
      _erro = null;
      _salvando = true;
    });
    try {
      await AtuacaoApi.salvarEquipamento(widget.dispositivoId, {'modelo': modelo, 'reconciliar': _reconciliar, 'tarifaKwh': tarifa, 'perifericos': lista});
      if (!mounted) return;
      Navegacao.avisar('Equipamento salvo.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  Future<void> _removerEquipamento() async {
    final ok = await confirmar(
      context,
      titulo: 'Remover equipamento',
      texto: 'Os periféricos deixam de existir; comandos pendentes, cenas, agendamentos e automações deste dispositivo podem falhar.',
      rotuloConfirmar: 'Remover',
      perigo: true,
      icone: Icons.delete_outline,
    );
    if (!ok || !mounted) return;
    try {
      await AtuacaoApi.removerEquipamento(widget.dispositivoId);
      if (!mounted) return;
      Navegacao.avisar('Equipamento removido.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    Widget corpo;
    if (_carregando) {
      corpo = const Carregando();
    } else if (_modelos.isEmpty) {
      corpo = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar os modelos', texto: mensagemDeErro(_erroCarga ?? 'erro'), acao: _carregar);
    } else {
      final m = _modeloSel;
      corpo = ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          Text(widget.dispositivoNome, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
          const SizedBox(height: 16),
          if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
          CampoRotulado(
            rotulo: 'Modelo do firmware',
            ajuda: m?.texto('descricao'),
            child: DropdownButtonFormField<String>(
              initialValue: _modelo,
              isExpanded: true,
              items: [for (final x in _modelos) itemMenu(x.texto('modelo')!, x.texto('rotulo') ?? x.texto('modelo')!)],
              onChanged: _mudarModelo,
            ),
          ),
          for (final o in (m?['observacoes'] is List ? (m!['observacoes'] as List).map((e) => '$e') : const <String>[]))
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Text('• $o', style: TextStyle(fontSize: 12.5, height: 1.4, color: cs.onSurfaceVariant)),
            ),
          const SizedBox(height: 8),
          InterruptorRotulado(
            titulo: 'Reconciliar relés',
            subtitulo: 'Se o estado reportado divergir do desejado (reinício, acionamento local), o servidor manda de volta.',
            valor: _reconciliar,
            aoMudar: (b) => setState(() => _reconciliar = b),
          ),
          CampoRotulado(
            rotulo: 'Tarifa de energia (R\$/kWh)',
            ajuda: 'Para calcular o custo na aba Energia. Em branco, sem custo.',
            child: TextField(
              controller: _tarifa,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
              decoration: const InputDecoration(hintText: 'Ex.: 0,95'),
            ),
          ),
          const TituloSecao('Periféricos'),
          if (_perifericos.isEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text('Nenhum periférico. Adicione os relés e medidores ligados ao equipamento.', style: TextStyle(color: cs.onSurfaceVariant)),
            ),
          for (var i = 0; i < _perifericos.length; i++) _cartaoPeriferico(context, i),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final p in _suportados)
              OutlinedButton.icon(
                onPressed: () => _adicionar(p.texto('tipo')!),
                icon: const Icon(Icons.add),
                label: Text('Adicionar ${p.texto('rotulo') ?? p.texto('tipo')}', maxLines: 1, overflow: TextOverflow.ellipsis),
              ),
          ]),
          const SizedBox(height: 8),
          Text('Relé crítico (bomba, portão, aquecedor) pede confirmação antes de qualquer acionamento.', style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
          const SizedBox(height: 20),
          FilledButton.icon(onPressed: _salvando ? null : _salvar, icon: const Icon(Icons.check), label: Text(_salvando ? 'Salvando…' : 'Salvar')),
          if (_configurado) ...[
            const SizedBox(height: 8),
            TextButton.icon(
              onPressed: _salvando ? null : _removerEquipamento,
              style: TextButton.styleFrom(foregroundColor: cs.error),
              icon: const Icon(Icons.delete_outline),
              label: const Text('Remover equipamento'),
            ),
          ],
        ],
      );
    }
    return Scaffold(appBar: AppBar(title: const Text('Equipamento')), body: corpo);
  }

  Widget _cartaoPeriferico(BuildContext context, int i) {
    final p = _perifericos[i];
    final rele = p.tipo == 'RELE';
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 8, 8, 6),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Icon(rele ? Icons.power_settings_new : Icons.bolt, size: 20, color: rele ? CmCores.azul500 : CmCores.aviso),
            const SizedBox(width: 8),
            Expanded(child: Text(_rotuloTipo(p.tipo), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700))),
            IconButton(tooltip: 'Remover', icon: const Icon(Icons.delete_outline), onPressed: () => _remover(i)),
          ]),
          Padding(
            padding: const EdgeInsets.only(right: 6),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              CampoRotulado(
                rotulo: 'Nome',
                child: TextField(controller: p.nome, maxLength: 80, decoration: InputDecoration(hintText: rele ? 'Ex.: Bomba da caixa' : 'Ex.: Quadro geral', counterText: '')),
              ),
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(
                  child: CampoRotulado(
                    rotulo: 'Canal',
                    child: TextField(controller: p.canal, keyboardType: TextInputType.number, inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(2)]),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: rele
                      ? CampoRotulado(
                          rotulo: _modelo == 'RADIOENGE_ORIGINAL' ? 'GPIO' : 'GPIO (opcional)',
                          child: TextField(controller: p.gpio, keyboardType: TextInputType.number, inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(2)]),
                        )
                      : CampoRotulado(
                          rotulo: 'Endereço Modbus',
                          child: TextField(controller: p.modbus, keyboardType: TextInputType.number, inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(3)]),
                        ),
                ),
              ]),
              if (rele)
                InterruptorRotulado(
                  titulo: 'Carga crítica',
                  subtitulo: 'Pede confirmação antes de acionar.',
                  valor: p.critico,
                  aoMudar: (b) => setState(() => p.critico = b),
                ),
            ]),
          ),
        ]),
      ),
    );
  }
}

/// Aba "Comandos" do dispositivo: histórico + botão de envio (se houver equipamento e permissão).
class ComandosDoDispositivo extends StatelessWidget {
  const ComandosDoDispositivo({super.key, required this.dispositivoId, required this.dispositivoNome, required this.equipamento, this.indiceAba});

  final int dispositivoId;
  final String dispositivoNome;

  /// EquipamentoResponse já carregado (nulo = ainda não carregou).
  final Json? equipamento;
  final int? indiceAba;

  @override
  Widget build(BuildContext context) {
    final eq = equipamento;
    final pode = (Sessao.instancia.usuario?.podeAlterar ?? false) && eq != null && eq.logico('configurado');
    return ComandosDispositivo(
      dispositivoId: dispositivoId,
      indiceAba: indiceAba,
      aoEnviar: !pode
          ? null
          : () async {
              await Navigator.of(context).push<bool>(MaterialPageRoute(
                builder: (_) => EnviarComandoPage(dispositivoId: dispositivoId, dispositivoNome: dispositivoNome, equipamento: eq),
              ));
            },
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';
import 'package:chirpstack_manager/features/atuacao/parametros.dart';

/// Executa uma ação que a API pode recusar por carga crítica (HTTP 409 "Carga crítica…"): pergunta
/// ao usuário e, se ele confirmar, repete com confirmarCritico = true. Nulo = o usuário desistiu.
/// Os demais erros seguem para quem chamou.
Future<T?> executarComCritico<T>(BuildContext context, Future<T> Function(bool confirmarCritico) acao) async {
  try {
    return await acao(false);
  } on ApiException catch (e) {
    if (!Atuacao.ehCargaCritica(e.status, e.mensagem) || !context.mounted) rethrow;
    final ok = await confirmarCargaCritica(context, e.mensagem!);
    if (!ok) return null;
    return await acao(true);
  }
}

/// Diálogo explícito de acionamento de carga crítica.
Future<bool> confirmarCargaCritica(BuildContext context, String mensagem) => confirmar(
      context,
      titulo: 'Carga crítica',
      texto: '$mensagem\n\nEste acionamento pode desligar ou ligar um equipamento essencial. Confirma mesmo assim?',
      rotuloConfirmar: 'Confirmar acionamento',
      perigo: true,
      icone: Icons.warning_amber_rounded,
    );

/// Rótulo de uma opção em uma linha: em telas estreitas encolhe em vez de quebrar a palavra.
class RotuloEncolhe extends StatelessWidget {
  const RotuloEncolhe(this.texto, {super.key});

  final String texto;

  @override
  Widget build(BuildContext context) => FittedBox(fit: BoxFit.scaleDown, child: Text(texto, maxLines: 1, softWrap: false));
}

/// Item de lista suspensa com texto que nunca transborda.
DropdownMenuItem<T> itemMenu<T>(T valor, String texto) =>
    DropdownMenuItem<T>(value: valor, child: Text(texto, maxLines: 1, overflow: TextOverflow.ellipsis));

/// Interruptor com título e explicação (texto quebra em várias linhas).
class InterruptorRotulado extends StatelessWidget {
  const InterruptorRotulado({super.key, required this.titulo, this.subtitulo, required this.valor, required this.aoMudar});

  final String titulo;
  final String? subtitulo;
  final bool valor;
  final ValueChanged<bool>? aoMudar;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(titulo, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
            if (subtitulo != null) ...[
              const SizedBox(height: 2),
              Text(subtitulo!, style: TextStyle(fontSize: 12.5, height: 1.4, color: cs.onSurfaceVariant)),
            ],
          ]),
        ),
        const SizedBox(width: 12),
        Switch(value: valor, onChanged: aoMudar),
      ]),
    );
  }
}

/// Formulário "comando + parâmetros", o mesmo no envio, nas cenas, nos agendamentos e nas automações.
///
/// [comandos] são os ComandoSuportado do equipamento ({tipo, rotulo}); [perifericos] os periféricos
/// dele ({tipo, canal, nome, critico}) para escolher relé/medidor pelo nome. [erros] vem de
/// [ValoresComando.validar] e aparece embaixo de cada campo.
class FormComando extends StatefulWidget {
  const FormComando({
    super.key,
    required this.valores,
    required this.comandos,
    this.perifericos = const [],
    this.erros = const {},
    this.aoMudar,
    this.mostrarTipo = true,
  });

  final ValoresComando valores;
  final List<Json> comandos;
  final List<Json> perifericos;
  final Map<String, String> erros;
  final VoidCallback? aoMudar;
  final bool mostrarTipo;

  @override
  State<FormComando> createState() => _FormComandoState();
}

class _FormComandoState extends State<FormComando> {
  late final _duracao = TextEditingController(text: widget.valores.duracaoMs);
  late final _segundos = TextEditingController(text: widget.valores.segundos);
  late final _atraso = TextEditingController(text: widget.valores.atrasoMs);
  late final _watts = TextEditingController(text: widget.valores.watts);
  late final _porta = TextEditingController(text: widget.valores.porta);
  late final _hex = TextEditingController(text: widget.valores.dadosHex);
  late final _canal = TextEditingController(text: widget.valores.canal?.toString() ?? '');

  static const _todos = -1;

  ValoresComando get v => widget.valores;

  @override
  void dispose() {
    for (final c in [_duracao, _segundos, _atraso, _watts, _porta, _hex, _canal]) {
      c.dispose();
    }
    super.dispose();
  }

  void _mudou(VoidCallback f) {
    setState(f);
    widget.aoMudar?.call();
  }

  List<Json> _perifericosDo(String? tipo) => widget.perifericos.where((p) => p.texto('tipo') == tipo).toList();

  String _nomePeriferico(Json p) {
    final tipo = p.texto('tipo') == 'RELE' ? 'Relé' : 'Medidor';
    final nome = p.texto('nome');
    return [
      '$tipo ${p.inteiro('canal') ?? '?'}',
      ?nome,
      if (p.logico('critico')) '(crítico)',
    ].join(' · ').replaceFirst(' · (crítico)', ' (crítico)');
  }

  Widget _campoNumero(TextEditingController c, String chave, String dica, void Function(String) aoMudar) => TextField(
        controller: c,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        onChanged: (s) => _mudou(() => aoMudar(s)),
        decoration: InputDecoration(hintText: dica, errorText: widget.erros[chave], errorMaxLines: 3),
      );

  @override
  Widget build(BuildContext context) {
    final tipos = widget.comandos.where((c) => c.texto('tipo') != null).toList();
    final tipoValido = tipos.any((c) => c.texto('tipo') == v.tipo) ? v.tipo : null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.mostrarTipo)
          CampoRotulado(
            rotulo: 'Comando',
            child: DropdownButtonFormField<String>(
              key: ValueKey('tipo-$tipoValido-${tipos.length}'),
              initialValue: tipoValido,
              isExpanded: true,
              hint: const Text('Escolha o comando', maxLines: 1, overflow: TextOverflow.ellipsis),
              decoration: InputDecoration(errorText: widget.erros['tipo'], errorMaxLines: 3),
              items: [for (final c in tipos) itemMenu(c.texto('tipo')!, c.texto('rotulo') ?? Atuacao.rotuloComando(c.texto('tipo')))],
              onChanged: (t) => _mudou(() => v.tipo = t),
            ),
          ),
        ..._parametros(context),
      ],
    );
  }

  List<Widget> _parametros(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final campos = <Widget>[];
    if (v.usaCanal) campos.add(_campoCanal());
    switch (v.tipo) {
      case 'RELE_DEFINIR':
        campos.add(CampoRotulado(
          rotulo: 'Ação',
          child: SegmentedButton<String>(
            showSelectedIcon: false,
            segments: const [
              ButtonSegment(value: 'LIGAR', label: RotuloEncolhe('Ligar')),
              ButtonSegment(value: 'DESLIGAR', label: RotuloEncolhe('Desligar')),
              ButtonSegment(value: 'ALTERNAR', label: RotuloEncolhe('Alternar')),
            ],
            selected: {v.acao},
            onSelectionChanged: (s) => _mudou(() => v.acao = s.first),
          ),
        ));
      case 'RELE_PULSO':
        campos.add(InterruptorRotulado(
          titulo: v.estado ? 'Ligar durante o pulso' : 'Desligar durante o pulso',
          subtitulo: 'Ao fim da duração o relé volta ao estado oposto.',
          valor: v.estado,
          aoMudar: (b) => _mudou(() => v.estado = b),
        ));
        campos.add(CampoRotulado(
          rotulo: 'Duração (ms)',
          ajuda: 'De 100 ms a 24 h (86.400.000 ms). Ex.: 1000 = 1 segundo.',
          child: _campoNumero(_duracao, 'duracaoMs', 'Ex.: 1000', (s) => v.duracaoMs = s),
        ));
      case 'RELE_MASCARA':
        campos.addAll(_mascara(cs));
      case 'PZEM_LIMITE_POTENCIA':
        campos.add(CampoRotulado(
          rotulo: 'Limite de potência (W)',
          ajuda: 'De 0 a 23.000 W. Acima do limite o medidor sinaliza o alarme.',
          child: _campoNumero(_watts, 'watts', 'Ex.: 3500', (s) => v.watts = s),
        ));
      case 'INTERVALO':
        campos.add(CampoRotulado(
          rotulo: 'Intervalo de envio (s)',
          ajuda: 'De 5 s a 24 h (86.400 s), conforme o firmware.',
          child: _campoNumero(_segundos, 'segundos', 'Ex.: 300', (s) => v.segundos = s),
        ));
      case 'CONFIRMADO':
        campos.add(InterruptorRotulado(
          titulo: 'Uplinks confirmados',
          subtitulo: 'O dispositivo passa a pedir confirmação (ack) de cada uplink.',
          valor: v.confirmado,
          aoMudar: (b) => _mudou(() => v.confirmado = b),
        ));
      case 'CLASSE':
        campos.add(CampoRotulado(
          rotulo: 'Classe LoRaWAN',
          ajuda: 'Classe C escuta o rádio o tempo todo: comandos chegam na hora, com mais consumo.',
          child: SegmentedButton<String>(
            showSelectedIcon: false,
            segments: const [
              ButtonSegment(value: 'A', label: RotuloEncolhe('Classe A')),
              ButtonSegment(value: 'C', label: RotuloEncolhe('Classe C')),
            ],
            selected: {v.classe},
            onSelectionChanged: (s) => _mudou(() => v.classe = s.first),
          ),
        ));
      case 'REINICIAR':
        campos.add(CampoRotulado(
          rotulo: 'Atraso (ms)',
          ajuda: 'Opcional: de 0 a 60.000 ms. Vazio = 2.000 ms.',
          child: _campoNumero(_atraso, 'atrasoMs', 'Ex.: 2000', (s) => v.atrasoMs = s),
        ));
      case 'BRUTO':
        campos.add(CampoRotulado(
          rotulo: 'Porta (fPort)',
          ajuda: 'De 1 a 223.',
          child: _campoNumero(_porta, 'porta', 'Ex.: 10', (s) => v.porta = s),
        ));
        campos.add(CampoRotulado(
          rotulo: 'Bytes (hexadecimal)',
          ajuda: 'De 1 a 242 bytes; espaços são ignorados.',
          child: TextField(
            controller: _hex,
            minLines: 1,
            maxLines: 4,
            textCapitalization: TextCapitalization.characters,
            style: const TextStyle(fontFamily: 'monospace'),
            onChanged: (s) => _mudou(() => v.dadosHex = s),
            decoration: InputDecoration(hintText: 'Ex.: 01 A0 FF', errorText: widget.erros['dadosHex'], errorMaxLines: 3),
          ),
        ));
        campos.add(InterruptorRotulado(
          titulo: 'Downlink confirmado',
          subtitulo: 'Pede o ack LoRaWAN do dispositivo.',
          valor: v.confirmado,
          aoMudar: (b) => _mudou(() => v.confirmado = b),
        ));
      case null:
        break;
      default:
        if (!v.usaCanal) {
          campos.add(Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text('Este comando não tem parâmetros.', style: TextStyle(color: cs.onSurfaceVariant)),
          ));
        }
    }
    return campos;
  }

  Widget _campoCanal() {
    final ehRele = v.periferico == 'RELE';
    final lista = _perifericosDo(v.periferico);
    final rotulo = ehRele ? 'Relé' : 'Medidor';
    if (lista.isEmpty) {
      return CampoRotulado(
        rotulo: '$rotulo (canal)',
        ajuda: ehRele && v.tipo == 'RELE_DEFINIR' ? 'De 0 a 15. Vazio = todos os relés.' : 'De 0 a 15.',
        child: TextField(
          controller: _canal,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (s) => _mudou(() => v.canal = int.tryParse(s)),
          decoration: InputDecoration(hintText: 'Ex.: 0', errorText: widget.erros['canal'], errorMaxLines: 3),
        ),
      );
    }
    final todos = v.tipo == 'RELE_DEFINIR';
    final canais = lista.map((p) => p.inteiro('canal')).toSet();
    final atual = v.canal == null ? (todos ? _todos : null) : (canais.contains(v.canal) ? v.canal : null);
    return CampoRotulado(
      rotulo: rotulo,
      child: DropdownButtonFormField<int>(
        key: ValueKey('canal-${v.tipo}-$atual'),
        initialValue: atual,
        isExpanded: true,
        hint: Text('Escolha o ${rotulo.toLowerCase()}', maxLines: 1, overflow: TextOverflow.ellipsis),
        decoration: InputDecoration(errorText: widget.erros['canal'], errorMaxLines: 3),
        items: [
          if (todos) itemMenu(_todos, 'Todos os relés'),
          for (final p in lista)
            if (p.inteiro('canal') != null) itemMenu(p.inteiro('canal')!, _nomePeriferico(p)),
        ],
        onChanged: (c) => _mudou(() => v.canal = c == _todos ? null : c),
      ),
    );
  }

  List<Widget> _mascara(ColorScheme cs) {
    final reles = _perifericosDo('RELE');
    if (reles.isEmpty) {
      return [
        const Padding(
          padding: EdgeInsets.only(bottom: 12),
          child: CaixaMensagem(texto: 'Cadastre os relés no equipamento para acionar vários de uma vez.', cor: CmCores.aviso, icone: Icons.info_outline),
        ),
      ];
    }
    return [
      Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Text('Escolha o que fazer com cada relé; "Manter" não mexe nele.', style: TextStyle(fontSize: 12.5, height: 1.4, color: cs.onSurfaceVariant)),
      ),
      for (final r in reles)
        if (r.inteiro('canal') != null)
          CampoRotulado(
            rotulo: _nomePeriferico(r),
            espacoAbaixo: 12,
            child: DropdownButtonFormField<int>(
              initialValue: switch (v.mascara[r.inteiro('canal')!]) { true => 1, false => 0, null => -1 },
              isExpanded: true,
              items: [itemMenu(-1, 'Manter'), itemMenu(1, 'Ligar'), itemMenu(0, 'Desligar')],
              onChanged: (x) => _mudou(() {
                final c = r.inteiro('canal')!;
                if (x == null || x < 0) {
                  v.mascara.remove(c);
                } else {
                  v.mascara[c] = x == 1;
                }
              }),
            ),
          ),
      if (widget.erros['mascara'] != null)
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(widget.erros['mascara']!, style: TextStyle(color: cs.error, fontSize: 12.5)),
        ),
    ];
  }
}

/// Campo de horário "HH:mm" com o relógio do sistema no ícone.
class CampoHora extends StatelessWidget {
  const CampoHora({super.key, required this.controller, required this.aoMudar, this.erro, this.dica = 'HH:mm'});

  final TextEditingController controller;
  final ValueChanged<String> aoMudar;
  final String? erro;
  final String dica;

  Future<void> _escolher(BuildContext context) async {
    final partes = controller.text.split(':');
    final inicial = TimeOfDay(hour: int.tryParse(partes.first) ?? 8, minute: partes.length > 1 ? int.tryParse(partes[1]) ?? 0 : 0);
    final t = await showTimePicker(context: context, initialTime: inicial);
    if (t == null) return;
    final s = '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
    controller.text = s;
    aoMudar(s);
  }

  @override
  Widget build(BuildContext context) => TextField(
        controller: controller,
        keyboardType: TextInputType.datetime,
        inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9:]')), LengthLimitingTextInputFormatter(5)],
        onChanged: aoMudar,
        decoration: InputDecoration(
          hintText: dica,
          errorText: erro,
          errorMaxLines: 3,
          suffixIcon: IconButton(tooltip: 'Escolher horário', icon: const Icon(Icons.schedule), onPressed: () => _escolher(context)),
        ),
      );
}

/// Dias da semana (1 = segunda … 7 = domingo) como chips.
class SeletorDias extends StatelessWidget {
  const SeletorDias({super.key, required this.dias, required this.aoMudar});

  final Set<int> dias;
  final ValueChanged<Set<int>> aoMudar;

  @override
  Widget build(BuildContext context) => Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (var d = 1; d <= 7; d++)
            FilterChip(
              label: Text(Atuacao.diasCurtos[d - 1]),
              selected: dias.contains(d),
              onSelected: (s) => aoMudar(s ? ({...dias, d}) : ({...dias}..remove(d))),
            ),
        ],
      );
}

/// Formulário expansível "Política de novas tentativas" (vazio = padrão do servidor).
class FormPolitica extends StatefulWidget {
  const FormPolitica({super.key, required this.valores, this.erros = const {}, this.aberta = false, this.aoMudar});

  final ValoresPolitica valores;
  final Map<String, String> erros;
  final bool aberta;
  final VoidCallback? aoMudar;

  @override
  State<FormPolitica> createState() => _FormPoliticaState();
}

class _FormPoliticaState extends State<FormPolitica> {
  ValoresPolitica get p => widget.valores;
  late final _tent = TextEditingController(text: p.maxTentativas);
  late final _interv = TextEditingController(text: p.intervaloTentativaS);
  late final _recuo = TextEditingController(text: p.fatorRecuo);
  late final _espera = TextEditingController(text: p.aguardarRespostaS);
  late final _validade = TextEditingController(text: p.validadeMin);
  late final _ini = TextEditingController(text: p.janelaInicio);
  late final _fim = TextEditingController(text: p.janelaFim);

  @override
  void dispose() {
    for (final c in [_tent, _interv, _recuo, _espera, _validade, _ini, _fim]) {
      c.dispose();
    }
    super.dispose();
  }

  void _mudou(VoidCallback f) {
    setState(f);
    widget.aoMudar?.call();
  }

  Widget _numero(TextEditingController c, String chave, String rotulo, String dica, String ajuda, void Function(String) set, {bool decimal = false}) =>
      CampoRotulado(
        rotulo: rotulo,
        ajuda: ajuda,
        child: TextField(
          controller: c,
          keyboardType: TextInputType.numberWithOptions(decimal: decimal),
          inputFormatters: [FilteringTextInputFormatter.allow(decimal ? RegExp(r'[0-9.,]') : RegExp(r'[0-9]'))],
          onChanged: (s) => _mudou(() => set(s)),
          decoration: InputDecoration(hintText: dica, errorText: widget.erros[chave], errorMaxLines: 3),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final temErro = widget.erros.isNotEmpty;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: widget.aberta || temErro,
          leading: const Icon(Icons.replay),
          title: const Text('Política de novas tentativas', style: TextStyle(fontWeight: FontWeight.w700)),
          subtitle: Text(p.vazia ? 'Opcional · vazio usa o padrão do servidor' : 'Personalizada', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
          childrenPadding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
          expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _numero(_tent, 'maxTentativas', 'Tentativas', 'Ex.: 5', 'De 1 a 50.', (s) => p.maxTentativas = s),
            _numero(_interv, 'intervaloTentativaS', 'Intervalo entre tentativas (s)', 'Ex.: 60', 'De 10 s a 24 h (86.400 s).', (s) => p.intervaloTentativaS = s),
            _numero(_recuo, 'fatorRecuo', 'Fator de recuo', 'Ex.: 1,5', 'De 1 a 4: multiplica o intervalo a cada nova tentativa.', (s) => p.fatorRecuo = s, decimal: true),
            _numero(_espera, 'aguardarRespostaS', 'Espera pela resposta (s)', 'Ex.: 120', 'De 10 s a 24 h.', (s) => p.aguardarRespostaS = s),
            _numero(_validade, 'validadeMin', 'Validade (min)', 'Ex.: 60', 'De 1 min a 30 dias (43.200 min). Depois disso o comando expira.', (s) => p.validadeMin = s),
            CampoRotulado(
              rotulo: 'Janela: início',
              child: CampoHora(controller: _ini, erro: widget.erros['janelaInicio'], dica: 'Ex.: 08:00', aoMudar: (s) => _mudou(() => p.janelaInicio = s)),
            ),
            CampoRotulado(
              rotulo: 'Janela: fim',
              ajuda: 'Só transmite dentro da janela de horário (ou nenhuma).',
              child: CampoHora(controller: _fim, erro: widget.erros['janelaFim'], dica: 'Ex.: 18:00', aoMudar: (s) => _mudou(() => p.janelaFim = s)),
            ),
            CampoRotulado(
              rotulo: 'Dias permitidos',
              ajuda: 'Nenhum marcado = todos os dias.',
              espacoAbaixo: 4,
              child: SeletorDias(
                dias: p.diasSemana,
                aoMudar: (d) => _mudou(() => p.diasSemana
                  ..clear()
                  ..addAll(d)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/atualizacao_periodica.dart';
import 'package:chirpstack_manager/features/atuacao/controle_page.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';

/// Aba "Energia" do dispositivo: última medição de cada PZEM-004T, consumo e custo no período,
/// e as ações ler agora, limite de potência e zerar energia.
class EnergiaDispositivo extends StatefulWidget {
  const EnergiaDispositivo({super.key, required this.dispositivoId, this.indiceAba});

  final int dispositivoId;
  final int? indiceAba;

  @override
  State<EnergiaDispositivo> createState() => _EnergiaDispositivoState();
}

class _EnergiaDispositivoState extends State<EnergiaDispositivo> with AtualizacaoPeriodica {
  static const _periodos = <(String, Duration)>[
    ('24 h', Duration(hours: 24)),
    ('7 dias', Duration(days: 7)),
    ('31 dias', Duration(days: 31)),
  ];

  Json? _eq;
  Object? _erro;
  bool _carregando = true;
  int _periodo = 0;
  final Map<int, Json> _resumos = {};
  String? _enviando;

  @override
  int? get indiceAba => widget.indiceAba;

  @override
  Duration get intervaloAtualizacao => const Duration(seconds: 30);

  bool get _podeAlterar => Sessao.instancia.usuario?.podeAlterar ?? false;

  List<Json> get _medidores {
    final l = (_eq?.lista('perifericos') ?? const <Json>[]).where((p) => p.texto('tipo') == 'PZEM004T').toList();
    l.sort((a, b) => (a.inteiro('canal') ?? 0).compareTo(b.inteiro('canal') ?? 0));
    return l;
  }

  @override
  void initState() {
    super.initState();
    _carregar();
    iniciarAtualizacaoPeriodica();
  }

  Future<void> _carregar() async {
    try {
      final e = await AtuacaoApi.equipamento(widget.dispositivoId);
      if (!mounted) return;
      setState(() {
        _eq = e;
        _erro = null;
      });
      await _carregarResumos();
    } catch (e) {
      if (mounted) setState(() => _erro = e);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  Future<void> _carregarResumos() async {
    final ate = DateTime.now();
    final de = ate.subtract(_periodos[_periodo].$2);
    await Future.wait(_medidores.map((m) async {
      final canal = m.inteiro('canal');
      if (canal == null) return;
      try {
        final r = await AtuacaoApi.energia(widget.dispositivoId, canal: canal, de: de, ate: ate);
        if (mounted) setState(() => _resumos[canal] = r);
      } catch (_) {
        if (mounted) setState(() => _resumos.remove(canal));
      }
    }));
  }

  @override
  Future<void> atualizarEmSegundoPlano() => _carregar();

  bool _suporta(String tipo) => (_eq?.lista('comandos') ?? const []).any((c) => c.texto('tipo') == tipo);

  Future<void> _comando(String chave, String tipo, Json parametros) async {
    setState(() => _enviando = chave);
    await enviarComandoRapido(context, widget.dispositivoId, tipo, parametros);
    if (mounted) setState(() => _enviando = null);
  }

  Future<void> _zerar(Json m) async {
    final ok = await confirmar(
      context,
      titulo: 'Zerar energia',
      texto: 'Zerar o contador de energia (kWh) de "${m.texto('nome') ?? 'medidor'}"? Não há como desfazer.',
      rotuloConfirmar: 'Zerar energia',
      perigo: true,
      icone: Icons.restart_alt,
    );
    if (ok && mounted) await _comando('zerar-${m.inteiro('canal')}', 'PZEM_ZERAR_ENERGIA', {'canal': m.inteiro('canal')});
  }

  Future<void> _limite(Json m) async {
    final w = await showDialog<int>(context: context, builder: (_) => _DialogoLimite(nome: m.texto('nome') ?? 'Medidor'));
    if (w != null && mounted) await _comando('limite-${m.inteiro('canal')}', 'PZEM_LIMITE_POTENCIA', {'canal': m.inteiro('canal'), 'watts': w});
  }

  @override
  Widget build(BuildContext context) {
    Widget corpo;
    final medidores = _medidores;
    if (_carregando) {
      corpo = const Carregando();
    } else if (_eq == null) {
      corpo = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar', texto: mensagemDeErro(_erro ?? 'erro'), acao: _carregar);
    } else if (medidores.isEmpty) {
      corpo = EstadoMensagem(
        icone: Icons.bolt_outlined,
        titulo: 'Sem medidor de energia',
        texto: _eq!.logico('configurado')
            ? 'Este equipamento não tem medidor PZEM-004T.'
            : 'Configure o equipamento na aba Controle e adicione o medidor PZEM-004T.',
      );
    } else {
      corpo = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          SegmentedButton<int>(
            showSelectedIcon: false,
            segments: [for (var i = 0; i < _periodos.length; i++) ButtonSegment(value: i, label: RotuloEncolhe(_periodos[i].$1))],
            selected: {_periodo},
            onSelectionChanged: (s) {
              setState(() => _periodo = s.first);
              _carregarResumos();
            },
          ),
          const SizedBox(height: 8),
          for (final m in medidores) _cartaoMedidor(context, m),
        ],
      );
    }
    return RefreshIndicator(onRefresh: _carregar, child: corpo);
  }

  Widget _cartaoMedidor(BuildContext context, Json m) {
    final cs = Theme.of(context).colorScheme;
    final canal = m.inteiro('canal') ?? 0;
    final med = Atuacao.jsonDeTexto(m.texto('leitura'));
    final r = _resumos[canal];
    final ocupado = _enviando != null && _enviando!.endsWith('-$canal');
    String n(num? v, int casas, String un) => v == null ? '—' : '${Formatos.decimal(v, casas: casas)} $un';
    final energiaKwh = med?.decimal('energia') == null ? null : med!.decimal('energia')! / 1000;
    final valores = <(String, String)>[
      if (med != null) ...[
        ('Tensão', n(med.decimal('tensao'), 1, 'V')),
        ('Corrente', n(med.decimal('corrente'), 3, 'A')),
        ('Potência', n(med.decimal('potencia'), 1, 'W')),
        ('Energia', n(energiaKwh, 3, 'kWh')),
        ('Frequência', n(med.decimal('frequencia'), 1, 'Hz')),
        ('Fator de potência', med.decimal('fp') == null ? '—' : Formatos.decimal(med.decimal('fp'), casas: 2)),
      ],
    ];
    final periodo = <(String, String)>[
      ('Consumo', n(r?.decimal('consumoKwh'), 3, 'kWh')),
      ('Custo', r?.decimal('custo') == null ? '—' : 'R\$ ${Formatos.decimal(r!.decimal('custo'), casas: 2)}'),
      ('Potência média', n(r?.decimal('potenciaMedia'), 1, 'W')),
      ('Potência máxima', n(r?.decimal('potenciaMaxima'), 1, 'W')),
    ];
    return Card(
      margin: const EdgeInsets.only(top: 12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Padding(padding: EdgeInsets.only(top: 2), child: Icon(Icons.bolt, color: CmCores.aviso)),
            const SizedBox(width: 8),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(m.texto('nome') ?? 'Medidor $canal', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                Text('Canal $canal · medido ${Formatos.relativo(m.data('leituraEm'))}', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
              ]),
            ),
            if (med?.logico('alarme') ?? false) const ChipEstado(texto: 'Alarme', cor: CmCores.offline),
          ]),
          const SizedBox(height: 10),
          if (med == null)
            Text('Nenhuma medição recebida ainda.', style: TextStyle(color: cs.onSurfaceVariant))
          else
            _Grade(valores: valores),
          const SizedBox(height: 12),
          Text('No período (${_periodos[_periodo].$1})', style: const TextStyle(fontWeight: FontWeight.w600)),
          const SizedBox(height: 6),
          _Grade(valores: periodo),
          if (r != null && r.decimal('tarifaKwh') == null) ...[
            const SizedBox(height: 6),
            Text('Sem tarifa: informe-a no equipamento (aba Controle) para ver o custo.', style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
          ],
          if (_podeAlterar && (_suporta('PZEM_LER') || _suporta('PZEM_LIMITE_POTENCIA') || _suporta('PZEM_ZERAR_ENERGIA'))) ...[
            const SizedBox(height: 12),
            if (ocupado)
              const LinearProgressIndicator()
            else
              Wrap(spacing: 8, runSpacing: 8, children: [
                if (_suporta('PZEM_LER'))
                  FilledButton.tonalIcon(style: FilledButton.styleFrom(minimumSize: const Size(0, 44)), onPressed: () => _comando('ler-$canal', 'PZEM_LER', {'canal': canal}), icon: const Icon(Icons.download, size: 18), label: const Text('Ler agora')),
                if (_suporta('PZEM_LIMITE_POTENCIA')) OutlinedButton(onPressed: () => _limite(m), child: const Text('Limite')),
                if (_suporta('PZEM_ZERAR_ENERGIA'))
                  OutlinedButton(onPressed: () => _zerar(m), style: OutlinedButton.styleFrom(foregroundColor: cs.error), child: const Text('Zerar energia')),
              ]),
          ],
        ]),
      ),
    );
  }
}

class _Grade extends StatelessWidget {
  const _Grade({required this.valores});

  final List<(String, String)> valores;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return LayoutBuilder(builder: (context, c) {
      final colunas = c.maxWidth >= 520 ? 3 : 2;
      const espaco = 8.0;
      final largura = (c.maxWidth - espaco * (colunas - 1)) / colunas;
      return Wrap(spacing: espaco, runSpacing: espaco, children: [
        for (final v in valores)
          Container(
            width: largura,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(color: cs.surfaceContainerHighest, borderRadius: BorderRadius.circular(10)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(v.$1, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
              FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerLeft, child: Text(v.$2, maxLines: 1, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15))),
            ]),
          ),
      ]);
    });
  }
}

class _DialogoLimite extends StatefulWidget {
  const _DialogoLimite({required this.nome});

  final String nome;

  @override
  State<_DialogoLimite> createState() => _DialogoLimiteState();
}

class _DialogoLimiteState extends State<_DialogoLimite> {
  final _watts = TextEditingController(text: '2300');
  String? _erro;

  @override
  void dispose() {
    _watts.dispose();
    super.dispose();
  }

  void _ok() {
    final w = int.tryParse(_watts.text.trim());
    if (w == null || w < 0 || w > 23000) {
      setState(() => _erro = 'De 0 a 23.000 W.');
      return;
    }
    Navigator.pop(context, w);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text('Limite: ${widget.nome}', maxLines: 2, overflow: TextOverflow.ellipsis),
        content: SingleChildScrollView(
          child: CampoRotulado(
            rotulo: 'Limite de potência (W)',
            ajuda: 'Acima disso o medidor liga o alarme. Use uma automação para desligar um relé quando isso acontecer.',
            espacoAbaixo: 0,
            child: TextField(
              controller: _watts,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(hintText: 'Ex.: 2300', errorText: _erro, errorMaxLines: 3),
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(0, 44)), onPressed: _ok, child: const Text('Enviar')),
        ],
      );
}

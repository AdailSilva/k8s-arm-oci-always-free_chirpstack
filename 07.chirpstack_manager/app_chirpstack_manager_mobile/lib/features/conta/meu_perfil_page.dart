import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/dialogos.dart';
import 'package:chirpstack_manager/features/conta/mascaras.dart';

/// Tipos de logradouro (os mesmos do painel web).
const Map<String, String> tiposLogradouro = {
  'RUA': 'Rua', 'AVENIDA': 'Avenida', 'ALAMEDA': 'Alameda', 'BECO': 'Beco', 'CALCADA': 'Calçada',
  'CONJUNTO': 'Conjunto', 'ESTRADA': 'Estrada', 'JARDIM': 'Jardim', 'LADEIRA': 'Ladeira', 'LARGO': 'Largo',
  'LOTE': 'Lote', 'PATIO': 'Pátio', 'PRACA': 'Praça', 'PARQUE': 'Parque', 'PASSAGEM': 'Passagem',
  'PONTE': 'Ponte', 'RODOVIA': 'Rodovia', 'QUADRA': 'Quadra', 'TRAVESSA': 'Travessa', 'VIA': 'Via',
  'VIADUTO': 'Viaduto', 'VIELA': 'Viela',
};

const List<String> ufs = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', //
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
];

/// Foto de perfil (data URI JPEG) → bytes; null se não houver ou não for legível.
Uint8List? bytesDaFoto(String? dataUri) {
  if (dataUri == null || !dataUri.startsWith('data:image/')) return null;
  final i = dataUri.indexOf('base64,');
  if (i < 0) return null;
  try {
    return base64Decode(dataUri.substring(i + 7));
  } catch (_) {
    return null;
  }
}

/// Meu perfil (o Minha Conta do painel web): nome, cargo, telefone e endereço com busca do CEP.
/// A foto é trocada pelo painel web; aqui ela é mantida. E-mail e perfil só o administrador altera.
class MeuPerfilPage extends StatefulWidget {
  const MeuPerfilPage({super.key});

  @override
  State<MeuPerfilPage> createState() => _MeuPerfilPageState();
}

class _MeuPerfilPageState extends State<MeuPerfilPage> {
  final _form = GlobalKey<FormState>();
  final _nome = TextEditingController();
  final _cargo = TextEditingController();
  final _telefone = TextEditingController();
  final _cep = TextEditingController();
  final _endereco = TextEditingController();
  final _numero = TextEditingController();
  final _complemento = TextEditingController();
  final _bairro = TextEditingController();
  final _cidade = TextEditingController();
  String? _tipo;
  String? _uf;
  String? _foto;
  String _email = '';
  String _perfil = '';
  Json _cadastro = {};

  bool _carregando = true;
  bool _salvando = false;
  bool _buscandoCep = false;
  String? _erro;

  List<TextEditingController> get _campos => [_nome, _cargo, _telefone, _cep, _endereco, _numero, _complemento, _bairro, _cidade];

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  @override
  void dispose() {
    for (final c in _campos) {
      c.dispose();
    }
    super.dispose();
  }

  String _retrato() => jsonEncode([for (final c in _campos) c.text, _tipo, _uf]);

  Future<void> _carregar() async {
    try {
      final u = await AuthService.instancia.me();
      final c = u.mapa('cadastro');
      if (!mounted) return;
      setState(() {
        _email = u.texto('email') ?? '';
        _perfil = switch (u.texto('perfil')) { 'ADMIN' => 'Administrador', 'OPERADOR' => 'Operador', _ => 'Visualizador' };
        _cadastro = c;
        _foto = c.texto('fotoPerfil');
        _nome.text = u.texto('nome') ?? '';
        _cargo.text = c.texto('cargo') ?? '';
        _telefone.text = _formatarTelefone(c.texto('telefone'));
        _cep.text = MascaraCep().formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: c.texto('cep') ?? '')).text;
        _tipo = tiposLogradouro.containsKey(c.texto('enderecoTipo')) ? c.texto('enderecoTipo') : null;
        _endereco.text = c.texto('endereco') ?? '';
        _numero.text = c.texto('numero') ?? '';
        _complemento.text = c.texto('complemento') ?? '';
        _bairro.text = c.texto('bairro') ?? '';
        _cidade.text = c.texto('cidade') ?? '';
        _uf = ufs.contains(c.texto('uf')) ? c.texto('uf') : null;
        _carregando = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _carregando = false;
          _erro = mensagemDeErro(e);
        });
      }
    }
  }

  static String _formatarTelefone(String? t) {
    final d = (t ?? '').replaceAll(RegExp(r'\D'), '');
    return MascaraTelefone(celular: d.length == 11).formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: d)).text;
  }

  Future<void> _buscarCep() async {
    final d = _cep.text.replaceAll(RegExp(r'\D'), '');
    if (d.length != 8 || _buscandoCep) return;
    setState(() => _buscandoCep = true);
    try {
      final r = comoMapa(await ApiClient.instancia.get('/api/v1/localidades/cep/$d'));
      if (!mounted) return;
      if (!r.logico('encontrado')) {
        Navegacao.avisar('CEP não encontrado. Preencha o endereço manualmente.');
        return;
      }
      setState(() {
        var logradouro = (r.texto('logradouro') ?? '').trim();
        String? tipo;
        for (final e in tiposLogradouro.entries) {
          if (logradouro.toLowerCase().startsWith('${e.value.toLowerCase()} ')) {
            tipo = e.key;
            logradouro = logradouro.substring(e.value.length).trim();
            break;
          }
        }
        if (tipo != null) _tipo = tipo;
        if (logradouro.isNotEmpty) _endereco.text = logradouro;
        if ((r.texto('bairro') ?? '').isNotEmpty) _bairro.text = r.texto('bairro')!;
        if ((r.texto('cidade') ?? '').isNotEmpty) _cidade.text = r.texto('cidade')!;
        if (ufs.contains(r.texto('uf'))) _uf = r.texto('uf');
      });
    } catch (_) {
      Navegacao.avisar('Não foi possível consultar o CEP agora. Preencha o endereço manualmente.');
    } finally {
      if (mounted) setState(() => _buscandoCep = false);
    }
  }

  Future<void> _salvar() async {
    FocusScope.of(context).unfocus();
    if (!(_form.currentState?.validate() ?? false)) return;
    String? vazio(TextEditingController c) => c.text.trim().isEmpty ? null : c.text.trim();
    final cadastro = <String, dynamic>{
      ..._cadastro,
      'cargo': vazio(_cargo),
      'telefone': _telefone.text.replaceAll(RegExp(r'\D'), '').isEmpty ? null : _telefone.text.replaceAll(RegExp(r'\D'), ''),
      'cep': _cep.text.replaceAll(RegExp(r'\D'), '').isEmpty ? null : _cep.text.replaceAll(RegExp(r'\D'), ''),
      'enderecoTipo': _tipo,
      'endereco': vazio(_endereco),
      'numero': vazio(_numero),
      'complemento': vazio(_complemento),
      'bairro': vazio(_bairro),
      'cidade': vazio(_cidade),
      'uf': _uf,
    };
    setState(() {
      _salvando = true;
      _erro = null;
    });
    try {
      await AuthService.instancia.atualizarMe(_nome.text.trim(), cadastro);
      if (!mounted) return;
      Navegacao.avisar('Seus dados foram atualizados.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  Widget _campo(String rotulo, TextEditingController c, String dica,
          {TextInputType? teclado, List<TextInputFormatter>? formatos, String? Function(String?)? validar, int? max, Widget? sufixo}) =>
      CampoRotulado(
        rotulo: rotulo,
        child: TextFormField(
          controller: c,
          keyboardType: teclado,
          inputFormatters: formatos,
          maxLength: max,
          validator: validar,
          textCapitalization: teclado == null ? TextCapitalization.words : TextCapitalization.none,
          decoration: InputDecoration(hintText: dica, counterText: '', suffixIcon: sufixo),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final foto = bytesDaFoto(_foto);
    return TravaEdicao(
      retrato: _retrato,
      child: Scaffold(
        appBar: AppBar(title: const Text('Meu perfil')),
        body: _carregando
            ? const Carregando()
            : Form(
                key: _form,
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
                  children: [
                    if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
                    Row(children: [
                      CircleAvatar(
                        radius: 30,
                        backgroundColor: CmCores.azul600,
                        backgroundImage: foto == null ? null : MemoryImage(foto),
                        child: foto == null
                            ? Text(_nome.text.isEmpty ? '?' : _nome.text.trim()[0].toUpperCase(),
                                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 22))
                            : null,
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(_email, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                          const SizedBox(height: 4),
                          Text('$_perfil · a foto é trocada pelo painel web', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
                        ]),
                      ),
                    ]),
                    const TituloSecao('Dados'),
                    _campo('Nome', _nome, 'Seu nome completo', max: 120,
                        validar: (v) => (v ?? '').trim().length < 2 ? 'Informe o nome.' : null),
                    _campo('Cargo (opcional)', _cargo, 'Ex.: Técnico de campo', max: 80),
                    _campo('Telefone (opcional)', _telefone, '(00) 90000-0000',
                        teclado: TextInputType.phone,
                        formatos: [FilteringTextInputFormatter.digitsOnly, MascaraTelefone(celular: true)],
                        validar: (v) {
                          final d = (v ?? '').replaceAll(RegExp(r'\D'), '');
                          return d.isEmpty || d.length >= 10 ? null : 'Telefone incompleto.';
                        }),
                    const TituloSecao('Endereço (opcional)'),
                    _campo('CEP', _cep, '00000-000 (preenche o endereço)',
                        teclado: TextInputType.number,
                        formatos: [FilteringTextInputFormatter.digitsOnly, MascaraCep()],
                        validar: (v) {
                          final d = (v ?? '').replaceAll(RegExp(r'\D'), '');
                          return d.isEmpty || d.length == 8 ? null : 'CEP com 8 dígitos.';
                        },
                        sufixo: _buscandoCep
                            ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))
                            : IconButton(tooltip: 'Buscar CEP', icon: const Icon(Icons.search), onPressed: _buscarCep)),
                    CampoRotulado(
                      rotulo: 'Tipo de logradouro',
                      child: DropdownButtonFormField<String>(
                        // A busca do CEP também escolhe o tipo: a chave recria o campo com o valor novo.
                        key: ValueKey('tipo-$_tipo'),
                        initialValue: _tipo,
                        isExpanded: true,
                        hint: const Text('Rua, Avenida...'),
                        items: [for (final e in tiposLogradouro.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
                        onChanged: (v) => setState(() => _tipo = v),
                      ),
                    ),
                    _campo('Logradouro', _endereco, 'Ex.: Dom Pedro II', max: 255),
                    Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Expanded(flex: 2, child: _campo('Número', _numero, 'Ex.: 120 ou S/N', max: 20)),
                      const SizedBox(width: 12),
                      Expanded(flex: 3, child: _campo('Complemento', _complemento, 'Ex.: Sala 2', max: 255)),
                    ]),
                    _campo('Bairro', _bairro, 'Ex.: Centro', max: 150),
                    Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Expanded(flex: 3, child: _campo('Cidade', _cidade, 'Ex.: Tianguá', max: 100)),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 2,
                        child: CampoRotulado(
                          rotulo: 'UF',
                          child: DropdownButtonFormField<String>(
                            key: ValueKey('uf-$_uf'),
                            initialValue: _uf,
                            isExpanded: true,
                            hint: const Text('UF'),
                            items: [for (final u in ufs) DropdownMenuItem(value: u, child: Text(u))],
                            onChanged: (v) => setState(() => _uf = v),
                          ),
                        ),
                      ),
                    ]),
                    const SizedBox(height: 4),
                    FilledButton.icon(
                      onPressed: _salvando ? null : _salvar,
                      icon: const Icon(Icons.check),
                      label: Text(_salvando ? 'Salvando…' : 'Salvar'),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}

import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';

/// Periférico exigido por cada comando (TipoComando.periferico na API; ausente = do equipamento).
const Map<String, String> perifericoDoComando = {
  'RELE_DEFINIR': 'RELE',
  'RELE_PULSO': 'RELE',
  'RELE_MASCARA': 'RELE',
  'RELE_LER': 'RELE',
  'PZEM_LER': 'PZEM004T',
  'PZEM_ZERAR_ENERGIA': 'PZEM004T',
  'PZEM_LIMITE_POTENCIA': 'PZEM004T',
};

/// Estado do formulário "comando + parâmetros" (campos numéricos como texto, como estão na tela).
///
/// Cada tipo usa só os seus parâmetros (ParametrosComando da API):
/// RELE_DEFINIR canal (nulo = todos) + acao; RELE_PULSO canal + estado + duracaoMs; RELE_MASCARA
/// mascara + valores; PZEM_* canal; PZEM_LIMITE_POTENCIA + watts; INTERVALO segundos; CONFIRMADO
/// confirmado; CLASSE classe; REINICIAR atrasoMs; BRUTO porta + dadosHex + confirmado.
class ValoresComando {
  ValoresComando({this.tipo});

  String? tipo;

  /// Canal do relé ou do medidor; nulo em RELE_DEFINIR = todos os relés.
  int? canal;
  String acao = 'LIGAR';
  bool estado = true;
  String duracaoMs = '1000';

  /// RELE_MASCARA: relé (canal) → true liga, false desliga; ausente = não mexe.
  final Map<int, bool> mascara = {};
  String segundos = '';
  bool confirmado = false;
  String classe = 'A';
  String atrasoMs = '';
  String watts = '';
  String porta = '';
  String dadosHex = '';

  /// Preenche a partir do tipo e dos parâmetros de um comando salvo (edição de cena/agendamento).
  factory ValoresComando.deJson(String? tipo, Json p) {
    final v = ValoresComando(tipo: tipo);
    v.canal = p.inteiro('canal');
    v.acao = p.texto('acao') ?? 'LIGAR';
    v.estado = p.logico('estado', true);
    if (p.inteiro('duracaoMs') != null) v.duracaoMs = '${p.inteiro('duracaoMs')}';
    final m = p.inteiro('mascara') ?? 0;
    final val = p.inteiro('valores') ?? 0;
    for (var i = 0; i < 16; i++) {
      if (m & (1 << i) != 0) v.mascara[i] = val & (1 << i) != 0;
    }
    if (p.inteiro('segundos') != null) v.segundos = '${p.inteiro('segundos')}';
    v.confirmado = p.logico('confirmado');
    v.classe = p.texto('classe') ?? 'A';
    if (p.inteiro('atrasoMs') != null) v.atrasoMs = '${p.inteiro('atrasoMs')}';
    if (p.inteiro('watts') != null) v.watts = '${p.inteiro('watts')}';
    if (p.inteiro('porta') != null) v.porta = '${p.inteiro('porta')}';
    v.dadosHex = p.texto('dadosHex') ?? '';
    return v;
  }

  /// Periférico de que o tipo atual precisa (RELE, PZEM004T ou nulo).
  String? get periferico => perifericoDoComando[tipo];

  /// O tipo atual pede a escolha de um canal.
  bool get usaCanal => const {'RELE_DEFINIR', 'RELE_PULSO', 'PZEM_LER', 'PZEM_ZERAR_ENERGIA', 'PZEM_LIMITE_POTENCIA'}.contains(tipo);

  /// Bytes do campo hexadecimal (aceita espaços, ":" e "-" entre os pares).
  static String hexLimpo(String s) => s.replaceAll(RegExp(r'[\s:\-]'), '').toUpperCase();

  /// Problemas do formulário (campo → mensagem). Vazio = pode enviar.
  Map<String, String> validar() {
    final e = <String, String>{};
    if (tipo == null) {
      e['tipo'] = 'Escolha o comando.';
      return e;
    }
    int? inteiroEm(String campo, String texto, String rotulo, int min, int max, {bool obrigatorio = true}) {
      final t = texto.trim();
      if (t.isEmpty) {
        if (obrigatorio) e[campo] = 'Informe $rotulo.';
        return null;
      }
      final n = int.tryParse(t);
      if (n == null || n < min || n > max) {
        e[campo] = '${rotulo[0].toUpperCase()}${rotulo.substring(1)}: de $min a $max.';
        return null;
      }
      return n;
    }

    switch (tipo) {
      case 'RELE_PULSO':
        if (canal == null) e['canal'] = 'Escolha o relé.';
        inteiroEm('duracaoMs', duracaoMs, 'a duração em ms', 100, 86400000);
      case 'PZEM_LER' || 'PZEM_ZERAR_ENERGIA':
        if (canal == null) e['canal'] = 'Escolha o medidor.';
      case 'PZEM_LIMITE_POTENCIA':
        if (canal == null) e['canal'] = 'Escolha o medidor.';
        inteiroEm('watts', watts, 'o limite em W', 0, 23000);
      case 'RELE_MASCARA':
        if (mascara.isEmpty) e['mascara'] = 'Escolha ao menos um relé para ligar ou desligar.';
      case 'INTERVALO':
        inteiroEm('segundos', segundos, 'o intervalo em segundos', 5, 86400);
      case 'REINICIAR':
        inteiroEm('atrasoMs', atrasoMs, 'o atraso em ms', 0, 60000, obrigatorio: false);
      case 'BRUTO':
        inteiroEm('porta', porta, 'a porta (fPort)', 1, 223);
        final h = hexLimpo(dadosHex);
        if (h.isEmpty) {
          e['dadosHex'] = 'Informe os bytes em hexadecimal.';
        } else if (!RegExp(r'^[0-9A-F]+$').hasMatch(h) || h.length.isOdd) {
          e['dadosHex'] = 'Bytes em hexadecimal inválidos (ex.: 01 A0 FF).';
        } else if (h.length > 484) {
          e['dadosHex'] = 'Informe de 1 a 242 bytes.';
        }
    }
    return e;
  }

  /// Parâmetros para a API (só os do tipo atual; campos vazios ficam de fora).
  Json paraJson() {
    int? n(String s) => int.tryParse(s.trim());
    switch (tipo) {
      case 'RELE_DEFINIR':
        return {'canal': ?canal, 'acao': acao};
      case 'RELE_PULSO':
        return {'canal': ?canal, 'estado': estado, 'duracaoMs': ?n(duracaoMs)};
      case 'RELE_MASCARA':
        var m = 0;
        var v = 0;
        mascara.forEach((c, liga) {
          m |= 1 << c;
          if (liga) v |= 1 << c;
        });
        return {'mascara': m, 'valores': v};
      case 'PZEM_LER' || 'PZEM_ZERAR_ENERGIA':
        return {'canal': ?canal};
      case 'PZEM_LIMITE_POTENCIA':
        return {'canal': ?canal, 'watts': ?n(watts)};
      case 'INTERVALO':
        return {'segundos': ?n(segundos)};
      case 'CONFIRMADO':
        return {'confirmado': confirmado};
      case 'CLASSE':
        return {'classe': classe};
      case 'REINICIAR':
        return {'atrasoMs': ?n(atrasoMs)};
      case 'BRUTO':
        return {'porta': ?n(porta), 'dadosHex': hexLimpo(dadosHex), 'confirmado': confirmado};
      default:
        return {};
    }
  }
}

/// Estado do formulário "política de novas tentativas" (vazio = padrão do servidor).
class ValoresPolitica {
  String maxTentativas = '';
  String intervaloTentativaS = '';
  String fatorRecuo = '';
  String aguardarRespostaS = '';
  String validadeMin = '';
  String janelaInicio = '';
  String janelaFim = '';
  final Set<int> diasSemana = {};

  bool get vazia =>
      [maxTentativas, intervaloTentativaS, fatorRecuo, aguardarRespostaS, validadeMin, janelaInicio, janelaFim].every((s) => s.trim().isEmpty) &&
      diasSemana.isEmpty;

  /// Problemas (campo → mensagem), com as mesmas faixas da API (PoliticaRequest).
  Map<String, String> validar() {
    final e = <String, String>{};
    void faixa(String campo, String texto, num min, num max, String msg, {bool decimal = false}) {
      final t = texto.trim().replaceAll(',', '.');
      if (t.isEmpty) return;
      final n = decimal ? double.tryParse(t) : int.tryParse(t);
      if (n == null || n < min || n > max) e[campo] = msg;
    }

    faixa('maxTentativas', maxTentativas, 1, 50, 'Tentativas: de 1 a 50.');
    faixa('intervaloTentativaS', intervaloTentativaS, 10, 86400, 'Intervalo entre tentativas: de 10 s a 24 h.');
    faixa('fatorRecuo', fatorRecuo, 1, 4, 'Recuo: de 1 a 4.', decimal: true);
    faixa('aguardarRespostaS', aguardarRespostaS, 10, 86400, 'Espera pela resposta: de 10 s a 24 h.');
    faixa('validadeMin', validadeMin, 1, 43200, 'Validade: de 1 min a 30 dias.');
    final ini = janelaInicio.trim();
    final fim = janelaFim.trim();
    if (ini.isNotEmpty && !Atuacao.horaValida(ini)) e['janelaInicio'] = 'Use HH:mm (ex.: 08:00).';
    if (fim.isNotEmpty && !Atuacao.horaValida(fim)) e['janelaFim'] = 'Use HH:mm (ex.: 18:00).';
    if ((ini.isEmpty) != (fim.isEmpty)) {
      e[ini.isEmpty ? 'janelaInicio' : 'janelaFim'] = 'Informe o início e o fim da janela (ou nenhum).';
    }
    return e;
  }

  /// Política para a API, ou nulo quando nada foi preenchido.
  Json? paraJson() {
    if (vazia) return null;
    int? n(String s) => int.tryParse(s.trim());
    final recuo = double.tryParse(fatorRecuo.trim().replaceAll(',', '.'));
    return {
      'maxTentativas': ?n(maxTentativas),
      'intervaloTentativaS': ?n(intervaloTentativaS),
      'fatorRecuo': ?recuo,
      'aguardarRespostaS': ?n(aguardarRespostaS),
      'validadeMin': ?n(validadeMin),
      if (janelaInicio.trim().isNotEmpty) 'janelaInicio': janelaInicio.trim(),
      if (janelaFim.trim().isNotEmpty) 'janelaFim': janelaFim.trim(),
      if (diasSemana.isNotEmpty) 'diasSemana': (diasSemana.toList()..sort()),
    };
  }
}

/// Resumo curto de um comando para listas (o servidor manda o seu "resumo" nos itens já salvos).
String resumirComando(String? tipo, Json p) {
  final rotulo = Atuacao.rotuloComando(tipo);
  final canal = p.inteiro('canal');
  String acao(String? a) => switch (a) { 'LIGAR' => 'ligar', 'DESLIGAR' => 'desligar', 'ALTERNAR' => 'alternar', _ => a ?? '' };
  final detalhe = switch (tipo) {
    'RELE_DEFINIR' => '${canal == null ? 'todos os relés' : 'relé $canal'}: ${acao(p.texto('acao'))}',
    'RELE_PULSO' => 'relé ${canal ?? '?'}: ${p.logico('estado', true) ? 'liga' : 'desliga'} por ${p.inteiro('duracaoMs') ?? '?'} ms',
    'RELE_MASCARA' => 'máscara ${p.inteiro('mascara') ?? 0}, valores ${p.inteiro('valores') ?? 0}',
    'PZEM_LER' || 'PZEM_ZERAR_ENERGIA' => 'medidor ${canal ?? '?'}',
    'PZEM_LIMITE_POTENCIA' => 'medidor ${canal ?? '?'}: ${p.inteiro('watts') ?? '?'} W',
    'INTERVALO' => '${p.inteiro('segundos') ?? '?'} s',
    'CONFIRMADO' => p.logico('confirmado') ? 'ligado' : 'desligado',
    'CLASSE' => 'classe ${p.texto('classe') ?? '?'}',
    'REINICIAR' => p.inteiro('atrasoMs') == null ? null : 'em ${p.inteiro('atrasoMs')} ms',
    'BRUTO' => 'porta ${p.inteiro('porta') ?? '?'}: ${p.texto('dadosHex') ?? ''}',
    _ => null,
  };
  return detalhe == null ? rotulo : '$rotulo · $detalhe';
}

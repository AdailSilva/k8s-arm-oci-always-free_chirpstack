/// Formatação para a tela (pt-BR), sem dependências externas.
class Formatos {
  Formatos._();

  static String _dois(int n) => n.toString().padLeft(2, '0');

  /// 25/11/2026 14:05
  static String dataHora(DateTime? d) {
    if (d == null) return '—';
    final l = d.toLocal();
    return '${_dois(l.day)}/${_dois(l.month)}/${l.year} ${_dois(l.hour)}:${_dois(l.minute)}';
  }

  /// "agora", "há 5 min", "há 3 h", "há 2 dias" (ou a data, depois de 30 dias).
  static String relativo(DateTime? d, {DateTime? agora}) {
    if (d == null) return 'nunca';
    final ref = agora ?? DateTime.now();
    final s = ref.difference(d).inSeconds;
    if (s < 0) return dataHora(d);
    if (s < 45) return 'agora';
    final m = (s / 60).round();
    if (m < 60) return 'há $m min';
    final h = (m / 60).round();
    if (h < 24) return 'há $h h';
    final dias = (h / 24).round();
    if (dias <= 30) return dias == 1 ? 'há 1 dia' : 'há $dias dias';
    return dataHora(d);
  }

  /// 1.284.093
  static String inteiro(num? n) {
    if (n == null) return '—';
    final negativo = n < 0;
    final s = n.abs().round().toString();
    final b = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) b.write('.');
      b.write(s[i]);
    }
    return negativo ? '-$b' : b.toString();
  }

  /// 7,5 (vírgula decimal, sem zeros sobrando)
  static String decimal(num? n, {int casas = 1}) {
    if (n == null) return '—';
    var s = n.toStringAsFixed(casas);
    if (s.contains('.')) s = s.replaceFirst(RegExp(r'\.?0+$'), '');
    return s.replaceAll('.', ',');
  }
}

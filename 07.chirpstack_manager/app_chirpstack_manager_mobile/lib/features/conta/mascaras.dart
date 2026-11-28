import 'package:flutter/services.dart';

/// Máscara de telefone brasileiro: (00) 0000-0000 ou, para celular, (00) 00000-0000.
/// Use depois de FilteringTextInputFormatter.digitsOnly.
class MascaraTelefone extends TextInputFormatter {
  MascaraTelefone({required this.celular});

  final bool celular;

  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final max = celular ? 11 : 10;
    var d = newValue.text.replaceAll(RegExp(r'\D'), '');
    if (d.length > max) d = d.substring(0, max);
    final b = StringBuffer();
    for (var i = 0; i < d.length; i++) {
      if (i == 0) b.write('(');
      if (i == 2) b.write(') ');
      if (i == (celular ? 7 : 6)) b.write('-');
      b.write(d[i]);
    }
    final t = b.toString();
    return TextEditingValue(text: t, selection: TextSelection.collapsed(offset: t.length));
  }
}

/// Máscara de CEP: 00000-000. Use depois de FilteringTextInputFormatter.digitsOnly.
class MascaraCep extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    var d = newValue.text.replaceAll(RegExp(r'\D'), '');
    if (d.length > 8) d = d.substring(0, 8);
    final t = d.length > 5 ? '${d.substring(0, 5)}-${d.substring(5)}' : d;
    return TextEditingValue(text: t, selection: TextSelection.collapsed(offset: t.length));
  }
}

/// Vazio ou completo (10 dígitos no fixo; 11 começando por 9 no celular).
bool telefoneValido(String? v, {required bool celular}) {
  final d = (v ?? '').replaceAll(RegExp(r'\D'), '');
  if (d.isEmpty) return true;
  return celular ? d.length == 11 && d[2] == '9' : d.length == 10;
}

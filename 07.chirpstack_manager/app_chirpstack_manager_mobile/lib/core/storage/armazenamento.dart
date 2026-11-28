import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Cofre cifrado do aparelho (Keystore no Android, Keychain no iOS).
///
/// [precarregar] lê tudo de uma vez na abertura; as leituras seguintes saem da cópia em memória.
class Armazenamento {
  Armazenamento._();

  static const FlutterSecureStorage _cofre = FlutterSecureStorage();

  static Map<String, String>? _copia;

  static Future<void> precarregar() async {
    try {
      _copia = Map<String, String>.of(await _cofre.readAll());
    } catch (_) {
      _copia = null;
    }
  }

  static Future<String?> ler(String chave) async {
    final copia = _copia;
    if (copia != null) return copia[chave];
    try {
      return await _cofre.read(key: chave);
    } catch (_) {
      return null;
    }
  }

  static Future<void> gravar(String chave, String? valor) async {
    final copia = _copia;
    if (copia != null) {
      if (valor == null) {
        copia.remove(chave);
      } else {
        copia[chave] = valor;
      }
    }
    try {
      if (valor == null) {
        await _cofre.delete(key: chave);
      } else {
        await _cofre.write(key: chave, value: valor);
      }
    } catch (_) {
      // Cofre indisponível: a sessão segue só em memória.
    }
  }

  static Future<void> remover(String chave) => gravar(chave, null);
}

class ChavesArmazenamento {
  ChavesArmazenamento._();

  static const String token = 'csm_mobile_token';
  static const String refresh = 'csm_mobile_refresh';
  static const String usuario = 'csm_mobile_usuario';
  static const String emailLembrado = 'csm_mobile_email_lembrado';
  static const String tema = 'csm_mobile_tema';
  static const String telaBloqueada = 'csm_mobile_tela_bloqueada';
}

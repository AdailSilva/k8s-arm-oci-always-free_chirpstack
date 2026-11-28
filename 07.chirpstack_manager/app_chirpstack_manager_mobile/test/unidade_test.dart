import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:chirpstack_manager/core/auth/auth_modelos.dart';
import 'package:chirpstack_manager/core/format/formatos.dart';

void main() {
  group('refresh token do Set-Cookie', () {
    test('lê o cookie csm_refresh', () {
      expect(refreshDoSetCookie('csm_refresh=abc.DEF-123_x; Path=/api/v1/auth; HttpOnly; SameSite=Strict'), 'abc.DEF-123_x');
    });

    test('lê no meio de vários Set-Cookie juntados por vírgula', () {
      expect(refreshDoSetCookie('outro=1; Path=/, csm_refresh=xyz; Path=/api/v1/auth; Max-Age=604800'), 'xyz');
    });

    test('cookie apagado (valor vazio) ou ausente vira null', () {
      expect(refreshDoSetCookie('csm_refresh=; Path=/api/v1/auth; Max-Age=0'), isNull);
      expect(refreshDoSetCookie('outro=1'), isNull);
      expect(refreshDoSetCookie(null), isNull);
    });
  });

  test('validade do token pelo claim exp', () {
    final carga = base64Url.encode(utf8.encode(jsonEncode({'sub': 'a@b.com', 'exp': 1800000000}))).replaceAll('=', '');
    expect(expiracaoDoToken('h.$carga.s'), DateTime.fromMillisecondsSinceEpoch(1800000000 * 1000));
    expect(expiracaoDoToken('invalido'), isNull);
  });

  test('iniciais do usuário', () {
    expect(const UsuarioSessao(id: 1, nome: 'Maria da Silva', email: 'm@x.com', perfil: 'ADMIN').iniciais, 'MS');
    expect(const UsuarioSessao(id: 1, nome: '', email: 'joao@x.com', perfil: 'ADMIN').iniciais, 'J');
  });

  group('formatos', () {
    test('inteiro com separador de milhar', () {
      expect(Formatos.inteiro(1284093), '1.284.093');
      expect(Formatos.inteiro(999), '999');
      expect(Formatos.inteiro(-1000), '-1.000');
    });

    test('decimal com vírgula', () {
      expect(Formatos.decimal(7.5), '7,5');
      expect(Formatos.decimal(10), '10');
    });

    test('tempo relativo', () {
      final agora = DateTime(2026, 11, 25, 12);
      expect(Formatos.relativo(agora.subtract(const Duration(seconds: 10)), agora: agora), 'agora');
      expect(Formatos.relativo(agora.subtract(const Duration(minutes: 5)), agora: agora), 'há 5 min');
      expect(Formatos.relativo(agora.subtract(const Duration(hours: 3)), agora: agora), 'há 3 h');
      expect(Formatos.relativo(agora.subtract(const Duration(days: 2)), agora: agora), 'há 2 dias');
      expect(Formatos.relativo(null), 'nunca');
    });
  });
}

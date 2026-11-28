/// Leitura tolerante de JSON: a API pode omitir campos ou mandar números como texto.
typedef Json = Map<String, dynamic>;

Json comoMapa(Object? valor) => valor is Map ? Map<String, dynamic>.from(valor) : <String, dynamic>{};

List<Json> comoLista(Object? valor) =>
    valor is List ? valor.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList() : <Json>[];

extension LeituraJson on Json {
  String? texto(String chave) {
    final v = this[chave];
    if (v == null) return null;
    final s = v.toString();
    return s.isEmpty ? null : s;
  }

  int? inteiro(String chave) {
    final v = this[chave];
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v);
    return null;
  }

  double? decimal(String chave) {
    final v = this[chave];
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v);
    return null;
  }

  bool logico(String chave, [bool padrao = false]) {
    final v = this[chave];
    if (v is bool) return v;
    if (v is String) return v.toLowerCase() == 'true';
    return padrao;
  }

  DateTime? data(String chave) {
    final s = texto(chave);
    return s == null ? null : DateTime.tryParse(s)?.toLocal();
  }

  Json mapa(String chave) => comoMapa(this[chave]);

  List<Json> lista(String chave) => comoLista(this[chave]);
}

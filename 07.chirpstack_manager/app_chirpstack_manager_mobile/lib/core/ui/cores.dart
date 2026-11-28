import 'package:flutter/material.dart';

/// Paleta do ChirpStack Manager (brand/BRAND.md): azul do sistema + teal da marca ChirpStack.
class CmCores {
  CmCores._();

  // Azul Manager (o mesmo "blue" do preset PrimeNG do painel web)
  static const Color azul300 = Color(0xFF93C5FD);
  static const Color azul400 = Color(0xFF60A5FA);
  static const Color azul500 = Color(0xFF3B82F6);
  static const Color azul600 = Color(0xFF2563EB);
  static const Color azul700 = Color(0xFF1D4ED8);

  // Azul noite do login e da abertura
  static const Color noite950 = Color(0xFF070F24);
  static const Color noite = Color(0xFF0B1A3A);
  static const Color noite800 = Color(0xFF13254D);

  /// Filete teal (assinatura do sistema, cor da marca ChirpStack).
  static const Color teal = Color(0xFF2DD4BF);
  static const Color tealEscuro = Color(0xFF0F766E);

  // Estados
  static const Color online = Color(0xFF22C55E);
  static const Color offline = Color(0xFFEF4444);
  static const Color aviso = Color(0xFFF59E0B);
  static const Color neutro = Color(0xFF94A3B8);

  static const LinearGradient degradeSistema = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [azul500, azul700],
  );
}

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/ui/cores.dart';

/// Tema do app nos modos claro e escuro, com o azul do sistema como cor primária.
ThemeData construirTema(Brightness brilho) {
  final escuro = brilho == Brightness.dark;
  final esquema = ColorScheme.fromSeed(
    seedColor: CmCores.azul600,
    brightness: brilho,
    primary: escuro ? CmCores.azul400 : CmCores.azul600,
    secondary: escuro ? CmCores.teal : CmCores.tealEscuro,
    surface: escuro ? const Color(0xFF0E1A33) : const Color(0xFFF7F9FC),
  );
  final borda = OutlineInputBorder(
    borderRadius: BorderRadius.circular(14),
    borderSide: BorderSide(color: escuro ? const Color(0xFF2A3F6B) : const Color(0xFFD3DCEA), width: 1.5),
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: esquema,
    scaffoldBackgroundColor: escuro ? CmCores.noite950 : const Color(0xFFF1F5F9),
    appBarTheme: AppBarTheme(
      centerTitle: false,
      elevation: 0,
      scrolledUnderElevation: 1,
      backgroundColor: escuro ? CmCores.noite : Colors.white,
      foregroundColor: escuro ? Colors.white : const Color(0xFF0B1220),
      titleTextStyle: TextStyle(
        fontSize: 19,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
        color: escuro ? Colors.white : const Color(0xFF0B1220),
      ),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      color: escuro ? CmCores.noite800 : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: escuro ? const Color(0xFF203763) : const Color(0xFFE2E8F0)),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: escuro ? const Color(0xFF0E1D40) : const Color(0xFFF4F7FB),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: borda,
      enabledBorder: borda,
      focusedBorder: borda.copyWith(borderSide: const BorderSide(color: CmCores.azul500, width: 1.8)),
      errorBorder: borda.copyWith(borderSide: const BorderSide(color: CmCores.offline, width: 1.5)),
      focusedErrorBorder: borda.copyWith(borderSide: const BorderSide(color: CmCores.offline, width: 1.8)),
      hintStyle: TextStyle(color: escuro ? const Color(0xFF7F90B3) : const Color(0xFF8A9BBD)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w700),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: escuro ? CmCores.noite : Colors.white,
      indicatorColor: (escuro ? CmCores.azul500 : CmCores.azul600).withValues(alpha: 0.16),
      labelTextStyle: const WidgetStatePropertyAll(TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
    ),
  );
}

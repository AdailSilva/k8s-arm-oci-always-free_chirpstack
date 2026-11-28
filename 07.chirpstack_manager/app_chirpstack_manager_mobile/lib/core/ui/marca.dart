import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import 'package:chirpstack_manager/core/ui/cores.dart';

/// Símbolo do sistema (brand/chirpstack-manager-simbolo.svg): antena da marca sobre o azul Manager.
class SimboloManager extends StatelessWidget {
  const SimboloManager({super.key, this.tamanho = 40});

  final double tamanho;

  @override
  Widget build(BuildContext context) {
    return SvgPicture.asset(
      'assets/brand/chirpstack-manager-simbolo.svg',
      width: tamanho,
      height: tamanho,
      semanticsLabel: 'ChirpStack Manager',
    );
  }
}

/// Marca horizontal: símbolo + "ChirpStack Manager" (+ linha de apoio opcional).
class MarcaManager extends StatelessWidget {
  const MarcaManager({super.key, this.negativo = false, this.tamanho = 36, this.apoio = true});

  final bool negativo;
  final double tamanho;
  final bool apoio;

  @override
  Widget build(BuildContext context) {
    final corNome = negativo ? Colors.white : const Color(0xFF0B1220);
    final corSistema = negativo ? CmCores.azul300 : CmCores.azul600;
    final corApoio = negativo ? const Color(0xFFCBD5E1) : const Color(0xFF64748B);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        SimboloManager(tamanho: tamanho),
        SizedBox(width: tamanho * 0.3),
        Flexible(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text.rich(
                TextSpan(children: [
                  TextSpan(text: 'ChirpStack ', style: TextStyle(color: corNome, fontWeight: FontWeight.w800)),
                  TextSpan(text: 'Manager', style: TextStyle(color: corSistema, fontWeight: FontWeight.w700)),
                ]),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: tamanho * 0.46, letterSpacing: -0.3, height: 1.1),
              ),
              if (apoio)
                Text(
                  'por ChirpStack · LoRaWAN na OCI',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: tamanho * 0.25, color: corApoio, letterSpacing: 0.2),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

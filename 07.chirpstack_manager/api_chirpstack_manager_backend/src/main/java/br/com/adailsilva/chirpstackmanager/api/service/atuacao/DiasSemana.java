package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;

/** Dias da semana guardados em bits: 1 = segunda ... 64 = domingo (127 = todos). API: lista 1..7 (ISO). */
public final class DiasSemana {

    public static final int TODOS = 127;

    private DiasSemana() {
    }

    public static int mascara(List<Integer> dias) {
        if (dias == null || dias.isEmpty()) {
            return TODOS;
        }
        int m = 0;
        for (Integer d : dias) {
            if (d == null || d < 1 || d > 7) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Dias da semana: de 1 (segunda) a 7 (domingo).");
            }
            m |= 1 << (d - 1);
        }
        return m;
    }

    public static List<Integer> lista(int mascara) {
        List<Integer> l = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            if ((mascara & (1 << (d - 1))) != 0) {
                l.add(d);
            }
        }
        return l;
    }

    public static boolean contem(int mascara, DayOfWeek dia) {
        return (mascara & (1 << (dia.getValue() - 1))) != 0;
    }
}

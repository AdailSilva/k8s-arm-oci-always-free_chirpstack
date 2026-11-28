import { DestroyRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { GatilhoAutomacao, MedicaoPzem, OrigemComando, ParametrosComando, ResultadoTentativa, StatusComando, TipoComando } from '@/app/core/http/atuacao.models';

export type Severidade = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

export const ROTULO_STATUS: Record<StatusComando, string> = {
    PENDENTE: 'Pendente',
    ENFILEIRADO: 'Enfileirado',
    TRANSMITIDO: 'Transmitido',
    EXECUTADO: 'Executado',
    FALHOU: 'Falhou',
    EXPIRADO: 'Expirado',
    CANCELADO: 'Cancelado'
};

export const COR_STATUS: Record<StatusComando, Severidade> = {
    PENDENTE: 'secondary',
    ENFILEIRADO: 'info',
    TRANSMITIDO: 'info',
    EXECUTADO: 'success',
    FALHOU: 'danger',
    EXPIRADO: 'warn',
    CANCELADO: 'contrast'
};

export const ICONE_STATUS: Record<StatusComando, string> = {
    PENDENTE: 'pi pi-clock',
    ENFILEIRADO: 'pi pi-inbox',
    TRANSMITIDO: 'pi pi-send',
    EXECUTADO: 'pi pi-check',
    FALHOU: 'pi pi-times',
    EXPIRADO: 'pi pi-hourglass',
    CANCELADO: 'pi pi-ban'
};

export const STATUS_OPCOES = (Object.keys(ROTULO_STATUS) as StatusComando[]).map((s) => ({ label: ROTULO_STATUS[s], value: s }));

export function statusFinal(s: StatusComando): boolean {
    return s === 'EXECUTADO' || s === 'FALHOU' || s === 'EXPIRADO' || s === 'CANCELADO';
}

export const ROTULO_ORIGEM: Record<OrigemComando, string> = {
    MANUAL: 'Manual',
    AGENDAMENTO: 'Agendamento',
    AUTOMACAO: 'Automação',
    CENA: 'Cena',
    RECONCILIACAO: 'Reconciliação',
    PULSO: 'Fim do pulso'
};

export const ROTULO_RESULTADO: Record<ResultadoTentativa, string> = {
    EXECUTADO: 'Executado',
    SEM_RESPOSTA: 'Sem resposta',
    NAO_CONFIRMADO: 'Não confirmado',
    ERRO: 'Erro',
    RECUSADO: 'Recusado',
    OCUPADO: 'Ocupado',
    PERDIDO: 'Perdido',
    EXPIRADO: 'Expirado',
    CANCELADO: 'Cancelado'
};

export function corResultado(r?: ResultadoTentativa | null): Severidade {
    if (!r) {
        return 'info';
    }
    if (r === 'EXECUTADO') {
        return 'success';
    }

    return r === 'CANCELADO' || r === 'EXPIRADO' || r === 'OCUPADO' ? 'warn' : 'danger';
}

export const ROTULO_GATILHO: Record<GatilhoAutomacao, string> = {
    ALERTA_ABERTO: 'Quando o alerta abrir',
    ALERTA_RESOLVIDO: 'Quando o alerta for resolvido'
};

/** 1 = segunda ... 7 = domingo (ISO-8601, como o backend). */
export const DIAS_SEMANA = [
    { value: 1, curto: 'Seg', nome: 'Segunda' },
    { value: 2, curto: 'Ter', nome: 'Terça' },
    { value: 3, curto: 'Qua', nome: 'Quarta' },
    { value: 4, curto: 'Qui', nome: 'Quinta' },
    { value: 5, curto: 'Sex', nome: 'Sexta' },
    { value: 6, curto: 'Sáb', nome: 'Sábado' },
    { value: 7, curto: 'Dom', nome: 'Domingo' }
];

export function rotuloDias(dias?: number[] | null): string {
    if (!dias?.length || dias.length === 7) {
        return 'todos os dias';
    }
    const ord = [...dias].sort((a, b) => a - b);

    if (ord.join() === '1,2,3,4,5') {
        return 'dias úteis';
    }

    if (ord.join() === '6,7') {
        return 'fins de semana';
    }

    return ord.map((d) => DIAS_SEMANA[d - 1]?.curto ?? d).join(', ');
}

/** "HH:mm:ss" → "HH:mm". */
export function hora(h?: string | null): string {
    return h ? h.substring(0, 5) : '';
}

/** Comandos que agem sobre um canal de relé ou de medidor (o formulário oferece os periféricos). */
export function usaCanal(t?: TipoComando | null): boolean {
    return t === 'RELE_DEFINIR' || t === 'RELE_PULSO' || t === 'PZEM_LER' || t === 'PZEM_ZERAR_ENERGIA' || t === 'PZEM_LIMITE_POTENCIA';
}

/** Remove os parâmetros que o comando não usa (o backend ignoraria, mas o JSON fica limpo). */
export function limparParametros(t: TipoComando, p: ParametrosComando): ParametrosComando | null {
    const campos: Partial<Record<TipoComando, (keyof ParametrosComando)[]>> = {
        RELE_DEFINIR: ['canal', 'acao'],
        RELE_PULSO: ['canal', 'estado', 'duracaoMs'],
        RELE_MASCARA: ['mascara', 'valores'],
        PZEM_LER: ['canal'],
        PZEM_ZERAR_ENERGIA: ['canal'],
        PZEM_LIMITE_POTENCIA: ['canal', 'watts'],
        INTERVALO: ['segundos'],
        CONFIRMADO: ['confirmado'],
        CLASSE: ['classe'],
        REINICIAR: ['atrasoMs'],
        BRUTO: ['porta', 'dadosHex', 'confirmado']
    };
    const usados = campos[t] ?? [];

    if (usados.length === 0) {
        return null;
    }
    const out: Record<string, unknown> = {};

    for (const c of usados) {
        const v = p[c];

        if (v !== null && v !== undefined && v !== '') {
            out[c] = c === 'dadosHex' ? String(v).replace(/\s+/g, '') : v;
        }
    }

    return out as ParametrosComando;
}

/** Validação local dos parâmetros; devolve a mensagem do primeiro problema ou null. */
export function validarParametros(t: TipoComando | null | undefined, p: ParametrosComando): string | null {
    if (!t) {
        return 'Escolha o comando.';
    }
    switch (t) {
        case 'RELE_DEFINIR':
            return p.acao ? null : 'Escolha a ação do relé.';
        case 'RELE_PULSO':
            if (p.canal === null || p.canal === undefined) {
                return 'Escolha o relé.';
            }

            return p.duracaoMs && p.duracaoMs > 0 ? null : 'Informe a duração do pulso.';
        case 'RELE_MASCARA':
            return p.mascara ? null : 'Marque ao menos um relé.';
        case 'PZEM_LER':
        case 'PZEM_ZERAR_ENERGIA':
            return p.canal === null || p.canal === undefined ? 'Escolha o medidor.' : null;
        case 'PZEM_LIMITE_POTENCIA':
            if (p.canal === null || p.canal === undefined) {
                return 'Escolha o medidor.';
            }

            return p.watts !== null && p.watts !== undefined ? null : 'Informe o limite em watts.';
        case 'INTERVALO':
            return p.segundos && p.segundos > 0 ? null : 'Informe o intervalo em segundos.';
        case 'CLASSE':
            return p.classe ? null : 'Escolha a classe.';
        case 'BRUTO': {
            if (!p.porta) {
                return 'Informe a porta (1 a 223).';
            }
            const hex = (p.dadosHex ?? '').replace(/\s+/g, '');

            return hex.length > 0 && hex.length % 2 === 0 && /^[0-9a-fA-F]+$/.test(hex) ? null : 'Informe os bytes em hexadecimal (pares de dígitos).';
        }
        default:
            return null;
    }
}

/** Lê o JSON de uma medição do PZEM (null se vazio ou inválido). */
export function medicao(json?: string | null): MedicaoPzem | null {
    if (!json) {
        return null;
    }
    try {
        return JSON.parse(json) as MedicaoPzem;
    } catch {
        return null;
    }
}

/** 409 "carga crítica": o backend pede a confirmação explícita do acionamento. */
export function ehCargaCritica(err: unknown): boolean {
    return err instanceof HttpErrorResponse && err.status === 409 && /carga cr[ií]tica/i.test(NotifyService.mensagem(err));
}

/**
 * Atualiza a cada {@code ms} enquanto a aba do navegador estiver visível; para sozinho quando o
 * componente é destruído.
 */
export function autoAtualizar(destroyRef: DestroyRef, fn: () => void, ms = 10_000): void {
    const t = setInterval(() => {
        if (typeof document === 'undefined' || document.visibilityState === 'visible') {
            fn();
        }
    }, ms);
    destroyRef.onDestroy(() => clearInterval(t));
}

/** Número em pt-BR com até {@code casas} casas decimais ("—" se vazio). */
export function numBr(v: number | null | undefined, casas = 2, minimo = 0): string {
    if (v === null || v === undefined || !Number.isFinite(Number(v))) {
        return '—';
    }

    return Number(v).toLocaleString('pt-BR', { minimumFractionDigits: minimo, maximumFractionDigits: casas });
}

export function moeda(v: number | null | undefined): string {
    if (v === null || v === undefined || !Number.isFinite(Number(v))) {
        return '—';
    }

    return Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/** Segundos → "1 h 5 min", "3 d 2 h"... */
export function duracao(seg?: number | null): string {
    if (seg === null || seg === undefined) {
        return '—';
    }
    const s = Math.max(0, Math.floor(seg));
    const d = Math.floor(s / 86400);
    const h = Math.floor((s % 86400) / 3600);
    const m = Math.floor((s % 3600) / 60);

    if (d > 0) {
        return `${d} d ${h} h`;
    }

    if (h > 0) {
        return `${h} h ${m} min`;
    }

    return m > 0 ? `${m} min` : `${s} s`;
}

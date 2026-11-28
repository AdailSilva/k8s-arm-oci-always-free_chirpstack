import { SituacaoDispositivo } from '@/app/core/http/models';

export const ROTULO_SITUACAO: Record<SituacaoDispositivo, string> = {
    ONLINE: 'On-line',
    SEM_COMUNICACAO: 'Sem comunicação',
    NUNCA: 'Sem mensagens'
};

export function rotuloSituacao(s: SituacaoDispositivo): string {
    return ROTULO_SITUACAO[s] ?? s;
}

export function corSituacao(s: SituacaoDispositivo): 'success' | 'danger' | 'secondary' {
    return s === 'ONLINE' ? 'success' : s === 'SEM_COMUNICACAO' ? 'danger' : 'secondary';
}

export function corBateria(b?: number | null): string {
    if (b === null || b === undefined) {
        return 'text-surface-400';
    }
    return b < 20 ? 'text-red-500' : b < 50 ? 'text-amber-500' : 'text-green-600';
}

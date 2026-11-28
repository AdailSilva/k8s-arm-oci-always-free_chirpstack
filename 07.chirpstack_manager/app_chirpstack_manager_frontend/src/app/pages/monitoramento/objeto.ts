/** Texto curto de um objeto decodificado: "temperatura 25,5 · umidade 55". */
export function resumoObjeto(json?: string | null, max = 6): string {
    if (!json) {
        return '—';
    }
    try {
        const pares: string[] = [];
        achatar('', JSON.parse(json), pares);
        if (pares.length === 0) {
            return '—';
        }
        return pares.slice(0, max).join(' · ') + (pares.length > max ? ' …' : '');
    } catch {
        return json.length > 80 ? json.slice(0, 80) + '…' : json;
    }
}

function achatar(prefixo: string, v: unknown, out: string[]): void {
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
        for (const [k, x] of Object.entries(v as Record<string, unknown>)) {
            achatar(prefixo ? `${prefixo}.${k}` : k, x, out);
        }
    } else if (prefixo) {
        const texto = typeof v === 'number' ? v.toLocaleString('pt-BR', { maximumFractionDigits: 3 }) : String(v);
        out.push(`${prefixo} ${texto}`);
    }
}

/** JSON indentado para exibição. */
export function jsonBonito(json?: string | null): string {
    if (!json) {
        return '';
    }
    try {
        return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
        return json;
    }
}

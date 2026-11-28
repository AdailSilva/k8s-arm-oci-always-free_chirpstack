/** Dados fixos dos formulários com endereço (mesmo conjunto dos demais sistemas). */

export interface Opcao {
    label: string;
    value: string;
}

/** Tipos de logradouro; o valor gravado é o NOME em maiúsculas (RUA, AVENIDA...). */
export const TIPOS_LOGRADOURO: Opcao[] = [
    { label: 'Rua', value: 'RUA' },
    { label: 'Avenida', value: 'AVENIDA' },
    { label: 'Alameda', value: 'ALAMEDA' },
    { label: 'Beco', value: 'BECO' },
    { label: 'Calçada', value: 'CALCADA' },
    { label: 'Conjunto', value: 'CONJUNTO' },
    { label: 'Estrada', value: 'ESTRADA' },
    { label: 'Jardim', value: 'JARDIM' },
    { label: 'Ladeira', value: 'LADEIRA' },
    { label: 'Largo', value: 'LARGO' },
    { label: 'Lote', value: 'LOTE' },
    { label: 'Pátio', value: 'PATIO' },
    { label: 'Praça', value: 'PRACA' },
    { label: 'Parque', value: 'PARQUE' },
    { label: 'Passagem', value: 'PASSAGEM' },
    { label: 'Ponte', value: 'PONTE' },
    { label: 'Rodovia', value: 'RODOVIA' },
    { label: 'Quadra', value: 'QUADRA' },
    { label: 'Travessa', value: 'TRAVESSA' },
    { label: 'Via', value: 'VIA' },
    { label: 'Viaduto', value: 'VIADUTO' },
    { label: 'Viela', value: 'VIELA' }
];

/** As 27 unidades da federação. */
export const UFS: Opcao[] = [
    ['AC', 'Acre'], ['AL', 'Alagoas'], ['AP', 'Amapá'], ['AM', 'Amazonas'], ['BA', 'Bahia'], ['CE', 'Ceará'],
    ['DF', 'Distrito Federal'], ['ES', 'Espírito Santo'], ['GO', 'Goiás'], ['MA', 'Maranhão'], ['MT', 'Mato Grosso'],
    ['MS', 'Mato Grosso do Sul'], ['MG', 'Minas Gerais'], ['PA', 'Pará'], ['PB', 'Paraíba'], ['PR', 'Paraná'],
    ['PE', 'Pernambuco'], ['PI', 'Piauí'], ['RJ', 'Rio de Janeiro'], ['RN', 'Rio Grande do Norte'],
    ['RS', 'Rio Grande do Sul'], ['RO', 'Rondônia'], ['RR', 'Roraima'], ['SC', 'Santa Catarina'], ['SP', 'São Paulo'],
    ['SE', 'Sergipe'], ['TO', 'Tocantins']
].map(([sigla, nome]) => ({ label: `${sigla} — ${nome}`, value: sigla }));

/** Reconhece o tipo no início do logradouro devolvido pelo CEP ("Avenida Brasil" → AVENIDA, "Brasil"). */
export function separarTipoLogradouro(logradouro: string | undefined | null): { tipo: string | null; nome: string } {
    const texto = (logradouro ?? '').trim();
    const semAcento = texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();
    for (const t of TIPOS_LOGRADOURO) {
        const rotulo = t.label.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();
        if (semAcento === rotulo || semAcento.startsWith(rotulo + ' ')) {
            return { tipo: t.value, nome: texto.slice(t.label.length).trim() };
        }
    }
    return { tipo: null, nome: texto };
}

/** "(88) 99999-0000" / "(88) 3333-0000" a partir dos dígitos (vazio fica vazio). */
export function formatarTelefone(digitos: string | null | undefined): string {
    const d = (digitos ?? '').replace(/\D/g, '');
    if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
    if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
    return d;
}

/** "62320-000" a partir dos dígitos. */
export function formatarCep(digitos: string | null | undefined): string {
    const d = (digitos ?? '').replace(/\D/g, '');
    return d.length === 8 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

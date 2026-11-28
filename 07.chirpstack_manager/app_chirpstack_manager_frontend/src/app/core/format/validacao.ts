/**
 * Validação de e-mail usada nos formulários. Vazio é tratado como válido:
 * a obrigatoriedade é verificada à parte.
 */
const EMAIL = /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;

export const MSG_EMAIL_INVALIDO = 'E-mail inválido.';

export function emailValido(v: string | null | undefined): boolean {
    const s = (v ?? '').trim();
    return !s || (s.length <= 160 && EMAIL.test(s));
}

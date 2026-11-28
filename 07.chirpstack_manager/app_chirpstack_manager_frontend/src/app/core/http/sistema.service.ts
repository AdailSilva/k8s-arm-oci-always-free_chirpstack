import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@/environments/environment';
import { CepConsulta, ContaEmail, DadosCadastro, ListResponse, PerfilAcesso, RegistroAuditoria, UsuarioSistema } from './models';

export interface UsuarioSistemaForm {
    nome: string;
    email: string;
    perfil: PerfilAcesso;
    ativo: boolean;
    senha?: string;
    novaSenha?: string;
    /** Novo usuário: envia o convite para ele definir a própria senha (padrão). */
    enviarConvite?: boolean;
    cadastro?: DadosCadastro;
}

/**
 * Usuários do painel e auditoria (recursos do próprio backend, não do ChirpStack).
 */
@Injectable({ providedIn: 'root' })
export class SistemaService {
    private http = inject(HttpClient);
    private base = environment.apiUrl;

    listarUsuarios(busca: string, pagina: number, tamanho: number): Observable<ListResponse<UsuarioSistema>> {
        const params = new HttpParams().set('busca', busca ?? '').set('pagina', pagina).set('tamanho', tamanho);
        return this.http.get<ListResponse<UsuarioSistema>>(`${this.base}/usuarios`, { params });
    }
    criarUsuario(u: UsuarioSistemaForm): Observable<UsuarioSistema> {
        return this.http.post<UsuarioSistema>(`${this.base}/usuarios`, u);
    }
    atualizarUsuario(id: number, u: UsuarioSistemaForm): Observable<UsuarioSistema> {
        return this.http.put<UsuarioSistema>(`${this.base}/usuarios/${id}`, u);
    }
    desbloquearUsuario(id: number): Observable<void> {
        return this.http.post<void>(`${this.base}/usuarios/${id}/desbloquear`, {});
    }
    excluirUsuario(id: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/usuarios/${id}`);
    }
    /** Reenvia o convite (sem senha definida) ou o link de confirmação do e-mail. */
    reenviarLinkUsuario(id: number): Observable<{ enviado: boolean; para: string }> {
        return this.http.post<{ enviado: boolean; para: string }>(`${this.base}/usuarios/${id}/reenviar-link`, {});
    }

    // ── localidades ──
    buscarCep(cep: string): Observable<CepConsulta> {
        return this.http.get<CepConsulta>(`${this.base}/localidades/cep/${encodeURIComponent(cep.replace(/\D/g, ''))}`);
    }

    listarAuditoria(busca: string, acao: string, pagina: number, tamanho: number): Observable<ListResponse<RegistroAuditoria>> {
        const params = new HttpParams().set('busca', busca ?? '').set('acao', acao ?? '').set('pagina', pagina).set('tamanho', tamanho);
        return this.http.get<ListResponse<RegistroAuditoria>>(`${this.base}/auditoria`, { params });
    }

    // ── conta de e-mail (ADMIN) ──
    contaEmail(): Observable<ContaEmail> {
        return this.http.get<ContaEmail>(`${this.base}/conta-email`);
    }
    alterarSenhaEmail(senha: string, confirmacao: string, testarAntes: boolean): Observable<ContaEmail> {
        return this.http.put<ContaEmail>(`${this.base}/conta-email`, { senha, confirmacao, testarAntes });
    }
    removerSenhaEmail(): Observable<ContaEmail> {
        return this.http.delete<ContaEmail>(`${this.base}/conta-email`);
    }
    testarSenhaEmail(senha: string | null): Observable<void> {
        return this.http.post<void>(`${this.base}/conta-email/testar`, { senha });
    }
    enviarTesteEmail(): Observable<{ destinatario: string }> {
        return this.http.post<{ destinatario: string }>(`${this.base}/conta-email/enviar-teste`, {});
    }

    // ── recuperação de senha (público) ──
    esqueciSenha(email: string): Observable<void> {
        return this.http.post<void>(`${this.base}/auth/senha/esqueci`, { email });
    }
    redefinirSenha(token: string, novaSenha: string): Observable<void> {
        return this.http.post<void>(`${this.base}/auth/senha/redefinir`, { token, novaSenha });
    }
    confirmarEmail(token: string): Observable<{ mensagem: string }> {
        return this.http.post<{ mensagem: string }>(`${this.base}/auth/email/confirmar`, { token });
    }
    reenviarConfirmacao(email: string): Observable<void> {
        return this.http.post<void>(`${this.base}/auth/email/reenviar`, { email });
    }
}

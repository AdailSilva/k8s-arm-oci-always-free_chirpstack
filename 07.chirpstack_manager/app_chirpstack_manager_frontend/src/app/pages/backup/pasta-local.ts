/**
 * GRAVAÇÃO NUMA PASTA DA MÁQUINA DO USUÁRIO.
 *
 * Encapsula a **File System Access API** (`showDirectoryPicker`), que é o único
 * caminho pelo qual uma página web pode escrever num diretório escolhido pela
 * pessoa. Sem ela o navegador só sabe fazer uma coisa com um arquivo: jogá-lo na
 * pasta de downloads, sem escolha e sem nome de pasta.
 *
 * ## Por que não dá para o servidor escrever direto na máquina
 *
 * Quem executa o `pg_dump` é o pod do PostgreSQL, dentro do cluster na Oracle
 * Cloud. Ele não tem — nem poderia ter — acesso ao disco de quem está no
 * navegador. O arquivo necessariamente nasce no servidor e depois atravessa a
 * rede. O que esta classe faz é dar destino a essa travessia: em vez de cair na
 * pasta de downloads, o conteúdo é escrito na pasta que a pessoa apontou.
 *
 * ## Suporte
 *
 * A API existe em navegadores baseados em Chromium (Chrome, Edge, Opera,
 * Brave). Firefox e Safari não a implementam. Por isso todo o fluxo tem um
 * caminho alternativo por download comum: {@link suportado} responde antes, a
 * tela ajusta o texto, e ninguém fica sem conseguir salvar — só sem escolher a
 * pasta.
 *
 * A API também só funciona em contexto seguro (HTTPS ou localhost) e exige que
 * a chamada parta de um gesto do usuário — um clique. Chamar
 * {@link escolherPasta} fora de um handler de clique faz o navegador recusar.
 *
 * ## PASTAS QUE O NAVEGADOR RECUSA
 *
 * O Chromium mantém uma lista fixa de diretórios que o seletor NÃO aceita, e
 * recusa cada um deles com a mesma caixa: *"não é possível abrir esta pasta
 * porque ela contém arquivos de sistema"*, com os botões Cancelar e Escolher
 * outra pasta. A lista cobre os diretórios do sistema (`/`, `/usr`, `/etc`,
 * `C:\Windows`, a Library do macOS) e também as **pastas conhecidas do usuário
 * na raiz**: a própria pasta pessoal, Área de Trabalho, Documentos, Downloads.
 *
 * Não é defeito do sistema nem permissão do sistema operacional: é código
 * compilado no navegador, sem flag e sem política que o desligue. Um pen drive
 * ou HD externo funciona justamente por não estar na lista.
 *
 * Isso importa aqui porque as pastas recusadas são exatamente as que o operador
 * tentaria primeiro. A saída é apontar uma pasta PRÓPRIA (`~/chirpstack-manager/backups`,
 * `D:\Backups`) — ou nenhuma, e deixar o arquivo seguir por download comum, que
 * não passa por essa lista. O caminho do download é o {@link baixarPeloNavegador}
 * e continua sendo backup completo: muda só quem escolhe o destino.
 *
 * O seletor NÃO avisa o site quando recusa uma pasta. A caixa é do navegador e
 * o seletor continua aberto; o site só fica sabendo de alguma coisa se a pessoa
 * desistir e clicar em Cancelar — e aí chega um `AbortError`, o mesmo de um
 * cancelamento comum. Por isso {@link escolherPasta} não tem como distinguir
 * "desisti" de "o navegador não deixou", e quem chama deve tratar o `null` como
 * as duas coisas ao mesmo tempo, oferecendo a alternativa em vez de silenciar.
 */

/** Tipos mínimos da File System Access API — o TS padrão ainda não os traz. */
interface FileSystemWritableStream {
    write(data: Blob | BufferSource | string): Promise<void>;
    close(): Promise<void>;
}
interface FileSystemFileHandleLike {
    createWritable(options?: { keepExistingData?: boolean }): Promise<FileSystemWritableStream>;
}
export interface PastaLocal {
    name: string;
    getFileHandle(name: string, options?: { create?: boolean }): Promise<FileSystemFileHandleLike>;
    queryPermission?(d: { mode: 'read' | 'readwrite' }): Promise<PermissionState>;
    requestPermission?(d: { mode: 'read' | 'readwrite' }): Promise<PermissionState>;
}

type JanelaComPicker = Window & {
    showDirectoryPicker?(options?: {
        mode?: 'read' | 'readwrite';
        startIn?: string;
        id?: string;
    }): Promise<PastaLocal>;
};

/** O navegador consegue gravar numa pasta escolhida pelo usuário? */
export function suportado(): boolean {
    return typeof (window as JanelaComPicker).showDirectoryPicker === 'function';
}

/**
 * Explicação da recusa, para a tela mostrar quando o seletor volta sem pasta.
 *
 * Fica aqui, e não na tela, porque é conhecimento sobre a API — a mesma frase
 * serve a qualquer lugar que venha a abrir o seletor.
 */
export const AVISO_PASTA_RECUSADA =
    'Se o navegador recusou a pasta dizendo que ela "contém arquivos de sistema", ' +
    'a restrição é dele: Documentos, Downloads, Área de Trabalho e a pasta pessoal ' +
    'não são aceitas. Escolha uma pasta sua (ex.: uma pasta Backups criada dentro ' +
    'da pasta pessoal) ou um disco externo — ou siga sem pasta, que o arquivo vai ' +
    'para os downloads do navegador.';

/**
 * Abre o seletor de pasta do sistema operacional.
 *
 * Devolve `null` quando a pessoa sai do seletor sem escolher — seja por ter
 * desistido, seja por o navegador ter recusado as pastas tentadas. Nenhum dos
 * dois é erro e nenhum deve virar toast vermelho; quem chama oferece o caminho
 * do download. Precisa ser chamado a partir de um clique.
 *
 * NÃO se passa `startIn`. Os valores que a API aceita para essa opção
 * ('documents', 'downloads', 'desktop') apontam justamente para as pastas que o
 * navegador recusa — abrir o seletor já dentro de uma delas empurra o operador
 * para a caixa de recusa antes de ele ter escolhido qualquer coisa.
 */
export async function escolherPasta(): Promise<PastaLocal | null> {
    const w = window as JanelaComPicker;
    if (!w.showDirectoryPicker) {
        throw new Error(
            'Este navegador não permite escolher uma pasta do computador. ' +
            'Use Chrome, Edge ou Opera — ou deixe o arquivo ir para a pasta de downloads.'
        );
    }
    try {
        // `id` faz o navegador lembrar a última pasta usada para backups,
        // separada das outras escolhas do site.
        return await w.showDirectoryPicker({ mode: 'readwrite', id: 'chirpstack-manager-backups' });
    } catch (e: unknown) {
        if (e instanceof DOMException) {
            // Desistência ou pasta recusada pelo navegador — os dois chegam
            // assim, e nenhum é falha do sistema.
            if (e.name === 'AbortError') return null;
            // Chamada fora de um gesto do usuário, ou contexto inseguro (HTTP).
            // Vale mensagem própria: aqui o problema é do sistema, não do operador.
            if (e.name === 'SecurityError') {
                throw new Error(
                    'O navegador bloqueou o seletor de pasta. Ele só abre a partir de um ' +
                    'clique e em conexão segura (HTTPS). Use o botão novamente ou siga sem ' +
                    'pasta, que o arquivo vai para os downloads.'
                );
            }
        }
        throw e;
    }
}

/**
 * Confirma que a permissão de escrita continua valendo.
 *
 * O navegador esquece a autorização entre sessões e pode revogá-la a qualquer
 * momento. Sem esta reconfirmação a gravação falharia no fim do processo, depois
 * de o backup inteiro já ter atravessado a rede.
 */
export async function temPermissaoDeEscrita(pasta: PastaLocal): Promise<boolean> {
    if (!pasta.queryPermission) return true;
    const atual = await pasta.queryPermission({ mode: 'readwrite' });
    if (atual === 'granted') return true;
    if (!pasta.requestPermission) return false;
    return (await pasta.requestPermission({ mode: 'readwrite' })) === 'granted';
}

/** Grava um blob como arquivo dentro da pasta escolhida, sobrescrevendo. */
export async function gravar(pasta: PastaLocal, nome: string, conteudo: Blob): Promise<void> {
    const arquivo = await pasta.getFileHandle(nome, { create: true });
    const fluxo = await arquivo.createWritable();
    try {
        await fluxo.write(conteudo);
    } finally {
        // close() no finally de propósito: sem ele um erro de escrita deixa o
        // fluxo aberto e o arquivo truncado no disco, parecendo um backup.
        await fluxo.close();
    }
}

/**
 * Caminho alternativo: download comum.
 *
 * Serve a DOIS casos, e não só a um. O primeiro é o navegador que não tem a API
 * (Firefox, Safari). O segundo — o que aparece no dia a dia — é o Chromium ter
 * recusado todas as pastas que o operador tentou: ali a API existe, mas não há
 * pasta escolhida, e sem esta saída o backup simplesmente não sairia.
 *
 * Não permite escolher a pasta: o arquivo vai para onde o navegador estiver
 * configurado para baixar. Em compensação NÃO passa pela lista de pastas
 * bloqueadas do seletor — é o mecanismo de download de sempre —, então funciona
 * mesmo quando a pasta de downloads é uma das pastas recusadas pelo seletor.
 */
export function baixarPeloNavegador(nome: string, conteudo: Blob): void {
    const url = URL.createObjectURL(conteudo);
    const a = document.createElement('a');
    a.href = url;
    a.download = nome;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

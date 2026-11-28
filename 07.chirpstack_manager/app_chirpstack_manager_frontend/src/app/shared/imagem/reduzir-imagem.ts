/**
 * Reduz uma imagem para no máximo `lado` px no maior lado e devolve um data URI JPEG.
 *
 * A foto de perfil é gravada em base64 e viaja na listagem de usuários e na sessão; a foto
 * original do celular (vários MB) pesaria em tudo isso. Reduzida, fica em dezenas de KB
 * (mesma regra dos demais sistemas: 512 px, qualidade 0,85).
 */
export function reduzirImagem(arquivo: File, lado = 512, qualidade = 0.85): Promise<string> {
    return new Promise((resolve, reject) => {
        const leitor = new FileReader();
        leitor.onerror = () => reject(new Error('Falha ao ler a imagem selecionada.'));
        leitor.onload = () => {
            const img = new Image();
            img.onerror = () => reject(new Error('O arquivo selecionado não pôde ser aberto como imagem.'));
            img.onload = () => {
                const escala = Math.min(1, lado / Math.max(img.width, img.height));
                const w = Math.max(1, Math.round(img.width * escala));
                const h = Math.max(1, Math.round(img.height * escala));
                const canvas = document.createElement('canvas');
                canvas.width = w;
                canvas.height = h;
                const ctx = canvas.getContext('2d');
                if (!ctx) {
                    resolve(leitor.result as string);
                    return;
                }
                // Fundo branco: PNG com transparência não fica preto no JPEG
                ctx.fillStyle = '#ffffff';
                ctx.fillRect(0, 0, w, h);
                ctx.drawImage(img, 0, 0, w, h);
                resolve(canvas.toDataURL('image/jpeg', qualidade));
            };
            img.src = leitor.result as string;
        };
        leitor.readAsDataURL(arquivo);
    });
}

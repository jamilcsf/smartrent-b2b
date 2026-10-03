package br.com.unisenai.smartrent.service;

/**
 * Termo de uso do anunciante. A versao e gravada em cada aceite; ao mudar o
 * texto, incremente a versao para que a auditoria saiba o que foi aceito.
 *
 * <p>O texto abaixo e um modelo de trabalho e precisa de revisao juridica
 * antes de ir a producao.
 */
public final class TermoUso {

    public static final String VERSAO = "1.0";

    public static final String TITULO = "Termo de uso e responsabilidade do anunciante";

    // TODO(juridico) - PENDENTE DE REVISAO: o item 5 do texto abaixo ainda cita o WhatsApp, canal que foi
    // REMOVIDO da plataforma (o contato agora e exclusivamente pelo SmartChat). O texto juridico NAO foi
    // alterado aqui por decisao de produto: o setor responsavel deve reescrever o item 5 e definir se a
    // versao do termo muda (VERSAO) e se os anunciantes precisam aceitar de novo.
    public static final String TEXTO = """
            1. Veracidade das informações. O anunciante declara que todas as informações, fotos e vídeos \
            do anúncio são verdadeiros, atuais e retratam fielmente o imóvel, e que possui direito de \
            anunciá-lo.

            2. Direitos sobre o conteúdo. O anunciante declara ser titular dos direitos das imagens e \
            vídeos enviados ou ter autorização para usá-los, e autoriza a plataforma a exibi-los no \
            catálogo.

            3. Preço e reservas. O preço em vigor no momento da criação de uma reserva é o preço dessa \
            reserva: alterações posteriores de preço ou de anúncio não afetam reservas já criadas, que \
            devem ser honradas nas condições originais.

            4. Alterações de anúncio. Ao editar um anúncio publicado, o anunciante está ciente de que ele \
            ficará fora do ar durante a edição e por mais um período após a confirmação das alterações, \
            sem receber novas reservas. Reservas existentes não são canceladas.

            5. Contato. O link de WhatsApp informado só é exibido a usuários autenticados, e o anunciante \
            concorda com o contato por esse canal para tratar de reservas.

            6. Registro. O aceite deste termo é registrado com data, hora, versão e endereço IP.
            """;

    private TermoUso() {
    }
}

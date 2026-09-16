# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

A = os.path.join(BASE, "assets")
styles = get_styles()
reset_counters()
story = []

TITULO = "SmartRent B2B"
SUBTITULO = ("Plataforma de precificação preditiva de diárias para pequenos anfitriões "
             "e administradores de imóveis da Grande Florianópolis")

story += capa(styles, TITULO, SUBTITULO)
story += folha_de_rosto(styles, TITULO, SUBTITULO,
    "Relatório técnico apresentado à disciplina de Projeto Aplicado IV, do curso de "
    "Tecnologia em Análise e Desenvolvimento de Sistemas do UniSENAI, como parte da "
    "avaliação da unidade curricular.")
story += resumo_page(styles,
    "Pequenos anfitriões e administradores de imóveis de temporada da Grande Florianópolis "
    "ainda controlam reservas por planilha ou aplicativo de mensagens e definem o preço da "
    "diária por intuição, sem levar em conta a sazonalidade ou a localização específica do "
    "imóvel. Esse cenário favorece a ocorrência de reservas sobrepostas para o mesmo período "
    "e mantém os imóveis ociosos fora da alta temporada. Este trabalho descreve o "
    "desenvolvimento do SmartRent B2B, uma plataforma para gestão de imóveis e reservas que "
    "inclui uma verificação automática de conflito de datas e um mecanismo de sugestão de "
    "preço baseado em inteligência artificial generativa, com um cálculo alternativo "
    "determinístico para os casos em que o serviço de IA não responde. O sistema foi "
    "construído em Java 17 e Spring Boot, com persistência em PostgreSQL, e alinha-se ao "
    "Objetivo de Desenvolvimento Sustentável 8 da Organização das Nações Unidas ao oferecer "
    "a um microempreendedor uma ferramenta de gestão antes restrita a operações de maior "
    "porte. São apresentados o levantamento de requisitos, a modelagem do domínio, os "
    "diagramas UML da solução e o estado atual da implementação.",
    "SmartRent B2B; precificação dinâmica; gestão de reservas; sistemas distribuídos; "
    "Objetivo de Desenvolvimento Sustentável 8")
story += sumario_page(styles)

# ---------------------------------------------------------------------
# 1 INTRODUÇÃO
# ---------------------------------------------------------------------
story.append(Paragraph("1 INTRODUÇÃO", styles["H1"]))

story.append(Paragraph("1.1 Contextualização e problema", styles["H2"]))
story.append(Paragraph(
    "A maior parte dos pequenos anfitriões e administradores de imóveis de temporada da "
    "Grande Florianópolis ainda depende de processos manuais para operar seus negócios: "
    "reservas anotadas em planilha ou combinadas por aplicativo de mensagens, e preço de "
    "diária definido por intuição ou por comparação informal com anúncios semelhantes. Não "
    "há, nesse processo, nenhuma ferramenta que relacione sazonalidade, localização "
    "específica do imóvel e perfil da propriedade para indicar um preço competitivo.",
    styles["Body"]))
story.append(Paragraph(
    "Esse modo de operação traz três consequências recorrentes. A primeira é o "
    "<i>overbooking</i>: sem uma verificação automática, é possível aceitar duas reservas "
    "para o mesmo imóvel em datas que se sobrepõem. A segunda é a ociosidade dos imóveis fora "
    "da alta temporada, decorrente da ausência de uma estratégia de preço que reaja à demanda "
    "ao longo do ano. A terceira é a exclusão do pequeno empreendedor das ferramentas de "
    "revenue management hoje disponíveis no mercado, que costumam ser corporativas, caras e "
    "voltadas a redes hoteleiras de grande porte, não a quem administra um punhado de imóveis.",
    styles["Body"]))

story.append(Paragraph("1.2 Solução proposta", styles["H2"]))
story.append(Paragraph(
    "O SmartRent B2B é uma plataforma para gestão de imóveis e reservas de temporada, "
    "desenvolvida com escopo delimitado por uma matriz MoSCoW para caber em um ciclo de 30 "
    "dias. O sistema mantém um cadastro de imóveis e reservas com verificação automática de "
    "conflito de datas, calcula uma sugestão de preço de diária combinando sazonalidade, "
    "microgeografia (latitude e longitude do imóvel) e perfil da propriedade por meio de "
    "inteligência artificial generativa, e recorre a uma regra de negócio determinística "
    "sempre que essa IA está indisponível, registrando qual dos dois caminhos gerou cada "
    "sugestão. Um <i>dashboard</i> reúne indicadores de ocupação e de evolução de preço para "
    "o anfitrião. A autenticação de usuários é reaproveitada do Projeto Aplicado III, evitando "
    "retrabalho em um domínio já resolvido pela equipe em semestre anterior.", styles["Body"]))

story.append(Paragraph("1.3 Alinhamento com o Objetivo de Desenvolvimento Sustentável 8", styles["H2"]))
story.append(Paragraph(
    "O projeto se relaciona com o Objetivo de Desenvolvimento Sustentável 8 da Agenda 2030, "
    "voltado ao trabalho decente e ao crescimento econômico (ORGANIZAÇÃO DAS NAÇÕES UNIDAS, "
    "2015), em três de suas metas.", styles["Body"]))
story.extend(quadro(styles, "Alinhamento do SmartRent B2B com as metas do ODS 8", quadro_table(
    [[Paragraph("Meta", styles["CellHeader"]), Paragraph("Contribuição do SmartRent B2B", styles["CellHeader"])],
     [Paragraph("8.2 — diversificação, modernização tecnológica e inovação produtiva", styles["Cell"]),
      Paragraph("Leva a um microempreendedor de temporada um instrumento de precificação orientado a "
                "dados, hoje restrito a redes hoteleiras de maior porte.", styles["Cell"])],
     [Paragraph("8.3 — apoio às micro e pequenas empresas e ao empreendedorismo", styles["Cell"]),
      Paragraph("Substitui o controle informal por planilha e mensagens por um registro único de "
                "reservas e indicadores, aproximando a operação do anfitrião de uma gestão "
                "profissional.", styles["Cell"])],
     [Paragraph("8.9 — turismo sustentável, geração de emprego e valorização da cultura local",
                styles["Cell"]),
      Paragraph("A sugestão de preço sensível à sazonalidade favorece uma distribuição mais "
                "equilibrada da ocupação ao longo do ano, reduzindo a ociosidade típica da baixa "
                "temporada no litoral catarinense.", styles["Cell"])]],
    [42 * mm, 108 * mm])))

story.append(PageBreak())

# ---------------------------------------------------------------------
# 2 PERSONAS
# ---------------------------------------------------------------------
story.append(Paragraph("2 PERSONAS", styles["H1"]))
story.append(Paragraph(
    "As personas a seguir sintetizam os dois perfis de usuário considerados no levantamento "
    "de requisitos do MVP.", styles["Body"]))

story.append(Paragraph("2.1 Marina Andrade, pequena anfitriã", styles["H2"]))
story.append(Paragraph(
    "Marina tem 34 anos, é professora de yoga e administra três imóveis de temporada "
    "próprios, em Canasvieiras e em Jurerê. Ela controla as reservas por uma planilha "
    "compartilhada e por conversas de WhatsApp com os hóspedes, e já enfrentou um caso de "
    "<i>overbooking</i>: aceitou duas reservas para o mesmo apartamento no mesmo fim de "
    "semana. O preço da diária é definido observando anúncios parecidos em outras "
    "plataformas, sem um critério mais sistemático. Marina não tem tempo nem conhecimento "
    "técnico para operar uma ferramenta de revenue management corporativa; do SmartRent B2B "
    "ela espera sobretudo a garantia de que duas reservas nunca vão colidir e uma sugestão de "
    "preço simples, que possa aceitar ou ajustar sem precisar entender a lógica estatística "
    "por trás do número.", styles["Body"]))

story.append(Paragraph("2.2 Carlos Bittencourt, administrador de imóveis", styles["H2"]))
story.append(Paragraph(
    "Carlos tem 45 anos, é ex-corretor de imóveis e administra uma carteira de catorze "
    "imóveis de terceiros na Grande Florianópolis, repassando parte da receita aos "
    "proprietários. Hoje mantém três planilhas separadas, para reservas, repasses e "
    "manutenção, que com frequência saem de sincronia entre si. Sua precificação é reativa: "
    "só reduz o valor da diária quando percebe queda na procura, geralmente tarde demais para "
    "recuperar a ocupação do período. Carlos já avaliou contratar um PMS (<i>property "
    "management system</i>) internacional, mas considerou as opções do mercado caras e "
    "complexas para o porte da sua operação. Do sistema, ele espera um painel único com visão "
    "consolidada de todos os imóveis e uma justificativa auditável para cada preço sugerido, "
    "que possa apresentar aos proprietários.", styles["Body"]))

story.append(Paragraph(
    "Uma persona secundária, beneficiada de forma indireta, é o próprio hóspede: ele se "
    "beneficia de um preço mais coerente com a demanda e de menor risco de encontrar sua "
    "reserva cancelada por conflito de agenda, ainda que não interaja diretamente com o "
    "sistema no escopo deste MVP.", styles["Body"]))
story.append(PageBreak())

# ---------------------------------------------------------------------
# 3 REQUISITOS
# ---------------------------------------------------------------------
story.append(Paragraph("3 REQUISITOS", styles["H1"]))
story.append(Paragraph(
    "Os requisitos foram organizados segundo a matriz MoSCoW (Must, Should, Could, Won't) "
    "definida para o MVP de 30 dias; itens classificados como Won't foram registrados como "
    "Visão Futura e não fazem parte do escopo atual.", styles["Body"]))

story.append(Paragraph("3.1 Requisitos funcionais", styles["H2"]))
rf_list = [
    ("RF01", "O sistema deve permitir que o anfitrião se autentique com as credenciais e a "
             "infraestrutura de autenticação herdadas do Projeto Aplicado III.", "Must"),
    ("RF02", "O sistema deve permitir cadastrar um imóvel com título, tipo, endereço completo "
             "(incluindo latitude e longitude), capacidade, número de quartos e banheiros, "
             "valor de diária base e comodidades.", "Must"),
    ("RF03", "O sistema deve permitir editar os dados de um imóvel e inativá-lo.", "Must"),
    ("RF04", "O sistema deve permitir listar os imóveis de um anfitrião, com filtro por "
             "situação ativa ou inativa.", "Must"),
    ("RF05", "O sistema deve permitir cadastrar uma reserva vinculada a um imóvel, com os "
             "dados do hóspede e o período de entrada e saída.", "Must"),
    ("RF06", "O sistema deve impedir o cadastro ou a edição de uma reserva cujo período se "
             "sobreponha a outra reserva ativa do mesmo imóvel.", "Must"),
    ("RF07", "O sistema deve permitir cancelar ou concluir uma reserva, atualizando sua "
             "situação.", "Must"),
    ("RF08", "O sistema deve calcular uma sugestão de preço de diária para um imóvel em uma "
             "data de referência, considerando sazonalidade e microgeografia, por meio de "
             "inteligência artificial generativa.", "Must"),
    ("RF09", "O sistema deve aplicar uma regra de negócio determinística quando a IA "
             "generativa estiver indisponível ou o tempo de resposta for excedido, registrando "
             "a origem de cada cálculo.", "Must"),
    ("RF10", "O sistema deve exibir um painel com indicadores de ocupação e evolução dos "
             "preços sugeridos por imóvel.", "Should"),
    ("RF11", "O sistema deve permitir consultar o histórico de sugestões de preço de um imóvel "
             "em um intervalo de datas.", "Should"),
    ("RF12", "Ao recusar uma reserva por conflito de datas, o sistema deve indicar quais "
             "reservas existentes causaram o conflito.", "Should"),
    ("RF13", "Sincronização bidirecional de reservas com plataformas externas via iCal.",
     "Won't (Visão Futura)"),
    ("RF14", "Processamento de pagamentos reais por meio de um gateway.", "Won't (Visão Futura)"),
]
rf_data = [[Paragraph("ID", styles["CellHeader"]), Paragraph("Descrição", styles["CellHeader"]),
            Paragraph("MoSCoW", styles["CellHeaderCenter"])]]
for rid, desc, mo in rf_list:
    rf_data.append([Paragraph(rid, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                     Paragraph(mo, styles["CellCenter"])])
story.extend(quadro(styles, "Requisitos funcionais do MVP", quadro_table(
    rf_data, [14 * mm, 108 * mm, 30 * mm], header_align_center=[2])))

story.append(Paragraph("3.2 Requisitos não funcionais", styles["H2"]))
rnf_list = [
    ("RNF01", "O backend deve ser implementado em Java 17 com Spring Boot 3, usando Spring "
              "Web, Spring Data JPA e o cliente HTTP nativo (RestClient).", "Tecnologia"),
    ("RNF02", "Os dados devem ser persistidos em PostgreSQL, gerenciado por meio do Supabase.", "Tecnologia"),
    ("RNF03", "Toda credencial ou chave de API deve ser fornecida ao sistema por variável de "
              "ambiente, nunca escrita diretamente no código-fonte.", "Segurança"),
    ("RNF04", "As chamadas à IA generativa e a quaisquer serviços externos devem ser simuladas "
              "(mock) nos testes automatizados, de modo que o build não dependa de rede.",
     "Testabilidade"),
    ("RNF05", "O esquema do banco de dados deve ser versionado por migration; em produção, o "
              "Hibernate deve apenas validar o esquema existente, nunca alterá-lo "
              "automaticamente.", "Manutenibilidade"),
    ("RNF06", "A interface deve ser responsiva, construída com HTML5 semântico, JavaScript "
              "assíncrono e Tailwind CSS, com prioridade ao layout para dispositivos móveis.",
     "Usabilidade"),
    ("RNF07", "As operações de cadastro, consulta, alteração e remoção devem responder em "
              "tempo compatível com uso interativo, excluída a latência da chamada externa à "
              "IA.", "Desempenho"),
    ("RNF08", "O código deve seguir os princípios SOLID e a separação em camadas de "
              "controlador, serviço e repositório, com objetos de transferência de dados "
              "implementados como Records.", "Manutenibilidade"),
    ("RNF09", "A esteira de integração contínua deve executar a compilação e os testes a cada "
              "envio de código, impedindo a integração de alterações que quebrem o build.",
     "Confiabilidade"),
]
rnf_data = [[Paragraph("ID", styles["CellHeader"]), Paragraph("Descrição", styles["CellHeader"]),
             Paragraph("Categoria", styles["CellHeaderCenter"])]]
for rid, desc, cat in rnf_list:
    rnf_data.append([Paragraph(rid, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                      Paragraph(cat, styles["CellCenter"])])
story.extend(quadro(styles, "Requisitos não funcionais do MVP", quadro_table(
    rnf_data, [14 * mm, 106 * mm, 32 * mm], header_align_center=[2])))
story.append(PageBreak())

# ---------------------------------------------------------------------
# 4 CASOS DE USO
# ---------------------------------------------------------------------
story.append(Paragraph("4 CASOS DE USO", styles["H1"]))
story.append(figura(styles, f"{A}/diagrama_casos_de_uso.png",
                     "diagrama de casos de uso do SmartRent B2B", 158, 1530 / 2210))

story.append(Paragraph("4.1 Gerenciar reservas e verificar conflito de datas", styles["H2"]))
uc1 = [
    ("Ator principal", "Anfitrião ou administrador de imóveis"),
    ("Pré-condição", "Anfitrião autenticado; imóvel já cadastrado e ativo"),
    ("Fluxo principal", "O anfitrião informa os dados da reserva (imóvel, hóspede, datas de "
                        "entrada e saída, valor). O sistema verifica se há sobreposição de "
                        "datas para o mesmo imóvel, desconsiderando reservas já canceladas. "
                        "Não havendo conflito, a reserva é registrada com situação pendente e "
                        "o sistema confirma a operação."),
    ("Fluxo alternativo", "Havendo conflito, o sistema recusa a operação e retorna as reservas "
                          "que causaram a sobreposição, sem registrar nada."),
    ("Pós-condição", "A reserva passa a constar na listagem do imóvel e nenhuma sobreposição "
                     "de datas ativa existe para ele."),
]
story.extend(quadro(styles, "Caso de uso — gerenciar reservas e verificar conflito de datas",
    quadro_table([[Paragraph(k, styles["CellHeader"]), Paragraph(v, styles["Cell"])] for k, v in uc1],
                 [32 * mm, 118 * mm], zebra=False)))

story.append(Paragraph("4.2 Gerar sugestão de preço com mecanismo de contingência", styles["H2"]))
uc2 = [
    ("Ator principal", "Anfitrião ou administrador de imóveis"),
    ("Ator secundário", "Serviço de IA generativa (Groq / Llama 3.3)"),
    ("Pré-condição", "Imóvel cadastrado com endereço geocodificado"),
    ("Fluxo principal", "O anfitrião solicita a sugestão de preço para um imóvel e uma data de "
                        "referência. O sistema monta o contexto de sazonalidade e microgeografia "
                        "e consulta a IA generativa, que devolve o valor sugerido, os fatores "
                        "de ajuste considerados e uma justificativa textual. A sugestão é "
                        "registrada com a origem marcada como IA generativa."),
    ("Fluxo alternativo", "Se a chamada à IA falhar ou exceder o tempo limite, o sistema aplica "
                          "uma regra determinística de contingência e registra a sugestão com a "
                          "origem marcada como cálculo de contingência, de modo que o anfitrião "
                          "nunca fique sem um valor sugerido."),
    ("Pós-condição", "A sugestão fica disponível para consulta e exibição no painel, com a "
                     "origem do cálculo identificada."),
]
story.extend(quadro(styles, "Caso de uso — gerar sugestão de preço com mecanismo de contingência",
    quadro_table([[Paragraph(k, styles["CellHeader"]), Paragraph(v, styles["Cell"])] for k, v in uc2],
                 [32 * mm, 118 * mm], zebra=False)))

story.append(Paragraph("4.3 Demais casos de uso", styles["H2"]))
resumo_list = [
    ("Autenticar-se", "Login com e-mail e senha, reaproveitando a infraestrutura de segurança "
                       "do Projeto Aplicado III."),
    ("Gerenciar imóveis", "Cadastro, edição, listagem e inativação de imóveis."),
    ("Consultar painel", "Visualização de indicadores de ocupação e evolução de preço "
                          "sugerido."),
    ("Cancelar ou concluir reserva", "Atualização da situação de uma reserva; reservas "
                                      "canceladas liberam o período para uma nova reserva."),
]
rdata2 = [[Paragraph("Caso de uso", styles["CellHeader"]), Paragraph("Resumo", styles["CellHeader"])]]
for uc, res in resumo_list:
    rdata2.append([Paragraph(uc, styles["CellBold"]), Paragraph(res, styles["Cell"])])
story.extend(quadro(styles, "Demais casos de uso do MVP", quadro_table(rdata2, [50 * mm, 100 * mm])))
story.append(PageBreak())

# ---------------------------------------------------------------------
# 5 DIAGRAMAS UML
# ---------------------------------------------------------------------
story.append(Paragraph("5 DIAGRAMAS UML", styles["H1"]))

story.append(Paragraph("5.1 Diagrama de classes", styles["H2"]))
story.append(Paragraph(
    "O diagrama a seguir representa as entidades do domínio e seus relacionamentos: um "
    "usuário possui vários imóveis; cada imóvel possui várias reservas e várias sugestões de "
    "preço ao longo do tempo. O endereço é modelado como um objeto de valor incorporado ao "
    "imóvel, pois é nele que residem a latitude e a longitude usadas pelo cálculo de "
    "microgeografia.", styles["Body"]))
story.append(figura(styles, f"{A}/diagrama_classes.png", "diagrama de classes do domínio", 158, 1462 / 2210))

story.append(Paragraph("5.2 Diagrama de sequência da criação de reserva", styles["H2"]))
story.append(figura(styles, f"{A}/diagrama_sequencia_reserva.png",
                     "criação de reserva com verificação de conflito de datas", 158, 1071 / 2192))

story.append(Paragraph("5.3 Diagrama de sequência da sugestão de preço", styles["H2"]))
story.append(figura(styles, f"{A}/diagrama_sequencia_precificacao.png",
                     "geração da sugestão de preço com desvio para o cálculo de contingência",
                     158, 1002 / 2192))
story.append(PageBreak())

# ---------------------------------------------------------------------
# 6 MODELO DE DADOS
# ---------------------------------------------------------------------
story.append(Paragraph("6 MODELO DE DADOS", styles["H1"]))

story.append(Paragraph("6.1 Modelo lógico", styles["H2"]))
story.append(Paragraph(
    "O modelo lógico corresponde ao diagrama de classes apresentado na Seção 5.1: quatro "
    "entidades principais (usuário, imóvel, reserva e sugestão de preço) e um objeto de valor "
    "incorporado (endereço). Todos os relacionamentos entre as entidades principais são de um "
    "para muitos.", styles["Body"]))

story.append(Paragraph("6.2 Modelo físico", styles["H2"]))
story.append(Paragraph(
    "O esquema físico, em PostgreSQL, está especificado nos quadros a seguir. A estratégia "
    "definida para o projeto é gerá-lo a partir do mapeamento objeto-relacional em modo "
    "somente-geração-de-script do Hibernate (FIELDING; BAUER; KING, [2024] apud "
    "documentação do projeto), versioná-lo como migration aplicada pelo Flyway na "
    "inicialização da aplicação e reservar ao Hibernate apenas a validação do esquema "
    "existente, nunca sua alteração. A especificação a seguir é o contrato que a primeira "
    "migration deve cumprir.", styles["Body"]))

tables_ddl = [
    ("usuarios", [
        ("id", "bigint identity", "chave primária"), ("nome", "varchar(120)", "obrigatório"),
        ("email", "varchar(150)", "obrigatório, único"), ("senha_hash", "varchar(60)", "obrigatório"),
        ("papel", "varchar(20)", "obrigatório"), ("telefone", "varchar(20)", "opcional"),
        ("ativo", "boolean", "obrigatório"), ("data_criacao", "timestamp", "obrigatório"),
    ]),
    ("imoveis", [
        ("id", "bigint identity", "chave primária"), ("usuario_id", "bigint", "obrigatório, referencia usuarios"),
        ("titulo", "varchar(150)", "obrigatório"), ("descricao", "text", "opcional"),
        ("tipo_imovel", "varchar(20)", "obrigatório"),
        ("endereço (logradouro, bairro, cidade, estado, cep)", "varchar", "obrigatório"),
        ("latitude, longitude", "numeric(10,7)", "obrigatório — microgeografia"),
        ("capacidade_hospedes, numero_quartos, numero_banheiros", "integer", "obrigatório"),
        ("valor_diaria_base", "numeric(10,2)", "obrigatório"),
        ("ativo", "boolean", "obrigatório"), ("data_cadastro", "timestamp", "obrigatório"),
    ]),
    ("reservas", [
        ("id", "bigint identity", "chave primária"), ("imovel_id", "bigint", "obrigatório, referencia imoveis"),
        ("hospede_nome, hospede_email", "varchar", "obrigatório"),
        ("hospede_telefone", "varchar(20)", "opcional"),
        ("data_checkin, data_checkout", "date", "obrigatório"),
        ("valor_total", "numeric(10,2)", "obrigatório"),
        ("status, origem", "varchar(20)", "obrigatório"),
        ("observacoes", "text", "opcional"), ("data_criacao", "timestamp", "obrigatório"),
        ("versao", "bigint", "controle de concorrência otimista"),
    ]),
    ("sugestoes_preco", [
        ("id", "bigint identity", "chave primária"), ("imovel_id", "bigint", "obrigatório, referencia imoveis"),
        ("data_referencia", "date", "obrigatório"),
        ("valor_base, valor_sugerido", "numeric(10,2)", "obrigatório"),
        ("percentual_ajuste, fator_sazonalidade, fator_microgeografia", "numeric(5,2)", "opcional"),
        ("justificativa_ia", "text", "opcional"), ("modelo_ia_utilizado", "varchar(80)", "opcional"),
        ("origem_calculo", "varchar(30)", "obrigatório — IA generativa ou contingência"),
        ("data_geracao", "timestamp", "obrigatório"),
    ]),
]
for tname, cols in tables_ddl:
    tdata = [[Paragraph("Coluna", styles["CellHeader"]), Paragraph("Tipo", styles["CellHeader"]),
              Paragraph("Observação", styles["CellHeader"])]]
    for c, t, r in cols:
        tdata.append([Paragraph(c, styles["Cell"]), Paragraph(t, styles["Cell"]), Paragraph(r, styles["Cell"])])
    story.extend(quadro(styles, f"Estrutura da tabela {tname}",
                         quadro_table(tdata, [72 * mm, 30 * mm, 50 * mm], font_size=8.6)))

story.append(Paragraph(
    "Além das colunas listadas, o esquema inclui a tabela de coleção imovel_comodidades, "
    "que armazena as comodidades de cada imóvel, e cinco índices: idx_imoveis_usuario, "
    "idx_imoveis_ativo, idx_reservas_imovel, idx_reservas_periodo (usado pela verificação "
    "de conflito de datas) e idx_sugestoes_imovel_data. A unicidade do endereço de e-mail "
    "do usuário é garantida por restrição de unicidade na própria coluna, não por índice "
    "nomeado.", styles["Body"]))
story.append(PageBreak())

# ---------------------------------------------------------------------
# 7 TELAS
# ---------------------------------------------------------------------
story.append(Paragraph("7 TELAS", styles["H1"]))
story.append(Paragraph(
    "Os protótipos de tela do SmartRent B2B já foram elaborados, mas ainda não haviam sido "
    "disponibilizados para incorporação a este relatório até o fechamento desta versão. O "
    "quadro a seguir relaciona as telas previstas para o MVP; será substituído pelas capturas "
    "reais assim que estas forem fornecidas.", styles["Body"]))
telas_list = [
    ("Painel executivo", "Visão consolidada de ocupação, receita prevista e evolução de preços "
                          "sugeridos (RF10)."),
    ("Gestão de imóveis", "Listagem, cadastro e edição de imóveis (RF02 a RF04)."),
    ("Gestão de reservas", "Listagem, cadastro e cancelamento de reservas, com destaque para "
                            "conflitos de data (RF05 a RF07, RF12)."),
    ("Precificação inteligente", "Solicitação e exibição da sugestão de preço, incluindo a "
                                  "justificativa da IA (RF08, RF09)."),
]
tel_data = [[Paragraph("Tela", styles["CellHeader"]), Paragraph("Propósito", styles["CellHeader"])]]
for t, p in telas_list:
    tel_data.append([Paragraph(t, styles["CellBold"]), Paragraph(p, styles["Cell"])])
story.extend(quadro(styles, "Telas previstas para o MVP (pendente de anexo dos protótipos)",
                     quadro_table(tel_data, [42 * mm, 108 * mm])))

# ---------------------------------------------------------------------
# REFERÊNCIAS
# ---------------------------------------------------------------------
story.append(PageBreak())
refs = [
    "ASSOCIAÇÃO BRASILEIRA DE NORMAS TÉCNICAS. <b>NBR 14724</b>: informação e documentação – "
    "trabalhos acadêmicos – apresentação. Rio de Janeiro: ABNT, 2011.",
    "ASSOCIAÇÃO BRASILEIRA DE NORMAS TÉCNICAS. <b>NBR 6024</b>: informação e documentação – "
    "numeração progressiva das seções de um documento escrito – apresentação. Rio de Janeiro: "
    "ABNT, 2012.",
    "ASSOCIAÇÃO BRASILEIRA DE NORMAS TÉCNICAS. <b>NBR 10520</b>: informação e documentação – "
    "citações em documentos – apresentação. Rio de Janeiro: ABNT, 2023.",
    "ASSOCIAÇÃO BRASILEIRA DE NORMAS TÉCNICAS. <b>NBR 6023</b>: informação e documentação – "
    "referências – elaboração. Rio de Janeiro: ABNT, 2018.",
    "ORGANIZAÇÃO DAS NAÇÕES UNIDAS. <b>Objetivo de Desenvolvimento Sustentável 8</b>: trabalho "
    "decente e crescimento econômico. Nova York: ONU, 2015. Disponível em: "
    "https://brasil.un.org/pt-br/sdgs/8. Acesso em: 10 set. 2026.",
    "SPRING. <b>Spring Boot Reference Documentation</b>. [S. l.]: Broadcom, [2024]. Disponível "
    "em: https://docs.spring.io/spring-boot/. Acesso em: 10 set. 2026.",
    "HIBERNATE. <b>Hibernate ORM User Guide</b>. [S. l.]: Red Hat, [2024]. Disponível em: "
    "https://hibernate.org/orm/documentation/. Acesso em: 10 set. 2026.",
    "GROQ. <b>Groq API Documentation</b>. [S. l.]: Groq, [2024]. Disponível em: "
    "https://console.groq.com/docs. Acesso em: 10 set. 2026.",
]
story += referencias_page(styles, refs)

doc = new_doc(os.path.join(BASE, "out", "01_RELATORIO_TECNICO.pdf"), "Relatório Técnico do Projeto",
              use_toc_template=True)
hf = make_header_footer_condicional()
doc.multiBuild(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 01_RELATORIO_TECNICO.pdf")

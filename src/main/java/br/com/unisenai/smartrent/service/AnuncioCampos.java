package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Ponte entre os campos do formulario ({@link AnuncioDados}) e a entidade.
 * E aqui que o texto livre e sanitizado, para que cadastro, edicao direta e
 * confirmacao de rascunho passem todos pelo mesmo caminho.
 */
public final class AnuncioCampos {

    private AnuncioCampos() {
    }

    /** Copia os campos do formulario para o imovel, sanitizando. Nao toca preco nem status. */
    public static void aplicar(Imovel imovel, AnuncioDados d) {
        imovel.setTitulo(Sanitizador.linha(d.titulo()));
        String descricao = Sanitizador.texto(d.descricao());
        imovel.setDescricao(descricao == null || descricao.isBlank() ? null : descricao);
        imovel.setTipoImovel(d.tipoImovel());
        imovel.setMetragemQuadrada(d.metragemQuadrada());
        imovel.setNumeroQuartos(d.numeroQuartos());
        imovel.setNumeroBanheiros(d.numeroBanheiros());
        imovel.setVagasGaragem(d.vagasGaragem());
        imovel.setCapacidadeHospedes(d.capacidadeHospedes());

        List<String> comodidades = new ArrayList<>(new LinkedHashSet<>(
                (d.comodidades() == null ? List.<String>of() : d.comodidades()).stream()
                        .map(Sanitizador::linha)
                        .filter(c -> c != null && !c.isBlank())
                        .toList()));
        imovel.setComodidades(comodidades);

        Endereco e = imovel.getEndereco() == null ? new Endereco() : imovel.getEndereco();
        e.setCep(normalizarCep(d.cep()));
        e.setLogradouro(Sanitizador.linha(d.logradouro()));
        e.setNumero(Sanitizador.linha(d.numero()));
        String complemento = Sanitizador.linha(d.complemento());
        e.setComplemento(complemento == null || complemento.isBlank() ? null : complemento);
        e.setBairro(Sanitizador.linha(d.bairro()));
        e.setCidade(Sanitizador.linha(d.cidade()));
        e.setEstado(d.estado() == null ? null : d.estado().trim().toUpperCase());
        imovel.setEndereco(e);

        imovel.setMinimoDiarias(d.minimoDiarias() == null ? 1 : d.minimoDiarias());
        imovel.setTaxaLimpeza(d.taxaLimpeza() == null ? java.math.BigDecimal.ZERO
                : d.taxaLimpeza().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** Estado atual do imovel como campos de formulario (base do rascunho de edicao). */
    public static AnuncioDados extrair(Imovel i) {
        Endereco e = i.getEndereco() == null ? new Endereco() : i.getEndereco();
        return new AnuncioDados(
                i.getTitulo(),
                i.getDescricao(),
                i.getTipoImovel(),
                i.getMetragemQuadrada(),
                i.getNumeroQuartos(),
                i.getNumeroBanheiros(),
                i.getVagasGaragem(),
                i.getCapacidadeHospedes(),
                i.getComodidades() == null ? List.of() : List.copyOf(i.getComodidades()),
                e.getCep(),
                e.getLogradouro(),
                e.getNumero(),
                e.getComplemento(),
                e.getBairro(),
                e.getCidade(),
                e.getEstado(),
                i.getMinimoDiarias(),
                i.getTaxaLimpeza(),
                i.getValorDiariaBase());
    }

    /** Sanitiza sem tocar a entidade: o rascunho guarda o texto ja limpo. */
    public static AnuncioDados sanitizar(AnuncioDados d) {
        Imovel temporario = new Imovel();
        aplicar(temporario, d);
        AnuncioDados limpo = extrair(temporario);
        return new AnuncioDados(limpo.titulo(), limpo.descricao(), limpo.tipoImovel(), limpo.metragemQuadrada(),
                limpo.numeroQuartos(), limpo.numeroBanheiros(), limpo.vagasGaragem(), limpo.capacidadeHospedes(),
                limpo.comodidades(), limpo.cep(), limpo.logradouro(), limpo.numero(), limpo.complemento(),
                limpo.bairro(), limpo.cidade(), limpo.estado(), limpo.minimoDiarias(), limpo.taxaLimpeza(), d.valorDiaria());
    }

    private static String normalizarCep(String cep) {
        if (cep == null) {
            return null;
        }
        String digitos = cep.replaceAll("\\D", "");
        return digitos.length() == 8 ? digitos.substring(0, 5) + "-" + digitos.substring(5) : cep.trim();
    }
}

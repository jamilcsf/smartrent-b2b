package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Anfitriao ou administrador da plataforma.
 *
 * <p>Nao implementa {@code UserDetails}: o projeto ainda nao inclui Spring
 * Security. Quando a autenticacao entrar, este e o ponto de extensao.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** Hash BCrypt da senha; nunca a senha em texto puro. */
    @Column(name = "senha_hash", nullable = false, length = 60)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private PapelUsuario papel;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    /** Aba SmartChat do cliente: liga na primeira interacao e fica ligada (guardada no backend, nao no navegador). */
    @Column(name = "smartchat_liberado", nullable = false)
    private boolean smartchatLiberado;

    /** Nome do arquivo (aleatorio) da foto de perfil, ou nulo quando nao ha foto. */
    @Column(name = "foto_arquivo", length = 64)
    private String fotoArquivo;

    /** Muda a cada troca de foto: entra na URL e evita cache antigo. */
    @Column(name = "foto_versao", nullable = false)
    private int fotoVersao;

    /** Sobe a cada troca de senha/e-mail; o token carrega a versao e so vale se for a atual. */
    @Column(name = "sessao_versao", nullable = false)
    private int sessaoVersao;

    /** false = conta criada pelo Google, sem senha conhecida. */
    @Column(name = "senha_definida", nullable = false)
    private boolean senhaDefinida = true;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    public Usuario() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now(br.com.unisenai.smartrent.config.PlataformaTempo.zona());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public PapelUsuario getPapel() {
        return papel;
    }

    public void setPapel(PapelUsuario papel) {
        this.papel = papel;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public boolean isSmartchatLiberado() {
        return smartchatLiberado;
    }

    public void setSmartchatLiberado(boolean smartchatLiberado) {
        this.smartchatLiberado = smartchatLiberado;
    }

    public String getFotoArquivo() {
        return fotoArquivo;
    }

    public void setFotoArquivo(String fotoArquivo) {
        this.fotoArquivo = fotoArquivo;
    }

    public int getFotoVersao() {
        return fotoVersao;
    }

    public void setFotoVersao(int fotoVersao) {
        this.fotoVersao = fotoVersao;
    }

    public int getSessaoVersao() {
        return sessaoVersao;
    }

    public void setSessaoVersao(int sessaoVersao) {
        this.sessaoVersao = sessaoVersao;
    }

    public boolean isSenhaDefinida() {
        return senhaDefinida;
    }

    public void setSenhaDefinida(boolean senhaDefinida) {
        this.senhaDefinida = senhaDefinida;
    }
}

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

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    public Usuario() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now();
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
}

package com.smartrentb2b.repository;

import com.smartrentb2b.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Usado pelo serviço de autenticação (login por e-mail). */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
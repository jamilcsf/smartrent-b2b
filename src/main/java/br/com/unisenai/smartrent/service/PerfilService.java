package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import org.springframework.stereotype.Service;

import java.util.List;

/** Perfil do usuario autenticado: leitura e (nas proximas etapas) alteracoes da propria conta. */
@Service
public class PerfilService {

    public PerfilResponse obter(Usuario usuario) {
        return new PerfilResponse(usuario.getNome(), usuario.getEmail(), usuario.getPapel(),
                UsuarioResponse.fotoUrl(usuario), usuario.getDataCriacao(), usuario.isSenhaDefinida(),
                null, List.of());
    }
}

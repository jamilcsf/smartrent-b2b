package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.service.AuthService;
import br.com.unisenai.smartrent.service.CaptchaService;
import br.com.unisenai.smartrent.service.GoogleTokenVerifier;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduz as excecoes da aplicacao em respostas JSON com o status adequado. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of(
                "erro", "Dados inválidos.",
                "campos", campos));
    }

    @ExceptionHandler(AuthService.EmailJaCadastradoException.class)
    public ResponseEntity<Map<String, Object>> emailDuplicado(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(AuthService.CredenciaisInvalidasException.class)
    public ResponseEntity<Map<String, Object>> credenciais(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(CaptchaService.CaptchaInvalidoException.class)
    public ResponseEntity<Map<String, Object>> captcha(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(GoogleTokenVerifier.TokenGoogleInvalidoException.class)
    public ResponseEntity<Map<String, Object>> tokenGoogle(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(GoogleTokenVerifier.LoginGoogleIndisponivelException.class)
    public ResponseEntity<Map<String, Object>> googleIndisponivel(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Map<String, Object>> acessoNegado(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> transicaoInvalida(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(ValidacaoAnuncioException.class)
    public ResponseEntity<Map<String, Object>> anuncioInvalido(ValidacaoAnuncioException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "erro", e.getMessage(),
                "campos", e.getCampos()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> arquivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("erro", "O arquivo excede o tamanho maximo permitido."));
    }

    @ExceptionHandler(br.com.unisenai.smartrent.service.erro.PagamentoRecusadoException.class)
    public ResponseEntity<Map<String, Object>> pagamentoRecusado(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException.class)
    public ResponseEntity<Map<String, Object>> conflitoComDetalhes(
            br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException e) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", e.getMessage());
        corpo.put("codigo", e.codigo());
        corpo.put("detalhes", e.detalhes());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumento(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }
}

package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.service.AuthService;
import br.com.unisenai.smartrent.service.CaptchaService;
import br.com.unisenai.smartrent.service.GoogleTokenVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumento(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }
}

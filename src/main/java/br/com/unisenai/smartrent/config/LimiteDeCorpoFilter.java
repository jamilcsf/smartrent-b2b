package br.com.unisenai.smartrent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Teto para corpo JSON (padrao 1 MB): o Spring nao limita o corpo de uma requisicao JSON, e um cliente poderia
 * mandar centenas de MB a qualquer endpoint. Com Content-Length conhecido a recusa e imediata (413); sem ele (chunked)
 * a leitura e interrompida ao passar do teto. Upload multipart e as partes de video tem limites proprios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimiteDeCorpoFilter extends OncePerRequestFilter {

    private final long maximo;

    public LimiteDeCorpoFilter(@Value("${smartrent.http.json-max-bytes:1048576}") long maximo) {
        this.maximo = maximo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String tipo = request.getContentType();
        return tipo == null || !tipo.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maximo) {
            recusar(response);
            return;
        }
        chain.doFilter(new Limitada(request, maximo), response);
    }

    private static void recusar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"erro\":\"O corpo da requisição é grande demais.\"}");
    }

    private static final class Limitada extends HttpServletRequestWrapper {
        private final long maximo;

        Limitada(HttpServletRequest request, long maximo) {
            super(request);
            this.maximo = maximo;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long lidos;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0 && ++lidos > maximo) {
                        throw new IOException("Corpo da requisicao acima do limite.");
                    }
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0 && (lidos += n) > maximo) {
                        throw new IOException("Corpo da requisicao acima do limite.");
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }
    }
}

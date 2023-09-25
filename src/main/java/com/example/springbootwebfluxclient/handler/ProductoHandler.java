package com.example.springbootwebfluxclient.handler;

import com.example.springbootwebfluxclient.models.Producto;
import com.example.springbootwebfluxclient.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Component
public class ProductoHandler {

    @Autowired
    private ProductService productService;

    public Mono<ServerResponse> listar(ServerRequest request) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .body(productService.findAll(), Producto.class);
    }

    public Mono<ServerResponse> ver(ServerRequest request) {
        String id = request.pathVariable("id");
        return errorHandler(productService.findById(id)
                .flatMap(p -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(p)))
                .switchIfEmpty(ServerResponse.notFound().build()));
    }

    public Mono<ServerResponse> crear(ServerRequest request) {
        Mono<Producto> productoMono = request.bodyToMono(Producto.class);
        return productoMono
                .flatMap(p -> productService.save(p))
                .flatMap(p -> ServerResponse.created(URI.create("api/client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(p)))
                .onErrorResume(error -> {
                    WebClientResponseException webClientErrorException = (WebClientResponseException) error;
                    if (webClientErrorException.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(webClientErrorException.getResponseBodyAsString()));
                    }
                    return Mono.error(webClientErrorException);
                });
    }

    public Mono<ServerResponse> editar(ServerRequest request) {
        Mono<Producto> productoMono = request.bodyToMono(Producto.class);
        String id = request.pathVariable("id");
        return errorHandler(productoMono
                .flatMap(p -> productService.update(p, id))
                .flatMap(p -> ServerResponse.created(URI.create("api/client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(p))));
    }

    public Mono<ServerResponse> borrar(ServerRequest request) {
        String id = request.pathVariable("id");
        return errorHandler(productService.delete(id)
                .then(ServerResponse.noContent().build()));
    }

    public Mono<ServerResponse> upload(ServerRequest request) {
        String id = request.pathVariable("id");
        return errorHandler(
                request.multipartData().map(multipart -> multipart.get("file"))
                        .cast(FilePart.class)
                        .flatMap(file -> productService.upload(file, id))
                        .flatMap(p -> ServerResponse.created(URI.create("/api/client/upload"))
                                .body(BodyInserters.fromValue(p))));
    }

    private static Mono<ServerResponse> errorHandler(Mono<ServerResponse> response) {
        return response.onErrorResume(error -> {
            WebClientResponseException webClientErrorException = (WebClientResponseException) error;
            if (webClientErrorException.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ServerResponse.status(HttpStatus.NOT_FOUND)
                        .body(BodyInserters.fromValue(Map.of("error", error.getMessage())));
            }
            return Mono.error(webClientErrorException);
        });
    }
}

package example.video;

import io.github.alex59.videostreams.VideoFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// EN: Error categories are public; process diagnostics, local paths and storage details stay server-side.
// RU: Категории ошибок публичны; диагностика процесса, локальные пути и детали хранилища остаются на сервере.
@RestControllerAdvice
public class VideoErrors {
    public record PreparationError(String code, String message) { }

    @ExceptionHandler(VideoFailure.class)
    public ResponseEntity<PreparationError> preparationFailed(VideoFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case INVALID_SOURCE -> HttpStatus.BAD_REQUEST;
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case BUSY -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case CLOSED, CANCELLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROCESS_FAILED, STORAGE_FAILED -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new PreparationError(failure.code().name(),
                "Preparation did not return a ready asset. A lost response is not proof of rollback."));
    }
}

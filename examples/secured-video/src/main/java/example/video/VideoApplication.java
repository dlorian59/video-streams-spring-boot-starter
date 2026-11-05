package example.video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// EN: In an existing application, retain its main class and place the example service/controller beneath its scan package.
// RU: В существующем приложении сохраните его main-класс и поместите сервис/контроллер под пакетом сканирования.
@SpringBootApplication
public class VideoApplication {
    public static void main(String[] args) { SpringApplication.run(VideoApplication.class, args); }
}

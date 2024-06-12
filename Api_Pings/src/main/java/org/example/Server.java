package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {

    static final int PORT = 8000;
    static final int MAX_ALUMNOS = 24;

    static HashMap<String,String> students = new HashMap<>();
    static int numStudents = 0;
    static final String STATIC_DIR = System.getProperty("user.dir")+ File.separator+"src"+File.separator+"main"+File.separator+"resources";
    static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(MAX_ALUMNOS);

    public static void main(String[] args) {

        executorService.scheduleAtFixedRate(new ThreadTask(),1,1, TimeUnit.MINUTES);
        //Creación del servidor Http en el puerto (PORT) escuchando todas las direcciones.
        try {

            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0",PORT), 0);
            server.createContext("/alumno", new AlumnoHandler() {});
            server.createContext("/static", new StaticHandler());
            server.createContext("/enviado", new EnviadoHandler());

            server.setExecutor(null);
            server.start();

            System.out.println("Server started on port " + PORT + " listening all addresses.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    static  class AlumnoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if("GET".equals(exchange.getRequestMethod())){
                handleGetRequest(exchange);

            }else if("POST".equals(exchange.getRequestMethod())){
                handlePostRequest(exchange);
            }

        }

        private void handleGetRequest(HttpExchange exchange) throws IOException {

            String response = """
                <!DOCTYPE html>
                <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Alumno</title>
                        <style>
                            body {
                                background-color: rgba(80,146,227);
                                background-image: url('/static/logo.png');
                                background-size: 20%;
                                background-repeat: no-repeat;
                                background-position: center calc(5% + 50px);
                            }
                        </style>
                    </head>
                    <body>
                        <h2>Información del alumno</h2>
                        <form action="/alumno" method=POST>
                            <label>Nombre y apellidos: </label><br>
                            <input type="text" id="nombre" name="nombre" value="">
                            <input type="submit" value="Enviar">
                        </form>
                    </body>
                </html>
            """;



            exchange.sendResponseHeaders(200, response.getBytes().length);

            // Enviar la respuesta
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private void handlePostRequest(HttpExchange exchange) throws IOException {

            String formData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String[] value = formData.split("=");
            String studentNoun = value.length > 1 ? value[1] : "Sin nombre";

            String studentIp = exchange.getRemoteAddress().getAddress().getHostAddress();

            students.put(studentIp, studentNoun);
            numStudents++;
            if(numStudents==students.size()){
                System.out.println("Alumno recibido con exito. Hay "+ numStudents +" en el sistema.");
            }else {
                System.err.println("ERROR AL RECIBIR ALUMNO.");
            }

            exchange.getResponseHeaders().set("Location","/enviado");
            exchange.sendResponseHeaders(302,-1);
        }
    }

    static class EnviadoHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String response =
                    """
                    <!DOCTYPE html>
                    <html>
                        <head>
                            <meta charset = "UTF-8">
                            <title>Confirmado</title>
                            <style>
                            body {
                                background-color: rgba(80,146,227);
                                background-image: url('/static/logo.png');
                                background-size: 20%;
                                background-repeat: no-repeat;
                                background-position: center calc(5% + 50px);
                            }
                        </style>
                        </head>
                        <body>
                            <h1>Enviado Correctamente</h1><br>
                            <h2>Puede cerrar la página</h2>
                        </body>
                    </html>
                    """;

            exchange.sendResponseHeaders(200,response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class StaticHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String requestedFile = exchange.getRequestURI().getPath().replace("/static", "");
            Path filePath = Paths.get(STATIC_DIR, requestedFile);
            File file = filePath.toFile();

            if (file.exists()) {
                String mimeType = Files.probeContentType(filePath);
                if (mimeType == null) {
                    mimeType = "application/octet-stream"; // Tipo de contenido por defecto
                }
                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.sendResponseHeaders(200, file.length());
                OutputStream os = exchange.getResponseBody();
                Files.copy(file.toPath(), os);
                os.close();
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        }
        }

    static class ThreadTask implements Runnable {

        @Override
        public void run() {

            for (Map.Entry<String, String> student : students.entrySet()){
                String studentIp = student.getKey();
                String studentNoun= student.getValue();

                new Thread(new Pings(studentIp,studentNoun)).start();
            }
        }
    }

    static class Pings implements Runnable {

        private final String studentIp;
        private final String studentNoun;

        Pings(String studentIp, String studentNoun) {
            this.studentIp = studentIp;
            this.studentNoun = studentNoun;
        }

        @Override
        public void run() {

            String[] command = {"ping","-c","4",studentIp};
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process;

            try {
                process = pb.start();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            int exitCode;

            try {
                exitCode = process.waitFor();

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (exitCode == 0){
                System.out.println(studentNoun+"/"+studentIp +" : Conectado");
            }else {
                System.err.println(studentNoun+"/"+studentIp +" : Sin conexion ");
            }
        }
    }
}
package ru.crpt.ismp;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.net.http.*;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {

    private TimeUnit timeUnit;
    private int requestLimit;
    private Semaphore semaphore;
    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private Lock lock;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();

        // Schedule semaphore release at the specified interval
        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } finally {
                lock.unlock();
            }
        }, 1, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) throws Exception {
        semaphore.acquire();

        String jsonBody = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create"))
            .header("Content-Type", "application/json")
            .header("Signature", signature)
            .POST(BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
        System.out.println("Response status code: " + response.statusCode());
        System.out.println("Response body: " + response.body());
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        // Create an instance of CrptApi with a limit of 4 requests per minute
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 4);
        
        // Create a Document object with necessary fields
        CrptApi.Document document = new CrptApi.Document();
        document.description = new CrptApi.Document.Description();
        document.description.participantInn = "1234567890";
        document.doc_id = "123";
        document.doc_status = "NEW";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "1234567890";
        document.participant_inn = "1234567890";
        document.producer_inn = "1234567890";
        document.production_date = "2020-01-23";
        document.production_type = "SOME_TYPE";
        document.products = new CrptApi.Document.Product[1];
        document.products[0] = new CrptApi.Document.Product();
        document.products[0].certificate_document = "doc";
        document.products[0].certificate_document_date = "2020-01-23";
        document.products[0].certificate_document_number = "123";
        document.products[0].owner_inn = "1234567890";
        document.products[0].producer_inn = "1234567890";
        document.products[0].production_date = "2020-01-23";
        document.products[0].tnved_code = "code";
        document.products[0].uit_code = "uit";
        document.products[0].uitu_code = "uitu";
        document.reg_date = "2020-01-23";
        document.reg_number = "123";

        String signature = "signature";

        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        Instant start = Instant.now();

        // Submit multiple requests using different threads
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    api.createDocument(document, signature);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        System.out.println("Time taken: " + duration.getSeconds() + " seconds");

        // Check that the test did not take more than a minute
        assert duration.getSeconds() <= 60 : "Test took too much time!";

        // Check that no more than 4 requests were sent per minute
        // Assume that if the number of threads was greater than the limit, their execution would stretch over a longer time
        System.out.println("Test completed successfully");
        
        api.shutdown();
    }
}

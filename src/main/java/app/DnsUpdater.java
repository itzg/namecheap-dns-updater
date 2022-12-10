package app;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public class DnsUpdater {

    public static final String PROPERTIES_FILENAME = "namecheap-dns-updater.properties";
    public static final String PROP_DOMAIN_NAME = "domain-name";
    public static final String PROP_DDNS_PASSWORD = "ddns-password";
    private static final String PROP_HOST = "host";
    private final String host;
    private final String domainName;
    private final String password;
    private final HttpClient httpClient;
    private String previousIpAddress;

    public static void main(String[] args) throws IOException, FailedRequestException {
        final Properties properties = new Properties();
        try {
            try (BufferedReader reader = Files.newBufferedReader(
                Path.of(System.getProperty("user.home"), PROPERTIES_FILENAME))) {
                properties.load(reader);
            }
        } catch (NoSuchFileException e) {
            System.err.printf("Could not find %s in home directory%n", PROPERTIES_FILENAME);
            System.err.println("Make sure it includes "+String.join(", ", PROP_HOST, PROP_DOMAIN_NAME, PROP_DDNS_PASSWORD));

            System.exit(1);
        }

        final String host = getProperty(properties, PROP_HOST);
        final String domainName = getProperty(properties, PROP_DOMAIN_NAME);
        final String password = getProperty(properties, PROP_DDNS_PASSWORD);

        final DnsUpdater dnsUpdater = new DnsUpdater(host, domainName, password);
        dnsUpdater.update();
    }

    public DnsUpdater(String host, String domainName, String password) {

        this.host = host;
        this.domainName = domainName;
        this.password = password;

        httpClient = HttpClient.newBuilder()
            .build();
    }

    public void update() throws FailedRequestException {
        final HttpRequest getIpReq = HttpRequest.newBuilder()
            .uri(URI.create("https://api.ipify.org"))
            .GET()
            .build();

        try {
            final HttpResponse<String> getIpResp = httpClient.send(getIpReq, BodyHandlers.ofString());
            if (getIpResp.statusCode() == 200) {
                final String ourIpAddress = getIpResp.body();
                if (!Objects.equals(ourIpAddress, previousIpAddress)) {
                    sendAddressUpdate(ourIpAddress);
                    previousIpAddress = ourIpAddress;
                }
            }
            else {
                throw new FailedRequestException("Request to get IP address failed: " + getIpResp);
            }
        } catch (IOException | InterruptedException e) {
            throw new FailedRequestException("Trying to get our IP address", e);
        }
    }

    private void sendAddressUpdate(String ourIpAddress) throws FailedRequestException {
        final HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://dynamicdns.park-your-domain.com/update?host=%s&domain=%s&password=%s&ip=%s".formatted(
                host, domainName, password, ourIpAddress
            )))
            .GET()
            .build();

        final HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new FailedRequestException("Trying to update address", e);
        }
        if (resp.statusCode() == 200) {
            System.out.println("IP address updated to "+ourIpAddress);
        }
        else {
            throw new FailedRequestException("Trying to update address: " + resp);
        }
    }

    private static String getProperty(Properties properties, String key) {
        final String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing " + key + " property from "+PROPERTIES_FILENAME);
        }
        return value;
    }
}

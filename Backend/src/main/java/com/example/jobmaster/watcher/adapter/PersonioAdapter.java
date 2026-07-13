package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Personio — official public XML feed at https://{host}/xml
 * Config: {"host": "egym.jobs.personio.de"}
 */
@Component
public class PersonioAdapter implements JobPortalAdapter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PersonioAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.PERSONIO;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) throws Exception {
        // the feed already carries full descriptions; find this position's entry
        Document doc = fetchFeed(company);
        NodeList positions = doc.getElementsByTagName("position");
        for (int i = 0; i < positions.getLength(); i++) {
            Element position = (Element) positions.item(i);
            if (!externalId.equals(text(position, "id"))) {
                continue;
            }
            StringBuilder out = new StringBuilder();
            NodeList descriptions = position.getElementsByTagName("jobDescription");
            for (int d = 0; d < descriptions.getLength(); d++) {
                Element description = (Element) descriptions.item(d);
                String name = text(description, "name");
                String value = HtmlText.toPlainText(text(description, "value"));
                if (value != null) {
                    if (name != null && !name.isBlank()) {
                        out.append(name).append('\n');
                    }
                    out.append(value).append("\n\n");
                }
            }
            if (!out.isEmpty()) {
                return out.toString().trim();
            }
            break; // found the position but its feed entry has no descriptions
        }
        // Some companies leave <jobDescriptions> empty in the feed (EGYM does);
        // fall back to parsing the server-rendered job page.
        return fetchDescriptionFromPage(url);
    }

    private String fetchDescriptionFromPage(String url) throws Exception {
        if (url == null || url.isBlank()) {
            return null;
        }
        org.jsoup.nodes.Document page = org.jsoup.Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                .timeout(20_000)
                .get();
        // CSS-module hashes change per deploy; match on the stable name fragments
        StringBuilder out = new StringBuilder();
        for (org.jsoup.nodes.Element block : page.select(
                "[class*=jobDescriptionItem], .jb-description-item, .detail-content-block")) {
            String text = HtmlText.toPlainText(block.html());
            if (text != null) {
                out.append(text).append("\n\n");
            }
        }
        String text = out.toString().trim();
        return text.length() > 100 ? text : null;
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) throws Exception {
        Document doc = fetchFeed(company);
        String host = host(company);
        List<FetchedJob> jobs = new ArrayList<>();
        NodeList positions = doc.getElementsByTagName("position");
        for (int i = 0; i < positions.getLength(); i++) {
            Element position = (Element) positions.item(i);
            String id = text(position, "id");
            if (id == null || id.isBlank()) {
                continue;
            }
            jobs.add(new FetchedJob(
                    id,
                    text(position, "name"),
                    text(position, "office"),
                    text(position, "department"),
                    "https://" + host + "/job/" + id,
                    null));
        }
        return jobs;
    }

    private Document fetchFeed(WatchedCompany company) throws Exception {
        String xml = restClient.get()
                .uri("https://{host}/xml", host(company))
                .retrieve()
                .body(String.class);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // untrusted XML: no DTDs / external entities
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String host(WatchedCompany company) {
        String host = objectMapper.readTree(company.getConfigJson()).path("host").asString();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Personio config needs {\"host\": \"acme.jobs.personio.de\"}");
        }
        return host;
    }

    private String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null ? null : value.trim();
    }
}

package be.sandervl.jiraharvest.services;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

//@Service
public class JiraService {

    private final RestTemplate restClient;

    public JiraService(Environment environment) {
        restClient = new RestTemplateBuilder()
                .rootUri(environment.getProperty("jira.url"))
                .basicAuthentication(environment.getProperty("jira.username"), environment.getProperty("jira.token"))
                .build();
    }

    public Iterable<BasicIssue> getIssues() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
        return restClient.exchange("/rest/api/2/search?expand=changelog&jql=labels in (HARVEST-Billable, HARVEST-NON-Billable) AND updated >= -1w AND assignee in (currentUser()) ORDER BY created DESC", HttpMethod.GET, requestEntity, new ParameterizedTypeReference<JiraPage<BasicIssue>>() {
        }).getBody().issues;
    }

    public record BasicIssue(String key, BasicIssueFields fields) {
    }

    public record BasicIssueFields(String summary, List<String> labels) {
    }

    public static class JiraPage<T> {
        private int startAt;
        private int maxResults;
        private int total;
        private boolean isLast;
        private List<T> issues;

        public JiraPage() {
        }

        public int getStartAt() {
            return startAt;
        }

        public void setStartAt(int startAt) {
            this.startAt = startAt;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public boolean isLast() {
            return isLast;
        }

        public void setLast(boolean last) {
            isLast = last;
        }

        public List<T> getIssues() {
            return issues;
        }

        public void setIssues(List<T> issues) {
            this.issues = issues;
        }
    }
}

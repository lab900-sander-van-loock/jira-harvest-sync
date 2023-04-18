package be.sandervl.jiraharvest.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JiraService {

    private final RestTemplate restClient;

    public JiraService(Environment environment) {
        restClient = new RestTemplateBuilder()
                .rootUri(environment.getProperty("jira.url"))
                .basicAuthentication(environment.getProperty("jira.username"), environment.getProperty("jira.token"))
                .build();
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");


    public Optional<Duration> getWorkedTimeForIssue(BasicIssue issue) {
        var statusChange = issue.changelog.histories.stream()
                .flatMap(history -> history.items.stream()
                        .filter(item -> item.fieldId() != null
                                && item.fromString() != null
                                && item.toStringJava() != null
                                && item.fieldId().equalsIgnoreCase("status")
                        )
                        .map(item -> new StatusChange(item.fromString(), item.toStringJava(), LocalDateTime.parse(history.created, formatter)))
                ).toList();

        Optional<LocalDateTime> startingTime = statusChange.stream()
                .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("To Do"))
                .min(Comparator.comparing(StatusChange::time))
                .map(StatusChange::time);
        Optional<LocalDateTime> endingTime = statusChange.stream()
                .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("In Progress"))
                .max(Comparator.comparing(StatusChange::time))
                .map(StatusChange::time);
        return startingTime.flatMap(st -> endingTime.map(et -> Duration.between(st,et)));
    }

    public record StatusChange(String from, String to, LocalDateTime time) {
    }

    public Iterable<BasicIssue> getIssues() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
        var currentUser = restClient.exchange("/rest/api/2/myself", HttpMethod.GET, requestEntity, new ParameterizedTypeReference<JiraUser>() {
        }).getBody();

        if (currentUser == null) {
            throw new RuntimeException("Could not get current user");
        }
        List<BasicIssue> issues = restClient.exchange("/rest/api/2/search?expand=changelog&jql=labels in (HARVEST-Billable, HARVEST-NON-Billable) AND updated >= -1w AND statusCategory in (4, 3) ORDER BY updated DESC", HttpMethod.GET, requestEntity, new ParameterizedTypeReference<JiraPage<BasicIssue>>() {
                }).getBody()
                .getIssues()
                .stream()
                .filter(issue -> isCurrentAssigneeOrWasAssigneeInChangelog(currentUser, issue))
                .collect(Collectors.toList());
        return issues;
    }

    private static boolean isCurrentAssigneeOrWasAssigneeInChangelog(JiraUser currentUser, BasicIssue issue) {
        return (issue.fields.assignee() != null && issue.fields().assignee().accountId.equals(currentUser.accountId()))
                || issue.changelog.histories.stream().anyMatch(history -> history.items.stream().anyMatch(item -> {
            return item.fieldId() != null && item.fromString() != null && item.fieldId().equalsIgnoreCase("assignee") && item.from().equals(currentUser.accountId());
        }));
    }

    public record JiraUser(String accountId, String emailAddress, String displayName, String active, String timeZone) {
    }

    public record HistoryItem(String field, String fieldType, String fieldId, String from, String fromString, String to,
                              @JsonProperty("toString") String toStringJava) {
    }

    public record History(String created, List<HistoryItem> items) {
    }

    public record Changelog(List<History> histories) {
    }

    public record BasicIssue(String key, BasicIssueFields fields, Changelog changelog) {
    }

    public record BasicIssueFields(String summary, List<String> labels, JiraUser assignee) {
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

package be.sandervl.jiraharvest.services;

import be.sandervl.jiraharvest.config.JiraConfig;
import be.sandervl.jiraharvest.config.JiraHarvestSyncConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class JiraService {

    private final RestTemplate restClient;
  private final JiraIssueParser jiraIssueParser;
  private final JiraHarvestSyncConfig config;

  public JiraService(
      JiraIssueParser jiraIssueParser, JiraHarvestSyncConfig config, JiraConfig jiraConfig) {
    restClient =
        new RestTemplateBuilder()
            .rootUri(jiraConfig.url())
            .basicAuthentication(jiraConfig.username(), jiraConfig.token())
            .build();
    this.jiraIssueParser = jiraIssueParser;
    this.config = config;
  }

  public Iterable<BasicIssue> getIssues() {
    MultiValueMap<String, String> headers = new HttpHeaders();
    HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
    var currentUser =
        restClient
            .exchange(
                "/rest/api/2/myself",
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<JiraUser>() {})
            .getBody();

    if (currentUser == null) {
      throw new RuntimeException("Could not get current user");
    }
    int daysToGoBack = config.daysToGoBack();
    List<BasicIssue> issues =
        restClient
            .exchange(
                "/rest/api/2/search?expand=changelog&jql=labels in (HARVEST-Billable, HARVEST-NON-Billable) AND updated > -"
                    + daysToGoBack
                    + "d AND statusCategory in (4, 3) ORDER BY updated DESC",
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<JiraPage<BasicIssue>>() {})
            .getBody()
            .getIssues()
            .stream()
            .filter(
                issue ->
                    JiraIssueParser.isCurrentAssigneeOrWasAssigneeInChangelog(currentUser, issue)
                        && jiraIssueParser
                            .getWorkedOnTimeForIssue(issue)
                            .map(
                                d ->
                                    Duration.between(d.atStartOfDay(), LocalDateTime.now()).toDays()
                                        < daysToGoBack)
                            .orElse(false))
            .collect(Collectors.toList());
    return issues;
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

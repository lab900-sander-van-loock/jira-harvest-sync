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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

//@Service
public class HarvestService {

    private final RestTemplate restClient;

    public HarvestService(Environment environment) {
        restClient = new RestTemplateBuilder()
                .rootUri(environment.getProperty("harvest.url"))
                .defaultHeader("Authorization", "Bearer " + environment.getProperty("harvest.token"))
                .defaultHeader("Harvest-Account-ID", environment.getProperty("harvest.account-id"))
                .build();
    }

    public Iterable<ProjectAssignment> getProjectAssignments() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
        return restClient.exchange("/api/v2/users/me/project_assignments", HttpMethod.GET, requestEntity, new ParameterizedTypeReference<HarvestResponse<ProjectAssignment>>() {
        }).getBody().projectAssignments;
    }

    public TimeEntry create(Long projectId, Long taskId, LocalDate spentDate, Double hours, String notes) {
        TimeEntryCreate body = new TimeEntryCreate(
                projectId,
                taskId,
                hours,
                spentDate.format(DateTimeFormatter.ISO_DATE),
                notes
        );
        HttpEntity<TimeEntryCreate> requestEntity = new HttpEntity<>(body, new HttpHeaders());
        return restClient.exchange("/api/v2/time_entries", HttpMethod.POST, requestEntity, new ParameterizedTypeReference<TimeEntry>() {
        }).getBody();
    }

    public record TimeEntryCreate(@JsonProperty("project_id") Long projectId,
                                  @JsonProperty("task_id") Long taskId,
                                  Double hours,
                                  @JsonProperty("spent_date") String spentDate, String notes) {
    }

    public record TimeEntry(Project project, Client client, Task task, String notes) {
    }

    public record ProjectAssignment(Project project, Client client,
                                    @JsonProperty("task_assignments") List<TaskAssignment> taskAssignments) {
    }

    public record Project(Long id, String name) {
    }

    public record Task(Long id, String name) {
    }

    public record Client(Long id, String name) {
    }

    public record TaskAssignment(Task task) {
    }

    public static class HarvestResponse<T> {

        @JsonProperty("project_assignments")
        private List<T> projectAssignments;

        public HarvestResponse() {
        }

        public List<T> getProjectAssignments() {
            return projectAssignments;
        }

        public void setProjectAssignments(List<T> projectAssignments) {
            this.projectAssignments = projectAssignments;
        }
    }
}

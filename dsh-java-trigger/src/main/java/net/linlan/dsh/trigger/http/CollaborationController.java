package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.api.port.ICollaborationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(value={"/api/collaboration"})
public class CollaborationController {
    private final ICollaborationService collaborationService;

    public CollaborationController(ICollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping(value={"/rooms"})
    public Response<Map<String, Object>> createRoom(@RequestBody Map<String, Object> body) {
        try {
            return Response.success(this.collaborationService.createRoom(this.text(body.get("workspaceId")), this.text(body.get("title")), this.text(body.get("objective")), this.text(body.get("orchestrationMode"))));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @GetMapping(value={"/rooms/{roomId}"})
    public Response<Map<String, Object>> roomDetail(@PathVariable(value="roomId") String roomId) {
        try {
            return Response.success(this.collaborationService.roomDetail(roomId));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping(value={"/rooms/{roomId}/participants"})
    public Response<Map<String, Object>> joinRoom(@PathVariable(value="roomId") String roomId, @RequestBody Map<String, Object> body) {
        try {
            return Response.success(this.collaborationService.joinRoom(roomId, this.text(body.get("digitalHumanId")), this.text(body.get("roomRole"))));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @DeleteMapping(value={"/rooms/{roomId}/participants/{digitalHumanId}"})
    public Response<Map<String, Object>> leaveRoom(@PathVariable(value="roomId") String roomId, @PathVariable(value="digitalHumanId") String digitalHumanId) {
        try {
            return Response.success(this.collaborationService.leaveRoom(roomId, digitalHumanId));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping(value={"/rooms/{roomId}/messages"})
    public Response<Map<String, Object>> postMessage(@PathVariable(value="roomId") String roomId, @RequestBody Map<String, Object> body) {
        try {
            List<Object> list;
            List<Object> list2;
            Object object = body.get("mentions");
            if (object instanceof List) {
                List list3 = (List)object;
                list2 = list3.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            } else {
                list2 = List.of();
            }
            List mentions = list2;
            Map<String, String> digitalHumanTokens = this.stringMap(body.get("digitalHumanTokens"));
            Object object2 = body.get("sandboxRoots");
            if (object2 instanceof List) {
                List roots = (List)object2;
                list = roots.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            } else {
                list = List.of();
            }
            List sandboxRoots = list;
            return Response.success(this.collaborationService.postMessage(roomId, this.text(body.get("content")), mentions, this.text(body.get("token")), digitalHumanTokens, this.text(body.get("channelCode")), this.text(body.get("approvalMode")), this.text(body.get("cwd")), sandboxRoots));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping(value={"/tasks/{taskId}/cancel"})
    public Response<Map<String, Object>> cancelTask(@PathVariable(value="taskId") String taskId, @RequestBody(required=false) Map<String, Object> body) {
        try {
            String value;
            Object object;
            String roomId = body != null && (object = body.get("roomId")) instanceof String ? (value = (String)object) : "";
            return Response.success(this.collaborationService.cancelTask(roomId, taskId));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping(value={"/tasks/{taskId}/resume"})
    public Response<Map<String, Object>> resumeTask(@PathVariable(value="taskId") String taskId, @RequestBody(required=false) Map<String, Object> body) {
        try {
            String value;
            Object object;
            String value2;
            Object object2;
            String value3;
            Object object3;
            String value4;
            Object object4;
            String roomId = body != null && (object4 = body.get("roomId")) instanceof String ? (value4 = (String)object4) : "";
            String token = body != null && (object3 = body.get("token")) instanceof String ? (value3 = (String)object3) : null;
            Map digitalHumanTokens = body == null ? Map.of() : this.stringMap(body.get("digitalHumanTokens"));
            String channelCode = body != null && (object2 = body.get("channelCode")) instanceof String ? (value2 = (String)object2) : null;
            String approvalMode = body != null && (object = body.get("approvalMode")) instanceof String ? (value = (String)object) : null;
            return Response.success(this.collaborationService.resumeTask(roomId, taskId, token, digitalHumanTokens, channelCode, approvalMode));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping(value={"/tasks/{taskId}/retry"})
    public Response<Map<String, Object>> retryTask(@PathVariable(value="taskId") String taskId, @RequestBody(required=false) Map<String, Object> body) {
        try {
            String value;
            Object object;
            String value2;
            Object object2;
            String value3;
            Object object3;
            String value4;
            Object object4;
            String roomId = body != null && (object4 = body.get("roomId")) instanceof String ? (value4 = (String)object4) : "";
            String token = body != null && (object3 = body.get("token")) instanceof String ? (value3 = (String)object3) : null;
            Map digitalHumanTokens = body == null ? Map.of() : this.stringMap(body.get("digitalHumanTokens"));
            String channelCode = body != null && (object2 = body.get("channelCode")) instanceof String ? (value2 = (String)object2) : null;
            String approvalMode = body != null && (object = body.get("approvalMode")) instanceof String ? (value = (String)object) : null;
            return Response.success(this.collaborationService.retryTask(roomId, taskId, token, digitalHumanTokens, channelCode, approvalMode));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping(value={"/tasks/{taskId}/reassign"})
    public Response<Map<String, Object>> reassignTask(@PathVariable(value="taskId") String taskId, @RequestBody(required=false) Map<String, Object> body) {
        try {
            String value;
            Object object;
            String value2;
            Object object2;
            String value3;
            Object object3;
            String value4;
            Object object4;
            String value5;
            Object object5;
            String roomId = body != null && (object5 = body.get("roomId")) instanceof String ? (value5 = (String)object5) : "";
            String targetDigitalHumanId = body != null && (object4 = body.get("targetDigitalHumanId")) instanceof String ? (value4 = (String)object4) : "";
            String token = body != null && (object3 = body.get("token")) instanceof String ? (value3 = (String)object3) : null;
            Map digitalHumanTokens = body == null ? Map.of() : this.stringMap(body.get("digitalHumanTokens"));
            String channelCode = body != null && (object2 = body.get("channelCode")) instanceof String ? (value2 = (String)object2) : null;
            String approvalMode = body != null && (object = body.get("approvalMode")) instanceof String ? (value = (String)object) : null;
            return Response.success(this.collaborationService.reassignTask(roomId, taskId, targetDigitalHumanId, token, digitalHumanTokens, channelCode, approvalMode));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @GetMapping(value={"/rooms/{roomId}/events"})
    public Response<List<Map<String, Object>>> listEvents(@PathVariable(value="roomId") String roomId, @RequestParam(value="afterSeq", defaultValue="0") long afterSeq) {
        return Response.success(this.collaborationService.listEvents(roomId, afterSeq));
    }

    @GetMapping(value={"/rooms/{roomId}/events/stream"}, produces={"text/event-stream"})
    public SseEmitter streamEvents(@PathVariable(value="roomId") String roomId, @RequestParam(value="afterSeq", defaultValue="0") long afterSeq) {
        return this.collaborationService.subscribe(roomId, afterSeq);
    }

    private String text(Object value) {
        String string;
        return value instanceof String && !(string = (String)value).isBlank() ? string.trim() : null;
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map)) {
            return Map.of();
        }
        Map raw = (Map)value;
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        raw.forEach((key, entry) -> {
            if (key instanceof String) {
                String textValue;
                String textKey = (String)key;
                if (entry instanceof String && !(textValue = (String)entry).isBlank()) {
                    result.put(textKey, textValue);
                }
            }
        });
        return result;
    }
}


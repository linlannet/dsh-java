package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.api.port.IDigitalHumanService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/digital-humans"})
public class DigitalHumanController {
    private final IDigitalHumanService digitalHumanService;

    public DigitalHumanController(IDigitalHumanService digitalHumanService) {
        this.digitalHumanService = digitalHumanService;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list() {
        return Response.success(this.digitalHumanService.list());
    }

    @GetMapping(value={"/{id}"})
    public Response<Map<String, Object>> get(@PathVariable(value="id") String id) {
        try {
            return Response.success(this.digitalHumanService.get(id));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @PostMapping
    public Response<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return Response.success(this.digitalHumanService.create(body));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError((String)("\u521b\u5efa\u6570\u5b57\u4eba\u5931\u8d25\uff1a" + e.getMessage()));
        }
    }

    @PutMapping(value={"/{id}"})
    public Response<Map<String, Object>> update(@PathVariable(value="id") String id, @RequestBody Map<String, Object> body) {
        try {
            return Response.success(this.digitalHumanService.update(id, body));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }

    @DeleteMapping(value={"/{id}"})
    public Response<Void> delete(@PathVariable(value="id") String id) {
        this.digitalHumanService.delete(id);
        return Response.success(null);
    }

    @PostMapping(value={"/discover"})
    public Response<Map<String, Object>> discover(@RequestBody Map<String, Object> body) {
        String value;
        String value2;
        String value3;
        String value4;
        Object object = body.get("baseUrl");
        String baseUrl = object instanceof String ? (value4 = (String)object) : null;
        Object object2 = body.get("credentialRef");
        String credentialRef = object2 instanceof String ? (value3 = (String)object2) : null;
        Object object3 = body.get("endpointType");
        String endpointType = object3 instanceof String ? (value2 = (String)object3) : "remote-dsh";
        Object object4 = body.get("token");
        String token = object4 instanceof String ? (value = (String)object4) : null;
        return Response.success(this.digitalHumanService.discover(baseUrl, endpointType, credentialRef, token));
    }

    @PostMapping(value={"/{id}/health-check"})
    public Response<Map<String, Object>> healthCheck(@PathVariable(value="id") String id, @RequestBody(required=false) Map<String, Object> body) {
        try {
            String value;
            Object object;
            String token = body != null && (object = body.get("token")) instanceof String ? (value = (String)object) : null;
            return Response.success(this.digitalHumanService.healthCheck(id, token));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument((String)e.getMessage());
        }
    }
}


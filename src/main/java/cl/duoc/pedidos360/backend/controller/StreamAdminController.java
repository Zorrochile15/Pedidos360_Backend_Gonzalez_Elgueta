package cl.duoc.pedidos360.backend.controller;

import cl.duoc.pedidos360.backend.service.StreamAdminService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/streams")
@CrossOrigin(origins = "*")
public class StreamAdminController {

    private final StreamAdminService streamAdminService;

    public StreamAdminController(StreamAdminService streamAdminService) {
        this.streamAdminService = streamAdminService;
    }

    @PostMapping
    public Map<String, String> crearStream(
            @RequestParam String name,
            @RequestParam(defaultValue = "3") int shards) {
        streamAdminService.crearStream(name, shards);
        return Map.of("streamName", name, "status", "CREATING");
    }

    @GetMapping
    public List<String> listarStreams() {
        return streamAdminService.listarStreams();
    }

    @GetMapping("/{name}")
    public Map<String, Object> describirStream(@PathVariable String name) {
        return streamAdminService.describirStream(name);
    }

    @PutMapping("/{name}/shards")
    public Map<String, Object> modificarShards(
            @PathVariable String name,
            @RequestParam int shardCount) {
        streamAdminService.modificarShards(name, shardCount);
        return Map.of("streamName", name, "targetShards", shardCount, "status", "UPDATING");
    }

    @PutMapping("/{name}/retention")
    public Map<String, Object> modificarRetencion(
            @PathVariable String name,
            @RequestParam int hours) {
        streamAdminService.modificarRetencion(name, hours);
        return Map.of("streamName", name, "retentionHours", hours, "status", "UPDATED");
    }

    @DeleteMapping("/{name}")
    public Map<String, String> eliminarStream(@PathVariable String name) {
        streamAdminService.eliminarStream(name);
        return Map.of("streamName", name, "status", "DELETED");
    }

    @GetMapping("/consumers/status")
    public Map<String, Object> inspeccionarConsumidores() {
        return streamAdminService.inspeccionarConsumidores();
    }
}
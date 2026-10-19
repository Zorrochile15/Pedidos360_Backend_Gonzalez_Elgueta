package cl.duoc.pedidos360.admin.controller;

import cl.duoc.pedidos360.admin.service.ColaAdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*")
public class ColaAdminController {

    private final ColaAdminService adminService;

    public ColaAdminController(ColaAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/queues")
    public String crearCola(@RequestParam String name) {
        return adminService.crearCola(name);
    }

    @DeleteMapping("/queues/{name}")
    public void eliminarCola(@PathVariable String name) {
        adminService.eliminarCola(name);
    }

    @GetMapping("/queues/{name}/status")
    public Map<String, String> estadoCola(@PathVariable String name) {
        return adminService.estadoCola(name);
    }

    @PostMapping("/topics")
    public String crearTopico(@RequestParam String name) {
        return adminService.crearTopico(name);
    }

    @DeleteMapping("/topics")
    public void eliminarTopico(@RequestParam String arn) {
        adminService.eliminarTopico(arn);
    }

    @PostMapping("/subscriptions")
    public String suscribir(
            @RequestParam String topicArn,
            @RequestParam String queueArn) {

        return adminService.suscribir(
                topicArn,
                queueArn
        );
    }

    @DeleteMapping("/subscriptions")
    public void eliminarSuscripcion(
            @RequestParam String arn) {

        adminService.eliminarSuscripcion(arn);
    }

    @PostMapping("/consumers/{id}/pause")
    public void pausar(
            @PathVariable String id,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String authorization) {

        adminService.pausarConsumidor(
                id,
                authorization
        );
    }

    @PostMapping("/consumers/{id}/resume")
    public void reanudar(
            @PathVariable String id,
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String authorization) {

        adminService.reanudarConsumidor(
                id,
                authorization
        );
    }

    @PostMapping("/dlq/{dlqName}/reprocess")
    public int reprocesar(
            @PathVariable String dlqName,
            @RequestParam String targetQueue) {

        return adminService.reprocesarDlq(
                dlqName,
                targetQueue
        );
    }

    @DeleteMapping("/dlq/{dlqName}/purge")
    public void purgar(
            @PathVariable String dlqName) {

        adminService.purgarCola(dlqName);
    }
}
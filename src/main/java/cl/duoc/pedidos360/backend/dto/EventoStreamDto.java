package cl.duoc.pedidos360.backend.dto;

public class EventoStreamDto {

    private String eventoId;
    private Long pedidoId;
    private Long localId;
    private Long insumoId;
    private Integer cantidad;
    private String estado;
    private String tipoFlujo;
    private String timestamp;

    public EventoStreamDto() {
    }

    public EventoStreamDto(String eventoId, Long pedidoId, Long localId, Long insumoId, Integer cantidad, String estado, String tipoFlujo, String timestamp) {
        this.eventoId = eventoId;
        this.pedidoId = pedidoId;
        this.localId = localId;
        this.insumoId = insumoId;
        this.cantidad = cantidad;
        this.estado = estado;
        this.tipoFlujo = tipoFlujo;
        this.timestamp = timestamp;
    }

    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public Long getLocalId() { return localId; }
    public void setLocalId(Long localId) { this.localId = localId; }
    public Long getInsumoId() { return insumoId; }
    public void setInsumoId(Long insumoId) { this.insumoId = insumoId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTipoFlujo() { return tipoFlujo; }
    public void setTipoFlujo(String tipoFlujo) { this.tipoFlujo = tipoFlujo; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
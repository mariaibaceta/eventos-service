package com.duoc.eventos_service.dto;

import java.util.List;
import java.util.Map;

public record HistorialResponse(int total, Map<String, Object> filtros, List<TicketResponse> tickets) {
}

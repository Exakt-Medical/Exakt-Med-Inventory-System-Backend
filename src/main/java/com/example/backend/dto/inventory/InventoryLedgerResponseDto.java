package com.example.backend.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLedgerResponseDto {

    private InventoryLedgerHeaderDto header;
    @Builder.Default
    private List<InventoryTransactionResponseDto> entries = new ArrayList<>();
}

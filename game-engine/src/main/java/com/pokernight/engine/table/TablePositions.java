package com.pokernight.engine.table;

import java.util.List;

public record TablePositions(
    String dealerId,
    String smallBlindId,
    String bigBlindId,
    String firstToActPreFlopId,
    String firstToActPostFlopId,
    List<String> clockwisePlayerIds
) {
    public TablePositions {
        clockwisePlayerIds = List.copyOf(clockwisePlayerIds);
    }
}

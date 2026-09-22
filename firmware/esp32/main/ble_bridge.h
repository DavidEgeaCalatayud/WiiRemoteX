#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef void (*ble_bridge_packet_callback_t)(
    const uint8_t *packet,
    size_t packet_length
);

void ble_bridge_init(ble_bridge_packet_callback_t callback);

bool ble_bridge_notify_packet(
    const uint8_t *packet,
    size_t packet_length
);

bool ble_bridge_connected(void);

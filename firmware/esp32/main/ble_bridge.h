#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef void (*ble_bridge_packet_callback_t)(
    const uint8_t *packet,
    size_t packet_length
);

typedef void (*ble_bridge_connection_callback_t)(bool connected);

void ble_bridge_init(
    ble_bridge_packet_callback_t packet_callback,
    ble_bridge_connection_callback_t connection_callback
);

bool ble_bridge_notify_packet(
    const uint8_t *packet,
    size_t packet_length
);

bool ble_bridge_connected(void);

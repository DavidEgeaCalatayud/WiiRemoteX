#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef void (*classic_hid_output_callback_t)(
    uint8_t report_id,
    const uint8_t *payload,
    size_t payload_length
);

typedef void (*classic_hid_connection_callback_t)(bool connected);

void classic_hid_init(
    classic_hid_output_callback_t output_callback,
    classic_hid_connection_callback_t connection_callback
);

bool classic_hid_send_input(
    uint8_t report_id,
    const uint8_t *payload,
    size_t payload_length
);

void classic_hid_set_pairing(bool enabled);

void classic_hid_clear_bonds(void);

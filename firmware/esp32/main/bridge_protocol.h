#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define BRIDGE_PROTOCOL_VERSION 1
#define BRIDGE_HEADER_SIZE 6
#define BRIDGE_MAX_PACKET_SIZE 20
#define BRIDGE_MAX_FRAGMENT_PAYLOAD (BRIDGE_MAX_PACKET_SIZE - BRIDGE_HEADER_SIZE)
#define BRIDGE_MAX_MESSAGE_SIZE 256
#define BRIDGE_MAX_FRAGMENTS     ((BRIDGE_MAX_MESSAGE_SIZE + BRIDGE_MAX_FRAGMENT_PAYLOAD - 1) / BRIDGE_MAX_FRAGMENT_PAYLOAD)

typedef enum {
    BRIDGE_INPUT_REPORT = 0x01,
    BRIDGE_OUTPUT_REPORT = 0x02,
    BRIDGE_STATUS = 0x03,
    BRIDGE_CONTROL = 0x04,
} bridge_message_type_t;

typedef enum {
    BRIDGE_CONTROL_START_WII_PAIRING = 0x01,
    BRIDGE_CONTROL_STOP_WII_PAIRING = 0x02,
    BRIDGE_CONTROL_CLEAR_WII_BOND = 0x03,
} bridge_control_code_t;

typedef enum {
    BRIDGE_STATUS_WII_CONNECTION = 0x01,
    BRIDGE_STATUS_READY = 0x02,
    BRIDGE_STATUS_ERROR = 0x7F,
} bridge_status_code_t;

typedef enum {
    BRIDGE_WII_DISCONNECTED = 0,
    BRIDGE_WII_CONNECTING = 1,
    BRIDGE_WII_CONNECTED = 2,
} bridge_wii_connection_state_t;

typedef struct {
    bridge_message_type_t type;
    uint16_t sequence;
    uint8_t fragment_index;
    uint8_t fragment_count;
    const uint8_t *payload;
    size_t payload_length;
} bridge_fragment_t;

typedef struct {
    bridge_message_type_t type;
    uint16_t sequence;
    const uint8_t *payload;
    size_t payload_length;
} bridge_message_t;

typedef struct {
    bool active;
    bridge_message_type_t type;
    uint16_t sequence;
    uint8_t fragment_count;
    uint32_t received_mask;
    size_t fragment_lengths[BRIDGE_MAX_FRAGMENTS];
    uint8_t buffer[BRIDGE_MAX_MESSAGE_SIZE];
} bridge_reassembler_t;

bool bridge_decode_fragment(
    const uint8_t *packet,
    size_t packet_length,
    bridge_fragment_t *fragment
);

size_t bridge_fragment_count(size_t payload_length);

size_t bridge_encode_fragment(
    bridge_message_type_t type,
    uint16_t sequence,
    const uint8_t *payload,
    size_t payload_length,
    uint8_t fragment_index,
    uint8_t out_packet[BRIDGE_MAX_PACKET_SIZE]
);

void bridge_reassembler_reset(bridge_reassembler_t *reassembler);

bool bridge_reassembler_accept(
    bridge_reassembler_t *reassembler,
    const uint8_t *packet,
    size_t packet_length,
    bridge_message_t *message
);

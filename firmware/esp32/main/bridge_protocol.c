#include "bridge_protocol.h"

#include <string.h>

bool bridge_decode_fragment(
    const uint8_t *packet,
    size_t packet_length,
    bridge_fragment_t *fragment
) {
    if (packet == NULL || fragment == NULL) return false;
    if (packet_length < BRIDGE_HEADER_SIZE || packet_length > BRIDGE_MAX_PACKET_SIZE) return false;
    if (packet[0] != BRIDGE_PROTOCOL_VERSION) return false;
    if (packet[1] < BRIDGE_INPUT_REPORT || packet[1] > BRIDGE_CONTROL) return false;

    const uint8_t fragment_index = packet[4];
    const uint8_t fragment_count = packet[5];

    if (fragment_count == 0 || fragment_count > BRIDGE_MAX_FRAGMENTS) return false;
    if (fragment_index >= fragment_count) return false;

    fragment->type = (bridge_message_type_t)packet[1];
    fragment->sequence = (uint16_t)packet[2] | ((uint16_t)packet[3] << 8);
    fragment->fragment_index = fragment_index;
    fragment->fragment_count = fragment_count;
    fragment->payload = packet + BRIDGE_HEADER_SIZE;
    fragment->payload_length = packet_length - BRIDGE_HEADER_SIZE;
    return true;
}

size_t bridge_fragment_count(size_t payload_length) {
    if (payload_length == 0) return 1;
    return (payload_length + BRIDGE_MAX_FRAGMENT_PAYLOAD - 1) /
        BRIDGE_MAX_FRAGMENT_PAYLOAD;
}

size_t bridge_encode_fragment(
    bridge_message_type_t type,
    uint16_t sequence,
    const uint8_t *payload,
    size_t payload_length,
    uint8_t fragment_index,
    uint8_t out_packet[BRIDGE_MAX_PACKET_SIZE]
) {
    if (out_packet == NULL) return 0;
    if (payload_length > BRIDGE_MAX_MESSAGE_SIZE) return 0;

    const size_t count = bridge_fragment_count(payload_length);
    if (count == 0 || count > BRIDGE_MAX_FRAGMENTS || fragment_index >= count) return 0;

    const size_t start = fragment_index * BRIDGE_MAX_FRAGMENT_PAYLOAD;
    const size_t remaining = payload_length > start ? payload_length - start : 0;
    const size_t chunk = remaining > BRIDGE_MAX_FRAGMENT_PAYLOAD
        ? BRIDGE_MAX_FRAGMENT_PAYLOAD
        : remaining;

    out_packet[0] = BRIDGE_PROTOCOL_VERSION;
    out_packet[1] = (uint8_t)type;
    out_packet[2] = (uint8_t)(sequence & 0xFF);
    out_packet[3] = (uint8_t)((sequence >> 8) & 0xFF);
    out_packet[4] = fragment_index;
    out_packet[5] = (uint8_t)count;

    if (chunk > 0 && payload != NULL) {
        memcpy(out_packet + BRIDGE_HEADER_SIZE, payload + start, chunk);
    }

    return BRIDGE_HEADER_SIZE + chunk;
}

void bridge_reassembler_reset(bridge_reassembler_t *reassembler) {
    if (reassembler != NULL) {
        memset(reassembler, 0, sizeof(*reassembler));
    }
}

bool bridge_reassembler_accept(
    bridge_reassembler_t *reassembler,
    const uint8_t *packet,
    size_t packet_length,
    bridge_message_t *message
) {
    if (reassembler == NULL || message == NULL) return false;

    bridge_fragment_t fragment;
    if (!bridge_decode_fragment(packet, packet_length, &fragment)) return false;

    if (
        !reassembler->active ||
        reassembler->sequence != fragment.sequence ||
        reassembler->type != fragment.type ||
        reassembler->fragment_count != fragment.fragment_count
    ) {
        bridge_reassembler_reset(reassembler);
        reassembler->active = true;
        reassembler->type = fragment.type;
        reassembler->sequence = fragment.sequence;
        reassembler->fragment_count = fragment.fragment_count;
    }

    const size_t offset = fragment.fragment_index * BRIDGE_MAX_FRAGMENT_PAYLOAD;
    if (offset + fragment.payload_length > BRIDGE_MAX_MESSAGE_SIZE) {
        bridge_reassembler_reset(reassembler);
        return false;
    }

    memcpy(
        reassembler->buffer + offset,
        fragment.payload,
        fragment.payload_length
    );
    reassembler->fragment_lengths[fragment.fragment_index] = fragment.payload_length;
    reassembler->received_mask |= (1UL << fragment.fragment_index);

    const uint32_t expected_mask =
        fragment.fragment_count == 32
            ? UINT32_MAX
            : ((1UL << fragment.fragment_count) - 1UL);

    if ((reassembler->received_mask & expected_mask) != expected_mask) {
        return false;
    }

    size_t total = 0;
    for (uint8_t index = 0; index < fragment.fragment_count; ++index) {
        total += reassembler->fragment_lengths[index];
    }

    message->type = reassembler->type;
    message->sequence = reassembler->sequence;
    message->payload = reassembler->buffer;
    message->payload_length = total;

    reassembler->active = false;
    return true;
}

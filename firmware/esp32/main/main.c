#include <stddef.h>
#include <stdint.h>

#include "ble_bridge.h"
#include "bridge_protocol.h"
#include "classic_hid.h"

#include "esp_bt.h"
#include "esp_bt_main.h"
#include "esp_event.h"
#include "esp_log.h"
#include "nvs_flash.h"

static const char *TAG = "wiiremotex_bridge";

#define WIIMOTEX_FW_VERSION_MAJOR 0
#define WIIMOTEX_FW_VERSION_MINOR 6
#define WIIMOTEX_FW_VERSION_PATCH 0

static bridge_reassembler_t s_phone_reassembler;
static uint16_t s_bridge_sequence;
static bridge_wii_connection_state_t s_wii_state = BRIDGE_WII_DISCONNECTED;

static void send_bridge_message(
    bridge_message_type_t type,
    const uint8_t *payload,
    size_t payload_length
) {
    const size_t fragment_count = bridge_fragment_count(payload_length);
    const uint16_t sequence = s_bridge_sequence++;

    for (uint8_t index = 0; index < fragment_count; ++index) {
        uint8_t packet[BRIDGE_MAX_PACKET_SIZE];
        const size_t packet_length = bridge_encode_fragment(
            type,
            sequence,
            payload,
            payload_length,
            index,
            packet
        );

        if (packet_length == 0) {
            ESP_LOGE(TAG, "Unable to encode BLE bridge fragment");
            return;
        }

        if (!ble_bridge_send_packet(packet, packet_length)) {
            ESP_LOGW(TAG, "BLE packet could not be queued for iPhone");
            return;
        }
    }
}

static void send_wii_connection_status(void) {
    const uint8_t payload[] = {
        BRIDGE_STATUS_WII_CONNECTION,
        (uint8_t)s_wii_state,
    };
    send_bridge_message(BRIDGE_STATUS, payload, sizeof(payload));
}

static void send_bridge_ready(void) {
    const uint8_t payload[] = {
        BRIDGE_STATUS_READY,
        BRIDGE_PROTOCOL_VERSION,
        WIIMOTEX_FW_VERSION_MAJOR,
        WIIMOTEX_FW_VERSION_MINOR,
        WIIMOTEX_FW_VERSION_PATCH,
    };
    send_bridge_message(BRIDGE_STATUS, payload, sizeof(payload));
}

static void on_wii_output(
    uint8_t report_id,
    const uint8_t *payload,
    size_t payload_length
) {
    if (payload_length + 1 > BRIDGE_MAX_MESSAGE_SIZE) {
        ESP_LOGE(TAG, "Wii output report too large: %u", (unsigned)payload_length);
        return;
    }

    uint8_t message[BRIDGE_MAX_MESSAGE_SIZE];
    message[0] = report_id;

    if (payload_length > 0 && payload != NULL) {
        for (size_t index = 0; index < payload_length; ++index) {
            message[index + 1] = payload[index];
        }
    }

    send_bridge_message(
        BRIDGE_OUTPUT_REPORT,
        message,
        payload_length + 1
    );
}

static void on_wii_connection(bool connected) {
    s_wii_state =
        connected ? BRIDGE_WII_CONNECTED : BRIDGE_WII_DISCONNECTED;
    send_wii_connection_status();
}

static void handle_control_message(
    const uint8_t *payload,
    size_t payload_length
) {
    if (payload_length == 0) return;

    switch (payload[0]) {
        case BRIDGE_CONTROL_START_WII_PAIRING:
            ESP_LOGI(TAG, "iPhone requested Wii pairing mode");
            if (s_wii_state != BRIDGE_WII_CONNECTED) {
                s_wii_state = BRIDGE_WII_CONNECTING;
                send_wii_connection_status();
            }
            classic_hid_set_pairing(true);
            break;

        case BRIDGE_CONTROL_STOP_WII_PAIRING:
            ESP_LOGI(TAG, "iPhone requested pairing stop");
            classic_hid_set_pairing(false);
            if (s_wii_state != BRIDGE_WII_CONNECTED) {
                s_wii_state = BRIDGE_WII_DISCONNECTED;
                send_wii_connection_status();
            }
            break;

        case BRIDGE_CONTROL_CLEAR_WII_BOND:
            ESP_LOGI(TAG, "iPhone requested Wii bond reset");
            classic_hid_clear_bonds();
            s_wii_state = BRIDGE_WII_DISCONNECTED;
            send_wii_connection_status();
            break;

        default:
            ESP_LOGW(TAG, "Unknown bridge control code: 0x%02X", payload[0]);
            break;
    }
}

static void on_phone_packet(
    const uint8_t *packet,
    size_t packet_length
) {
    bridge_message_t message;

    if (!bridge_reassembler_accept(
        &s_phone_reassembler,
        packet,
        packet_length,
        &message
    )) {
        return;
    }

    switch (message.type) {
        case BRIDGE_INPUT_REPORT:
            if (message.payload_length < 1) return;

            if (!classic_hid_send_input(
                message.payload[0],
                message.payload + 1,
                message.payload_length - 1
            )) {
                ESP_LOGD(
                    TAG,
                    "Wii input report 0x%02X queued while no host is connected",
                    message.payload[0]
                );
            }
            break;

        case BRIDGE_CONTROL:
            handle_control_message(
                message.payload,
                message.payload_length
            );
            break;

        case BRIDGE_OUTPUT_REPORT:
        case BRIDGE_STATUS:
        default:
            ESP_LOGW(
                TAG,
                "Unexpected phone bridge message type: 0x%02X",
                message.type
            );
            break;
    }
}

static void on_phone_connection(bool connected) {
    if (!connected) {
        bridge_reassembler_reset(&s_phone_reassembler);
        return;
    }

    /*
     * CoreBluetooth enables notifications just after characteristic discovery.
     * If these two first notifications race the CCC write they are harmless:
     * the next Wii connection event and every host report will refresh state.
     */
    send_bridge_ready();
    send_wii_connection_status();
}

static void init_nvs(void) {
    esp_err_t result = nvs_flash_init();

    if (
        result == ESP_ERR_NVS_NO_FREE_PAGES ||
        result == ESP_ERR_NVS_NEW_VERSION_FOUND
    ) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        result = nvs_flash_init();
    }

    ESP_ERROR_CHECK(result);
}

static void init_bluetooth_stack(void) {
    esp_bt_controller_config_t controller_config =
        BT_CONTROLLER_INIT_CONFIG_DEFAULT();

    ESP_ERROR_CHECK(esp_bt_controller_init(&controller_config));
    ESP_ERROR_CHECK(esp_bt_controller_enable(ESP_BT_MODE_BTDM));
    ESP_ERROR_CHECK(esp_bluedroid_init());
    ESP_ERROR_CHECK(esp_bluedroid_enable());
}

void app_main(void) {
    ESP_LOGI(
        TAG,
        "Starting WiiRemoteX ESP32 bridge firmware %u.%u.%u",
        WIIMOTEX_FW_VERSION_MAJOR,
        WIIMOTEX_FW_VERSION_MINOR,
        WIIMOTEX_FW_VERSION_PATCH
    );

    init_nvs();

    const esp_err_t event_loop_result = esp_event_loop_create_default();
    if (
        event_loop_result != ESP_OK &&
        event_loop_result != ESP_ERR_INVALID_STATE
    ) {
        ESP_ERROR_CHECK(event_loop_result);
    }

    init_bluetooth_stack();
    bridge_reassembler_reset(&s_phone_reassembler);

    ble_bridge_init(on_phone_packet, on_phone_connection);
    classic_hid_init(on_wii_output, on_wii_connection);

    ESP_LOGI(TAG, "Bridge initialized: iPhone BLE <-> ESP32 <-> Wii Classic HID");
}

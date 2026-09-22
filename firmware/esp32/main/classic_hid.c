#include "classic_hid.h"

#include <stdlib.h>
#include <string.h>

#include "esp_gap_bt_api.h"
#include "esp_hidd.h"
#include "esp_log.h"

static const char *TAG = "wii_classic_hid";

static esp_hidd_dev_t *s_hid_dev;
static classic_hid_output_callback_t s_output_callback;
static classic_hid_connection_callback_t s_connection_callback;

static const uint8_t s_wiimote_report_descriptor[] = {
    0x05, 0x01, 0x09, 0x05, 0xA1, 0x01,

    0x85, 0x10, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x01,
    0x06, 0x00, 0xFF, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x11, 0x95, 0x01, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x12, 0x95, 0x02, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x13, 0x95, 0x01, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x14, 0x95, 0x01, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x15, 0x95, 0x01, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x16, 0x95, 0x15, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x17, 0x95, 0x06, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x18, 0x95, 0x15, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x19, 0x95, 0x01, 0x09, 0x01, 0x91, 0x00,
    0x85, 0x1A, 0x95, 0x01, 0x09, 0x01, 0x91, 0x00,

    0x85, 0x20, 0x95, 0x06, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x21, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x22, 0x95, 0x04, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x30, 0x95, 0x02, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x31, 0x95, 0x05, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x32, 0x95, 0x0A, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x33, 0x95, 0x11, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x34, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x35, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x36, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x37, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x3D, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x3E, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,
    0x85, 0x3F, 0x95, 0x15, 0x09, 0x01, 0x81, 0x00,

    0xC0,
};

static esp_hid_raw_report_map_t s_report_maps[] = {
    {
        .data = s_wiimote_report_descriptor,
        .len = sizeof(s_wiimote_report_descriptor),
    },
};

static esp_hid_device_config_t s_hid_config = {
    .vendor_id = 0x057E,
    .product_id = 0x0306,
    .version = 0x0100,
    .device_name = "Nintendo RVL-CNT-01",
    .manufacturer_name = "Nintendo",
    .serial_number = "WiiRemoteX",
    .report_maps = s_report_maps,
    .report_maps_len = 1,
};

static void hidd_event_callback(
    void *handler_args,
    esp_event_base_t base,
    int32_t id,
    void *event_data
) {
    (void)handler_args;
    (void)base;

    const esp_hidd_event_t event = (esp_hidd_event_t)id;
    esp_hidd_event_data_t *param = (esp_hidd_event_data_t *)event_data;

    switch (event) {
        case ESP_HIDD_START_EVENT:
            if (param->start.status == ESP_OK) {
                ESP_LOGI(TAG, "Classic HID ready");
                classic_hid_set_pairing(true);
            } else {
                ESP_LOGE(TAG, "Classic HID start failed: %d", param->start.status);
            }
            break;

        case ESP_HIDD_CONNECT_EVENT:
            if (param->connect.status == ESP_OK) {
                ESP_LOGI(TAG, "Wii HID host connected");
                classic_hid_set_pairing(false);
                if (s_connection_callback != NULL) {
                    s_connection_callback(true);
                }
            } else {
                ESP_LOGE(TAG, "Classic HID connection failed: %d", param->connect.status);
            }
            break;

        case ESP_HIDD_OUTPUT_EVENT:
            ESP_LOGI(
                TAG,
                "Wii output report 0x%02X len=%u",
                (unsigned)param->output.report_id,
                (unsigned)param->output.length
            );
            if (s_output_callback != NULL) {
                s_output_callback(
                    (uint8_t)param->output.report_id,
                    param->output.data,
                    param->output.length
                );
            }
            break;

        case ESP_HIDD_DISCONNECT_EVENT:
            ESP_LOGI(TAG, "Wii HID host disconnected");
            if (s_connection_callback != NULL) {
                s_connection_callback(false);
            }
            classic_hid_set_pairing(true);
            break;

        case ESP_HIDD_PROTOCOL_MODE_EVENT:
            ESP_LOGI(
                TAG,
                "Protocol mode: %s",
                param->protocol_mode.protocol_mode ? "report" : "boot"
            );
            break;

        case ESP_HIDD_STOP_EVENT:
            ESP_LOGI(TAG, "Classic HID stopped");
            break;

        default:
            break;
    }
}

void classic_hid_init(
    classic_hid_output_callback_t output_callback,
    classic_hid_connection_callback_t connection_callback
) {
    s_output_callback = output_callback;
    s_connection_callback = connection_callback;

    ESP_ERROR_CHECK(esp_bt_gap_set_device_name(s_hid_config.device_name));

    esp_bt_cod_t cod = {0};
    cod.major = ESP_BT_COD_MAJOR_DEV_PERIPHERAL;
    cod.minor = ESP_BT_COD_MINOR_PERIPHERAL_POINTING;
    ESP_ERROR_CHECK(esp_bt_gap_set_cod(cod, ESP_BT_SET_COD_MAJOR_MINOR));

    ESP_ERROR_CHECK(
        esp_hidd_dev_init(
            &s_hid_config,
            ESP_HID_TRANSPORT_BT,
            hidd_event_callback,
            &s_hid_dev
        )
    );
}

bool classic_hid_send_input(
    uint8_t report_id,
    const uint8_t *payload,
    size_t payload_length
) {
    if (s_hid_dev == NULL || payload == NULL) return false;

    const esp_err_t result = esp_hidd_dev_input_set(
        s_hid_dev,
        0,
        report_id,
        (uint8_t *)payload,
        payload_length
    );

    if (result != ESP_OK) {
        ESP_LOGW(
            TAG,
            "Input report 0x%02X not sent: %s",
            report_id,
            esp_err_to_name(result)
        );
        return false;
    }

    return true;
}

void classic_hid_set_pairing(bool enabled) {
    const esp_bt_connection_mode_t connection_mode =
        enabled ? ESP_BT_CONNECTABLE : ESP_BT_NON_CONNECTABLE;
    const esp_bt_discovery_mode_t discovery_mode =
        enabled ? ESP_BT_GENERAL_DISCOVERABLE : ESP_BT_NON_DISCOVERABLE;

    const esp_err_t result =
        esp_bt_gap_set_scan_mode(connection_mode, discovery_mode);

    if (result != ESP_OK) {
        ESP_LOGW(TAG, "Unable to change Classic discovery mode: %s", esp_err_to_name(result));
    }
}

void classic_hid_clear_bonds(void) {
    int count = esp_bt_gap_get_bond_device_num();
    if (count <= 0) {
        ESP_LOGI(TAG, "No Classic Bluetooth bonds to clear");
        return;
    }

    esp_bd_addr_t *devices = calloc((size_t)count, sizeof(esp_bd_addr_t));
    if (devices == NULL) {
        ESP_LOGE(TAG, "Unable to allocate bond list");
        return;
    }

    int requested = count;
    if (esp_bt_gap_get_bond_device_list(&requested, devices) == ESP_OK) {
        for (int index = 0; index < requested; ++index) {
            const esp_err_t result = esp_bt_gap_remove_bond_device(devices[index]);
            if (result != ESP_OK) {
                ESP_LOGW(TAG, "Unable to remove Classic bond %d: %s", index, esp_err_to_name(result));
            }
        }
    }

    free(devices);
    classic_hid_set_pairing(true);
}

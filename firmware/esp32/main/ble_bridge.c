#include "ble_bridge.h"

#include <string.h>

#include "esp_gap_ble_api.h"
#include "esp_gatt_common_api.h"
#include "esp_gatts_api.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"

static const char *TAG = "ios_ble_bridge";

#define BRIDGE_APP_ID 0x52
#define BRIDGE_SERVICE_HANDLES 8
#define BRIDGE_TX_QUEUE_CAPACITY 64
#define BRIDGE_TX_MAX_RETRIES 2

typedef struct {
    uint8_t data[20];
    uint8_t length;
    uint8_t retries;
} bridge_tx_packet_t;

/*
 * ESP-IDF stores custom 128-bit UUID bytes least-significant byte first.
 *
 * Swift UUIDs:
 * service: 7C0A0001-6F4B-4A42-9D47-575258000001
 * phone -> bridge: 7C0A0002-6F4B-4A42-9D47-575258000001
 * bridge -> phone: 7C0A0003-6F4B-4A42-9D47-575258000001
 */
static const uint8_t SERVICE_UUID[16] = {
    0x01, 0x00, 0x00, 0x58, 0x52, 0x57, 0x47, 0x9D,
    0x42, 0x4A, 0x4B, 0x6F, 0x01, 0x00, 0x0A, 0x7C,
};

static const uint8_t PHONE_TO_BRIDGE_UUID[16] = {
    0x01, 0x00, 0x00, 0x58, 0x52, 0x57, 0x47, 0x9D,
    0x42, 0x4A, 0x4B, 0x6F, 0x02, 0x00, 0x0A, 0x7C,
};

static const uint8_t BRIDGE_TO_PHONE_UUID[16] = {
    0x01, 0x00, 0x00, 0x58, 0x52, 0x57, 0x47, 0x9D,
    0x42, 0x4A, 0x4B, 0x6F, 0x03, 0x00, 0x0A, 0x7C,
};

static esp_gatt_if_t s_gatts_if = ESP_GATT_IF_NONE;
static uint16_t s_service_handle;
static uint16_t s_phone_to_bridge_handle;
static uint16_t s_bridge_to_phone_handle;
static uint16_t s_bridge_to_phone_ccc_handle;
static uint16_t s_conn_id;

static bool s_connected;
static bool s_indications_enabled;
static bool s_tx_in_flight;
static bool s_congested;

static bridge_tx_packet_t s_tx_queue[BRIDGE_TX_QUEUE_CAPACITY];
static size_t s_tx_head;
static size_t s_tx_tail;
static size_t s_tx_count;
static portMUX_TYPE s_tx_lock = portMUX_INITIALIZER_UNLOCKED;

static ble_bridge_packet_callback_t s_packet_callback;
static ble_bridge_connection_callback_t s_connection_callback;

static esp_ble_adv_params_t s_adv_params = {
    .adv_int_min = 0x20,
    .adv_int_max = 0x40,
    .adv_type = ADV_TYPE_IND,
    .own_addr_type = BLE_ADDR_TYPE_PUBLIC,
    .channel_map = ADV_CHNL_ALL,
    .adv_filter_policy = ADV_FILTER_ALLOW_SCAN_ANY_CON_ANY,
};

static esp_ble_adv_data_t s_adv_data = {
    .set_scan_rsp = false,
    .include_name = true,
    .include_txpower = false,
    .min_interval = 0x0006,
    .max_interval = 0x0010,
    .appearance = 0x0000,
    .manufacturer_len = 0,
    .p_manufacturer_data = NULL,
    .service_data_len = 0,
    .p_service_data = NULL,
    .service_uuid_len = sizeof(SERVICE_UUID),
    .p_service_uuid = (uint8_t *)SERVICE_UUID,
    .flag = ESP_BLE_ADV_FLAG_GEN_DISC,
};

static bool uuid128_equal(
    const esp_bt_uuid_t *uuid,
    const uint8_t expected[16]
) {
    return uuid != NULL &&
        uuid->len == ESP_UUID_LEN_128 &&
        memcmp(uuid->uuid.uuid128, expected, ESP_UUID_LEN_128) == 0;
}

static esp_bt_uuid_t make_uuid128(const uint8_t value[16]) {
    esp_bt_uuid_t uuid = {
        .len = ESP_UUID_LEN_128,
    };
    memcpy(uuid.uuid.uuid128, value, ESP_UUID_LEN_128);
    return uuid;
}

static void send_write_response(
    esp_gatt_if_t gatts_if,
    esp_ble_gatts_cb_param_t *param
) {
    if (param->write.need_rsp) {
        esp_ble_gatts_send_response(
            gatts_if,
            param->write.conn_id,
            param->write.trans_id,
            ESP_GATT_OK,
            NULL
        );
    }
}

static void reset_tx_queue(void) {
    portENTER_CRITICAL(&s_tx_lock);
    s_tx_head = 0;
    s_tx_tail = 0;
    s_tx_count = 0;
    s_tx_in_flight = false;
    s_congested = false;
    portEXIT_CRITICAL(&s_tx_lock);
}

static bool enqueue_tx_packet(
    const uint8_t *packet,
    size_t packet_length
) {
    bool queued = false;

    portENTER_CRITICAL(&s_tx_lock);
    if (
        s_connected &&
        s_indications_enabled &&
        s_tx_count < BRIDGE_TX_QUEUE_CAPACITY
    ) {
        bridge_tx_packet_t *slot = &s_tx_queue[s_tx_tail];
        memcpy(slot->data, packet, packet_length);
        slot->length = (uint8_t)packet_length;
        slot->retries = 0;

        s_tx_tail = (s_tx_tail + 1U) % BRIDGE_TX_QUEUE_CAPACITY;
        s_tx_count++;
        queued = true;
    }
    portEXIT_CRITICAL(&s_tx_lock);

    return queued;
}

static void advance_tx_head(void) {
    s_tx_head = (s_tx_head + 1U) % BRIDGE_TX_QUEUE_CAPACITY;
    s_tx_count--;
}

static void drain_tx_queue(void) {
    for (;;) {
        bridge_tx_packet_t packet;
        esp_gatt_if_t gatts_if = ESP_GATT_IF_NONE;
        uint16_t conn_id = 0;
        uint16_t handle = 0;
        bool should_send = false;

        portENTER_CRITICAL(&s_tx_lock);
        if (
            s_connected &&
            s_indications_enabled &&
            !s_congested &&
            !s_tx_in_flight &&
            s_tx_count > 0 &&
            s_gatts_if != ESP_GATT_IF_NONE &&
            s_bridge_to_phone_handle != 0
        ) {
            packet = s_tx_queue[s_tx_head];
            gatts_if = s_gatts_if;
            conn_id = s_conn_id;
            handle = s_bridge_to_phone_handle;
            s_tx_in_flight = true;
            should_send = true;
        }
        portEXIT_CRITICAL(&s_tx_lock);

        if (!should_send) {
            return;
        }

        const esp_err_t result = esp_ble_gatts_send_indicate(
            gatts_if,
            conn_id,
            handle,
            packet.length,
            packet.data,
            true
        );

        if (result == ESP_OK) {
            return;
        }

        bool retry = false;
        portENTER_CRITICAL(&s_tx_lock);
        s_tx_in_flight = false;
        if (s_tx_count > 0) {
            bridge_tx_packet_t *head = &s_tx_queue[s_tx_head];
            if (head->retries < BRIDGE_TX_MAX_RETRIES) {
                head->retries++;
                retry = true;
            } else {
                advance_tx_head();
            }
        }
        portEXIT_CRITICAL(&s_tx_lock);

        ESP_LOGW(
            TAG,
            "BLE indication enqueue failed: %s%s",
            esp_err_to_name(result),
            retry ? " (retrying)" : " (dropped after retries)"
        );

        if (!retry) {
            continue;
        }
    }
}

static void gap_event_handler(
    esp_gap_ble_cb_event_t event,
    esp_ble_gap_cb_param_t *param
) {
    (void)param;

    switch (event) {
        case ESP_GAP_BLE_ADV_DATA_SET_COMPLETE_EVT:
            ESP_ERROR_CHECK(esp_ble_gap_start_advertising(&s_adv_params));
            break;

        case ESP_GAP_BLE_ADV_START_COMPLETE_EVT:
            ESP_LOGI(TAG, "BLE advertising started");
            break;

        default:
            break;
    }
}

static void gatts_event_handler(
    esp_gatts_cb_event_t event,
    esp_gatt_if_t gatts_if,
    esp_ble_gatts_cb_param_t *param
) {
    switch (event) {
        case ESP_GATTS_REG_EVT: {
            if (param->reg.status != ESP_GATT_OK) {
                ESP_LOGE(TAG, "GATT app registration failed: %d", param->reg.status);
                return;
            }

            s_gatts_if = gatts_if;
            ESP_ERROR_CHECK(esp_ble_gap_set_device_name("WiiRemoteX Bridge"));
            ESP_ERROR_CHECK(esp_ble_gap_config_adv_data(&s_adv_data));

            esp_gatt_srvc_id_t service_id = {
                .is_primary = true,
                .id = {
                    .inst_id = 0,
                    .uuid = {
                        .len = ESP_UUID_LEN_128,
                    },
                },
            };
            memcpy(
                service_id.id.uuid.uuid.uuid128,
                SERVICE_UUID,
                ESP_UUID_LEN_128
            );

            ESP_ERROR_CHECK(
                esp_ble_gatts_create_service(
                    gatts_if,
                    &service_id,
                    BRIDGE_SERVICE_HANDLES
                )
            );
            break;
        }

        case ESP_GATTS_CREATE_EVT: {
            if (param->create.status != ESP_GATT_OK) {
                ESP_LOGE(TAG, "Bridge service creation failed: %d", param->create.status);
                return;
            }

            s_service_handle = param->create.service_handle;
            ESP_ERROR_CHECK(esp_ble_gatts_start_service(s_service_handle));

            esp_bt_uuid_t tx_uuid = make_uuid128(PHONE_TO_BRIDGE_UUID);
            ESP_ERROR_CHECK(
                esp_ble_gatts_add_char(
                    s_service_handle,
                    &tx_uuid,
                    ESP_GATT_PERM_WRITE,
                    ESP_GATT_CHAR_PROP_BIT_WRITE |
                        ESP_GATT_CHAR_PROP_BIT_WRITE_NR,
                    NULL,
                    NULL
                )
            );
            break;
        }

        case ESP_GATTS_ADD_CHAR_EVT: {
            if (param->add_char.status != ESP_GATT_OK) {
                ESP_LOGE(TAG, "Characteristic creation failed: %d", param->add_char.status);
                return;
            }

            if (uuid128_equal(&param->add_char.char_uuid, PHONE_TO_BRIDGE_UUID)) {
                s_phone_to_bridge_handle = param->add_char.attr_handle;

                esp_bt_uuid_t rx_uuid = make_uuid128(BRIDGE_TO_PHONE_UUID);
                ESP_ERROR_CHECK(
                    esp_ble_gatts_add_char(
                        s_service_handle,
                        &rx_uuid,
                        ESP_GATT_PERM_READ,
                        ESP_GATT_CHAR_PROP_BIT_INDICATE |
                            ESP_GATT_CHAR_PROP_BIT_READ,
                        NULL,
                        NULL
                    )
                );
            } else if (uuid128_equal(&param->add_char.char_uuid, BRIDGE_TO_PHONE_UUID)) {
                s_bridge_to_phone_handle = param->add_char.attr_handle;

                esp_bt_uuid_t ccc_uuid = {
                    .len = ESP_UUID_LEN_16,
                    .uuid = {
                        .uuid16 = ESP_GATT_UUID_CHAR_CLIENT_CONFIG,
                    },
                };

                ESP_ERROR_CHECK(
                    esp_ble_gatts_add_char_descr(
                        s_service_handle,
                        &ccc_uuid,
                        ESP_GATT_PERM_READ | ESP_GATT_PERM_WRITE,
                        NULL,
                        NULL
                    )
                );
            }
            break;
        }

        case ESP_GATTS_ADD_CHAR_DESCR_EVT:
            if (param->add_char_descr.status == ESP_GATT_OK) {
                s_bridge_to_phone_ccc_handle = param->add_char_descr.attr_handle;
                ESP_LOGI(TAG, "BLE bridge GATT service ready");
            }
            break;

        case ESP_GATTS_CONNECT_EVT:
            reset_tx_queue();
            s_connected = true;
            s_indications_enabled = false;
            s_conn_id = param->connect.conn_id;
            ESP_LOGI(TAG, "iPhone BLE central connected; waiting for indication subscription");
            break;

        case ESP_GATTS_DISCONNECT_EVT:
            s_connected = false;
            s_indications_enabled = false;
            reset_tx_queue();
            ESP_LOGI(TAG, "iPhone BLE central disconnected");
            if (s_connection_callback != NULL) {
                s_connection_callback(false);
            }
            ESP_ERROR_CHECK(esp_ble_gap_start_advertising(&s_adv_params));
            break;

        case ESP_GATTS_WRITE_EVT: {
            if (param->write.is_prep) {
                ESP_LOGW(TAG, "Prepared writes are not supported");
                send_write_response(gatts_if, param);
                break;
            }

            if (param->write.handle == s_phone_to_bridge_handle) {
                if (
                    param->write.len > 0 &&
                    param->write.len <= 20 &&
                    s_packet_callback != NULL
                ) {
                    s_packet_callback(param->write.value, param->write.len);
                } else {
                    ESP_LOGW(TAG, "Rejected BLE bridge write len=%u", param->write.len);
                }

                send_write_response(gatts_if, param);
                break;
            }

            if (
                param->write.handle == s_bridge_to_phone_ccc_handle &&
                param->write.len >= 2
            ) {
                const uint16_t ccc =
                    (uint16_t)param->write.value[0] |
                    ((uint16_t)param->write.value[1] << 8);
                const bool was_enabled = s_indications_enabled;
                s_indications_enabled = (ccc & 0x0002) != 0;
                ESP_LOGI(
                    TAG,
                    "BLE bridge indications %s",
                    s_indications_enabled ? "enabled" : "disabled"
                );
                send_write_response(gatts_if, param);

                if (
                    s_connection_callback != NULL &&
                    was_enabled != s_indications_enabled
                ) {
                    s_connection_callback(s_indications_enabled);
                }

                if (s_indications_enabled) {
                    drain_tx_queue();
                }
                break;
            }

            send_write_response(gatts_if, param);
            break;
        }

        case ESP_GATTS_CONF_EVT: {
            bool dropped = false;
            bool retrying = false;

            portENTER_CRITICAL(&s_tx_lock);
            if (s_tx_in_flight && s_tx_count > 0) {
                bridge_tx_packet_t *head = &s_tx_queue[s_tx_head];

                if (param->conf.status == ESP_GATT_OK) {
                    advance_tx_head();
                } else if (head->retries < BRIDGE_TX_MAX_RETRIES) {
                    head->retries++;
                    retrying = true;
                } else {
                    advance_tx_head();
                    dropped = true;
                }

                s_tx_in_flight = false;
            }
            portEXIT_CRITICAL(&s_tx_lock);

            if (param->conf.status != ESP_GATT_OK) {
                ESP_LOGW(
                    TAG,
                    "BLE indication confirmation failed: status=%d%s",
                    param->conf.status,
                    retrying ? " (retrying)" : (dropped ? " (dropped)" : "")
                );
            }

            drain_tx_queue();
            break;
        }

        case ESP_GATTS_CONGEST_EVT:
            portENTER_CRITICAL(&s_tx_lock);
            s_congested = param->congest.congested;
            portEXIT_CRITICAL(&s_tx_lock);

            ESP_LOGW(
                TAG,
                "BLE GATT connection %s",
                param->congest.congested ? "congested" : "recovered"
            );

            if (!param->congest.congested) {
                drain_tx_queue();
            }
            break;

        default:
            break;
    }
}

void ble_bridge_init(
    ble_bridge_packet_callback_t packet_callback,
    ble_bridge_connection_callback_t connection_callback
) {
    s_packet_callback = packet_callback;
    s_connection_callback = connection_callback;

    ESP_ERROR_CHECK(esp_ble_gap_register_callback(gap_event_handler));
    ESP_ERROR_CHECK(esp_ble_gatts_register_callback(gatts_event_handler));
    ESP_ERROR_CHECK(esp_ble_gatts_app_register(BRIDGE_APP_ID));
}

bool ble_bridge_send_packet(
    const uint8_t *packet,
    size_t packet_length
) {
    if (
        packet == NULL ||
        packet_length == 0 ||
        packet_length > 20
    ) {
        return false;
    }

    if (!enqueue_tx_packet(packet, packet_length)) {
        ESP_LOGW(
            TAG,
            "BLE TX queue unavailable or full (depth=%u)",
            (unsigned)s_tx_count
        );
        return false;
    }

    drain_tx_queue();
    return true;
}

bool ble_bridge_connected(void) {
    return s_connected;
}

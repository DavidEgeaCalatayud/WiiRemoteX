idf_component_register(
    SRCS
        "main.c"
        "bridge_protocol.c"
        "ble_bridge.c"
        "classic_hid.c"
    INCLUDE_DIRS "."
    REQUIRES
        bt
        esp_hid
        nvs_flash
        esp_event
)

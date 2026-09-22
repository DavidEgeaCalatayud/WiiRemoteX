package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.protocol.HidInputReport

fun interface HidTransport {
    fun send(report: HidInputReport): Boolean
}
